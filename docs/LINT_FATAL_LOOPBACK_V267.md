# MARBLE_LINT_FATAL_LOOPBACK_V267 — the loopback exception, stated in the language lint speaks

Release run **#375** ("Build signed Android APKs", `main`, 2026-11-04) did not fail while
compiling, linking or packaging anything. Native preparation succeeded for all four ABIs, the
Xray/HEV/JNI checksums verified, the rule sets were pinned, Gradle restored from cache, and then:

```
> Task :app:lintVitalRelease FAILED
Error: app/src/main/res/xml/network_security_config.xml:13: Error:
       Missing includeSubdomains attribute [NetworkSecurityConfig]
        <domain>localhost</domain>
Error: app/src/main/res/xml/network_security_config.xml:14: Error:
       Missing includeSubdomains attribute [NetworkSecurityConfig]
        <domain>127.0.0.1</domain>
Error: app/src/main/res/xml/network_security_config.xml:15: Error:
       Missing includeSubdomains attribute [NetworkSecurityConfig]
        <domain>::1</domain>
```

`lintVitalRelease` runs inside `assembleRelease` and treats `NetworkSecurityConfig` errors as
*fatal*, so `BUILD FAILED` and no APK was produced — every expensive step upstream of it
(≈4 minutes of Go/native builds) had already succeeded.

## What was actually wrong

Nothing about the intent. The V265 chapter (`MARBLE_LOCALHOST_CLEARTEXT_V265`) exempted exactly
three loopback hosts from the global `cleartextTrafficPermitted="false"` so the Xray and sing-box
local controller APIs (`http://127.0.0.1:<port>/...`) keep working on Android 16 and older, where
the base-config also applies to loopback. Those `<domain>` elements simply omitted
`includeSubdomains`, and lint cannot tell "the author meant these exact hosts" from "the author
forgot the attribute" — it reports the omission as a fatal error rather than guessing.

The platform default for the attribute is `false`, which is precisely the intent here, so the fix
is to **state** the default instead of relying on it:

```xml
<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="false">localhost</domain>
    <domain includeSubdomains="false">127.0.0.1</domain>
    <domain includeSubdomains="false">::1</domain>
</domain-config>
```

`includeSubdomains="true"` would have been the wrong fix twice over: it widens the grant to every
subdomain of each name, and it would have made the exception recursive where the audit had
promised a three-host exception.

## Why this recurs, and what now stops it

The failure lives only in the release lane: `lintVitalRelease` is not part of `./gradlew test`,
so a green source-verification run says nothing about it, and the first sighting costs a full
native core preparation. Two guards now pin it locally:

- `scripts/system-integrity-check.py` (runs in the release workflow's preflight and in
  source verification) parses the config and requires exactly the three loopback hosts, each with
  `includeSubdomains` stated, and no `includeSubdomains="true"` anywhere in the file.
- `SecurityHardeningV265Test` (Robolectric/JVM) asserts the same two properties per `<domain>`
  element, beside the existing assertions that global cleartext stays denied and that no
  wildcard or public domain is exempt.

Both guards fail if the attribute is dropped again or flipped to `true`; the parser half was
verified by re-introducing the omission and watching both invariants go red.
