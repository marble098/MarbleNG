package internet

// MARBLE_TCP_INFO_TRUTH_V238 — pins the socket-validity filter behind `jitterPressure`.
//
// The Bug Finder report (P0-1) traced the false pressure verdicts to three kinds of sockets that
// all used to be medianed into the path statistics: dials to loopback (local plumbing with an RTT
// of ~0), sockets still in handshake (rtt=0 with the stack's 250 ms rttvar default), and closed
// sockets. Only an established, non-loopback socket with a kernel-measured RTT is a measurement.

import (
	"testing"

	"golang.org/x/sys/unix"
)

func TestMarbleSocketMeasurementValidFiltersNonPathSockets(t *testing.T) {
	established := &unix.TCPInfo{State: marbleTCPStateEstablished, Rtt: 41_000}
	handshake := &unix.TCPInfo{State: marbleTCPStateSynSent, Rtt: 0}
	closed := &unix.TCPInfo{State: marbleTCPStateClose, Rtt: 41_000}

	cases := []struct {
		name    string
		tracked *marbleTrackedSocket
		info    *unix.TCPInfo
		want    bool
	}{
		{"established remote socket", &marbleTrackedSocket{}, established, true},
		{"loopback dial is never a measurement", &marbleTrackedSocket{loopback: true}, established, false},
		{"handshake socket has no rtt yet", &marbleTrackedSocket{}, handshake, false},
		{"closed socket is not a measurement", &marbleTrackedSocket{}, closed, false},
	}

	for _, testCase := range cases {
		if got := marbleSocketMeasurementValid(testCase.tracked, testCase.info); got != testCase.want {
			t.Fatalf("%s: valid = %v, want %v", testCase.name, got, testCase.want)
		}
	}
}

func TestMarbleIsLoopbackAddress(t *testing.T) {
	cases := []struct {
		address string
		want    bool
	}{
		{"127.0.0.1:10808", true},
		{"127.7.7.7:443", true},
		{"[::1]:53", true},
		{"10.0.0.4:443", false},
		{"192.168.17.4:8080", false},
		{"[2001:db8::1]:443", false},
		{"example.com:443", false},
	}

	for _, testCase := range cases {
		if got := marbleIsLoopbackAddress(testCase.address); got != testCase.want {
			t.Fatalf("loopback(%q) = %v, want %v", testCase.address, got, testCase.want)
		}
	}
}
