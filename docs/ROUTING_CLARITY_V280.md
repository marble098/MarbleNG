# ساده‌سازی و درست‌کردن «چه چیزی مستقیم می‌ره» (V280)

شناسهٔ این فصل: `MARBLE_ROUTING_CLARITY_V280`.

گزارش کاربر سه خواسته داشت: **۱)** بخش مسیریابی (What goes direct) باگ دارد و کار نمی‌کند؛
**۲)** رابط کاربری‌اش شلوغ و پیچیده است و باید ساده‌تر شود؛ **۳)** هر چیزی که کاربردی ندارد باید حذف
شود. این فصل ابتدا باگ‌های واقعی را که با خواندن کد و تست‌ها پیدا شد فهرست می‌کند و بعد ساختار تازه را
توضیح می‌دهد.

---

## ۱. باگ‌هایی که پیدا شد (و چرا «کار نمی‌کند»)

### ۱.۱ باگ مسدودکننده: یک قانون ذخیره‌شده کل اتصال را خراب می‌کرد

`RoutingEngine.applyUserRules` قانون‌های دستی را فقط وقتی منتشر می‌کرد که
`customRoutingEnabled` روشن باشد. اما سه جای دیگر همین شرط را نداشتند:

| جا | رفتار |
| --- | --- |
| `XrayConfigHardener.verify` | برای هر قانون `emittableUserRules` انتظار یک مسیر واقعی داشت |
| `SingBoxConfigBuilder` | قانون‌ها را بدون توجه به پرچم منتشر می‌کرد |
| `SingBoxGeoRuleSets` | همان |

نتیجه: کاربر یک قانون «کشوری» (geosite/geoip) اضافه می‌کرد → صفحه حالت را `CUSTOM` می‌کرد → بعد
حالت را روی «سایت‌های ایرانی مستقیم» می‌گذاشت (`customRoutingEnabled = false`). حالا قانون در کانفیگ
نبود ولی `verify` آن را طلب می‌کرد و `require` شکست می‌خورد: **Xray کل کانفیگ را رد می‌کرد و تونل
وصل نمی‌شد**. همین یک باگ برای توضیح «مسیریابی کار نمی‌کند» کافی است.

**اصلاح:** قانون‌ها همیشه اجرا می‌شوند (مثل sing-box). پرچم `customRoutingEnabled` از مسیر انتشار
حذف شد و `emittableUserRules` تنها معیار ماند. تست
`aSavedCountryRuleCannotMakeTheXrayConfigInvalid` این را ثابت می‌کند.

### ۱.۲ حالت `BYPASS_PRIVATE` هیچ کاری نمی‌کرد

`routeBypassPrivate` پیش‌فرض `true` است و هیچ صفحه‌ای آن را تغییر نمی‌داد، در حالی که
`applyUserRules` در **همهٔ** حالت‌ها بازه‌های خصوصی را اضافه می‌کند. پس `BYPASS_PRIVATE` و
`PROXY_ALL` دقیقاً یک کانفیگ می‌ساختند؛ یک انتخاب چهارم برای کاربر و صفر تفاوت.

### ۱.۳ کلید «سایت‌های ایرانی مستقیم» روی Xray بی‌اثر بود

`iranDomesticDirect` فقط لایهٔ «دقت داخلی» را کنترل می‌کرد و `implicitRules` برای
`GEO_DIRECT` بدون توجه به آن `geoip:ir`/`geosite:ir` را منتشر می‌کرد. sing-box همان تگ‌ها را با
`settings.iranDomesticDirect || it != "ir"` فیلتر می‌کرد؛ یعنی **یک کلید، دو رفتار بین دو هسته**.
کلید در صفحهٔ «Iran mode» بود ولی در صفحهٔ مسیریابی هیچ راهی برای روشن‌کردنش نبود («این فقط وقتی کار
می‌کند که … روشن باشد» بدون آن‌که بتوانی روشنش کنی).

**اصلاح:** `implicitRules` حالا در `GEO_DIRECT` هم همان کلید را رعایت می‌کند؛ هر دو هسته یک حرف
می‌زنند.

### ۱.۴ کلید «بستن تبلیغ‌ها» هیچ‌جا نبود

