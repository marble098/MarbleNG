package internet

// Passive TCP_INFO telemetry for MarbleNG's live Xray process only.
// MARBLE_REALTIME_ENGINE_V70

import (
	"encoding/json"
	"os"
	"reflect"
	"sort"
	"sync"
	"time"

	"golang.org/x/sys/unix"
)

type marbleTrackedSocket struct {
	mu          sync.Mutex
	lastRetrans uint32
	lastLost    uint32
}

type marbleTelemetryEvent struct {
	AtMS         int64  `json:"atMs"`
	Sockets      int    `json:"sockets"`
	RttMS        int    `json:"rttMs"`
	P95RttMS     int    `json:"p95RttMs"`
	RttVarMS     int    `json:"rttVarMs"`
	RetransDelta int    `json:"retransDelta"`
	TotalRetrans int    `json:"totalRetrans"`
	Lost         int    `json:"lost"`
	LostDelta    int    `json:"lostDelta"`
	Unacked      int    `json:"unacked"`
	PMTU         int    `json:"pmtu"`
	MSS          int    `json:"mss"`
	CwndPackets  int    `json:"cwndPackets"`
	PacingBps    uint64 `json:"pacingBps"`
	DeliveryBps  uint64 `json:"deliveryBps"`
}

var marbleTelemetryOnce sync.Once
var marbleTelemetryPath string
var marbleSockets sync.Map

func marbleTrackSocket(fd uintptr, network string, _ string) {
	if !isTCPSocket(network) {
		return
	}
	path := os.Getenv("MARBLE_TELEMETRY_FILE")
	if path == "" {
		return
	}
	marbleTelemetryOnce.Do(func() {
		marbleTelemetryPath = path
		go marbleTelemetryLoop()
	})
	if marbleTelemetryPath != "" {
		marbleSockets.Store(int(fd), &marbleTrackedSocket{})
	}
}

func marbleTelemetryLoop() {
	ticker := time.NewTicker(2 * time.Second)
	defer ticker.Stop()
	for range ticker.C {
		marbleEmitTelemetry()
	}
}

func marblePct(values []int, quantile float64) int {
	if len(values) == 0 {
		return 0
	}
	ordered := append([]int(nil), values...)
	sort.Ints(ordered)
	index := int(float64(len(ordered))*quantile + 0.999999) - 1
	if index < 0 {
		index = 0
	}
	if index >= len(ordered) {
		index = len(ordered) - 1
	}
	return ordered[index]
}

func marbleMed(values []int) int {
	if len(values) == 0 {
		return 0
	}
	ordered := append([]int(nil), values...)
	sort.Ints(ordered)
	return ordered[len(ordered)/2]
}

func marbleCounter(value uint32) int {
	maxInt := int(^uint(0) >> 1)
	if uint64(value) > uint64(maxInt) {
		return maxInt
	}
	return int(value)
}

func marbleSaturatingAdd(total, value int) int {
	maxInt := int(^uint(0) >> 1)
	if value <= 0 {
		return total
	}
	if total > maxInt-value {
		return maxInt
	}
	return total + value
}

func marbleOptional(info unix.TCPInfo, names ...string) uint64 {
	value := reflect.ValueOf(info)
	if value.Kind() == reflect.Pointer {
		value = value.Elem()
	}
	for _, name := range names {
		field := value.FieldByName(name)
		if field.IsValid() && field.CanUint() {
			return field.Uint()
		}
	}
	return 0
}

