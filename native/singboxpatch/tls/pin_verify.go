// MARBLE_SINGBOX_PINNED_PEER_V164 — Xray-core compatible certificate pinning for outbound TLS.
//
// This file is the pinning verifier MarbleNG injects into the pinned sing-box extended source
// (scripts/inject-singbox-tls-pinning.py copies it verbatim into common/tls/pin_verify.go).
// It mirrors Xray's transport/internet/tls verifyPeerCert / verifyChain semantics so that the
// two engines accept exactly the same peer:
//
//   - pinned_peer_cert_sha256  (`pcs`) — hex SHA-256 of a certificate's DER bytes. A match on
//     the leaf accepts the peer outright; a match on a CA in the chain replaces the trust roots
//     and continues into the name verification below (or the server_name check).
//   - verify_peer_cert_by_name (`vcn`) — one or more DNS names the leaf must verify against.
//     The caller sets InsecureSkipVerify, so this is the only name check that runs.
//
// Nothing here depends on anything outside the standard library, so the same file compiles both
// inside the fork and in this repository's standalone test module (pin_verify_test.go), which CI
// runs against the pristine source before any ABI is compiled.
package tls

import (
	"bytes"
	"crypto/sha256"
	"crypto/x509"
	"errors"
	"time"
)

type pinVerifyResult int

const (
	pinNotFound pinVerifyResult = iota
	pinFoundLeaf
	pinFoundCA
)

// VerifyPeerCertPin verifies the peer's raw certificate chain against pinned certificate hashes
// and, when present, the expected names — the exact contract Xray implements for
// pinnedPeerCertSha256 + verifyPeerCertByName. pinnedCertSha256 holds 32-byte hashes of DER
// certificates; verifyNames holds DNS names; serverName is the TLS server_name the caller
// configured; rootCAs is the configured trust pool (system roots, or nil for none); timeFunc
// supplies verification time and defaults to time.Now.
func VerifyPeerCertPin(
	pinnedCertSha256 [][]byte,
	verifyNames []string,
	serverName string,
	rootCAs *x509.CertPool,
	timeFunc func() time.Time,
	rawCerts [][]byte,
) error {
	// Extract x509 certificates from rawCerts. verifiedChains is always nil here: the caller
	// sets InsecureSkipVerify so that pinning replaces, rather than augments, chain validation.
	certs := make([]*x509.Certificate, len(rawCerts))
	for i, asn1Data := range rawCerts {
		certs[i], _ = x509.ParseCertificate(asn1Data)
	}
	if len(certs) == 0 || certs[0] == nil {
		return errors.New("failed to parse peer certificate")
	}
	if timeFunc == nil {
		timeFunc = time.Now
	}

	// A pin that matches the leaf accepts the peer directly; a pin that matches a CA in the
	// chain replaces the trust roots and the handshake continues into name verification.
	roots := rootCAs
	var verifyResult pinVerifyResult
	var verifiedCA *x509.Certificate
	if len(pinnedCertSha256) > 0 {
		leafHash := sha256.Sum256(certs[0].Raw)
		leafMatched := false
		for _, pin := range pinnedCertSha256 {
			if bytes.Equal(leafHash[:], pin) {
				leafMatched = true
				break
			}
		}
		if leafMatched {
			verifyResult = pinFoundLeaf
		} else {
			for _, cert := range certs[1:] {
				if cert == nil {
					continue
				}
				certHash := sha256.Sum256(cert.Raw)
				for _, pin := range pinnedCertSha256 {
					if bytes.Equal(certHash[:], pin) {
						if cert.IsCA {
							verifyResult = pinFoundCA
							verifiedCA = cert
						}
						break
					}
				}
				if verifyResult == pinFoundCA {
					break
				}
			}
			if verifyResult == pinNotFound {
				return errors.New("peer cert is unrecognized (against pinned_peer_cert_sha256)")
			}
		}
	}

	if len(verifyNames) > 0 {
		caPool := roots
		if verifyResult == pinFoundCA {
			caPool = x509.NewCertPool()
			caPool.AddCert(verifiedCA)
		}
		intermediates := x509.NewCertPool()
		for _, cert := range certs[1:] {
			if cert != nil {
				intermediates.AddCert(cert)
			}
		}
		for _, name := range verifyNames {
			_, err := certs[0].Verify(x509.VerifyOptions{
				Roots:         caPool,
				Intermediates: intermediates,
				CurrentTime:   timeFunc(),
				DNSName:       name,
				KeyUsages:     []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
			})
			if err == nil {
				return nil
			}
		}
		return errors.New("peer cert is invalid (against root CAs and verify_peer_cert_by_name)")
	}

	if verifyResult == pinFoundCA {
		if serverName == "" {
			return errors.New("pinning CA needs a valid server_name")
		}
		caPool := x509.NewCertPool()
		caPool.AddCert(verifiedCA)
		intermediates := x509.NewCertPool()
		for _, cert := range certs[1:] {
			if cert != nil {
				intermediates.AddCert(cert)
			}
		}
		_, err := certs[0].Verify(x509.VerifyOptions{
			Roots:         caPool,
			Intermediates: intermediates,
			CurrentTime:   timeFunc(),
			DNSName:       serverName,
			KeyUsages:     []x509.ExtKeyUsage{x509.ExtKeyUsageServerAuth},
		})
		if err == nil {
			return nil
		}
		return errors.New("peer cert is invalid (against pinned CA and server_name)")
	}

	return nil
}
