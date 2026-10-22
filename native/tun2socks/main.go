//go:build android || linux

// MARBLE_TUN2SOCKS_CORE_V222
// MARBLE_TUN2SOCKS_SHUTDOWN_V223
// MARBLE_FDSAN_DOUBLE_CLOSE_V224
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
//	marble_t2s_run(proxy, mtu, fd, udp_timeout_sec, tcp_send_buffer,
//	               tcp_receive_buffer, moderate, log_level, log_path) -> int
//	marble_t2s_quit()
//	marble_t2s_stop(wait_ms) -> int
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
//
// MARBLE_TUN2SOCKS_SHUTDOWN_V223 — descriptor ownership and teardown order.
//
// The first version of this wrapper closed the TUN descriptor itself, at the end
// of run(), with unix.Close(). That was wrong twice over:
//
//   - the descriptor is not ours. It is the dup of the VpnService device the
//     service holds in `hevFd`, and the service closes it in closeHevFd(). HEV
//     never closes an externally provided descriptor (`tunnel_fini()` returns
//     early when `!tun_fd_local`), which is exactly why the HEV datapath never
//     had this problem and this one did: on the tun2socks datapath the number was
//     closed twice, and the window between the two closes is the few hundred
//     milliseconds the service spends persisting state and posting notifications
//     during disconnect. Any descriptor the app opened in that window could be
//     handed the freed number, and the second close() then destroyed a live,
//     unrelated descriptor — the classic way to end up with a SIGABRT inside
//     libc/ART with no Java stack and no tombstone of our own.
//
//   - gVisor's fdbased endpoint documents the same rule: it "does not take
//     ownership of fd, which must remain open for the lifetime of the returned
//     endpoint (until after the endpoint has stopped being used and Wait
//     returns)". Closing it under a dispatcher that is blocked in ppoll means the
//     dispatcher can observe — and readv() from — whatever descriptor reuses that
//     number next.
//
// So the rule is now: this library never closes the descriptor. It stops the
// inbound dispatchers through the endpoint itself (Attach(nil) writes the stopfd
// and waits for the dispatch loops), closes the gVisor stack, closes the tunnel
// fan-in, and only then reports the datapath as finished — to marble_t2s_stop,
// which is how the service knows it may close the descriptor.
//
// The lifecycle is also a real state machine now. quit() used to clear `running`
// before run() had finished tearing down, so a reconnect that arrived during that
// window started a second stack over the same descriptor and shared one
// statistic manager between two live datapaths. States are idle -> running ->
// stopping -> idle, and start() refuses both a live datapath (-1) and the
// teardown of a previous one (-7).
//
// MARBLE_FDSAN_DOUBLE_CLOSE_V224 — V223's "this library never closes the
// descriptor" was still wrong, and the device proved it: `fdsan: double-close
// of file descriptor 148 detected`, SIGABRT, on the datapath worker, one line
// after the service logged its (single, locked) `fd-close`. The close V223
// missed is not in this file and not in gVisor's endpoint either — gVisor's
// fdbased `Close()` really is a no-op. It is one frame up: `Stack.Wait()`
// removes every NIC with `closeLinkEndpoint=true`, collects
// `deferAct = ep.Close` and runs it, and `ep` is the pinned tun2socks
// `*fdbased.FD`, whose `Close()` is `unix.Close(f.fd)` plus the embedded
// endpoint's no-op. So `st.Wait()` in teardown() closed the service's number
// with a raw syscall — invisible to bionic's fdsan table — and the service's
// subsequent `ParcelFileDescriptor.adoptFd(fd).close()`, which closes *with
// an fdsan owner tag*, got EBADF from the kernel. In bionic that combination
// (tagged close + EBADF) is exactly `android_fdsan_close_with_tag`'s
// "double-close of file descriptor %d detected" abort. One number, two
// closers, again — this time with one of them inside `Wait()`.
//
// So the borrowed descriptor is never handed to anything that can close it.
// start() duplicates it with F_DUPFD_CLOEXEC and everything downstream — the
// fdbased device, the NIC, the stack — sees only the dup, which this library
// owns outright. `Stack.Wait()` may close it (it does, at the pinned
// versions), teardown() may close it as a guarded fallback, an early failure
// may close it: every one of those closes lands on our number, exactly once
// (the pinned FD.Close is guarded by its own `closed` flag), and the number
// the VpnService lent us is still open when it takes it back and closes it —
// once, in closeHevFd(). Two numbers for one TUN device, each with one owner
// and one close. The same borrowed-descriptor rule is applied to the HEV JNI
// bridge (jni/marbleng_jni.c) so a future HEV bump cannot reopen this class
// on the default datapath. docs/FDSAN_DOUBLE_CLOSE_V224.md is the chapter.
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
	"gvisor.dev/gvisor/pkg/tcpip/stack"

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
	codeStopping       = -7
)

// datapathState is the lifecycle of the single datapath this library can own.
// `stopping` exists so a reconnect during teardown is refused with a reason
// instead of opening a second stack over the descriptor the first one is still
// releasing.
type datapathState int

