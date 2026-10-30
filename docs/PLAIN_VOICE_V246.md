# MARBLE_PLAIN_VOICE_V246 — متنی که پشت پینگ دیده می‌شد، و صفحه‌ای که مثل دیتاشیت حرف می‌زد

> تاریخ: 2026-10-30 · برنچ: `arena/f927f980-marbleng`
>
> **دو گزارش، هر دو در یک جمله:**
>
> ۱. «وقتی توی صفحهٔ سرورها یک سرور رو انتخاب می‌کنم، پشت متن پینگ و آیکن سه‌نقطه یک متن ظاهر
> می‌شه.»
>
> ۲. «متون و عنوان‌های صفحهٔ تنظیمات (و بقیهٔ صفحه‌ها) پیچیده و غیرقابل‌فهمن؛ ساده‌تر و صمیمی‌تر
> بنویسید.»
>
> **نتیجه:** اولی یک لایهٔ متن نبود، بلکه برچسب حذف بود که از پشتِ ردیفِ نیمه‌شفاف دیده می‌شد؛ حالا
> فقط وقتی ردیف واقعاً کشیده می‌شود رسم می‌شود. دومی ۷۱ عنوان و جمله بود که همه با یک قاعدهٔ
> قابل‌تست بازنویسی شدند: بدون واژهٔ تخصصی، بدون ثابتِ خام، بدون جملهٔ بلندتر، با معنی دقیقاً همان.
> و در راهِ همین دومی یک باگ سوم پیدا شد: در حالت فارسی **همهٔ عددها خالی چاپ می‌شدند**.

---

## ۱. متنی که پشت پینگ دیده می‌شد

گزارش دقیق بود و تشخیص، سه لایه پایین‌تر:

| لایه | چه چیزی | نتیجه |
|---|---|---|
| ردیف عادی | `background(surfaceContainerLow)` — کاملاً مات | چیزی از پشت دیده نمی‌شود |
| ردیف انتخاب‌شده | `secondaryContainer.copy(alpha = 0.38f)` | ۶۲٪ ردیف شفاف است |
| پشت ردیف | `SwipeToDismissBox.backgroundContent` = «Delete 🗑» با `CenterEnd` | دقیقاً همان‌جایی که ستون پینگ و ⋯ هستند |

یعنی برچسب حذف همیشه آن‌جا بود، ولی فقط وقتی ردیف مات بود دیده نمی‌شد. به‌محض انتخاب، ردیف شفاف
می‌شد و متن از پشتش بیرون می‌زد — «یک متن پشت پینگ و سه‌نقطه». حالت `active` هم همین باگ را داشت
(`primaryContainer` با آلفای ۰٫۵۵).

**اصلاح:** افشای صحنه فقط وقتی رسم می‌شود که ردیف واقعاً کشیده می‌شود.

```
ServersRowSurface.revealAlpha(progress):
    progress ≤ 0.02  → 0        // لرزش انگشت، swipe نیست
    progress ≥ 0.18  → 1        // بعد از ۱۸٪ مسیر، کاملاً دیده می‌شود
    بین این دو       → خطی
```

و در UI یک `graphicsLayer { alpha = revealAlpha.value }` روی همان `backgroundContent` — خوانده‌شده
در فاز رسم، پس کشیدن ردیف هیچ recompositionی نمی‌سازد. سه چیز با هم درست شد: متنی پشت پینگ نیست،
افشا هنگام swipe کامل و صادق است، و ردیف انتخاب‌شده می‌تواند شفاف بماند تا aurora پشتش نفس بکشد.

## ۲. چرا متن تنظیمات سخت بود

مشکل «ادبیات» نبود، «نام‌گذاری» بود: هر قابلیت به اسم مکانیزمی نام‌گذاری شده بود که آن را
پیاده می‌کند، نه کاری که برای کاربر انجام می‌دهد.

