# MARBLE_NETWORK_COUNCIL_V236 — شورای دوازده‌نفرهٔ شبکه

> تاریخ: 2026-10-27 • شاخه: `arena/838503c6-marbleng`
>
> **درخواست:** «با تشکیل یک شورای ۱۲ نفرهٔ متخصص شبکه، یک اسکنر فوق‌حرفه‌ای به تنظیمات اضافه کن که با
> زدن «شروع اسکن»، شبکه‌ای که کاربر از طریق آن وصل است — چه وای‌فای، چه دادهٔ همراه — و اپراتور را از
> تمامی ابعاد (پینگ، TCP، UDP، پورت‌ها، DNS، MTU و مشابه‌ها) بررسی کند و پس از پایان اسکن بهترین
> تنظیمات ممکن را روی هسته‌ها، تنظیمات اتصال و باقی موارد اعمال کند.»
>
> **نتیجه:** `Settings → Network Council Scanner`. یک اسکن، دوازده رأی، یک نقشهٔ تنظیمات که یا خودش
> اعمال می‌شود یا با یک لمس.

---

## ۱. ترکیب شورا

هر کرسی یک متخصص است، یک ابزار اندازه‌گیری دارد، و **یک گروه از کلیدها** در اختیار اوست. هیچ
کرسی‌ای نمی‌تواند کلیدی را که مالک ندارد بنویسد؛ این محدودیت در کد با `TuningField` بیان شده است.

| # | کرسی | تخصص | آنچه می‌سنجد | کلیدهایی که مالک آن است |
|---|---|---|---|---|
| ۱ | **رادیو و لینک** | مهندس دسترسی رادیویی | نوع اتصال، باند، سیگنال، تخمین پهنای باند، MTU رابط، شمارنده‌بودن، معتبربودن لینک | سقف و کف MTU تونل |
| ۲ | **اپراتور و حامل** | تحلیل‌گر اپراتور | اپراتور (MCC/MNC و نام) و پروفایل فیلترش در `IranNetworkRegistry` | قطعه‌بندی TLS (فقط به‌عنوان پیشنهاد کم‌اطمینان) |
| ۳ | **تأخیر** | مهندس کارایی | نردبان دست‌دهی TCP به لنگرگاه‌های عددی، میانه و p95 | مهلت پینگ، تعداد نمونه |
| ۴ | **جیتر و پایداری** | مهندس کیفیت سرویس | گام مطلق میانه بین نمونه‌های پیاپی | مهلت اتصال هسته، keep-alive |
| ۵ | **افت بسته** | مهندس شبکهٔ بسته‌ای | نسبت دست‌دهی‌های ناموفق به کل تلاش‌ها | مهلت بیکاری UDP، تعداد جانشین‌ها |
| ۶ | **صحت DNS** | مهندس ریزولور | پرسش واقعی DNS به هر ریزولور، rcode، تأخیر، تشخیص صفحهٔ مسدودسازی | ریزولور اصلی/دوم، DoH، راهبرد پرسش، نگهبان ربایش، Fake-IP |
| ۷ | **پورت و فایروال** | مهندس امنیت شبکه | ماتریس TCP روی پورت‌های CDN (443، 80، 8443، 2053، 2083، 2087، 2096، 8080) | TCP fast open |
| ۸ | **UDP و QUIC** | مهندس ترابری | NTP واقعی + پرسش Version Negotiation به سرور QUIC روی 443 | آزمون UDP، سیاست UDP/443، هم‌زمانی xUDP |
| ۹ | **MTU و قطعه‌بندی** | مهندس مسیر | نردبان DNS با پدینگ EDNS0 (1232→1472) و اطمینان اندازه‌گیری | MTU مسیر دادهٔ تونل |
| ۱۰ | **مسیر و خانوادهٔ آدرس** | مهندس IPv6 | تماس مستقیم IPv4 و IPv6، جدا از هم | خانوادهٔ آدرس، IPv6، تأخیر Happy Eyeballs |
| ۱۱ | **DPI و سانسور** | مهندس دورزدن فیلتر | دست‌دهی TLS واقعی از مسیر خودِ محصول (`MultiVectorReachability`) | قطعه‌بندی TLS (اولویت اندازه‌گیری‌شده)، پدینگ mux |
| ۱۲ | **هسته و دستگاه** | مهندس پلتفرم | تعداد هسته، حافظه، دما، باتری، پهنای باند لینک | پروفایل توان، پروفایل بار، mux، بافر، هم‌زمانی پینگ |

