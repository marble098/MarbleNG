package main

import (
    "context"
    "encoding/json"
    "errors"
    "fmt"
    "io"
    "net"
    "net/http"
    "os"
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

const linePrefix = "MARBLE_RANK "

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
    Event      string  `json:"event"`
    ID         string  `json:"id"`
    OK         bool    `json:"ok,omitempty"`
    LatencyMS  float64 `json:"latencyMs,omitempty"`
    WarmupMS   float64 `json:"warmupMs,omitempty"`
    JitterMS   float64 `json:"jitterMs,omitempty"`
    Samples    int     `json:"samples,omitempty"`
    Target     string  `json:"target,omitempty"`
    Error      string  `json:"error,omitempty"`
    Jobs       int     `json:"jobs,omitempty"`
    Workers    int     `json:"workers,omitempty"`
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
    // PattNG's MeasureOutboundDelay protobuf trimming.
    essential := make([]*corecommserial.TypedMessage, 0, len(config.App))
    for _, app := range config.App {
        if app.Type == "xray.app.proxyman.OutboundConfig" ||
            app.Type == "xray.app.dispatcher.Config" ||
            app.Type == "xray.app.log.Config" {
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

func measure(job Job, primaryURL, fallbackURL string, timeout time.Duration) Event {
    inst, err := newInstance(job.Config)
    if err != nil {
        return Event{Event: "result", ID: job.ID, Error: compactError(err)}
    }
    defer inst.Close()

    transport := &http.Transport{
        TLSHandshakeTimeout: minDuration(timeout, 6*time.Second),
        DisableKeepAlives:   false,
        MaxIdleConns:        2,
        MaxIdleConnsPerHost: 2,
        IdleConnTimeout:     10 * time.Second,
        DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
            dest, err := corenet.ParseDestination(fmt.Sprintf("%s:%s", network, addr))
            if err != nil {
                return nil, err
            }
            return core.Dial(ctx, inst, dest)
        },
    }
    defer transport.CloseIdleConnections()
    client := &http.Client{Transport: transport}

    samples := make([]time.Duration, 0, 2)
    targetUsed := primaryURL
    first, firstErr := requestDelay(client, primaryURL, timeout)
    if firstErr == nil {
        samples = append(samples, first)
        // PattNG keeps the minimum of two valid attempts. Reuse the same core and
        // HTTP transport so the second sample costs almost nothing on healthy nodes.
        if second, err := requestDelay(client, primaryURL, timeout); err == nil {
            samples = append(samples, second)
        }
    } else {
        // A provider-specific Google reset must not become "node dead".
        // The fallback is attempted inside the SAME Xray instance: no respawn/retry sweep.
        targetUsed = fallbackURL
        if fallback, err := requestDelay(client, fallbackURL, timeout); err == nil {
            samples = append(samples, fallback)
        } else {
            return Event{
                Event: "result",
                ID:    job.ID,
                Error: compactError(errors.Join(firstErr, err)),
            }
        }
    }

    if len(samples) == 0 {
        return Event{Event: "result", ID: job.ID, Error: "no-valid-https-sample"}
    }

    best := samples[0]
    for _, sample := range samples[1:] {
        if sample < best {
            best = sample
        }
    }

    jitter := time.Duration(0)
    if len(samples) >= 2 {
        if samples[0] >= samples[1] {
            jitter = samples[0] - samples[1]
        } else {
            jitter = samples[1] - samples[0]
        }
    }

    return Event{
        Event:     "result",
        ID:        job.ID,
        OK:        true,
        LatencyMS: float64(best.Microseconds()) / 1000.0,
        WarmupMS:  float64(samples[0].Microseconds()) / 1000.0,
        JitterMS:  float64(jitter.Microseconds()) / 1000.0,
        Samples:   len(samples),
        Target:    targetUsed,
    }
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

    for _, job := range batch.Jobs {
        job := job
        if job.ID == "" || job.Config == "" {
            continue
        }
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
        Jobs:    len(batch.Jobs),
        Workers: workers,
    })
}