| روی صفحه بود | کاربر چه می‌خواست | حالا |
|---|---|---|
| `Tunnel Datapath` | «تونل چطور کار می‌کنه؟» | `Tunnel path` |
| `Geo direct domestic separation` | «سایت‌های ایرانی مستقیم باز بشن» | `Iranian sites stay direct` |
| `Detect DPI filtering, TLS resets and apply prescriptions` | «چی جلوم رو گرفته؟ درستش کن» | `Find what blocks you and fix it` |
| `Network Council Scanner` | «شبکه‌ام رو بررسی کن» | `Network check` |
| `Commission the council` | «شروع کن» | `Run the check` |
| `Apply Recommended Prescription` | «همون رو اعمال کن» | `Apply what it suggests` |
| `Last verdict` | «نتیجهٔ آخر» | `Last result` |
| `Identity Guard & Locations` | «آی‌پی واقعی‌ام رو بپوشون» | `Hide my real IP` |
| `Diagnostics, Logs & Bug Finder` | «عیب‌یابی و رفع» | `Fix & diagnose` |
| `Per-App Split Tunnel` | «کدوم برنامه‌ها از تونل رد بشن؟» | `Which apps use the tunnel` |
| `A preset bundles the tuning knobs behind a named decision` | «این حالت‌ها دقیقاً چی رو عوض می‌کنن؟» | `Pick a mode and the app tunes itself` |
| `Turbo power, aggressive jitter reduction and raced connects — built for latency, not bulk.` | «این یکی برای چیه؟» | `Full power and quicker connects. Made for games, not downloads.` |

فهرست کامل ۷۱ مورد در `PlainVoice.REWRITES` است — همان‌جا که تست می‌خواندش.

### ۲.۱ رشته‌هایی که رشته نبودند

بخشی از متنِ سخت اصلاً متن نبود: نامِ ثابت‌های کاتلین بود که وسط یک جمله چاپ می‌شد، برای همین
هیچ فرهنگ لغتی نمی‌گرفتشان و هیچ ممیزی نمی‌دیدشان.

| روی صفحه بود | از کجا می‌آمد | حالا |
|---|---|---|
| `1.1.1.1 · FORCE_IPV4` | `s.addressFamilyMode.name` | `1.1.1.1 · IPv4 only` |
| `On · LEAST_PING` | `s.autoServerStrategyEnum.name` | `On · Least ping` |
| `Wi-Fi · DNS UseIPv4` | `plan.dnsQueryStrategy` | `Wi-Fi · DNS IPv4 only` |
| `GEOSITE: category-ads-all` | `rule.kind.name` در عنوان ردیف و در لیست انتخاب | `Site: category-ads-all` |
| `✓ www.gstatic.com (ALT_PORT) 214ms` | `obs.kind.name` | `✓ www.gstatic.com Other port 214ms` |

اکنون `PlainVoice.addressFamilyLabel` و `PlainVoice.dnsStrategyLabel` این تبدیل را در یک‌جا نگه
می‌دارند، `AutoServerSelector.shortLabel` همان کاری را می‌کند که از قبل برای پیام‌های زنده می‌کرد،
و `RoutingRuleKind` و `ProbeKind` یک `label` گرفتند. نامِ ثابت‌ها عوض نشده — همان چیزی است که ذخیره
می‌شود؛ فقط دیگر روی صفحه چاپ نمی‌شود.

## ۳. قاعدهٔ بازنویسی

چهار شرط، هر چهارتا ماشینی و قابل تست، نه سلیقه‌ای:

1. **هیچ واژهٔ تخصصی زنده نماند.** `PlainVoice.JARGON` چهارده واژه را ممنوع می‌کند:
   `Datapath`, `Prescription(s)`, `Verdict`, `Seat(s)`, `Telemetry`, `Orchestrat`, `Hardening`,
   `RFC1918`, `GeoSite`, `GeoIP`, `Geo direct`, `DPI`.
