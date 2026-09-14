# REFRESH CRASH, CENSORSHIP REACH AND UNRESTRICTED BACKGROUND — V166

**Why tapping the subscription refresh icon crashed the app, why both cores declared every
server dead on a restricted network, and where the one-tap fix for a VPN that Android keeps
interrupting now lives.**

Engine pin: unchanged — the pinned Xray-core, hev-socks5-tunnel and sing-box extended from
`core-lock.json`. Nothing in this chapter touches a core binary.

---

## خلاصه (Persian summary)

سه مشکل، سه جواب:

- **کرش هنگام رفرش ساب:** آیکون رفرش ساب‌اسکریپشن با `java.util.ConcurrentModificationException`
  اپ را کرش می‌کرد. تسک رفرش روی استخر io مستقیماً فهرست‌های تحت‌نظر Compose (`profiles` و
  `subscriptions` از جنس `mutableStateListOf`) را تغییر می‌داد، درحالی‌که همان لحظه کامپوزِ
  صفحهٔ سرورها روی ترد اصلی روی همان فهرست‌ها پیمایش می‌کرد. حالا همهٔ تغییرات ساختاری این
  فهرست‌ها فقط از یک دروازهٔ واحد (`applyStateOnMainThread`) و روی لوپر اصلی اعمال می‌شوند و
  ذخیره‌سازی هم اسنپ‌شاتِ غیرقابل‌تغییرِ همان بلوک را می‌نویسد، نه فهرست زنده را.
- **پینگ هر دو هسته روی اینترنت محدود:** مرجعِ اندازه‌گیری هر دو هسته (ایکس‌ری و سینگ‌باکس)
  یک فهرست مشترک سه‌تایی بود که دوتای آن‌ها یک اپراتور بودند (gstatic و google.com). روی شبکه‌ای
  که گوگل را فیلتر می‌کند، «آدرس پشتیبان» همراه اصلی می‌مُرد و همهٔ کانفیگ‌ها — حتی تونلِ سالم —
  غیرقابل‌دسترس اندازه گرفته می‌شدند. حالا مرجع‌ها سه اپراتور متفاوت‌اند: گوگل، Cloudflare
  (`cp.cloudflare.com/generate_204`) و Firefox (`detectportal.firefox.com/success.txt`). چون هر
  دو هسته همین یک فهرست را مصرف می‌کنند، هر دو با هم مقاوم شدند.
- **دسترسی نامحدود در پس‌زمینه:** اگر وی‌پی‌ان کاربر در پس‌زمینه اختلال دارد، حالا در
  تنظیمات ← سیستم یک کارت «دسترسی در پس‌زمینه» هست: وضعیت را زنده نشان می‌دهد و با یک ضربه
  صفحهٔ خود اندروید را باز می‌کند تا استثنا — همان لحظه که کاربر تأیید کند — اعمال شود.

## 1. The report

> «وقتی روی رفرش ساب کلیک میکنم آیکونش، اپ کرش میکنه» — with
> `java.util.ConcurrentModificationException` on the main thread, inside the Compose frame
> dispatch (`Handler.handleCallback` → recomposer frame), iterator `next()`.

The stack is Compose iterating an observed list on the main looper while something structurally
modified that list from elsewhere. The only writers that ran off the main looper were the
subscription paths: the refresh tasks and the text import ran inside the io pool and mutated the
`mutableStateListOf` state lists in place — `profiles.removeAll`, `profiles.addAll`,
`subscriptions[index] = …`. A SnapshotStateList iterator pins the snapshot it was created in and
fails fast the moment the list changes outside it; the worker's mutation therefore detonated
inside the UI's own iteration, exactly once per refresh while any screen showing the servers was
composed.

## 2. The fix — one mutation gate on the main looper

`AppRepository.applyStateOnMainThread` is now the only way a background task touches
`profiles`, `subscriptions` or `history`:

  - the fetch, the parse and the dedup stay on the worker — the slow parts never moved to the
    main looper;
  - the structural mutation runs inside one block posted to the main looper, where Compose's
    iteration and the mutation are ordered by the looper instead of racing;
  - the worker waits for the block's value and persists the *immutable snapshot* the block
    returns — serialising a live SnapshotStateList from a pool thread was the same race one
    frame earlier;
  - the wait honours cancellation: an interrupted sweep wakes from the future's `get` exactly
    like one blocked in a socket.

Three paths pass through the gate: the single-source refresh, the refresh-all loop (one main-
looper block per source, one snapshot capture after the last) and the text import. Every other
writer was already a main-thread UI action and needed no change.

## 3. Censorship reach — the pool both cores ping through

Both engines measure against the same `DelayTest.candidates` list: Xray's real delay walks it
through its SOCKS inbound (`SocksHttpClient`), sing-box's URL test hands it to the core's own
`/proxies/{tag}/delay` endpoint (`ProbeTargetWalk.urlTest`), and the Rank sweep consumes it the
same way. The pool is therefore the measurement plane both cores share — one blocked operator in
it cannot be compensated by another spelling of the same operator.

The old pool was gstatic → google.com → cloudflare trace: one operator twice. On a network that
filters Google — the documented national-filter behaviour this product already routes around for
its *rank* targets (see the IRAN_AWARE_PING target-pool note in `RouteProbe`) — the second slot
died with the first and a healthy tunnel measured as unreachable on every config of both cores.

V166 replaces the second slot and sharpens the third, keeping the walk's pinned bound of three
distinct origins (`ProbeTargetWalk.MAX_TARGETS = 3`) and the walk's per-target budget intact:

  1. the configured primary (default `https://www.gstatic.com/generate_204`, user-overridable);
  2. `https://cp.cloudflare.com/generate_204` — an empty 204 on Cloudflare's anycast fleet;
  3. `https://detectportal.firefox.com/success.txt` — Firefox's captive-portal marker, a tiny
     200 that networks deliberately keep open so hotspot detection works.

Three registrable domains, three operators, all HTTPS without user info, so the URL-test
contract (`UrlTestTarget`) accepts every slot unchanged. The connect path needed no new plumbing:
both cores already dial through the hardened runtime config (fragment/mux, resolver-pool racing,
fingerprint-aware TLS); what failed on restricted internet was the *verdict plane*, and that is
what this pool is.

## 4. Unrestricted background access — Settings → System

A tunnel that Android is allowed to pause is a tunnel that dies on every screen-off, and the
re-connect storm is exactly when a VPN on a restricted network matters. The OS-level grant is
the battery-optimization exemption — Android calls it *unrestricted background activity*.

The new **Background access** card in Settings → System shows the live state of that exemption
and carries one button: it opens Android's own dialog for this package
(`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`), so the tap in Settings becomes the grant itself
the moment the user confirms — the exemption applies immediately, while the user is still there.
ROMs that removed the direct dialog fall back to the exemption list page
(`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`), and the activity-result launcher re-reads the
truth the instant the user returns. The manifest already declared
`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; this is the product surface that exercises it.

## 5. What is pinned

  - `ProbeMethodV151Test` — the candidate list is the new three-operator pool.
  - `CensorshipReachTargetsV166Test` — walk order, operator distinctness, URL-test acceptance of
    every slot, and duplicate collapse.
  - Source verification (`verify.yml`) — unit tests plus debug and release Kotlin compilation.