---

## ۲. آنچه یک اسکن می‌سنجد

| لایه | روش | بودجه |
|---|---|---|
| لینک | `ConnectivityManager` + `NetworkCapabilities` + `LinkProperties` + `TelephonyManager` (نام و MCC/MNC اپراتور) + `WifiManager` (باند و RSSI، هرجا اندروید اجازه بدهد) | ~۵۰ms |
| DNS | پرسش DNS واقعی روی UDP به ریزولورهای تنظیم‌شده + `1.1.1.1` + `8.8.8.8` + `9.9.9.9`؛ پاسخ برای پیشوند صفحهٔ مسدودسازی `IranNetworkRegistry` بررسی می‌شود | ۱٫۵s هر ریزولور |
| تأخیر | ۳ لنگرگاه × ۳ نمونه دست‌دهی TCP به `1.1.1.1`، `8.8.8.8`، `9.9.9.9` | ۱٫۵s هر تلاش |
| TLS/DPI | یک دست‌دهی کامل TLS با `MultiVectorReachability` (همان موتور پینگ محصول) | ۳s |
| پورت‌ها | ۸ پورت روی اولین لنگرگاه، همه موازی | ۱٫۲s هر پورت |
| UDP | یک درخواست SNTP واقعی به دو سرور زمان + پرسش Version Negotiation روی UDP/443 | ۱٫۴s هر آزمون |
| MTU | پرسش‌های DNS پد‌شده در ۱۲۳۲، ۱۲۸۰، ۱۴۰۰، ۱۴۵۲ و ۱۴۷۲ بایت؛ بزرگ‌ترین پاسخ = سقف مسیر، نسبت پاسخ‌ها = اطمینان | ۱٫۵s هر پله |
| خانواده | تماس مستقیم TCP به `1.1.1.1:443` و `2606:4700:4700::1111:443` | ۲s هر خانواده |
| دستگاه | هسته، حافظه، باتری، `currentThermalStatus` | ~۵ms |

**هیچ دانلودی انجام نمی‌شود.** عدد پهنای باند همان تخمین خودِ لینک است
(`linkDownstreamBandwidthKbps`). کاربری که عدد واقعی می‌خواهد، موتور بنچمارک را دارد؛ اسکنر شورا
حق ندارد برای یک عدد، حجم کاربر را خرج کند.

---

## ۳. قانون اساسی شورا

۱. **شاهد پیش از حس.** هر پیشنهاد یک «اطمینان» عددی دارد. کرسی‌ای که چیزی نسنجیده با اطمینان کمتری
   حرف می‌زند و به کرسی‌ای که سنجیده می‌بازد. جدول اپراتورها فقط وقتی بلندتر حرف می‌زند که آزمون
   دست‌دهی **بی‌نتیجه** بوده باشد.
۲. **محافظتی که لازم نیست، خرج نمی‌شود.** مسیر پاک روی `auto` می‌ماند؛ هرگز «محض احتیاط» به
   `full` پرتاب نمی‌شود.
۳. **چیزی که سرعت نباید بخرد، خریدنی نیست.** مسیریابی، تونل تفکیکی، هویت، اعتبارسنجی گواهی و
   رفتار fail-closed دی‌ان‌اس هیچ فیلدی در `TuningField` ندارند — نه اینکه امروز نوشته نمی‌شوند،
   بلکه **نمی‌توانند** نوشته شوند.
۴. **اعداد کران‌دار.** هر مقدار به همان بازه‌ای بسته می‌شود که سازندهٔ کانفیگ می‌بندد
   (`PingBudget`، MTU ۱۲۸۰..۱۵۰۰، mux 1..128، xUDP 1..1024، بافر 0..1024KB). `normalize()` آخرین
   ضامن است: نقشهٔ اعمال‌شده هرگز کانفیگی نیست که هستهٔ پین‌شده رد کند.
۵. **mux روی مسیری که آن را حمل نمی‌کند روشن نمی‌شود.** فراخوان `muxSafe` را از
   `CorePower.muxSafeFor(route)` می‌گیرد؛ اگر مسیر REALITY/XTLS باشد، پیشنهاد «false» است.

---