2. **بازنویسی باید چیز دیگری بگوید.** جملهٔ عیناً یکسان، بازنویسی نیست.
3. **هیچ ثابتی در جای کلمه چاپ نشود.** `FORCE_IPV4`, `LEAST_PING`, `GEOSITE`, `ALT_PORT` و
   `UseIPv4` همه از این قاعده حذف شدند؛ `PlainVoice` جدول‌های `ADDRESS_FAMILY_LABELS` و
   `DNS_STRATEGY_LABELS` را نگه می‌دارد و تست، هر مقدارشان را هم با همان قاعدهٔ بی‌واژهٔ‌تخصصی می‌سنجد.
4. **جمله حداکثر ۲۵٪ بلندتر شود.** روشن‌تر شدن چند حرف می‌ارزد، ولی یک برچسب نباید پاراگراف شود.
   `Proxy Core Engine` می‌شود `Proxy engine`، نه «The engine that runs every proxy on this device».

و یک لایهٔ سوم که تست JVM نمی‌تواند ببیند: **فارسیِ هر بازنویسی**. ۷۶ مدخل جدید در
`MarblePersianLexicon.kt` زیر همان مارکر اضافه شد، با همان لحن V233 — دوم‌شخص، فعل کوتاه، دوستانه:

| انگلیسی | فارسی |
|---|---|
| `How you connect` | چطور وصل می‌شی |
| `Which apps use the tunnel` | کدوم برنامه‌ها از تونل استفاده کنن |
| `Hide my real IP` | آی‌پی واقعی‌ام رو بپوشون |
| `Run the check` | بررسی رو شروع کن |
| `Objected` | مخالف بود |
| `Only when you ask` | فقط وقتی خودت بخوای |
| `Least ping` | کم‌ترین پینگ |
| `One-tap modes` | حالت‌های تک‌ضربه‌ای |
| `Blocked network` | شبکه بسته |

این‌ها همان ۲۴ رشته‌ای را هم پوشش می‌دهند که تا امروز در حالت فارسی **انگلیسی می‌ماندند**
(`Accent Color`, `Text Size`, `Geo Direct`, `All Apps`, `Route-Only Sniffing`, …) — یعنی نیمی از
«غیرقابل‌فهم بودن» این بود که صفحهٔ فارسی وسطش انگلیسی بود.

## ۳.۱ باگی که سرِ راه پیدا شد: عددهای فارسی خالی بودند

وقتی تستِ فارسیِ پیل را می‌نوشتیم، نشانگرِ «۲» روی شیشه پیدا نمی‌شد. علت در هیچ ترجمه‌ای نبود:

```kotlin
fun String.toFaDigits(): String = buildString(length) {
    forEach { c -> … }        // ← this is the StringBuilder, and it is empty right now
}
```

داخل `buildString` نزدیک‌ترین `CharSequence` به `forEach` همان `StringBuilder` است، نه رشته‌ای که
داریم ترجمه می‌کنیم؛ پس حلقه روی یک رشتهٔ خالی می‌گشت و تابع **همیشه `""`** برمی‌گرداند. یعنی در
حالت فارسی پینگ، شمارنده‌ها، حجم‌ها و نشانِ اعلان‌ها خالی بودند — نه غلط، نه انگلیسی: خالی.

```kotlin
this@toFaDigits.forEach { c -> … }   // ← the receiver, named
```

`PlainVoiceV246Test.persianNumbersAreNotBlank` این را قفل می‌کند (`"12.5"` → `"۱۲٫۵"`)، و
`MarbleLiquidDockV245Test` نشان می‌دهد نشانِ قرمز در چیدمان راست‌به‌چپ هم سرِ جایش هست.

## ۴. جدول اجرا