`routeBlockAds` پیش‌فرض روشن است، موتور آن را می‌خواند، خلاصهٔ صفحهٔ مسیریابی لایهٔ «تبلیغ‌ها» را
نشان می‌داد — ولی هیچ کنترلی در کل برنامه نداشت. یک لایهٔ سیاست بدون کلید.

**اصلاح:** کلید «Block ads and trackers» داخل کارت سیاست آمد (تنها جایی که معنی دارد).

### ۱.۵ شبیه‌ساز سه جا با هسته اختلاف داشت

- قانون‌های دستی را حتی وقتی خاموش بودند می‌خواند.
- لایهٔ «دقت داخلی» را **قبل** از قانون‌های دستی بررسی می‌کرد، در حالی که موتور آن را **بعد** از
  آن‌ها منتشر می‌کند؛ پس حکمی که کاربر می‌دید با حکم واقعی فرق داشت.
- بازه‌های خصوصی (`routeBypassPrivate`) را اصلاً نمی‌شناخت: `192.168.1.20` می‌گرفت «از تونل» در
  حالی که روتر آن را مستقیم می‌فرستاد.
- آمادگی فایل‌های geo را نمی‌دانست و لایه‌ای را «قطعاً» گزارش می‌کرد که هنوز دانلود نشده بود.

**اصلاح:** `simulate` دقیقاً ترتیب `applyUserRules` و `XrayConfigHardener` را راه می‌رود، آمادگی دو
فایل را می‌گیرد و برای هر لایه‌ای که فایلش نیست می‌گوید «هنوز دانلود نشده».

### ۱.۶ لایهٔ دقت داخلی می‌توانست outbound مستقیم را جا بیندازد

`needsDirectOutbound` بازه‌های خصوصی، تگ‌های کشوری و قانون‌های مستقیم را می‌دید، ولی
`precisionActive` را نه. اگر تگ‌های کشوری خالی می‌شدند و فقط لایهٔ دقت فعال بود، قانون‌های `direct`
بدون outbound `direct` نوشته می‌شدند و Xray کانفیگ را رد می‌کرد.

**اصلاح:** `precisionActive(settings)` هم بخشی از تصمیم است.

### ۱.۷ پرست‌ها فقط فهرست قانون را بازنویسی می‌کردند

شش «مجموعهٔ آماده» (Recommended/Privacy/Global/Iran/Russia/China) در عمل دو کار می‌کردند: نوشتن چند
قانون و روشن‌کردن `CUSTOM`. همهٔ آن‌ها با دو انتخاب سیاست و ویرایشگر قانون قابل بیان‌اند؛ سه‌تایشان
(Global/Iran/Russia/China در برابر حالت‌ها) تکرار محض بودند. حذف شدند (`core/RoutingPresets.kt`
پاک شد) و قانون‌های کشوری از طریق ویرایشگر با پیشنهاد تگ‌ها ساخته می‌شوند.

### ۱.۸ «هر دو TCP و UDP» یک کلید نیمه‌کاره بود

کلید فقط بین «هر دو» و «فقط TCP» جابه‌جا می‌شد؛ UDP تنها قابل انتخاب نبود.

**اصلاح:** یک انتخاب سه‌گزینه‌ای (Both / TCP only / UDP only).

---

## ۲. ساختار تازه

`core/RoutingOverview.kt` تنها جایی است که سیاست به زبان آدم ترجمه می‌شود:

- `Policy` = دو نتیجه: `IRAN_DIRECT` و `TUNNEL_ALL`.
- `policyOf(settings)` / `applyPolicy(settings, policy)` — تنها نویسندهٔ حالت؛
  `routingMode` و `iranDomesticDirect` را با هم می‌نویسد و اگر تگ‌های کشوری خالی شده باشند برمی‌گرداند.
- `layers(settings, geoIpReady, geoSiteReady)` — فقط لایه‌هایی که واقعاً قانون تولید می‌کنند.
- `missingLists`, `liveRules`, `duplicateMatchers`, `regexRules`, `legacyListCount`.

صفحه از بالا به پایین:

