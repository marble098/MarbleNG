// MARBLE_CORE_CONFIG_SUPERSET_V165 — the contract of MarbleNG's one outbound-policy patch.
//
// Copied into `infra/conf/` by scripts/prepare-native.sh (and by the Core Lock CI job) and run with
// `go test ./infra/conf`. Two failures matter, and they are opposite: losing upstream's default-deny
// would make every non-MarbleNG use of this package silently accept cleartext, and losing the consent
// exit would put a whole plaintext subscription back into "Unsupported VLESS".
package conf

import (
	"testing"

	"github.com/xtls/xray-core/common/net"
)

func TestMarblePlaintextOutboundConsentParsing(t *testing.T) {
	cases := []struct {
		value string
		want  bool
	}{
		{"", false},
		{"0", false},
		{"false", false},
		{"yes", false},
		{"1", true},
		{" true ", true},
		{"TRUE", true},
	}
	for _, testCase := range cases {
		t.Setenv(marblePlaintextOutboundEnv, testCase.value)
		if got := marbleAllowsUnencryptedOutbound(); got != testCase.want {
			t.Fatalf("consent %q = %v, want %v", testCase.value, got, testCase.want)
		}
	}
}

func TestMarblePlaintextOutboundConsentOverridesPublicAddress(t *testing.T) {
	public := &Address{Address: net.ParseAddress("8.8.8.8")}
	publicDomain := &Address{Address: net.ParseAddress("example.com")}
	private := &Address{Address: net.ParseAddress("192.168.17.4")}

	// 1 · upstream behaviour, untouched: no consent, public cleartext is refused, a LAN address is
	//     still allowed by the core's own matcher.
	t.Setenv(marblePlaintextOutboundEnv, "")
	if !requiresTransportSecurity(public) {
		t.Fatal("upstream default-deny lost: a public plaintext outbound must require transport security")
	}
	if !requiresTransportSecurity(publicDomain) {
		t.Fatal("upstream default-deny lost for a public domain")
	}
	if requiresTransportSecurity(private) {
		t.Fatal("the core's own private-network exemption must stay intact")
	}

	// 2 · consent: the same three answers, with the application's decision honoured. A private
	//     address must not start requiring security because consent flipped, and a domain must be
	//     treated exactly like an IP — the app-side matcher is shared for a reason.
	t.Setenv(marblePlaintextOutboundEnv, "1")
	if requiresTransportSecurity(public) {
		t.Fatal("MarbleNG consent ignored: the application owns this decision")
	}
	if requiresTransportSecurity(publicDomain) {
		t.Fatal("MarbleNG consent must cover a public domain, not only an IP")
	}
	if requiresTransportSecurity(private) {
		t.Fatal("consent must not make a private endpoint look unsafe")
	}
}
