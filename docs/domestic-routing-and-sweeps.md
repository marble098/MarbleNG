# Domestic routing and parallel sweeps

The domestic app review is an **offline, exact package-ID suggestion list**, not an
identity or security check. Android package visibility limits what it can see; a
package ID can also be reused by an impersonator. Review the list before confirming.
Unknown apps are not automatically bypassed. Selected bypass apps use the device's
ordinary network, which is not necessarily in Iran; reconnect to apply the change.
The financial and public-service IDs were curated from the community
[hiddify direct_ir list](https://github.com/hiddify/Android-GFW-Apps/blob/master/direct_ir)
and [Sophos's Iranian banking-app research](https://news.sophos.com/en-us/2023/07/27/uncovering-an-iranian-mobile-malware-campaign/).
These sources are clues rather than authentication; entries should be revisited when
providers change packages or ownership.

Enhanced domestic precision adds explicit domain suffixes, including `.ir` and the
DNS form of `.ایران`, to the configured geo rules. The curated non-`.ir` additions
include service-specific domains from the community
[Iran direct-host list](https://github.com/amirhosseinchoghaei/iran-iplist/blob/main/direct_host).
Suffixes match a whole DNS label; generic brand keywords are deliberately not used.
Standard precision, custom and proxy-all modes do not activate this pack. Domain
ownership can change, so this is not an exhaustive or permanent list. Geo assets
and custom rules remain important; custom rules retain their existing precedence.

New installations request the maximum of 64 sweep workers in manual mode. Existing
stored choices are preserved, including legacy automatic-mode inference. Editing the
worker stepper explicitly selects manual mode. The speed dial can scale the request;
the number actually running is still bounded by candidate count, thermal budget,
and the probe engine's safe core limits (especially per-profile Xray real-delay
cores). This avoids promising 64 heavyweight child processes on every device.
