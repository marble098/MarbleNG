package internet
// Passive TCP_INFO telemetry for MarbleNG live Xray only. MARBLE_REALTIME_ENGINE_V70
import (
    "encoding/json"
    "os"
    "reflect"
    "sort"
    "sync"
    "time"

    "golang.org/x/sys/unix"
)
type marbleTrackedSocket struct{mu sync.Mutex;lastRetrans uint32}
type marbleTelemetryEvent struct{AtMS int64 `json:"atMs"`;Sockets int `json:"sockets"`;RttMS int `json:"rttMs"`;P95RttMS int `json:"p95RttMs"`;RttVarMS int `json:"rttVarMs"`;RetransDelta int `json:"retransDelta"`;TotalRetrans int `json:"totalRetrans"`;Lost int `json:"lost"`;Unacked int `json:"unacked"`;PMTU int `json:"pmtu"`;MSS int `json:"mss"`;CwndPackets int `json:"cwndPackets"`;PacingBps uint64 `json:"pacingBps"`;DeliveryBps uint64 `json:"deliveryBps"`}
var marbleTelemetryOnce sync.Once;var marbleTelemetryPath string;var marbleSockets sync.Map
func marbleTrackSocket(fd uintptr,network string,_ string){if !isTCPSocket(network){return};path:=os.Getenv("MARBLE_TELEMETRY_FILE");if path==""{return};marbleTelemetryOnce.Do(func(){marbleTelemetryPath=path;go marbleTelemetryLoop()});if marbleTelemetryPath!=""{marbleSockets.Store(int(fd),&marbleTrackedSocket{})}}
func marbleTelemetryLoop(){t:=time.NewTicker(2*time.Second);defer t.Stop();for range t.C{marbleEmitTelemetry()}}
func marblePct(v []int,q float64)int{if len(v)==0{return 0};x:=append([]int(nil),v...);sort.Ints(x);i:=int(float64(len(x))*q+.999999)-1;if i<0{i=0};if i>=len(x){i=len(x)-1};return x[i]}
func marbleMed(v []int)int{if len(v)==0{return 0};x:=append([]int(nil),v...);sort.Ints(x);return x[len(x)/2]}
func marbleOptional(info *unix.TCPInfo,names ...string)uint64{v:=reflect.ValueOf(info);if v.Kind()==reflect.Pointer{v=v.Elem()};for _,n:=range names{f:=v.FieldByName(n);if f.IsValid()&&f.CanUint(){return f.Uint()}};return 0}
func marbleMin(cur,cand int)int{if cand<=0{return cur};if cur<=0||cand<cur{return cand};return cur}
func marbleEmitTelemetry(){if marbleTelemetryPath==""{return};rtts:=[]int{};vars:=[]int{};cwnd:=[]int{};sockets:=0;delta:=0;total:=0;lost:=0;unacked:=0;pmtu:=0;mss:=0;var pacing,delivery uint64
 marbleSockets.Range(func(k,v any)bool{fd,ok:=k.(int);if !ok{marbleSockets.Delete(k);return true};info,err:=unix.GetsockoptTCPInfo(fd,unix.IPPROTO_TCP,unix.TCP_INFO);if err!=nil{marbleSockets.Delete(k);return true};tr,ok:=v.(*marbleTrackedSocket);if !ok{marbleSockets.Delete(k);return true};sockets++;rtts=append(rtts,int(info.Rtt/1000));vars=append(vars,int(info.Rttvar/1000));cwnd=append(cwnd,int(info.Snd_cwnd));total+=int(info.Total_retrans);lost+=int(info.Lost);unacked+=int(info.Unacked);pmtu=marbleMin(pmtu,int(info.Pmtu));mss=marbleMin(mss,int(info.Snd_mss));if x:=marbleOptional(info,"Pacing_rate","PacingRate");x>pacing{pacing=x};if x:=marbleOptional(info,"Delivery_rate","DeliveryRate");x>delivery{delivery=x};tr.mu.Lock();if info.Total_retrans>=tr.lastRetrans{delta+=int(info.Total_retrans-tr.lastRetrans)};tr.lastRetrans=info.Total_retrans;tr.mu.Unlock();return true})
 if sockets==0{return};e:=marbleTelemetryEvent{time.Now().UnixMilli(),sockets,marbleMed(rtts),marblePct(rtts,.95),marbleMed(vars),delta,total,lost,unacked,pmtu,mss,marbleMed(cwnd),pacing,delivery};b,err:=json.Marshal(e);if err!=nil{return};if st,err:=os.Stat(marbleTelemetryPath);err==nil&&st.Size()>512*1024{_ = os.WriteFile(marbleTelemetryPath,nil,0600)};f,err:=os.OpenFile(marbleTelemetryPath,os.O_CREATE|os.O_WRONLY|os.O_APPEND,0600);if err!=nil{return};_,_=f.Write(append(b,'\n'));_=f.Close()}
