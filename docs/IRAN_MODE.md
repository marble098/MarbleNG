# Iran Mode

Iran Mode is MarbleNG's automatic response to the filtering stack deployed on Iranian networks. It
answers three questions continuously:

1. **Am I on an Iranian ISP, and which one?**
2. **How is this specific link being filtered right now?**
3. **Which countermeasures does that combination actually require?**

Detection runs on the physical underlay, never through the tunnel, so a connected VPN can never make
MarbleNG believe the user is in the exit country.

---

## 1. Why the mode exists

Iranian filtering is not a single blocklist. Published measurement work and operator reports describe
a layered system:

| Layer | Behaviour |
| --- | --- |
| DNS poisoning | Queries for blocked domains are answered with private-space injector addresses `10.10.34.34`, `10.10.34.35` and `10.10.34.36`, which serve a generic block page reachable only from inside Iran. |
| HTTP filtering | DPI inspects `Host` headers and URL paths, injecting 403 responses or TCP RSTs on keyword match. |
| TLS/SNI filtering | The censor reads the SNI in the ClientHello and resets the connection before the certificate exchange. Enforcement varies per operator. |
| Protocol allowlist | During clampdowns the national gateway forwards only DNS, HTTP and HTTPS — ports 53/80/443 — and drops everything else, including SSH and classic VPN protocols. |
| UDP suppression | QUIC and UDP-based transports (WireGuard, AmneziaWG, OpenVPN/UDP, Hysteria) are blocked at the UDP endpoint level, with UDP/53 kept open as the deliberate exception. SNI spoofing and ECH do not help because the datagram filter fires before any TLS negotiation. |
| Endpoint graylisting | Proxy endpoint IPs are flagged by traffic volume, IP range and SNI, then blocked. High-volume REALITY endpoints have been reported burning within days on the largest mobile operators. |
| Throttling / stealth blackout | International transit is degraded or withdrawn without withdrawing BGP routes, so connectivity looks normal while only the national network works. |

Enforcement differs by operator: the two largest mobile carriers (MCI and Irancell) are consistently
reported as the strictest, several fixed-line ISPs follow the same policy with a lag, and a few
operators have been observed with SNI enforcement effectively disabled. Iran Mode therefore keys its
countermeasure tier off the *detected operator* as well as the *observed behaviour*.

---

## 2. Detection

`IranModeDetector` scores independent signals. Soft evidence can support a decision but never make
one: activation requires at least one unambiguous signal **and** a total score of 65 or more.

| Signal | Weight | Hard? | Source |
| --- | --- | --- | --- |
| Device registered on an MCC 432 mobile network | +60 | yes | `TelephonyManager.getNetworkOperator()` |
| Uplink geolocates to `IR` | +70 | yes | Multi-endpoint lookup (Cloudflare speed meta, ipinfo, ipapi, country.is, Cloudflare trace) |
| Block-page DNS injection observed (`10.10.34.0/24`) | +75 | yes | Resolving known-filtered domains on the underlay resolver |
| Uplink ASN is a known Iranian ISP | +25 | no | Offline ASN table |
| Public IP inside a known Iranian allocation | +25 | no | Offline IPv4 prefix table |
| Iran-only resolvers reachable (Shecan, Electro, Begzar) | +30/+40 | no | TCP/53 probe |
| Domestic private-space resolver reachable (Radar, 403.online) | +10 | no | TCP/53 probe |
| Device timezone is `Asia/Tehran` | +12 | no | `TimeZone` |
| SIM country is `ir` | +10 | no | `TelephonyManager.getSimCountryIso()` |
| **Uplink geolocates outside Iran** | **−120** | — | Overrides every soft signal |

Iran-only resolver reachability is deliberately *not* treated as hard evidence: a foreign client can
sometimes complete a TCP handshake with those hosts even when they refuse to answer.

### Which ISPs are recognised

Iran has hundreds of allocated autonomous systems, so a hardcoded table can never be complete. Three
mechanisms work together:

