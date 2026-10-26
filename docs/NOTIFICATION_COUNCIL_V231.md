# شورای ۵ نفره اعلان‌ها — نقد کامل اپ نوتفیکیشن و بازآفرینی آن

> تاریخ: 2026-10-26 • شاخه: `arena/3f5dc6cf-marbleng` • مرجع: `6276f8c`
>
> درخواست: «طراحی نوتفیکیشن اپ را کاملاً نقد کن و بر اساس شورای ۵ نفره که هر کدام متخصص هستند نقد کن و به سطح بالاتری برسان.»
>
> این سند نقد است، نه تبلیغ. هر یافته با **شاهد در کد** و هر تصمیم با **رأی شورا** ثبت شده است. آنچه اجرا شد در بخش ۴ آمده و آنچه عمداً اجرا نشد در بخش ۵ — چون در این محیط قابل راستی‌آزمایی نبود.

---

## ۱. ترکیب شورا

| عضو | تخصص | لنز نقد |
|---|---|---|
| دکتر لیلا فرهادی | UX Researcher | آنچه کاربر در ۳ ثانیه از نوار وضعیت می‌فهمد؛ زبان، حالت‌ها، بار شناختی |
| کیان توکلی | Visual / Product Designer | سلسله‌مراتب بصری، آیکون، رنگ، تفکیک «وضعیت» از «هشدار» |
| آرمان بهزاد | Android Platform Engineer (Notifications) | کانال‌ها، گروه، chronometer، PendingIntent، نرخ به‌روزرسانی، نشتی حافظه |
| سارا مرادی | Product & IA Strategist | نگاشت رویداد → کانال → صفحه؛ کلیدهای مرده؛ کشف‌پذیری تنظیمات |
| رضا احمدی | Accessibility & i18n Engineer | فارسی/RTL، جداسازی دوطرفه (bidi)، اندازه متن، کنتراست، TalkBack، لمس |

قاعده‌ی کار: هر نقد باید **شاهد کد** داشته باشد. پیشنهادِ بدون شاهد رد می‌شد. تصمیم نهایی با اجماع یا اکثریت ۴ از ۵.

---

## ۲. نقد سطح‌به‌سطح (با شاهد کد)

### ۲.۱ نوار وضعیت (Ongoing notification) — «یک رشته‌ی چسبانده‌شده به‌جای یک وضعیت»

- **بهزاد:** اعلان همیشگی محصولِ یازده نقطه بود که هر کدام `"STATE • route • metric"` را با `notifyNow(...)` می‌ساختند:

  ```kotlin
  notifyNow("Protected • $name", true, "$ping • ↓ … • ↑ …")
  ```

  نتیجه: شید نمی‌دانست کدام بخش «وضعیت» است، کدام «مسیر» و کدام «اندازه‌گیری». عنوان هم همیشه `"MarbleNG"` بود (`updateConnection(NOTIFY, "MarbleNG", body, ongoing)`) — یعنی گران‌ترین جای اعلان، نام برند را تکرار می‌کرد و وضعیت را به `subText` تبعیدی می‌فرستاد.
  → **تصویب ۵/۵:** وضعیت یک **مقدار** شود، نه رشته. `ConnectionNotificationState` + `ConnectionStatus` (پنج حالت: `CONNECTING`, `PROTECTED`, `RECOVERING`, `BLOCKED`, `DISCONNECTED`). عنوان = وضعیت، متن = مسیر، خط اندازه‌گیری = یک سگمنت ایزوله‌شده.

- **توکلی:** هیچ تفکیک بصری بین «من محافظت‌شده‌ام» و «ترافیک من مسدود شده» نبود؛ هر دو یک آیکون و یک رنگ داشتند. آیکون فقط `ic_notification` بود، حتی برای هشدار حریم خصوصی.
  → **تصویب ۵/۵:** چهار آیکون سیلوئت هم‌وزن (۲.۱ واحد، همان گرید ۲۴): `ic_notification` (تونل)، `ic_notification_privacy` (سپر)، `ic_notification_reconnect` (فلش بازگشت)، `ic_notification_update` (سینی دریافت)، `ic_notification_manage` (اسلایدرها).

- **فرهادی:** کاربر برای دانستن «چند دقیقه است وصل هستم؟» عددی نداشت؛ نشست بی‌زمان بود.
  → **تصویب ۵/۵:** `setUsesChronometer(true)` + `setWhen(sessionStartedAtMs)`؛ ساعت را اندروید می‌کشد، نه ما. کلید کاربر: «Session Clock».