func marbleEmitTelemetry() {
	if marbleTelemetryPath == "" {
		return
	}

	rtts := make([]int, 0, 16)
	variances := make([]int, 0, 16)
	congestionWindows := make([]int, 0, 16)
	pmtus := make([]int, 0, 16)
	msses := make([]int, 0, 16)
	sockets, delta, total, lost, lostDelta, unacked := 0, 0, 0, 0, 0, 0
	var pacing, delivery uint64

	marbleSockets.Range(func(key, value any) bool {
		fd, ok := key.(int)
		if !ok {
			marbleSockets.Delete(key)
			return true
		}
		info, err := unix.GetsockoptTCPInfo(fd, unix.IPPROTO_TCP, unix.TCP_INFO)
		if err != nil {
			// Closed descriptors are removed. If Android reuses the descriptor, the next dial's
			// marbleTrackSocket call installs a fresh counter baseline.
			marbleSockets.Delete(key)
			return true
		}
		tracked, ok := value.(*marbleTrackedSocket)
		if !ok {
			marbleSockets.Delete(key)
			return true
		}

		sockets++
		if info.Rtt > 0 {
			rtts = append(rtts, int(info.Rtt/1000))
		}
		if info.Rttvar > 0 {
			variances = append(variances, int(info.Rttvar/1000))
		}
		if info.Snd_cwnd > 0 {
			congestionWindows = append(congestionWindows, marbleCounter(info.Snd_cwnd))
		}
		// TCP_INFO.Pmtu can report 65535 when the kernel has no learned path MTU. Do not let that
		// sentinel dominate a real 1500/1280 observation or make it look like a tunnel size.
		pmtu := int(info.Pmtu)
		if pmtu >= 1280 && pmtu <= 9000 {
			pmtus = append(pmtus, pmtu)
		}
		mss := int(info.Snd_mss)
		if mss > 0 && mss <= 9000 {
			msses = append(msses, mss)
		}
		total = marbleSaturatingAdd(total, marbleCounter(info.Total_retrans))
		lost = marbleSaturatingAdd(lost, marbleCounter(info.Lost))
		unacked = marbleSaturatingAdd(unacked, marbleCounter(info.Unacked))
		if rate := marbleOptional(info, "Pacing_rate", "PacingRate"); rate > pacing {
			pacing = rate
		}
		if rate := marbleOptional(info, "Delivery_rate", "DeliveryRate"); rate > delivery {
			delivery = rate
		}

		tracked.mu.Lock()
		if info.Total_retrans >= tracked.lastRetrans {
			delta = marbleSaturatingAdd(delta, marbleCounter(info.Total_retrans-tracked.lastRetrans))
		} else {
			// Descriptor reuse or uint32 wrap reset the kernel counter. Count the new socket's
			// current value instead of freezing retransDelta at zero forever.
			delta = marbleSaturatingAdd(delta, marbleCounter(info.Total_retrans))
		}
		tracked.lastRetrans = info.Total_retrans
		if info.Lost >= tracked.lastLost {
			lostDelta = marbleSaturatingAdd(lostDelta, marbleCounter(info.Lost-tracked.lastLost))
		} else {
			// The socket counter wrapped or the descriptor was reused; rebase instead of
			// carrying a negative/stale delta into the stress policy.
			lostDelta = marbleSaturatingAdd(lostDelta, marbleCounter(info.Lost))
		}
		tracked.lastLost = info.Lost
		tracked.mu.Unlock()
		return true
	})

	if sockets == 0 {
		return
	}
	event := marbleTelemetryEvent{
		AtMS:         time.Now().UnixMilli(),
		Sockets:      sockets,
		RttMS:        marbleMed(rtts),
		P95RttMS:     marblePct(rtts, 0.95),
		RttVarMS:     marbleMed(variances),
		RetransDelta: delta,
		TotalRetrans: total,
		Lost:         lost,
		LostDelta:    lostDelta,
		Unacked:      unacked,
		PMTU:         marbleMed(pmtus),
		MSS:          marbleMed(msses),
		CwndPackets:  marbleMed(congestionWindows),
		PacingBps:    pacing,
		DeliveryBps:  delivery,
	}
	line, err := json.Marshal(event)
	if err != nil {
		return
	}
	if stat, err := os.Stat(marbleTelemetryPath); err == nil && stat.Size() > 512*1024 {
		_ = os.WriteFile(marbleTelemetryPath, nil, 0600)
	}
	file, err := os.OpenFile(marbleTelemetryPath, os.O_CREATE|os.O_WRONLY|os.O_APPEND, 0600)
	if err != nil {
		return
	}
	_, _ = file.Write(append(line, '\n'))
	_ = file.Close()
}