1. **Curated ASN table** — the operators carrying the majority of Iranian consumer traffic, each with
   an English name, a Persian name, a network class and a documented filtering severity:

   | ASN | Operator | Persian | Class | Severity |
   | --- | --- | --- | --- | --- |
   | AS197207 | Mobile Communication Company of Iran (MCI) | همراه اول | Mobile | Extreme |
   | AS44244 | Iran Cell Service and Communication (MTN Irancell) | ایرانسل | Mobile | Extreme |
   | AS57218 | Rightel Communication Service Company | رایتل | Mobile | Heavy |
   | AS58224 | Iran Telecommunication Company (TCI) | مخابرات ایران | Fixed | Heavy |
   | AS48159 / AS49666 | Telecommunication Infrastructure Company (TIC) | شرکت ارتباطات زیرساخت | Backbone | Extreme |
   | AS12880 | Iran Information Technology Company (DCI/ITC) | شرکت ارتباطات داده‌ها | Backbone | Heavy |
   | AS31549 | Aria Shatel | شاتل | Fixed | Heavy |
   | AS16322 | Pars Online | پارس آنلاین | Fixed | Moderate |
   | AS43754 | Asiatech | آسیاتک | Fixed | Moderate |
   | AS49100 | Pishgaman Toseeh Ertebatat | پیشگامان توسعه ارتباطات | Fixed | Moderate |
   | AS50810 | Mobin Net Communication | مبین نت | Fixed | Moderate |
   | AS42337 | Respina Networks & Beyond | رسپینا | Fixed | Moderate |
   | AS25184 | Afranet | افرانت | Hosting | Moderate |
   | AS39501 | Parvaresh Dadeha | پرورش داده‌ها | Fixed | Moderate |
   | AS41881 | Fanava Group | گروه فناوا | Fixed | Moderate |
   | AS202468 | Noyan Abr Arvan (ArvanCloud) | ابر آروان | Hosting | Heavy |

2. **Carrier codes** — MCC 432 with MNC 11 (MCI), 19/35 (Irancell), 20/21 (Rightel), 32 (Taliya),
   14 (TKC), 70/93/99 (TCI family). Any unlisted MNC under MCC 432 is still treated as an Iranian
   mobile network.

3. **Organisation fingerprints** — the operator name returned by the live lookup is matched against
   known operator strings, so the smaller regional ASNs of a listed operator inherit that operator's
   filtering profile. Anything still unmatched activates Iran Mode from the country signal and is
   displayed with its real registry name.

### Filtering fingerprint

When Iran Mode is active and deep probing is enabled, MarbleNG classifies the link:

- **DNS poisoning** — filtered domains resolving into `10.10.34.0/24`.
- **SNI filtering / TCP reset** — a control TLS handshake succeeds while handshakes carrying a
  filtered SNI are reset or dropped.
- **Port/protocol allowlist** — port 443 works while well-known non-allowlisted ports (853) are
  dropped on multiple foreign hosts.
- **UDP blocked** — a UDP/53 query to a foreign resolver gets no answer, meaning every UDP transport
  is unusable on this link.
- **National-intranet only** — no foreign endpoint is reachable while domestic hosts still answer.
- **Throttling** — the control handshake completes but takes more than 3.5 s.

---

## 3. Countermeasures

`IranShield` maps operator severity plus observed techniques onto a tier and rewrites the effective
settings used for benchmarking, racing, the autopilot and the live tunnel alike.

| Tier | Trigger | TLS record shredding (`packets` / `length` / `interval`) |
| --- | --- | --- |
| 1 | Light or moderate operator, no SNI enforcement seen | `tlshello` / `100-200` / `10-20` |
| 2 | Heavy operator, or SNI filtering / RST injection observed | `1-3` / `10-30` / `10-20` |
| 3 | Extreme operator, protocol allowlist, or national-intranet state | `1-5` / `5-15` / `15-30` |

Alongside the fragmentation profile:

- **Resolver order** — DoH with Google first and Cloudflare as fallback, because Cloudflare's
  resolver is the most heavily targeted endpoint on Iranian networks. Plaintext `:53` is hijacked
  into the tunnel so the ISP resolver never gets the chance to inject a block page.
- **No hostname leaks** — endpoint hostnames are resolved inside Xray through encrypted bootstrap
  resolvers. Iran Mode never sends tunnel-endpoint lookups to domestic resolvers: doing so would
  expose the server hostname to the very infrastructure that graylists endpoints.
- **Transport preference** — candidate ordering is biased toward REALITY and XTLS-Vision, then TLS,
  then CDN-frontable transports (WebSocket/HTTPUpgrade/XHTTP/gRPC) on port 443. IP-literal endpoints
  gain a bonus because DNS poisoning cannot break them. UDP transports are heavily penalised when
  datagram transit is blocked, and off-allowlist ports are penalised when a port allowlist is seen.