- **بهزاد (نقد دوم، اجرایی):** `notifyNow` هر ۵ ثانیه اجرا می‌شد، حتی وقتی هیچ عددی تغییر نکرده بود (حلقه‌ی تلمتری یک‌ثانیه‌ای، `if (tick % 5 == 0)`). هر تماس `manager.notify` یعنی binder + relayout در SystemUI.
  → **تصویب ۵/۵:** دروازه‌ی امضا: `ConnectionSignatureGate` — اگر امضای وضعیت (`status|route|ping|↓|↑|timer|iran|detail|language`) عوض نشده باشد، اعلان اصلاً ارسال نمی‌شود؛ و اگر عوض شده باشد، حداکثر یک‌بار در ثانیه.

- **بهزاد (نقد سوم):** `setForegroundServiceBehavior(IMMEDIATE)` همیشه — یعنی هنگام اتصال، اعلان بلافاصله می‌پرید و صفحه را می‌پوشاند.
  → **تصویب ۴/۵:** `IMMEDIATE` فقط در `CONNECTING`، در بقیه‌ی حالت‌ها `DEFERRED`.

### ۲.۲ موتور هشدار — «سه کانال برای پنج نوع رویداد، بدون گروه، با جدول بی‌کران»

- **مرادی:** رویدادها هفت نوع‌اند (`CONNECTION`, `RECOVERY`, `PRIVACY`, `NETWORK`, `SUBSCRIPTION`, `CORE`, `TEST`) اما کانال‌ها سه‌تا. هشدار حریم خصوصی با `IMPORTANCE_DEFAULT` و `CATEGORY_ERROR` می‌رفت؛ روی Android 8+ مقدار `PRIORITY` نادیده گرفته می‌شود، پس «خطای مهم» هرگز مهم نبود.
  → **تصویب ۵/۵:** کانال چهارم `marbleng-privacy` با `IMPORTANCE_HIGH` + الگوی لرزش؛ و کلید کاربر «Privacy Alarms Can Interrupt» که همان هشدار را روی کانال `marbleng-smart` (آرام) می‌برد.

- **بهزاد (نشتی حافظه):** `lastEventAt` یک `ConcurrentHashMap<String, Long>` بود با کلید `"kind:session:profileId"` و **هیچ‌گاه پاک نمی‌شد** جز با «Clear Alerts». روی دستگاهی که مکرر وصل/قطع می‌شود، تا پایان عمر پروسه رشد می‌کرد. `optionalIds` هم همین‌طور.
  → **تصویب ۵/۵:** `NotificationCooldown` (حداکثر ۴۸ کلید، TTL ۳۰ دقیقه، prune در هر نوشتن) و `NotificationIdAllocator` (حداکثر ۶۴ کلید، آزادسازی id در eviction).

- **بهزاد (تصادم id):** شناسه از هش رشته محاسبه می‌شد: `OPTIONAL_ID_BASE + (safeHash % OPTIONAL_ID_RANGE)`. دو رویداد مختلف می‌توانستند یک id بگیرند و اعلان همدیگر را **بی‌صدا بازنویسی** کنند.
  → **تصویب ۵/۵:** `NotificationIdAllocator` با نقشه‌ی پایدار key→id و پیمایش به خانه‌ی آزاد بعدی.

- **توکلی:** `.setGroup(GROUP_ID)` بود ولی **گروه‌بند (summary)** نبود؛ یعنی Android گروه را بدون سرصفحه نشان می‌داد و پنج هشدار پشت‌سرهم پنج صدای جدا می‌ساختند.
  → **تصویب ۵/۵:** اعلان خلاصه (`setGroupSummary(true)`، بی‌صدا، شمارش‌شده: «۳ هشدار در انتظار») + `GROUP_ALERT_CHILDREN` برای فرزندان.

- **احمدی:** متن‌ها انگلیسیِ خام بودند در محصولی که فارسی دارد. `trx()` فقط داخل Compose کار می‌کرد، پس لایه‌ی اعلان هیچ راهی به واژه‌نامه نداشت. و `faFallback` (ترنسلیتره) برای یک اعلان قفل‌صفحه فاجعه است: `Kill switch is holding traffic` به حروف فارسی.
  → **تصویب ۴/۵:** `NotificationCopy.translate()` با واژه‌نامه‌ی اختصاصی اعلان (بیش از ۷۰ رشته) + الگوهای `Moving to X` / `Switching to X`؛ و قانون سخت: **رشته‌ی ناشناس انگلیسی می‌ماند**، هیچ‌وقت ترنسلیتره نمی‌شود.

