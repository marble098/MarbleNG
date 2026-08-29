package main

import (
    "context"
    "encoding/json"
    "errors"
    "fmt"
    "io"
    "math"
    "net"
    "net/http"
    "os"
    "sort"
    "strconv"
    "strings"
    "sync"
    "time"

    corenet "github.com/xtls/xray-core/common/net"
    corecommserial "github.com/xtls/xray-core/common/serial"
    core "github.com/xtls/xray-core/core"
    coreserial "github.com/xtls/xray-core/infra/conf/serial"
    "github.com/xtls/xray-core/main/commands/base"
)

const linePrefix = "MARBLE_RANK " // MARBLE_REALTIME_ENGINE_V70

type Job struct {
    ID     string `json:"id"`
    Config string `json:"config"`
}

type Batch struct {
    Workers     int    `json:"workers"`
    TimeoutMS   int    `json:"timeoutMs"`
    PrimaryURL  string `json:"primaryUrl"`
    FallbackURL string `json:"fallbackUrl"`
    Jobs        []Job  `json:"jobs"`
}

type Event struct {
    Event string `json:"event"`; ID string `json:"id"`; OK bool `json:"ok,omitempty"`
    LatencyMS float64 `json:"latencyMs,omitempty"`; WarmupMS float64 `json:"warmupMs,omitempty"`
    P90LatencyMS float64 `json:"p90LatencyMs,omitempty"`; P95LatencyMS float64 `json:"p95LatencyMs,omitempty"`
    JitterMS float64 `json:"jitterMs,omitempty"`; MedianJitterMS float64 `json:"medianJitterMs,omitempty"`
    P95JitterMS float64 `json:"p95JitterMs,omitempty"`; MADLatencyMS float64 `json:"madLatencyMs,omitempty"`
    LossPercent float64 `json:"lossPercent,omitempty"`; SpikePercent float64 `json:"spikePercent,omitempty"`
    Attempts int `json:"attempts,omitempty"`; Samples int `json:"samples,omitempty"`; Target string `json:"target,omitempty"`
    Error string `json:"error,omitempty"`; Jobs int `json:"jobs,omitempty"`; Workers int `json:"workers,omitempty"`
}

var emitMu sync.Mutex

func emit(event Event) {
    payload, err := json.Marshal(event)
    if err != nil {
        return
    }
    emitMu.Lock()
    fmt.Printf("%s%s\n", linePrefix, payload)
    emitMu.Unlock()
}

func minDuration(a, b time.Duration) time.Duration {
    if a <= b {
        return a
    }
    return b
}

func compactError(err error) string {
    if err == nil {
        return ""
    }
    value := strings.ReplaceAll(err.Error(), "\n", " ")
    value = strings.ReplaceAll(value, "\r", " ")
    if len(value) > 220 {
        value = value[:220]
    }
    return value
}

// Based on the architecture used by PattNG/AndroidLibXrayLite:
// load one Xray config in-process, core.Dial through that instance, and time real HTTPS.
// There is no local SOCKS listener and no separate Xray child per node.
func newInstance(configText string) (*core.Instance, error) {
    config, err := coreserial.LoadJSONConfig(strings.NewReader(configText))
    if err != nil {
        return nil, fmt.Errorf("config-load: %w", err)
    }

    config.Inbound = nil

    // Keep only the pieces required by an outbound delay instance, mirroring
    // PattNG's MeasureOutboundDelay protobuf trimming — plus the dns app, which is not optional
    // here: without it Xray installs the system resolver, so the rank path would resolve the node
    // hostname in plaintext and choose an address family by luck while the real tunnel used encrypted
    // DNS with an explicit order. Ranking a node over a different family than Marble dials it makes
    // every learned latency, and every auto-selected route, wrong.
    essential := make([]*corecommserial.TypedMessage, 0, len(config.App))
    for _, app := range config.App {
        if app.Type == "xray.app.proxyman.OutboundConfig" ||
            app.Type == "xray.app.dispatcher.Config" ||
            app.Type == "xray.app.log.Config" ||
            app.Type == "xray.app.dns.Config" {
            essential = append(essential, app)
        }
    }
    config.App = essential

    inst, err := core.New(config)
    if err != nil {
        return nil, fmt.Errorf("core-new: %w", err)
    }
    if err := inst.Start(); err != nil {
        inst.Close()
        return nil, fmt.Errorf("core-start: %w", err)
    }
    return inst, nil
}