| بخش | چه چیزی نشان می‌دهد |
| --- | --- |
| Where your traffic goes | دو نتیجه، هر کدام یک جمله + کلید تبلیغ‌ها |
| Running right now | لایه‌های فعال با مقصدشان |
| Country lists | آمادگی دو فایل، یک دکمهٔ دانلود، منبع |
| How much of Iran stays direct | سه سطح دقت (فقط وقتی سیاست ایرانی روشن است) |
| My rules | همیشه فعال، با افزودن/ویرایش/توقف/جابه‌جایی/کپی/حذف |
| Try a site | ورودی + حکم + مراحل واقعی |
| Advanced | منبع دانلود، آدرس‌های دلخواه، انتقال فهرست‌های قدیمی، بررسی با هسته |

### چه چیزی حذف شد

- حالت `CUSTOM` و `BYPASS_PRIVATE` از رابط کاربری (enum برای سازگاری ذخیره‌سازی می‌ماند).
- کلید جداگانهٔ «Use my own rules».
- شش چیپ پرست.
- پنج جعبهٔ متنی «فهرست‌های متخصص»؛ به‌جای آن یک دکمه که هر فهرست را به قانون تبدیل می‌کند و
  جعبه را خالی می‌کند (`importLegacyRoutingLists`).
- کلیدهای ترکیب چند منبع در صفحه (تنظیمات ذخیره‌شده دست‌نخورده می‌مانند).
- کلید تکراری «سایت‌های ایرانی مستقیم» در کارت Iran mode (صفحهٔ مسیریابی صاحبش است).
- دو دکمهٔ ۴۸dp جابه‌جایی روی هر ردیف قانون؛ جابه‌جایی/کپی داخل شیت ویرایشگر رفت.

---

## ۳. فایل‌ها

| فایل | تغییر |
| --- | --- |
| `core/RoutingOverview.kt` | **بازنویسی.** دو سیاست، لایه‌ها، شمارش فهرست‌های قدیمی |
| `core/RoutingEngine.kt` | ترتیب انتشار (قانون کاربر اول)، حذف پرچم از انتشار، رعایت `iranDomesticDirect`، شبیه‌ساز هم‌ترتیب با هسته، `needsDirectOutbound` با لایهٔ دقت |
| `core/RoutingPresets.kt` | **حذف** |
| `AppRepository.kt` | `setRoutingPolicy`, `setAdBlocking`, `importLegacyRoutingLists`؛ حذف `setRoutingMode`/`setCustomRoutingEnabled`/`applyRoutingPreset` |
| `ui/SettingsRoutingPages.kt` | صفحهٔ تازه، ویرایشگر تازه، شیت Advanced |
| `ui/SettingsScreen.kt` | خلاصهٔ هاب از `RoutingOverview.policyOf` |
| `ui/MarblePersianLexicon.kt` | ترجمهٔ همهٔ رشته‌های تازه |
| `test/core/RoutingClarityV280Test.kt` | **فایل جدید.** ۱۵ تست برای باگ‌های بالا |
| `test/core/RoutingOverviewV254Test.kt` | حذف (قرارداد قدیمی) |

---

## ۴. تست‌ها

روی JVM و بدون دستگاه:

- `thePageWritesBothFlagsOfTheCountryPolicyTogether`
- `aPolicyChangeNeverTouchesTheRulesSomebodyWrote`
- `theCountryTagsComeBackIfAnOlderPassBlankedThem`
- `theIranSwitchStopsTheCountryRulesOnXrayToo`
- `aSavedCountryRuleCannotMakeTheXrayConfigInvalid`
- `xrayAndSingBoxAgreeThatARuleIsAlwaysLive`
- `theTunnelPolicyStillKeepsTheHomeNetworkOut`
- `theDomesticLayerStillNeedsADirectOutbound`
- `theLayersNameOnlyWhatTheFilesCanAnswer`
- `theTunnelPolicyAddsNoCountryLayer`
- `everyAdvertisedOutcomeHasASentenceAPersonWouldSay`
- `theHomeNetworkNeverRidesTheTunnel`
- `aWrittenRuleBeatsTheDomesticLayerAndTheSimulatorShowsIt`
- `aPausedRuleIsSkippedInsteadOfWinning`
- `aMissingSiteListIsNamedInsteadOfGuessed`
