# IPv6 connectivity and DNS/IP leak hardening

## Root cause (both cores and the Android boundary)

A hostname's **physical node socket**, the proxy's **exit DNS/traffic**, and the Android
**TUN routes** are different address-family decisions. Previously they were treated as one:

- Full TUN sometimes omitted `::/0` based on `ipv6Enabled` or the *physical* network's IPv6.
  Android could then send app IPv6 traffic outside the VPN while IPv4 was tunneled, or an IPv4
  node could not provide IPv6 browsing at an IPv6-capable exit.
- An explicit IPv4-only Xray FakeDNS pool replaced the core's automatic dual-family defaults;
  sing-box FakeIP omitted `inet6_range`. AAAA received no fake address. ULA fake addresses also
  overlapped private/direct routing without a preceding proxy-only rule.
- The core's physical bootstrap, saved native per-dial resolver, and app-side endpoint probes
  disagreed about family policy. Some paths used Android/system or plaintext DNS as a fallback,
  exposing node hostnames before the VPN had started. ICMP/SSH could fall back to passing a
  hostname to a system resolver after encrypted resolution failed.
- Changing the family/DNS setting while connected persisted a preference without reconfiguring
  the running core. Replacing a running TUN closed the old interface too early; errors could
  expose the underlying route between attempts. One dual-stack IP audit could select IPv4 on
  both sides and miss an IPv6-only bypass.

## Current invariants

1. **Full TUN:** require IPv4 + IPv6 addresses, default routes and *internal* DNS sinks on every
   device, regardless of physical IPv6. Intercept classic port 53 in both engines; if DNS hijacking
   is turned off, refuse Full TUN rather than allow a plaintext DNS path. If core/HEV fails, retain
   the TUN as a blackhole. Do not establish a partial VPN. On replacement, retain
   the previous TUN until the new one is established. Android's always-on VPN **Block connections
   without VPN** option is required for OS-level coverage *before* the first TUN is installed or
   after Android revokes/terminates the service. An app cannot promise that coverage by itself.
2. **Node dial:** Force IPv6 selects only AAAA/IPv6 for the physical socket and encrypted
   IP-literal IPv6 bootstrap providers. It refuses an IPv4-literal (including IPv4-mapped IPv6)
   node and fails visibly if the link cannot dial IPv6; it does **not** secretly fall back to IPv4.
   Prefer IPv6 can dial an IPv4 node on an IPv4-only underlay while still requesting AAAA at its
   *remote exit*. The preference still permits an IPv4 fallback; use Force IPv6 to forbid it.
   In-process loopback bridges are exempt from the *node* restriction.
3. **App browsing:** default-route proxy traffic is captured for both families. Force IPv6
   rejects IPv4 destination traffic *in the core*; Force IPv4 similarly rejects IPv6. Xray and
   sing-box emit both A and AAAA Fake IP pools and route those tokens to the selected proxy ahead
   of private/direct rules. DNS requests through the TUN are hijacked and ordinary browsing
   lookups use encrypted resolvers through the proxy, without plaintext fallback.
4. **Bootstrap and probes:** neither core attempts local/system DNS when encrypted direct
   bootstrap fails. Native sing-box imports have physical dialer resolvers replaced with an
   encrypted family-aware one; unsafe bootstrap graphs are rejected. JVM TCP/ICMP/SSH/metadata
   endpoint resolution uses IP-literal HTTPS DoH with separate A and AAAA questions and no OS-DNS
   retry. Failed encrypted resolution is inconclusive, **never permission to try cleartext**.
5. **Live changes and verification:** family/DNS changes request a reconnect. Privacy audit can
   run on either core, independently tests IPv6-only proxy egress, and compares it to the
   physical IPv6 address only when the user starts the audit. No IPv6 exit observation means
   **unverified**, not "no leak". An equal physical/exit IPv6 is a critical possible bypass.

## How to verify on a real Android device

1. Enable Android's **always-on VPN** and **Block connections without VPN** for MarbleNG. Enable
   **DNS hijack**, select **Full TUN**, no split-app exclusions, and **Proxy all** for a strict
   international egress test.
   Confirm the notification says connected; don't mistake Local Proxy for a device-wide VPN.
2. With a node **that has an IPv6-capable exit**, select **Prefer IPv6** and run Privacy audit.
   Check the independent `IPv6 PROXY EXIT` line and an IPv6-only test page in a VPN-covered app.
   A node reached by IPv4 can *still* carry IPv6 requests at its exit. Switching the setting
   should reconnect the live session.
3. With an **IPv6-reachable node and IPv6-capable physical link**, select **Force IPv6**. Check
   the same audit, and confirm IPv4-only sites cannot be reached by the covered app. Try an
   IPv4-literal node: the app must explain why it is unavailable rather than connect via IPv4.
   On an IPv4-only physical link, an IPv6-only *node endpoint* cannot be reached: that failure
   is expected, not a reason to fall back silently or disable the TUN route.
4. For each attempt, inspect service diagnostics and an external DNS leak test. Verify no
   physical resolver gets the **node hostname** or **browsed hostnames**. Observe IPv4/IPv6
   egress separately. Repeat while switching networks and changing family modes mid-session;
   on failure the existing TUN should block traffic until explicitly disconnected/replaced.

The manual privacy audit intentionally contacts known diagnostic origins once through the proxy
and once through the physical network; that direct request reveals the physical IP to the audit
origin by design. Automatic Iran Mode physical-network *geolocation/TLS diagnostics* can contact
fixed service origins outside the tunnel, but do not send browsing or node hostnames to OS DNS;
the former automatic plaintext UDP/53 canary and system-DNS poisoning probes have been removed.
Split tunneling, direct domestic/private routing, per-app exclusions and Local Proxy are explicit
partial-coverage choices, **not** a guarantee that those traffic classes use the proxy. Devices
can also override the app (other VPNs, OS lockdown/permission changes). Automated JVM/config
regressions cannot establish radio/ROM-specific IPv6 availability; a device test is still needed.
