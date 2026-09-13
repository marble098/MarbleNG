// MARBLE_CORE_CONFIG_SUPERSET_V165
//
// MarbleNG's Xray build ships one policy deviation from upstream, and it is deliberately the
// smallest possible one: the question "may a cleartext outbound dial a public address?" stops being
// answered inside the core and is delegated to the application that owns the user's consent.
//
// Why this file exists
//
//	infra/conf/xray.go (upstream) refuses to *load* a config whose vless/trojan outbound reaches a
//	public address without TLS:
//
//		vless without TLS or other encryption is prohibited unless the server address is a
//		private IP or domain
//
//	That is the right default for a standalone binary, where a config file is a hand-written
//	artifact and silently dialling plaintext is a footgun. It is the wrong answer for a client that
//	imports a subscription: the user pasted the link, every other client on the market dials it, and
//	an app that refuses it has 42 unusable servers and no sentence explaining why. The alternative —
//	Marble rewriting the node to add TLS, or switching core behind the user's back — is worse, and
//	`docs/core-interoperability.md` forbids both.
//
// So the patch does not weaken the core: `requiresTransportSecurity` keeps its exact semantics
// whenever this package is used by anything other than MarbleNG (the standalone binary, tests, any
// third-party embedder), and MarbleNG opts in per-process through the environment variable below.
// Every other transport-security rule — certificate verification, REALITY's handshake, the private
// network matcher — is untouched, and the app-side policy (`CoreConfigSuperset`) is what decides,
// labels the node as unencrypted, and refuses when the user turns consent off.
package conf

import (
	"os"
	"strings"
)

// marblePlaintextOutboundEnv must equal `CoreConfigSuperset.PLAINTEXT_POLICY_ENV`, which
// `XrayManager.createProcessBuilder` sets for every process it starts (live tunnel, `run -test`
// verifier, Rank/Turbo children). scripts/system-integrity-check.py pins the two spellings together.
const marblePlaintextOutboundEnv = "MARBLE_ALLOW_UNENCRYPTED_PUBLIC_OUTBOUND"

// marbleAllowsUnencryptedOutbound reports whether the embedding application has taken ownership of
// the plaintext-outbound decision.
//
// Read per call, not cached: `infra/conf` is also used by `xray run -test` and by this package's own
// tests, which set the variable between cases. A handful of `os.Getenv` calls per config build is
// nothing next to parsing the JSON they guard, and it keeps the patch free of any global state the
// core would have to reason about.
func marbleAllowsUnencryptedOutbound() bool {
	value := strings.TrimSpace(os.Getenv(marblePlaintextOutboundEnv))
	return value == "1" || strings.EqualFold(value, "true")
}
