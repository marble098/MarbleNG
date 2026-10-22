//go:build android || linux

// MARBLE_TUN2SOCKS_CORE_V222
//
// This file is not part of upstream tun2socks. It is copied into the pinned
// tun2socks source tree as `cmd/libmarble/main.go` by scripts/prepare-native.sh
// and built as a c-shared library (`libtun2socks.so`) for every Android ABI.
//
// Why a library instead of the upstream binary: the tunnel has to read the file
// descriptor Android's VpnService created. ProcessBuilder cannot pass a file
// descriptor to a child process, so a separate binary would receive a descriptor
// number that means nothing in its own table. A c-shared library is loaded into
// the app process, so the descriptor the VpnService owns is the descriptor the
// gVisor stack reads.
//
// The exported ABI is deliberately small and never terminates the process:
//
//	marble_t2s_run(proxy, mtu, fd, udp_timeout_sec, log_level, log_path) -> int
//	marble_t2s_quit()
//	marble_t2s_stats(tx_packets, tx_bytes, rx_packets, rx_bytes)
//	marble_t2s_error() -> const char*
//	marble_t2s_version() -> const char*
//
// tun2socks' own engine package calls log.Fatalf() on a start failure, which for
// a library inside the VPN process would be a SIGABRT in the user's face. This
// wrapper composes the same public packages (fdbased device, SOCKS5 proxy,
// tunnel transport, gVisor stack) and returns an error code instead, so a bad
// proxy address or a revoked descriptor is a failed connect with a reason, not a
// crash the system has to recover from.
package main

/*
#include <stdlib.h>
*/
import "C"

import (
	"errors"
	"fmt"
	"net/url"
	"strconv"
	"strings"
	"sync"
	"time"
	"unsafe"

	"go.uber.org/zap"
	"golang.org/x/sys/unix"

	"github.com/xjasonlyu/tun2socks/v2/core"
	"github.com/xjasonlyu/tun2socks/v2/core/device"
	"github.com/xjasonlyu/tun2socks/v2/core/device/fdbased"
	"github.com/xjasonlyu/tun2socks/v2/core/option"
	t2slog "github.com/xjasonlyu/tun2socks/v2/log"
	"github.com/xjasonlyu/tun2socks/v2/proxy/socks5"
	"github.com/xjasonlyu/tun2socks/v2/tunnel"
	"github.com/xjasonlyu/tun2socks/v2/tunnel/statistic"
)

const (
	codeOK             = 0
	codeAlreadyRunning = -1
	codeProxy          = -2
	codeDevice         = -3
	codeStack          = -4
	codeDescriptor     = -5
	codeInternal       = -6
)

// buildVersion is replaced at link time with the pinned tag.
var buildVersion = "dev"

var (
	mu           sync.Mutex
	running      bool
	stopCh       chan struct{}
	liveDevice   device.Device
	liveTunnel   *tunnel.Tunnel
	lastError    string
	lastErrorC   *C.char
	versionC     *C.char
	versionOnce  sync.Once
)

func main() {}

func setError(format string, args ...any) string {
	lastError = fmt.Sprintf(format, args...)
	if lastErrorC != nil {
		C.free(unsafe.Pointer(lastErrorC))
	}
	lastErrorC = C.CString(lastError)
	return lastError
}

func configureLogger(levelText, path string) error {
	level, err := t2slog.ParseLevel(strings.TrimSpace(levelText))
	if err != nil {
		level = t2slog.WarnLevel
	}
	if level > t2slog.FatalLevel {
		t2slog.SetLogger(zap.NewNop())
		return nil
	}
	if strings.TrimSpace(path) == "" {
		logger, err := t2slog.NewLeveled(level)
		if err != nil {
			return err
		}
		t2slog.SetLogger(logger)
		return nil
	}
	cfg := zap.NewProductionConfig()
	cfg.Level = zap.NewAtomicLevelAt(level)
	cfg.OutputPaths = []string{path}
	cfg.ErrorOutputPaths = []string{path}
	logger, err := cfg.Build()
	if err != nil {
		return err
	}
	t2slog.SetLogger(logger)
	return nil
}

func parseProxy(raw string) (*socks5.Socks5, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" {
		return nil, errors.New("empty proxy address")
	}
	if !strings.Contains(raw, "://") {
		raw = "socks5://" + raw
	}
	u, err := url.Parse(raw)
	if err != nil {
		return nil, fmt.Errorf("parse proxy: %w", err)
	}
	if !strings.EqualFold(u.Scheme, "socks5") && !strings.EqualFold(u.Scheme, "socks") {
		return nil, fmt.Errorf("unsupported proxy scheme %q", u.Scheme)
	}
	if u.Host == "" {
		return nil, errors.New("proxy address has no host:port")
	}
	user, pass := "", ""
	if u.User != nil {
		user = u.User.Username()
		pass, _ = u.User.Password()
	}
	return socks5.New(u.Host, user, pass)
}

