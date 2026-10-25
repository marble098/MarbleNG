# شورای ۵ نفره تخصصی — نقد و بازآفرینی هر صفحه MarbleNG

> تاریخ: 2026-10-25 • شاخه: `arena/8646b82a-marbleng` • مرجع: `b36925f`
>
> درخواست: با عقل و خرد خودت یک شورای ۵ نفره تشکیل بده؛ هر صفحه از نظر طراحی، گرافیک، امکانات، چینش به چالش کشیده شود؛ نظر نهایی هر شورا اجرا، حذف، کدنویسی و اعمال شود؛ سپس PR و ادغام.

---

## ۱. ترکیب شورا

| عضو | تخصص | لنز نقد |
|---|---|---|
| دکتر لیلا فرهادی | UX Researcher (پژوهش رفتاری) | سفر کاربر، RTL فارسی، آموزش‌پذیری |
| کیان توکلی | Visual Brand Designer | رنگ، تایپوگرافی، آیکون، موشن، عمق |
| آرمان بهزاد | Compose Architect & Performance | چیدمان، Recomposition، Insets، Gesture |
| سارا مرادی | Product & IA Strategist | معماری اطلاعات، تنظیمات، کشف‌پذیری |
| رضا احمدی | Accessibility & i18n Engineer | مقیاس متن، کنتراست WCAG، اهداف لمس، RTL |

هر صفحه در حضور هر پنج نفر نقد شد؛ هر نقد باید با **شاهد کد** و **پیشنهاد اجرا** همراه می‌بود. وتوی تنها یک عضو کافی بود تا پیشنهاد به «بازنگری» برود. تصمیم نهایی با اجماع یا اکثریت ۴ از ۵ ثبت شد.

---

## ۲. نقد صفحه‌به‌صفحه (خلاصه‌ی تصمیم‌ها)

### صفحه‌ی خانه (Home)

- **فرهادی:** قهرمان اتصال (Hero) فقط یک دکمه‌ی بزرگ ۱۱۶dp بود — کاربر وضعیت را از رنگ می‌خواند نه از متن ساختاری. شورتکات‌های پایین صفحه عملاً تکرار آیکون‌های هدر (QR/+ / SmartRank) بودند و اسکرول اضافی می‌ساختند.
  → *تصویب:* Hero با هاله‌ی تنفس (breathing) + متن راهنمای «Import a server to begin» برای حالت خالی؛ شورتکات‌ها از لیست عمودی ۴ ردیفی به گرید ۲×۲ تایل تبدیل شوند.

- **توکلی:** سایه‌ی کارت‌ها (2dp @ 0x0A000000) روی #F8F9FA نامرئی بود؛ لبه‌ها فقط border تخت داشتند، نور نداشتند. اسلایدر فقط یک قرص خاکستری با یک thumb رنگی بود؛ fill انرژی و chevron راهنما نداشت.
  → *تصویب:* elevation به 3dp @ 0x12000000، border به 0.85dp؛ اسلایدر با انرژی-fill گرادینت و ۳ chevron محوشونده و thumb با رینگ سفید lit.

- **بهزاد:** telemetری دو مقدار را در یک خط با «·» می‌چپاند؛ خوانش سریع ممکن نبود. recomposition هاله‌ی Hero با spring بادی زیاد.
  → *تصویب:* Telemetری به دو جفت تایل گرید (↓/↑ و Session/Total) با آیکون ۲۸dp و پس‌زمینه cardSecondary. تایپوگرافی hero از headlineMedium به headlineSmall Bold برای جلوگیری از شکست فارسی.

- **مرادی:** «Quick Actions» جایگاه یگانه‌ی Censorship Lab را تضعیف می‌کرد.
  → *تصویب:* Grید تایل‌ها هر عمل را هم‌وزن نشان دهد؛ عنوان‌ها کوتاه: «Scan QR»، «Smart Rank».

- **احمدی:** عنوان‌های طولانی فارسی در subtitle hero بریده می‌شدند؛ حداقل touch برای تایل‌ها رعایت نشده بود.
  → *تصویب:* subline فارسی now 12.5sp bodySmall + line-height 17؛ تایل‌ها 48dp+ با clip 20dp.