func requestDelay(
    client *http.Client,
    target string,
    timeout time.Duration,
) (time.Duration, error) {
    ctx, cancel := context.WithTimeout(context.Background(), timeout)
    defer cancel()

    req, err := http.NewRequestWithContext(ctx, http.MethodGet, target, nil)
    if err != nil {
        return 0, err
    }
    req.Header.Set("Accept", "*/*")
    req.Header.Set("Accept-Encoding", "identity")
    req.Header.Set("Cache-Control", "no-cache")
    req.Header.Set("User-Agent", "MarbleNG-Rank/63")

    started := time.Now()
    resp, err := client.Do(req)
    if err != nil {
        return 0, err
    }
    defer resp.Body.Close()

    if _, err := io.Copy(io.Discard, io.LimitReader(resp.Body, 64*1024)); err != nil {
        return 0, err
    }
    if resp.StatusCode != http.StatusOK && resp.StatusCode != http.StatusNoContent {
        return 0, fmt.Errorf("HTTP %s", resp.Status)
    }
    return time.Since(started), nil
}

func safeMeasure(job Job, primaryURL, fallbackURL string, timeout time.Duration) (event Event) {
    defer func() {
        if recovered := recover(); recovered != nil {
            event = Event{
                Event: "result",
                ID:    job.ID,
                Error: compactError(fmt.Errorf("panic: %v", recovered)),
            }
        }
    }()
    return measure(job, primaryURL, fallbackURL, timeout)
}

func marbleMS(v time.Duration) float64 { return float64(v.Microseconds()) / 1000.0 }
func marbleMedian(v []float64) float64 { if len(v)==0{return 0};x:=append([]float64(nil),v...);sort.Float64s(x);return x[len(x)/2] }
func marblePct(v []float64,q float64) float64 { if len(v)==0{return 0};x:=append([]float64(nil),v...);sort.Float64s(x);i:=int(math.Ceil(float64(len(x))*q))-1;if i<0{i=0};if i>=len(x){i=len(x)-1};return x[i] }
func marbleProbeTarget(client *http.Client,target string,timeout time.Duration)([]time.Duration,int,error){
    samples:=make([]time.Duration,0,3);attempts:=0;var firstErr error
    for i:=0;i<3;i++{attempts++;d,err:=requestDelay(client,target,timeout);if err!=nil{if len(samples)==0{firstErr=err};break};samples=append(samples,d)}
    if len(samples)==0{return nil,attempts,firstErr};return samples,attempts,nil
}
func measure(job Job, primaryURL, fallbackURL string, timeout time.Duration) Event {
    inst,err:=newInstance(job.Config);if err!=nil{return Event{Event:"result",ID:job.ID,Error:compactError(err)}};defer inst.Close()
    transport:=&http.Transport{TLSHandshakeTimeout:minDuration(timeout,6*time.Second),DisableKeepAlives:false,MaxIdleConns:2,MaxIdleConnsPerHost:2,IdleConnTimeout:10*time.Second,
        DialContext:func(ctx context.Context,network,addr string)(net.Conn,error){dest,err:=corenet.ParseDestination(fmt.Sprintf("%s:%s",network,addr));if err!=nil{return nil,err};return core.Dial(ctx,inst,dest)}}
    defer transport.CloseIdleConnections();client:=&http.Client{Transport:transport};target:=primaryURL
    samples,attempts,firstErr:=marbleProbeTarget(client,primaryURL,timeout)
    if len(samples)==0{target=fallbackURL;fallback,fa,ferr:=marbleProbeTarget(client,fallbackURL,timeout);samples=fallback;attempts=fa;if len(samples)==0{return Event{Event:"result",ID:job.ID,Error:compactError(errors.Join(firstErr,ferr))}}}
    ms:=make([]float64,0,len(samples));for _,d:=range samples{ms=append(ms,marbleMS(d))};best:=ms[0];for _,v:=range ms[1:]{if v<best{best=v}}
    deltas:=make([]float64,0,len(ms)-1);for i:=1;i<len(ms);i++{deltas=append(deltas,math.Abs(ms[i]-ms[i-1]))}
    ewma:=0.0;if len(deltas)>0{ewma=deltas[0];for _,v:=range deltas[1:]{ewma=ewma*.75+v*.25}}
    med:=marbleMedian(ms);dev:=make([]float64,0,len(ms));for _,v:=range ms{dev=append(dev,math.Abs(v-med))};mad:=marbleMedian(dev)
    th:=math.Max(15,math.Max(med*.25,mad*3));spikes:=0;for _,v:=range deltas{if v>=th{spikes++}};spikePct:=0.0;if len(deltas)>0{spikePct=float64(spikes)*100/float64(len(deltas))}
    lossPct:=0.0;if attempts>0{lossPct=float64(attempts-len(samples))*100/float64(attempts)}
    return Event{Event:"result",ID:job.ID,OK:true,LatencyMS:best,WarmupMS:ms[0],P90LatencyMS:marblePct(ms,.90),P95LatencyMS:marblePct(ms,.95),
        JitterMS:ewma,MedianJitterMS:marbleMedian(deltas),P95JitterMS:marblePct(deltas,.95),MADLatencyMS:mad,LossPercent:lossPct,SpikePercent:spikePct,Attempts:attempts,Samples:len(samples),Target:target}
}