// start prepares the stack and returns the channel the caller blocks on. The
// mutex is released before blocking so quit() can always make progress.
func start(proxyRaw string, mtu int, fd int, udpTimeoutSec int, tcpSendBuffer, tcpReceiveBuffer int64, moderate bool, logLevel, logPath string) (int, chan struct{}) {
	mu.Lock()
	defer mu.Unlock()

	if running {
		setError("the tun2socks datapath is already running")
		return codeAlreadyRunning, nil
	}
	if fd < 0 {
		setError("invalid TUN descriptor: %d", fd)
		return codeDescriptor, nil
	}
	if _, err := unix.FcntlInt(uintptr(fd), unix.F_GETFD, 0); err != nil {
		setError("TUN descriptor %d is not open: %v", fd, err)
		return codeDescriptor, nil
	}
	if mtu < 576 || mtu > 65535 {
		setError("invalid MTU: %d", mtu)
		return codeDevice, nil
	}
	if udpTimeoutSec <= 0 {
		udpTimeoutSec = 60
	}

	if err := configureLogger(logLevel, logPath); err != nil {
		setError("logger: %v", err)
		return codeInternal, nil
	}
	t2slog.Infof("[MARBLE] starting tun2socks %s (mtu=%d fd=%d udp=%ds)", buildVersion, mtu, fd, udpTimeoutSec)

	proxy, err := parseProxy(proxyRaw)
	if err != nil {
		setError("%v", err)
		return codeProxy, nil
	}

	dev, err := fdbased.Open(strconv.Itoa(fd), uint32(mtu), 0)
	if err != nil {
		setError("TUN device: %v", err)
		return codeDevice, nil
	}

	transport := tunnel.New(proxy, statistic.DefaultManager)
	transport.SetUDPTimeout(time.Duration(udpTimeoutSec) * time.Second)
	transport.ProcessAsync()

	var opts []option.Option
	if moderate {
		opts = append(opts, option.WithTCPModerateReceiveBuffer(true))
	}
	if tcpSendBuffer > 0 {
		opts = append(opts, option.WithTCPSendBufferSize(int(tcpSendBuffer)))
	}
	if tcpReceiveBuffer > 0 {
		opts = append(opts, option.WithTCPReceiveBufferSize(int(tcpReceiveBuffer)))
	}

	stack, err := core.CreateStack(&core.Config{
		LinkEndpoint:     dev,
		TransportHandler: transport,
		Options:          opts,
	})
	if err != nil {
		transport.Close()
		dev.Close()
		setError("gVisor stack: %v", err)
		return codeStack, nil
	}
	_ = stack

	liveDevice = dev
	liveTunnel = transport
	stopCh = make(chan struct{})
	running = true
	t2slog.Infof("[MARBLE] datapath ready: %s <-> %s", proxyRaw, fdbased.Driver)
	return codeOK, stopCh
}

//export marble_t2s_run
func marble_t2s_run(cProxy *C.char, mtu C.int, fd C.int, udpTimeout C.int, tcpSendBuffer C.longlong, tcpReceiveBuffer C.longlong, moderate C.int, cLogLevel *C.char, cLogPath *C.char) C.int {
	code, stop := start(
		C.GoString(cProxy),
		int(mtu),
		int(fd),
		int(udpTimeout),
		int64(tcpSendBuffer),
		int64(tcpReceiveBuffer),
		moderate != 0,
		C.GoString(cLogLevel),
		C.GoString(cLogPath),
	)
	if code != codeOK {
		return C.int(code)
	}
	<-stop
	mu.Lock()
	dev, transport := liveDevice, liveTunnel
	liveDevice, liveTunnel = nil, nil
	running = false
	mu.Unlock()
	// The packages are closed here, not in quit(): by the time the Java side has
	// asked to stop, the descriptor is about to be revoked, and a read that never
	// returns must not keep this goroutine alive for the next session.
	if dev != nil {
		dev.Close()
	}
	if transport != nil {
		transport.Close()
	}
	return C.int(codeOK)
}

//export marble_t2s_quit
func marble_t2s_quit() {
	mu.Lock()
	stop := stopCh
	stopCh = nil
	running = false
	mu.Unlock()
	if stop != nil {
		// Closing the channel releases marble_t2s_run, which owns the teardown.
		// quit() must return immediately: it is called from the app's cleanup
		// path and blocking there is an ANR. Only this function closes it, so a
		// second quit() is a no-op instead of a panic.
		close(stop)
	}
}

//export marble_t2s_stats
func marble_t2s_stats(txPackets *C.longlong, txBytes *C.longlong, rxPackets *C.longlong, rxBytes *C.longlong) {
	var connections, up, down int64
	if snapshot := statistic.DefaultManager.Snapshot(); snapshot != nil {
		connections = int64(len(snapshot.Connections))
		up = snapshot.UploadTotal
		down = snapshot.DownloadTotal
	}
	if txPackets != nil {
		*txPackets = C.longlong(connections)
	}
	if txBytes != nil {
		*txBytes = C.longlong(up)
	}
	if rxPackets != nil {
		*rxPackets = C.longlong(0)
	}
	if rxBytes != nil {
		*rxBytes = C.longlong(down)
	}
}

//export marble_t2s_error
func marble_t2s_error() *C.char {
	if lastErrorC == nil {
		lastErrorC = C.CString(lastError)
	}
	return lastErrorC
}

//export marble_t2s_version
func marble_t2s_version() *C.char {
	versionOnce.Do(func() { versionC = C.CString(buildVersion) })
	return versionC
}