- **فرهادی:** عنوان هشدار همیشه کلی بود و کاربر نمی‌دانست با زدن روی آن کجا می‌رود (همه‌جا `MainActivity`).
  → **تصویب ۵/۵:** هر نوع، مسیر خودش را دارد: حریم خصوصی/بازیابی/شبکه → تشخیص‌ها، اشتراک → صفحه اشتراک‌ها، به‌روزرسانی/اتصال → داشبورد، آزمایش → تنظیمات اعلان.

### ۲.۳ کلیک اعلان — «یک PendingIntent برای همه»

- **بهزاد:** `openAppIntent()` همیشه `requestCode = 7302` با `FLAG_UPDATE_CURRENT` بود. یعنی همه‌ی اعلان‌ها **یک** Intent داشتند؛ افزودن اکسترای «صفحه‌ی مقصد» به این ساختار بی‌معنا بود، چون آخرین اعلان اکستراهای بقیه را بازنویسی می‌کرد.
  → **تصویب ۵/۵:** یک request code به‌ازای هر مسیر (`REQUEST_OPEN_BASE + route.ordinal`) + `setData(Uri.parse("marbleng://notify/<route>"))` برای یکتایی Intent. سپس اکسترای `marble.page`، مصرف یک‌باره در `MainActivity` و ناوبری در `MarbleApp`.

### ۲.۴ صفحه‌ی تنظیمات اعلان — «توصیف اعلانی که هرگز نشان داده نمی‌شد»

- **فرهادی:** صفحه فقط ردیف سوییچ بود. کاربر نمی‌توانست ببیند نتیجه چه شکلی است. چهار کانال، اهمیت‌شان و وضعیت بی‌صدا/مسدود بودنشان هیچ‌جا دیده نمی‌شد.
  → **تصویب ۵/۵:** کارت «پیش‌نمایش زنده» که با همان توابع واقعی (`statusTitle`, `telemetryCompact`, `statusSubText`) ساخته می‌شود — همان چیزی که شید می‌گیرد.

- **مرادی:** دو کلید مدل (`notifyNetworkChanges`, `notifyCoreUpdates`) در صفحه **ردیف نداشتند**؛ و از آن بدتر، `notifyCoreUpdates` در کل کد **هیچ تولیدکننده‌ای نداشت** — یک سوییچ مرده.
  → **تصویب ۵/۵:** هر هفت کلید در صفحه؛ و برای `CORE` یک تولیدکننده‌ی واقعی: هنگام آماده‌بودن نسخه‌ی تازه (`App update available`)، با سقف ۶ ساعت.

- **مرادی (کشف‌پذیری):** هیچ راهی به صفحه‌ی کانال اندروید نبود؛ کاربر برای بی‌صدا کردن یک نوع هشدار باید خودش مسیر سیستم را پیدا می‌کرد.
  → **تصویب ۵/۵:** ردیف کانال‌ها با نشان وضعیت (`ON` / `LOUD` / `QUIET` / `MUTED` / `OFF`) و دیپ‌لینک به `ACTION_CHANNEL_NOTIFICATION_SETTINGS` با `EXTRA_CHANNEL_ID`.

- **احمدی:** دکمه‌ی «Send Test Alert» غیرفعال می‌شد و **دلیل** را نمی‌گفت.
  → **تصویب ۵/۵:** فوتر دلیل زیر دکمه‌ها: «Android is blocking notifications…» یا «Turn on Smart Event Alerts…»؛ و پیام نتیجه‌ی تست هم دیگر یک جمله‌ی مبهم نیست.

- **احمدی:** سه حالت دسترسی (نپرسیده / رد‌شده / در سطح اپ خاموش) از هم تفکیک نمی‌شد و دکمه همیشه یک کار می‌کرد.
  → **تصویب ۴/۵:** نشان `ASK` / `BLOCKED` / `ON`، و ردیف اضافی «بازکردن تنظیمات اعلان اندروید» وقتی اجازه مسدود شده است.

### ۲.۵ اعلان «برنامه به‌روزرسانی شد» — «یک هشدار روی کانال بی‌صدا»