**نتیجه‌ی اجرا:** `HomeScreen.kt` بازنویسی شد — `HomeConnectionHeroCard` (هاله 118dp، اسکیل 1.06، رینگ lit)، `IosSlideConnectBar` (fill + chevrons + lit rim)، `HomeRouteAndIdentityCard` (dot پالس هنگام measuring)، `HomeTelemetryCard` (grid دو ستون + HomeStatTile)، `HomeQuickTile` (تایل گرید با squircle 34dp).

### صفحه‌ی سرورها (Servers)

- **فرهادی:** empty state ها زیبا بودند اما «No reply» با قرمز هشدار نشان داده می‌شد — کاربر فکر می‌کرد خطای خودش است.
  → *تصویب:* quiet failure — متن «No reply» در tertiary/14% + نام سرور 58% alpha، قرمز فقط برای latency بالا (≥250ms).

- **توکلی:** هدر گروه (section header) فقط عنوان بود؛ تعداد سرورها پنهان. chevron ساده بود، هویت نداشت.
  → *تصویب:* `IosSectionHeader` با badge عددی (رنگ accent/indigo/teal)؛ chevron داخل دیسک 26dp cardSecondary.

- **بهزاد:** divider با inset 66dp یکسان برای همه؛ در گروه‌ها باید inset دقیق avatar باشد. چیپ‌های فیلتر 34dp بودند — زیر floor تست لمس.
  → *تصویب:* divider گروه به 66dp با 0.75dp؛ چیپ‌ها به 36dp با border cardBorder.

- **مرادی:** chip strip تعداد را نشان نمی‌داد؛ کاربر نمی‌دانست 48 سرور فیلتر شده است.
  → *تصویب:* `IosSearchBar` با `trailingCount` («48») + protocol tallies با count.

- **احمدی:** متن راهنمای «Tap server to select · ⋯ for actions» وجود نداشت؛ discoverability پایین.
  → *تصویب:* فوتر 7+ آیتم گروه — hint با tertiary labelSmall.

**نتیجه‌ی اجرا:** `ServersScreen.kt` — header با badge، دیسک chevron، divider 0.75dp، quiet failure، فوتر hint.

### تب چهارم (Custom Slot)

- **همه:** صفحه Pulse فقط یک لیست سه‌تایی بود؛ هویت «چرا این تب وجود دارد» را توضیح نمی‌داد.
  → *تصویب:* Pulse به hero 54dp + دکمه‌ی بزرگ (Primary/Destructive) با dot زنده؛ empty state ها به `IosEmptyState` یکسان‌سازی. Source-empty و Config-empty اکنون illustration 56dp دارند.

**نتیجه‌ی اجرا:** `CustomSlotScreen.kt` — pulse-live hero جدید، `IosEmptyState` برای source-empty و config-empty.

### تنظیمات — هاب (Settings Hub)

- **فرهادی و مرادی:** 20 مقصد در 5 گروه خوب است، اما جستجو highlight نداشت و badge تعداد نداشت.
  → *تصویب:* `IosSearchBar` با count کل مقصدها؛ هر گروه now badge تعداد ردیف‌ها؛ فوتر نسخه + hint جستجو.

- **احمدی:** search empty (no matches) خالی می‌ماند.
  → *تصویب:* `IosEmptyState` با «Try a shorter keyword — DNS, Theme, Ping».

**نتیجه‌ی اجرا:** `SettingsScreen.kt` — search با count، header با badge، empty + footer.

### تنظیمات — ظاهر (Appearance)

- **توکلی:** انتخاب accent 40dp بود و روی dark mode border نداشت؛ preview زنده وجود نداشت.
  → *تصویب:* swatch به 44dp با ریم cardBorder در dark؛ کارت preview جدید (44dp آکادمیک + badge Aa).

**نتیجه‌ی اجرا:** `SettingsAppearancePages.kt` — `theme-preview` card جدید + swatch 44dp.

### ناوبری — Dock

- **توکلی:** تب فعال فقط رنگ داشت؛ pill لنز نداشت؛ dot وضعیت روی آیکون بدون ریم card بود.
  → *تصویب:* تب فعال با pill 0.10/0.18 alpha + نوار 14×3dp زیر آیکون + dot 8dp با ریم tabBar.

- **بهزاد:** padding افقی 8dp بود، روی فارسی‌های بلند label بریده می‌شد.
  → *تصویب:* padding به 6dp + spacedBy 2dp؛ weight 1f + clip 16dp pill.

- **احمدی:** label فارسی «سرورها» باید Medium→Bold هنگام انتخاب باشد.
  → *تصویب:* وزن 1.18f فعال + Bold label، 1f غیرفعال + Medium.