const (
	stateIdle datapathState = iota
	stateRunning
	stateStopping
)

// buildVersion is replaced at link time with the pinned tag.
var buildVersion = "dev"

var (
	mu         sync.Mutex
	state      = stateIdle
	stopCh     chan struct{}
	doneCh     chan struct{}
	liveDevice device.Device
	liveTunnel *tunnel.Tunnel
	liveStack  *stack.Stack
)

// Error text published to marble_t2s_error(). A caller on another thread may be
// inside NewStringUTF() with the pointer this returns, so a slot is only reused
// after errorSlots further messages: freeing the buffer that was just handed out
// (what the first version did) is a use-after-free in the JVM's string decoder.
const errorSlots = 4

var (
	errMu      sync.Mutex
	errText    string
	errBuffers [errorSlots]*C.char
	errSlot    int
	errVersion *C.char
)

func main() {}

func setError(format string, args ...any) string {
	text := fmt.Sprintf(format, args...)
	errMu.Lock()
	errText = text
	slot := errSlot % errorSlots
	if errBuffers[slot] != nil {
		C.free(unsafe.Pointer(errBuffers[slot]))
	}
	errBuffers[slot] = C.CString(text)
	errSlot++
	errMu.Unlock()
	return text
}

func currentError() *C.char {
	errMu.Lock()
	defer errMu.Unlock()
	if errSlot == 0 {
		errBuffers[0] = C.CString(errText)
		errSlot = 1
	}
	return errBuffers[(errSlot-1)%errorSlots]
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

// stopLinkEndpoint stops the inbound path without closing any descriptor.
//
// gVisor's fdbased endpoint stops its dispatchers by writing a stopfd, and its
// Attach(nil) waits for every dispatch loop to leave ppoll/readv before it
// returns. That has to happen before any close: since V224 the device was
// opened on this library's own dup, so device.Close() is allowed to close it —
// but only once no dispatcher is still polling the number, which is what this
// call establishes.
func stopLinkEndpoint(dev device.Device) {
	if dev == nil {
		return
	}
	dev.Attach(nil)
}

// start prepares the stack and returns the channels the caller blocks on. The
// mutex is released before blocking so quit() can always make progress.
func start(proxyRaw string, mtu int, fd int, udpTimeoutSec int, tcpSendBuffer, tcpReceiveBuffer int64, moderate bool, logLevel, logPath string) (int, chan struct{}, chan struct{}) {
	mu.Lock()
	defer mu.Unlock()

	switch state {
	case stateRunning:
		setError("the tun2socks datapath is already running")
		return codeAlreadyRunning, nil, nil
	case stateStopping:
		setError("the previous tun2socks datapath is still shutting down")
		return codeStopping, nil, nil
	}
	if fd < 0 {
		setError("invalid TUN descriptor: %d", fd)
		return codeDescriptor, nil, nil
	}
	if _, err := unix.FcntlInt(uintptr(fd), unix.F_GETFD, 0); err != nil {
		setError("TUN descriptor %d is not open: %v", fd, err)
		return codeDescriptor, nil, nil
	}
	if mtu < 576 || mtu > 65535 {
		setError("invalid MTU: %d", mtu)
		return codeDevice, nil, nil
	}
	if udpTimeoutSec <= 0 {
		udpTimeoutSec = 60
	}

	if err := configureLogger(logLevel, logPath); err != nil {
		setError("logger: %v", err)
		return codeInternal, nil, nil
	}
	t2slog.Infof("[MARBLE] starting tun2socks %s (mtu=%d fd=%d udp=%ds)", buildVersion, mtu, fd, udpTimeoutSec)

	proxy, err := parseProxy(proxyRaw)
	if err != nil {
		setError("%v", err)
		return codeProxy, nil, nil
	}

	// MARBLE_FDSAN_DOUBLE_CLOSE_V224 — the descriptor in `fd` is borrowed from
	// the VpnService, and the stack below closes the descriptor it is given
	// (Stack.Wait() -> pinned FD.Close() -> unix.Close). So the stack is given
	// a dup this library owns, taken here, at the last moment before the
	// device is opened: every refusal above this line returns without having
	// created a number it would have to close, and every close below this line
	// lands on ownedFd, never on fd.
	ownedFd, err := unix.FcntlInt(uintptr(fd), unix.F_DUPFD_CLOEXEC, 0)
	if err != nil {
		setError("duplicate TUN descriptor %d: %v", fd, err)
		return codeDescriptor, nil, nil
	}
	t2slog.Infof("[MARBLE] datapath descriptor: borrowed fd=%d owned dup=%d", fd, ownedFd)

	dev, err := fdbased.Open(strconv.Itoa(ownedFd), uint32(mtu), 0)
	if err != nil {
		// Neither the pinned tun2socks open() nor gVisor's fdbased.New closes
		// the descriptor on failure, so this is the dup's one close.
		_ = unix.Close(ownedFd)
		setError("TUN device: %v", err)
		return codeDevice, nil, nil
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

	liveStack, err = core.CreateStack(&core.Config{
		LinkEndpoint:     dev,
		TransportHandler: transport,
		Options:          opts,
	})
	if err != nil {
		liveStack = nil
		// CreateStack may fail before or after it attached the endpoint to a NIC,
		// so the inbound path is stopped either way. A half-built stack is also
		// discarded without Wait(), so nothing else will ever close the device:
		// dev.Close() here closes this library's own dup (ownedFd) — its one
		// close on this path — while the service's borrowed descriptor stays
		// open for the service to close.
		stopLinkEndpoint(dev)
		dev.Close()
		transport.Close()
		setError("gVisor stack: %v", err)
		return codeStack, nil, nil
	}

	liveDevice = dev
	liveTunnel = transport
	stopCh = make(chan struct{})
	doneCh = make(chan struct{})
	state = stateRunning
	t2slog.Infof("[MARBLE] datapath ready: %s <-> %s", proxyRaw, fdbased.Driver)
	return codeOK, stopCh, doneCh
}

// teardown releases the datapath in the only order that is safe:
//
//  1. the inbound path first, so no handler is entered any more and every
//     dispatch loop has left the descriptor;
//  2. the stack, which aborts the TCP/UDP endpoints the transport handlers are
//     blocked on — and whose Wait() removes each NIC with
//     closeLinkEndpoint=true, i.e. it already calls the device's Close();
//  3. the device, closing this library's own dup: after step 2 that close has
//     normally already happened inside Wait() and the pinned FD.Close() is
//     guarded by its `closed` flag, so this call is a no-op today and the
//     leak-proof fallback if a future stack stops closing link endpoints —
//     never a second close, and never of the caller's descriptor;
//  4. the tunnel fan-in, which is what ends this run.
//
// The caller's descriptor is not closed here: only the dup taken in start()
// is (see the V224 note in the file header).
func teardown(dev device.Device, transport *tunnel.Tunnel, st *stack.Stack) {
	stopLinkEndpoint(dev)
	if st != nil {
		st.Close()
		st.Wait()
	}
	if dev != nil {
		dev.Close()
	}
	if transport != nil {
		transport.Close()
	}
}

//export marble_t2s_run
func marble_t2s_run(cProxy *C.char, mtu C.int, fd C.int, udpTimeout C.int, tcpSendBuffer C.longlong, tcpReceiveBuffer C.longlong, moderate C.int, cLogLevel *C.char, cLogPath *C.char) C.int {
	code, stop, done := start(
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
	state = stateStopping
	dev, transport, st := liveDevice, liveTunnel, liveStack
	liveDevice, liveTunnel, liveStack = nil, nil, nil
	mu.Unlock()

	teardown(dev, transport, st)

	mu.Lock()
	state = stateIdle
	stopCh = nil
	doneCh = nil
	mu.Unlock()
	// Published last: a waiter may close the descriptor the moment this is closed.
	close(done)
	return C.int(codeOK)
}

// signalStop asks run() to tear the datapath down. Only the first call closes the
// channel; a second stop() is a no-op instead of a panic.
func signalStop() chan struct{} {
	mu.Lock()
	defer mu.Unlock()
	if state == stateIdle {
		return nil
	}
	if stopCh != nil {
		close(stopCh)
		stopCh = nil
	}
	return doneCh
}

//export marble_t2s_quit
func marble_t2s_quit() {
	// Signal only: this is called from the service's cleanup path, where blocking
	// is an ANR. A caller that must know the teardown is finished uses
	// marble_t2s_stop with a budget.
	signalStop()
}

//export marble_t2s_stop
func marble_t2s_stop(waitMs C.int) C.int {
	done := signalStop()
	if done == nil {
		return 1
	}
	budget := int(waitMs)
	if budget <= 0 {
		return 0
	}
	timer := time.NewTimer(time.Duration(budget) * time.Millisecond)
	defer timer.Stop()
	select {
	case <-done:
		return 1
	case <-timer.C:
		return 0
	}
}

//export marble_t2s_stats
func marble_t2s_stats(txPackets *C.longlong, txBytes *C.longlong, rxPackets *C.longlong, rxBytes *C.longlong) {
	var connections, up, down int64
	mu.Lock()
	live := state == stateRunning
	mu.Unlock()
	// Only a live datapath is reported. Reading the shared statistic manager after
	// run() has torn the tunnel down would race the teardown for numbers that no
	// longer describe a tunnel the service has.
	if live {
		if snapshot := statistic.DefaultManager.Snapshot(); snapshot != nil {
			connections = int64(len(snapshot.Connections))
			up = snapshot.UploadTotal
			down = snapshot.DownloadTotal
		}
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
	return currentError()
}

//export marble_t2s_version
func marble_t2s_version() *C.char {
	errMu.Lock()
	defer errMu.Unlock()
	if errVersion == nil {
		errVersion = C.CString(buildVersion)
	}
	return errVersion
}