- **QUIC handling** — with UDP blocked, UDP/443 is rejected at the tunnel so applications fall back
  to TCP immediately instead of stalling on retransmits.
- **Domestic direct routing** — when `geoip.dat` is available, `geoip:ir` is routed direct. Iranian
  services stay fast and reachable, and tunnel volume drops, which matters because endpoints are
  graylisted by transferred volume. Without geo data, the domestic resolvers and private ranges are
  routed direct instead.
- **MTU ceiling** — 1420 fixed-line, 1400 mobile, 1380 in tier 3, so shredded records are not
  reassembled by IP fragmentation downstream.
- **Failover posture** — wider connection race (≥4), at least five standby routes, longer handshake
  timeouts, shorter autopilot switch cooldown, and `STEALTH` workload weighting from tier 2 upward.
- **Vision safety** — multiplexing is never forced onto a REALITY/XTLS-Vision path, where it is both
  slower and a stronger fingerprint.

---

## 4. User controls

Deck panel and **Settings → Iran Mode**:

- **Auto** (default) — detect and engage automatically.
- **Always** — force the countermeasures on regardless of detection.
- **Off** — no detection, no countermeasures.
- Independent switches for countermeasures, domestic-direct routing, filtering fingerprinting and
  the activation notification.
- **Re-scan network** re-runs the full sweep on demand.

When Iran Mode is on, the deck shows the detected ISP (English, Persian and ASN), the network class,
the operator's filtering severity, detection confidence, the filtering techniques observed on the
link, every active countermeasure and every scored detection signal.

---

## 5. Privacy notes

- No identifier is persisted: the detector reads the carrier MCC/MNC and the public IP for the
  current sweep only. Nothing is written to disk beyond the in-memory state.
- Probe traffic is ordinary DNS, TCP and TLS to public infrastructure.
- The Iran-only resolvers are used strictly as probe targets and direct-routing exceptions. They are
  never used to resolve tunnel endpoints.

---

## 6. Sources

Detection thresholds and countermeasures were derived from public measurement work and operator
reports, including:

- [IRBlock: A Large-Scale Measurement Study of the Great Firewall of Iran (USENIX Security '25)](https://www.usenix.org/system/files/usenixsecurity25-tai.pdf)
- [I(ra)nconsistencies: Novel Insights into Iran's Censorship (FOCI 2025)](https://www.petsymposium.org/foci/2025/foci-2025-0002.pdf)
- [Internet Censorship in Iran: A First Look (FOCI '13)](https://www.usenix.org/system/files/conference/foci13/foci13-aryan.pdf)
- [Iran's Stealth Internet Blackout: A New Model of Censorship](https://arxiv.org/pdf/2507.14183)
- [OONI: Internet Censorship in Iran — Network Measurement Findings 2014-2017](https://ooni.org/post/iran-internet-censorship)
- [OONI: Technical multi-stakeholder report on Internet shutdowns — Iran, autumn 2022](https://ooni.org/post/2022-iran-technical-multistakeholder-report)
- [IODA: Iran's nation-wide Internet blackout — measurement data and technical observations](https://ioda.inetintel.cc.gatech.edu/reports/irans-nation-wide-internet-blackout-measurement-data-and-technical-observations/)
- [net4people/bbs #628 — Advanced DPI reassembling TCP fragments to extract SNI](https://github.com/net4people/bbs/issues/628)
- [XTLS/Xray-core #2451 — MCI blocking REALITY servers in Iran](https://github.com/XTLS/Xray-core/issues/2451)
- [XTLS/Xray-core discussion #3269 — Investigation on blocking of REALITY in Iran](https://github.com/XTLS/Xray-core/discussions/3269)
- [Lantern circumvention corpus — UDP endpoint blocks of QUIC in Iran](https://corpus.lantern.io/findings/2021-elmenhorst-web__iran-udp-endpoint-blocks-quic/)
- [Lantern circumvention corpus — TCP/WebSocket survived while UDP was blocked, June 2025](https://corpus.lantern.io/findings/2025-piotrowska-nym-iran-blackout__iran-tcp-websocket-survived-udp-blocked/)
- [Filterwatch — network monitoring reports](https://filter.watch/english/)
- ASN and carrier-code data cross-checked against [IPinfo](https://ipinfo.io/countries/ir),
  [ip2location](https://www.ip2location.com/) and [mcc-mnc.org](https://mcc-mnc.org/).