**نتیجه‌ی اجرا:** `MarbleApp.kt` — `IosBottomTabBar` با active pill lens.

### سیستم جهانی (Design System)

- **توکلی:** تایپوگرافی 28/22/19/18pt خیلی بزرگ؛ Vazir Black faux-bold بود.
  → *تصویب:* type ramp یک پله کوچک شد: 24/20/17/17/15/15/12.5/13.5 (headlineLarge 28→24، etc) — فارسی line-height حفظ.

- **بهزاد:** سایه‌ی کارت‌ها و border باید directional باشند.
  → *تصویب:* `IosSpec` با `CARD_PRESSED_SCALE`, `HERO_PULSE_*`, `DOCK_ACTIVE_PILL_CORNER`, `SLIDE_*`؛ palette: softShadow 0x0A→0x12, cardBorder 0x0D→0x0F (light), 0x1F→0x22 (dark).

- **احمدی:** dividerها باید fade باشند؛ header باید badge بپذیرد؛ empty state یکسان شود.
  → *تصویب:* `IosDivider` 0.75dp + `IosFadeDivider`؛ `IosSectionHeader` با badge؛ `IosEmptyState` + `IosPulseDot`؛ چیپ‌ها به 36dp با `IosCountChip`؛ search 42→44dp با trailingCount.

**نتیجه‌ی اجرا:** `IosTheme.kt` + `IosComponents.kt` — توکن‌های جدید، palette refine، تایپوگرافی فشرده، کامپوننت‌های جدید شورا.

---

## ۳. جدول اجرا — چه حذف/اضافه شد

| فایل | افزوده | حذف/اصلاح | رای شورا |
|---|---|---|---|
| `IosTheme.kt` | توکن‌های `CARD_PRESSED_SCALE`…`EMPTY_ILLUSTRATION_SIZE`, palette refine | headline 28→24/22→20/18→17, softShadow تقویت | 5/5 |
| `IosComponents.kt` | `IosEmptyState`, `IosPulseDot`, `IosCountChip`, `IosFadeDivider`, header badge, search count | Card elevation 2→3dp, divider 1→0.75dp, chip 34→36dp, search 42→44dp | 5/5 |
| `HomeScreen.kt` | `HomeQuickTile`, `HomeStatTile`, halo تنفس, dot پالس | شورتکات list→grid 2×2, telemetri تک‌خط→grid | 5/5 |
| `MarbleApp.kt` | active pill lens + 14×3 bar, dot 8dp با ریم | padding 8→6, iconWeight 1.15→1.18 | 5/5 |
| `ServersScreen.kt` | badge header, دیسک chevron, hint فوتر, trailingCount | quiet failure به‌جای قرمز | 5/5 |
| `CustomSlotScreen.kt` | hero pulse 54dp + hero card, emptyState یکسان | list سه‌تایی ساده | 5/5 |
| `SettingsScreen.kt` | search count, header badge, empty, footer نسخه | — | 5/5 |
| `SettingsAppearancePages.kt` | کارت preview زنده + badge Aa, swatch 40→44dp | — | 5/5 |

هیچ رفتارِ هسته (Xray/sing-box، ping، routing) تغییر نکرد — شورا فقط «ماده» را لمس کرد.

---

## ۴. راستی‌آزمایی

- `python3 scripts/system-integrity-check.py` — **249/249 PASS** (شامل توکن‌های iOS، 20 مقصد تنظیمات، wrapper زبان/مقیاس/تم، توکن‌های V189/V218)
- `IosDesignSystemTest` — توکن‌های `BACKGROUND_HEX`, `CARD_HEX`, `CARD_CORNER_DP` و palette دست‌نخورده
- `MarbleGlyphArtV219Test` — stroke قاعده 1.75/24 بدون تغییر
- `:app:compileDebugKotlin` / `:app:compileReleaseKotlin` — UI خالص Compose، وابستگی جدید ندارد

---

## ۵. جمع‌بندی اجماع

> «محصول قبل از شورا «درست کار می‌کرد» اما «یک لایه کم داشت». شورا همان یک لایه را اضافه کرد: سایه‌ای که دیده شود، چیپی که لمس شود، متنی که جا شود، خالی‌ای که راه نشان دهد، تب فعالی که بدرخشد — بدون اینکه یک مسیرِ شبکه جابجا شود.»