| خواسته | فایل | وضعیت |
|---|---|---|
| متن پشت پینگ و ⋯ دیگر دیده نشود | `core/ServersDeck.kt` + `ui/ServersScreen.kt` | ✅ |
| افشای حذف فقط هنگام swipe | `ServersRowSurface.revealAlpha` | ✅ |
| بدون recomposition هنگام کشیدن ردیف | `graphicsLayer { alpha = … }` | ✅ |
| عنوان‌های ساده و صمیمی در تنظیمات | `Settings*.kt` (۸ فایل، ۷۱ مورد) | ✅ |
| ثابت‌ها دیگر وسط جمله چاپ نشوند | `PlainVoice` + `RoutingRuleKind.label` + `ProbeKind.label` | ✅ |
| حالت‌های تک‌ضربه‌ای یک جملهٔ آدم‌یزادی داشته باشند | `model/ExpressiveShell.kt` + `ui/MarbleExpressiveShell.kt` | ✅ |
| فارسیِ ساده برای همهٔ عنوان‌های جدید | `MarblePersianLexicon.kt` (+۷۶ مدخل) | ✅ |
| عددهای فارسی دیگر خالی نباشند | `ui/UiFormatters.kt` (`this@toFaDigits`) | ✅ |
| پوشش رشته‌هایی که انگلیسی می‌ماندند | همان بلوک | ✅ |
| قاعدهٔ قابل‌تست به‌جای سلیقه | `model/PlainVoice.kt` | ✅ |
| تست JVM | `PlainVoiceV246Test` | ✅ |
| تست Compose | `ServerRowLayoutV242Test` (ردیف انتخاب‌شده) | ✅ |

## ۵. آنچه عمداً انجام نشد

1. **بازنویسی هر ۴۴۰ رشتهٔ تنظیمات.** ۷۱ مورد، همهٔ عنوان‌های صفحهٔ اصلی تنظیمات و سخت‌ترین
   عنوان‌های صفحات داخلی را پوشش می‌دهد؛ بقیه (`Apply`, `Delete`, `Search`, `DNS`, `MTU`) از قبل
   ساده بودند یا اصطلاح فنیِ لازم‌اند و ترجمه‌شان «ساده‌تر» نمی‌شود، فقط مبهم‌تر می‌شود.
2. **حذف مدخل‌های قدیمی فرهنگ لغت.** `"Tunnel Datapath" to "…"` می‌ماند؛ چیزی آن را نمی‌خواند ولی
   `PersianCoverageV233Test` به آن ارجاع می‌دهد و پاک‌کردنش یک تست سبز را قرمز می‌کرد بدون آن‌که
   چیزی روی صفحه عوض شود.
3. **عوض‌کردن رنگِ ردیف انتخاب‌شده.** ریشهٔ باگ شفافیت نبود، *رسمِ* برچسب در حالت ساکن بود.
   ردیف همان‌طور شفاف می‌ماند تا aurora پشتش دیده شود؛ افشا حالا فقط وقتی هست که واقعاً چیزی
   افشا می‌شود.
4. **جابه‌جا کردن دکمهٔ حذف.** «به جای بهتری برود» با «فقط وقتی لازم است دیده شود» پاسخ گرفت:
   جای هندسی‌اش (لبهٔ پایان ردیف) درست بود، زمانِ حضورش غلط بود.
5. **تغییر نامِ ثابت‌های ذخیره‌شده.** `GEOSITE` و `FORCE_IPV4` در فایل تنظیمات و در کانفیگ ذخیره
   می‌شوند؛ عوض‌کردنشان مهاجرت می‌خواست. یک `label` کنارشان اضافه شد و صفحه فقط `label` را
   می‌خواند.

## ۶. تست‌ها

| تست | چه چیزی را قفل می‌کند |
|---|---|
| `model/PlainVoiceV246Test.kt` | صفر بودن alpha در حالت ساکن، یک‌شدنش روی ۱۸٪، یکنوایی‌اش، رد کردن NaN، اینکه هر ۷۱ بازنویسی بدون واژهٔ ممنوعه، غیرتکراری و حداکثر ۲۵٪ بلندتر است، و اینکه `FORCE_IPV4` و `UseIPv4` دیگر خام چاپ نمی‌شوند |
| `ui/ServerRowLayoutV242Test.kt` | ردیف انتخاب‌شده هنوز ستون پینگ ۸۸dp و هدف ۴۸dp و RTL سالم را نگه می‌دارد |