var cmdMarbleRank = &base.Command{
    UsageLine: "{{.Exec}} marble-rank [batch.json]",
    Short:     "Run MarbleNG batch outbound delay probes",
    Long:      "Runs MarbleNG batch outbound delay probes.",
    CustomFlags: true,
    Run:       executeMarbleRank,
}

func executeMarbleRank(cmd *base.Command, args []string) {
    if len(args) != 1 {
        emit(Event{Event: "fatal", Error: "bad-args"})
        fmt.Fprintln(os.Stderr, "usage: xray marble-rank [batch.json]")
        base.SetExitStatus(2)
        return
    }

    raw, err := os.ReadFile(args[0])
    if err != nil {
        emit(Event{Event: "fatal", Error: compactError(fmt.Errorf("input: %w", err))})
        base.SetExitStatus(2)
        return
    }

    var batch Batch
    if err := json.Unmarshal(raw, &batch); err != nil {
        emit(Event{Event: "fatal", Error: compactError(fmt.Errorf("json: %w", err))})
        base.SetExitStatus(2)
        return
    }

    if len(batch.Jobs) == 0 {
        emit(Event{Event: "batch", Jobs: 0, Workers: 0, OK: true})
        emit(Event{Event: "done", Jobs: 0, Workers: 0, OK: true})
        return
    }
    workers := batch.Workers
    if workers <= 0 {
        workers = 16
    }
    if workers > 32 {
        workers = 32
    }
    if workers > len(batch.Jobs) {
        workers = len(batch.Jobs)
    }

    timeoutMS := batch.TimeoutMS
    if timeoutMS < 1500 {
        timeoutMS = 1500
    }
    if timeoutMS > 12000 {
        timeoutMS = 12000
    }
    timeout := time.Duration(timeoutMS) * time.Millisecond

    if batch.PrimaryURL == "" {
        batch.PrimaryURL = "https://www.gstatic.com/generate_204"
    }
    if batch.FallbackURL == "" {
        batch.FallbackURL = "https://cp.cloudflare.com/generate_204"
    }

    fmt.Fprintf(os.Stderr, "MarbleNG Rank workers=%s jobs=%s timeoutMs=%s\n",
        strconv.Itoa(workers), strconv.Itoa(len(batch.Jobs)), strconv.Itoa(timeoutMS))

    emit(Event{
        Event:   "batch",
        OK:      true,
        Jobs:    len(batch.Jobs),
        Workers: workers,
    })

    sem := make(chan struct{}, workers)
    var wg sync.WaitGroup
    executed := 0

    for _, job := range batch.Jobs {
        job := job
        if job.ID == "" || job.Config == "" {
            continue
        }
        executed++
        wg.Add(1)
        go func() {
            defer wg.Done()
            sem <- struct{}{}
            defer func() { <-sem }()

            emit(Event{Event: "start", ID: job.ID})
            emit(safeMeasure(job, batch.PrimaryURL, batch.FallbackURL, timeout))
        }()
    }

    wg.Wait()
    emit(Event{
        Event:   "done",
        OK:      true,
        Jobs:    executed,
        Workers: workers,
    })
}
