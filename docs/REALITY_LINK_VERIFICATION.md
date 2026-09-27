# REALITY share-link investigation

## Finding

The reported unpadded base64url `pbk` format is correct, but this checkout does **not**
Base64-decode that parameter in Kotlin. `ShareLinkParams` percent-decodes it once;
`ProxyParser` stores the string in Xray's `realitySettings.password`;
`SingBoxTransportTranslator` copies it unchanged to `tls.reality.public_key`.
Do not decode it into a Kotlin string or re-encode it as standard Base64.

The source pinned by `core-lock.json` (sing-box commit
`55faa763f986f4ca8a492d9b2719bc6330d2bef5`, `common/tls/reality_client.go`)
uses `base64.RawURLEncoding.DecodeString` and checks for 32 bytes. It hex-decodes
`short_id` into an eight-byte buffer. An invalid alphabet/length is a config-load
error, not evidence of a successful handshake with a silently changed key.
`reality verification failed` is emitted after the handshake when authenticated
REALITY certificate verification did not succeed; that message alone does not
identify which input or peer behavior caused the rejection.

HTML query separators are normalized before parsing. `spx` is percent-decoded
and retained as Xray `spiderX`; the sing-box translator explicitly reports its
unsupported crawling behavior. XHTTP `extra` remains transport configuration.

## Confirmed bug fixed

`realityPublicKey` was recognized when inferring `security=reality`, but omitted
when writing `realitySettings.password`. Links using that alias could therefore
lose the key. The writer now accepts the same aliases as security inference.
This is **not** proof that the reported link using `pbk` failed for that reason.
No TLS verification bypass, alternate key, or fingerprint substitution is added.

## Regression coverage

- The IPv6/XHTTP/HTML-escaped fixture now has a synthetic 32-byte unpadded key
  containing both `-` and `_`, rather than an all-`A` key, and a full eight-byte
  short ID. Both parser preferences must retain the exact key in sing-box JSON.
- Tests cover percent-escaped URL-safe symbols and all public-key aliases,
  including security inference and the translated `public_key` field.
- The native integration test now imports an HTML-escaped VLESS link instead of
  constructing equivalent client JSON manually. It generates an X25519 pair whose
  public key contains both URL-safe symbols, then exercises URL Test and HTTPS
  Real Delay against a loopback Xray REALITY/XHTTP server. A wrong public key and
  a wrong account must both fail. No production credentials enter these fixtures.

## Verification limits

The local integrity audit passes (265/265), and `git diff --check` passes.
This sandbox has no JDK or Android SDK. Attempts to obtain the JDK, Android tools,
Maven dependencies and pinned native release binaries failed at their download
hosts. Consequently JVM/native tests were not executed locally; their addition
is not a claim that they passed. Source verification CI is requested by the PR.
The user's IPv6 endpoint has not been authenticated from an Android device.
A green loopback test, when available, proves interoperability of that fixture,
not connectivity to the reported server. The reported `pbk` connection failure
remains unproven until reproduced with the actual emitted config and core version.