- **بهزاد:** `MarblePackageReplacedReceiver` روی `SmartNotifier.CHANNEL_CONNECTION` (کانال همیشگی، `IMPORTANCE_LOW`، `setSound(null, null)`) یک اعلان با `PRIORITY_DEFAULT` می‌فرستاد — یعنی هشداری که باید دیده می‌شد، در آرام‌ترین کانال دفن می‌شد. بدون اکشن، بدون ترجمه، و با یک id ثالث (`7302`).
  → **تصویب ۵/۵:** `SmartNotifier.restoreNotice()` روی کانال هشدار، با اکشن «Restore» که همان مسیر مجوزدار اتصال را طی می‌کند.

### ۲.۶ دیالوگ دسترسی

- **فرهادی و احمدی:** متن دیالوگ خوب بود اما «اگر رد کردی چه می‌شود؟» را نمی‌گفت؛ و مسیر بازیابی (تنظیمات سیستم) در آن نبود.
  → **تصویب ۴/۵:** توضیح صریح در صفحه‌ی تنظیمات (بخش ۲.۴) و پیام نتیجه‌ی تست؛ متن دیالوگ دست‌نخورده ماند چون واقعاً روشن بود (وتوی توکلی بر بازنویسی صرفاً سبکی).

---

## ۳. نقد سیستمیک (پنج عضو، اجماع)

1. **اعلان‌ها یک «اپ» نبودند؛ یک تابع کمکی بودند.** هیچ‌جا وضعیت، هویت بصری، زبان و مقصد یکجا تعریف نشده بود.
2. **تست‌ناپذیری، علت اصلی بدهی بود.** هر چیزی که «اعلان چه می‌گوید» را تصمیم می‌گرفت داخل کلاسی بود که `Context` می‌خواست.
3. **دو زبان و یک صدا.** محصولی که RTL را جدی گرفته بود، روی قفل‌صفحه انگلیسی حرف می‌زد.
4. **بی‌کرانی، نه بی‌دقتی.** جدول‌های `HashMap` که هرگز پاک نمی‌شدند یک باگ طراحی بودند، نه یک فراموشی.
5. **تنظیمات بدون بازخورد.** کلیدی که اثرش دیده نمی‌شود، کلید نیست.

---

## ۴. جدول اجرا — چه چیزی ساخته، عوض یا حذف شد

| فایل | افزوده | حذف/اصلاح | رأی |
|---|---|---|---|
| `core/NotificationCopy.kt` (نو) | `NotificationLanguage`, `NotificationRoute`, `NotificationChannelKey`, `ConnectionStatus`-aware copy, `isolate/rtlLine/ellipsize/rate/latency/telemetryCompact/telemetryExpanded`, `translate` + واژه‌نامه‌ی ۷۰+ رشته‌ای, `NotificationCooldown`, `NotificationIdAllocator`, `ConnectionSignatureGate` | — | ۵/۵ |
| `core/SmartNotifier.kt` | `ConnectionNotificationState` + `signature()`, سازنده‌ی اعلان همیشگی از وضعیت، chronometer، progress، اکشن Reconnect/Disconnect، کانال چهارم Privacy، گروه‌بند و شمارش، اکشن Manage با دیپ‌لینک کانال، `restoreNotice()`, `openAppIntent(route)`, زبان‌محور شدن کانال‌ها | رشته‌های چسبانده‌شده، `ConcurrentHashMap` بی‌کران، id بر پایه‌ی هش، title ثابت `"MarbleNG"`، `setShowWhen(false)` همیشگی | ۵/۵ |
| `vpn/MarbleVpnService.kt` | `connectionState()`, `publishStatus()`, `promoteStatus()`, `sessionStartedAtMs`, منبع زبان | `promoteForeground`/`notifyNow` حذف شدند؛ ۱۱ نقطه‌ی رشته‌ساز جای خود را به وضعیت دادند | ۵/۵ |
| `MainActivity.kt` | `handleNotificationIntent()`, اکشن `ACTION_RECONNECT_LAST` با همان دروازه‌ی مجوز | — | ۵/۵ |
| `ui/MarbleApp.kt` | مصرف یک‌باره‌ی `pendingNotificationPage` و نگاشت مسیر → صفحه | — | ۵/۵ |
| `AppRepository.kt` | `pendingNotificationPage`/`request`/`consume`, منبع زبان برای notifier، هشدار CORE روی نسخه‌ی تازه، دلیل دقیق شکست تست | — | ۵/۵ |
| `MarblePackageReplacedReceiver.kt` | استفاده از `restoreNotice()` | اعلان انگلیسی روی کانال بی‌صدا و بدون اکشن | ۵/۵ |
| `ui/SettingsSystemPages.kt` | کارت پیش‌نمایش زنده، جدول چهار کانال با دیپ‌لینک، هر هفت کلید، دو کلید تازه، نشان وضعیت دسترسی، فوتر دلیل | توصیف بدون نمایش؛ کلیدهای پنهان؛ دکمه‌ی بی‌دلیل | ۵/۵ |
| `model/Models.kt` + `data/AppStore.kt` | `notifyPrivacyHeadsUp`, `notificationSessionTimer` | — | ۵/۵ |
| `res/drawable/` | `ic_notification_privacy`, `ic_notification_reconnect`, `ic_notification_update`, `ic_notification_manage` | استفاده از آیکون تونل برای همه‌ی انواع | ۵/۵ |
| `ui/MarblePersianLexicon.kt` | ۵۰+ رشته‌ی تازه‌ی فارسی برای صفحه و پیش‌نمایش | کلیدهای تکراری واژه‌نامه یکتا شدند (سه ترجمه‌ی «آخرین» به‌عنوان مرجع حفظ شد) | ۵/۵ |
| `app/src/test/.../NotificationCopyV231Test.kt` (نو) | ۱۲ تست JVM: bidi، ellipsis، عدد/واحد، واژه‌نامه، کانال‌ها، cooldown، id، دروازه‌ی امضا | — | ۵/۵ |
| `scripts/system-integrity-check.py` | بلوک V231 با ۱۲ ناوردا | ناوردای V213 برای `kindSubText` به شکل زبان‌محور به‌روز شد | ۵/۵ |