## ۴. اختلاف‌ها چطور حل می‌شوند

```text
پیشنهادها  ──►  گروه‌بندی بر اساس فیلد  ──►  بیشترین اطمینان (و در تساوی، کرسی نزدیک‌تر به سیم)
                                                    │
                                                    ├─ برنده: یک تغییر با «قبل → بعد»
                                                    └─ بازنده: یک خط «مخالفت» زیر همان تغییر
```

نتیجهٔ بازنده حذف نمی‌شود؛ در صفحه به‌صورت `مخالفت · Operator & carrier wanted record` دیده
می‌شود. شورایی که اختلافش را پنهان کند، شورا نیست.

---

## ۵. جدول اجرا

| فایل | افزوده | چرا |
|---|---|---|
| `core/NetworkCouncil.kt` | ۱۲ کرسی، `NetworkEvidence`، `TuningField`، `Metrics`، `deliberate`/`read`/`write`/`normalize`/`resolve` | قضاوت، **خالص** و قابل تست روی JVM |
| `core/NetworkScanner.kt` | ضبط لینک (Connectivity/Wi-Fi/Telephony/باتری/دما)، نردبان TCP، DNS، ماتریس پورت، NTP، QUIC VN، نردبان MTU، خانواده‌ها | تنها فایلی که به اندروید دست می‌زند |
| `core/CouncilReport.kt` | گزارش ذخیره‌شدنی (JSON) بدون نگه‌داشتن شواهد کهنه | «آخرین رأی» بعد از ری‌استارت هم می‌ماند |
| `data/AppStore.kt` | `councilAutoApply`/`setCouncilAutoApply` + `loadCouncilReport`/`saveCouncilReport` | گزارش و سوئیچ هر دو بیرون از `marbleng-backup` می‌مانند |
| `model/Models.kt` | **هیچ** — `AppSettings` دست‌نخورده ماند | ۲۵۴ فیلد، سقف ۲۵۵ پارامتر JVM؛ یک فیلد بیشتر `copy` را غیرقابل‌بارگذاری می‌کند |
| `AppRepository.kt` | `startNetworkCouncilScan`، `applyNetworkCouncilPlan`، `clearNetworkCouncilReport`، وضعیت‌های Compose | یک worker، یک مسیر نوشتن (`updateSettings`) |
| `ui/SettingsNetworkCouncilPages.kt` | صفحهٔ کامل: لینک، دوازده کرسی، تغییرها با «قبل → بعد»، اندازه‌گیری‌ها، دلایل | هر ادعا کنار عددش |
| `ui/SettingsScreen.kt` | مقصد `NETWORK_COUNCIL` در گروه تست‌ها + زیرنویس «آخرین رأی» | کشف‌پذیری + حافظه |
| `ui/MarblePersianLexicon.kt` | ۱۳۲ جملهٔ فارسی جدید (کرسی‌ها، کلیدها، احکام، دکمه‌ها) | فارسی واقعی، نه ترنسلیت |
| `AndroidManifest.xml` | `ACCESS_WIFI_STATE` (مجوز عادی، بدون پاپ‌آپ) | باند و RSSI وای‌فای |
| `test/.../NetworkCouncilV236Test.kt` | ۲۴ تست | هر قاعده‌ای که این سند ادعا می‌کند |

---

## ۶. آنچه عمداً انجام نشد

- **تست سرعت دانلودی.** اسکن روی دادهٔ همراه هم اجرا می‌شود؛ خرج‌کردن حجم کاربر برای یک عدد،
  تصمیمی است که او باید بگیرد، نه شورا. عدد لینک کافی است و همان هم صادقانه برچسب «تخمین» دارد.
- **تغییر خودکار هستهٔ پروکسی (Xray ↔ sing-box).** شورا کلیدهای *هر دو* هسته را تنظیم می‌کند، اما
  جابه‌جایی موتور یک تصمیم کاربر است؛ اپراتور و DPI روی همان موتور فعلی اعمال می‌شوند.
- **خاموش‌کردن IPv6.** حتی وقتی IPv6 جواب نمی‌دهد. `PREFER_IPV4` + تأخیر Happy Eyeballs ۱۲۰ms
  همان نتیجه را می‌دهد، بی‌آنکه درِ نشت را باز کند.
