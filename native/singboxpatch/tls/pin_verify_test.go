// MARBLE_SINGBOX_PINNED_PEER_V164 — regression tests for the pinning verifier that
// scripts/inject-singbox-tls-pinning.py injects into the pinned sing-box extended core.
//
// These tests reproduce Xray's pinnedPeerCertSha256 / verifyPeerCertByName contract on any
// host, with a self-signed CA + leaf generated in-memory: the exact condition a pinning server
// (vps1.maje.eu.org behind sni=spotify.com) presents, which the system CA store can never
// validate. CI runs this package before any ABI is compiled, so a regression in the verifier
// dies here instead of shipping a core that connects without Internet.
package tls

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"math/big"
	"strings"
	"testing"
	"time"
)

type pinFixture struct {
	ca         *x509.Certificate
	caDER      []byte
	leaf       *x509.Certificate
	leafDER    []byte
	leafHash   []byte
	caHash     []byte
	otherHash  []byte
	serverName string
	caPool     *x509.CertPool
}

func newPinFixture(t *testing.T) pinFixture {
	t.Helper()
	now := time.Now()
	caKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	caTemplate := &x509.Certificate{
		SerialNumber:          big.NewInt(1),
		Subject:               pkix.Name{CommonName: "Marble Pin Test CA"},
		NotBefore:             now.Add(-time.Hour),
		NotAfter:              now.Add(24 * time.Hour),
		IsCA:                  true,
		BasicConstraintsValid: true,
		KeyUsage:              x509.KeyUsageCertSign | x509.KeyUsageDigitalSignature,
	}
	caDER, err := x509.CreateCertificate(rand.Reader, caTemplate, caTemplate, &caKey.PublicKey, caKey)
	if err != nil {
		t.Fatal(err)
	}
	ca, err := x509.ParseCertificate(caDER)
	if err != nil {
		t.Fatal(err)
	}

	leafKey, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatal(err)
	}
	leafTemplate := &x509.Certificate{
		SerialNumber: big.NewInt(2),
		Subject:      pkix.Name{CommonName: "vps1.example.org"},
		DNSNames:     []string{"vps1.example.org"},
		NotBefore:    now.Add(-time.Hour),
		NotAfter:     now.Add(24 * time.Hour),
		KeyUsage:     x509.KeyUsageDigitalSignature,
		ExtKeyUsage:  []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
	}
	leafDER, err := x509.CreateCertificate(rand.Reader, leafTemplate, ca, &leafKey.PublicKey, caKey)
	if err != nil {
		t.Fatal(err)
	}
	leaf, err := x509.ParseCertificate(leafDER)
	if err != nil {
		t.Fatal(err)
	}

	leafHash := sha256.Sum256(leaf.Raw)
	caHash := sha256.Sum256(ca.Raw)
	other := sha256.Sum256([]byte("not a certificate"))
	pool := x509.NewCertPool()
	pool.AddCert(ca)
	return pinFixture{
		ca: ca, caDER: caDER, leaf: leaf, leafDER: leafDER,
		leafHash: leafHash[:], caHash: caHash[:], otherHash: other[:],
		serverName: "vps1.example.org", caPool: pool,
	}
}

func (f pinFixture) chain() [][]byte {
	return [][]byte{f.leafDER, f.caDER}
}

func TestPinLeafHashAcceptsThePeerWithoutAnyName(t *testing.T) {
	f := newPinFixture(t)
	err := VerifyPeerCertPin([][]byte{f.leafHash}, nil, "", nil, nil, f.chain())
	if err != nil {
		t.Fatalf("leaf pin must accept the peer: %v", err)
	}
}

func TestPinLeafHashMismatchIsRejected(t *testing.T) {
	f := newPinFixture(t)
	err := VerifyPeerCertPin([][]byte{f.otherHash}, nil, "", nil, nil, f.chain())
	if err == nil || !strings.Contains(err.Error(), "unrecognized") {
		t.Fatalf("mismatched leaf pin must be rejected as unrecognized, got: %v", err)
	}
}

func TestPinCAWithServerNameVerifiesTheLeaf(t *testing.T) {
	f := newPinFixture(t)
	err := VerifyPeerCertPin([][]byte{f.caHash}, nil, f.serverName, nil, nil, f.chain())
	if err != nil {
		t.Fatalf("CA pin + server_name must verify the leaf: %v", err)
	}
}

func TestPinCAWithWrongServerNameIsRejected(t *testing.T) {
	f := newPinFixture(t)
	err := VerifyPeerCertPin([][]byte{f.caHash}, nil, "spotify.com", nil, nil, f.chain())
	if err == nil || !strings.Contains(err.Error(), "server_name") {
		t.Fatalf("CA pin + wrong server_name must be rejected, got: %v", err)
	}
}

func TestPinCAWithVerifyNameAcceptsTheRealCertificateName(t *testing.T) {
	// sni=spotify.com + vcn=vps1.example.org: the fronted server_name must not decide the
	// verdict — the pinned CA and the verified name do.
	f := newPinFixture(t)
	err := VerifyPeerCertPin([][]byte{f.caHash}, []string{"vps1.example.org"}, "spotify.com", nil, nil, f.chain())
	if err != nil {
		t.Fatalf("CA pin + verify_peer_cert_by_name must verify the leaf: %v", err)
	}
}

func TestPinCAWithWrongVerifyNameIsRejected(t *testing.T) {
	f := newPinFixture(t)
	err := VerifyPeerCertPin([][]byte{f.caHash}, []string{"attacker.example"}, "", nil, nil, f.chain())
	if err == nil || !strings.Contains(err.Error(), "verify_peer_cert_by_name") {
		t.Fatalf("CA pin + wrong verify name must be rejected, got: %v", err)
	}
}

func TestVerifyNameAgainstConfiguredRootsWorksLikeXray(t *testing.T) {
	// No certificate pins, only a verify name: the check runs against the configured roots
	// (Xray's system CA pool equivalent) with InsecureSkipVerify semantics.
	f := newPinFixture(t)
	err := VerifyPeerCertPin(nil, []string{"vps1.example.org"}, "", f.caPool, nil, f.chain())
	if err != nil {
		t.Fatalf("verify name against configured roots must pass: %v", err)
	}
	err = VerifyPeerCertPin(nil, []string{"attacker.example"}, "", f.caPool, nil, f.chain())
	if err == nil || !strings.Contains(err.Error(), "verify_peer_cert_by_name") {
		t.Fatalf("wrong verify name must be rejected, got: %v", err)
	}
}

func TestEmptyCertificateChainIsRejected(t *testing.T) {
	err := VerifyPeerCertPin(nil, nil, "", nil, nil, nil)
	if err == nil || !strings.Contains(err.Error(), "parse peer certificate") {
		t.Fatalf("empty chain must be rejected, got: %v", err)
	}
}