### آنچه حذف شد و برنگشت

- `promoteForeground(text)` و `notifyNow(text)` — دو دروازه‌ی رشته‌ای که هر کدام می‌توانستند وضعیت را از دست بدهند.
- `OPTIONAL_ID_BASE + hash % range` — تصادم خاموش.
- `lastEventAt: ConcurrentHashMap<String, Long>` بی‌کران.
- کلید `notifyCoreUpdates` بی‌تولیدکننده و کلیدهای `notifyNetworkChanges` بی‌ردیف.
- اعلان انگلیسی روی کانال بی‌صدا در receiver.

---

## ۵. آنچه عمداً انجام نشد

- **پروموشن اعلان همیشگی (Android 16 promoted ongoing).** قرارداد آن یک تعهد قابل‌سنجش «به‌روزرسانی دوره‌ای» است که باید روی دستگاه واقعی دیده شود؛ در این محیط قابل راستی‌آزمایی نیست. ادعای بدون اثبات، همان چیزی است که این مخزن از آن پرهیز می‌کند.
- **Live Update / bubble / full-screen intent.** هیچ‌کدام برای یک تونل مناسب نیستند و اولی نیازمند مجوز اضافی است.
- **بازنویسی متن دیالوگ دسترسی.** روشن بود؛ تغییرش سلیقه بود، نه نقص.
- **دکمه‌ی «Mute» داخل خود اعلان برای همیشه.** اکشن «Manage» به صفحه‌ی کانال اندروید می‌برد؛ mute واقعی باید در سطح سیستم بماند تا کاربر بتواند بازگرداند.

---

## ۶. راستی‌آزمایی

- `NotificationCopyV231Test` — ۱۲ تست روی JVM: `languageFor`، ایزوله‌ی bidi، ellipsis بدون شکستن گرافم، قالب عدد/واحد، یکتایی عنوان پنج وضعیت، واژه‌نامه و قانون «ترنسلیتره نکن»، یکتایی/ترتیب کانال‌ها، گردش مسیرها، کران‌داری cooldown، پایداری و بدون‌تصادم بودن id، دروازه‌ی امضا و کامل‌بودن امضای وضعیت.
- `scripts/system-integrity-check.py` — بلوک `MARBLE_NOTIFICATION_COUNCIL_V231` با ۱۲ ناوردا: تاکسونومی کانال، واژگان وضعیت، ساخت اعلان از وضعیت (chronometer/progress/actions)، دروازه‌ی امضا، گروه‌بندی و کران‌داری، کانال حریم خصوصی، ساعت نشست، فارسی/bidi، دیپ‌لینک، صفحه‌ی تنظیمات، کلیدهای زنده‌شده، تست و این سند.
- `python3 tools/kotlin-structure-check.py` و `python3 tools/compose-scope-check.py` — بدون خطا.
- CI: `:app:testDebugUnitTest` + `:app:compileDebugKotlin` + `:app:compileReleaseKotlin` روی همان ورک‌فلوی `verify.yml`.