- **مجوز `READ_PHONE_STATE`.** نام و MCC/MNC اپراتور بدون این مجوز خوانده می‌شوند؛ نوع شبکهٔ
  سلولی هرجا اندروید اجازه بدهد خوانده می‌شود و هرجا نه، «دادهٔ همراه» گزارش می‌شود. یک اسکنر
  حرفه‌ای نباید برای یک برچسب، یک پاپ‌آپ مجوز بخواهد.
- **ذخیرهٔ شواهد خام.** یک نوار سیگنال از هفتهٔ پیش، شاهد امروز نیست؛ صفحه با شواهد تازه کار می‌کند
  و فقط احکام و تغییرها می‌مانند.

---

## ۷. تست‌ها

`app/src/test/java/com/marbleng/app/core/NetworkCouncilV236Test.kt`

| تست | آنچه پین می‌کند |
|---|---|
| `aCleanPathNeverSpendsTheHeavyHandshakeProtection` | مسیر پاک روی `auto` می‌ماند، پدینگ نوشته نمی‌شود |
| `anInjectedResetBuysFullFragmentationAndPadding` | ریست اندازه‌گیری‌شده ⇒ `full` + پدینگ + CRITICAL |
| `theOperatorTableMayAdviseButNeverOverrulesACompletedHandshake` | ایرانسل (پروفایل EXTREME) می‌بازد و به‌عنوان «مخالفت» ثبت می‌شود |
| `anInconclusiveHandshakeLetsTheOperatorPriorSpeakLouder` | دست‌دهی بی‌نتیجه ⇒ حکم اندازه‌گیری‌شده ولی ارزان‌تر |
| `theCouncilNeverTouchesRoutingIdentityOrSplitTunnelling` | قانون اساسی ۳ |
| `aBlockPageMovesTheResolverAndRaisesTheHijackGuard` | صفحهٔ مسدودسازی ⇒ ریزولور سالم + نگهبان ربایش + Fake-IP |
| `anHonestResolverIsKeptAndItsEncryptionFollowsIt` | DoH همراه ریزولور می‌ماند |
| `aDeadIpv6PrefersIpv4WithoutEverDisablingIt` | `PREFER_IPV4`، IPv6 روشن، `UseIPv4`، HE=۱۲۰ |
| `bothFamiliesHealthyKeepsTheSmartRaceAndTheDefaultDelay` | SMART + ۶۰ms |
| `aMeasuredPathLeavesSixtyBytesOfHeadroomForTheTunnel` | ۱۴۷۲ ⇒ ۱۴۱۲ |
| `anUnmeasuredPathOnWifiKeepsTheAutomaticTunnelMtu` | اندازه‌نگرفته ⇒ ۰ (خودکار) |
| `aLossyPathWidensTheFallbacksAndReclaimsUdpMappingsSooner` | ۲۵٪ افت ⇒ ۴۵s و ۴ جانشین |
| `noUdpAtAllStopsSpendingBudgetOnUdpProbes` | UDP مرده ⇒ آزمون UDP خاموش، `reject` |
| `aFiltered443IsCriticalEvenWhenOtherPortsAnswer` | ۴۴۳ فیلتر ⇒ CRITICAL، بدون TFO |
| `aFastSteadyPathOnAStrongPhoneAsksForTheExtremeProfile` | EXTREME + STREAMING + ۵۱۲KB + ۲۴ |
| `aThrottledDeviceIsGivenTheEfficientProfileInsteadOfSpeed` | دما ⇒ EFFICIENT |
| `muxIsNeverEnabledOnARouteThatCannotCarryIt` | قانون ۵ |
| `theHighestMeasuredConfidenceWinsAndTheLoserIsQuoted` | مکانیک `resolve` + `dissentFor` |
| `everyAppliedValueIsBoundedToWhatTheCoresAccept` | قانون ۴ |
| `anUnmeasuredLossIsNotScoredAsACleanPath` | تلاش‌نشده ≠ بی‌افت |
| `aHealthyPathScoresAnAAndSaysSoPerSeat` | نمره/رتبه و دوازده رأی |
| `readAgreesWithTheValueTheSeatWrites` | خواندن و نوشتن برای هر ۳۱ فیلد |
| `theSavedVerdictRoundTripsThroughItsJsonForm` | ماندگاری گزارش |
| `aForeignOrBrokenDocumentIsRefusedNotGuessed` | گزارش بیگانه پذیرفته نمی‌شود |
