# نوار تبِ کشیدنی، ریپلِ منطبق بر خطوط شکل (V265)

شناسهٔ این فصل: `MARBLE_LUMEN_DOCK_V265`.

دو گزارش، یک قاعده: **فیدبکِ لمس باید داخلِ شکل بماند، و نوارِ پایین باید با انگشت کشیده شود.**

مرجع: اپ اندرویدِ [Lumen](https://github.com/krambovic/Lumen) (انتشار `android-v1.5.0`) — کدِ نوارِ
تبِ آن در GitHub خوانده شد: `android/ui/src/main/java/com/lumen/ui/components/FloatingNavigationDock.kt`.
در `DockTab` ابتدا `.clip(RoundedCornerShape(26.dp))` میآید و بعد `.selectable(...)`، یعنی **ریپلِ
متریال ۳ داخلِ شکلِ خودِ تب画 می‌شود**؛ ردیفِ تب‌ها با وزنِ برابر چیده شده؛ و دکمهٔ اتصالِ داک
(`DockConnectionButton.kt`) هر سه حالتِ اتصال (قطع، در حال اتصال، متصل) را با یک شکلِ morphing،
نشانگرِ پیشرفت و هپتیکِ خودش نمایش می‌دهد. هیچ کدی از Lumen (GPL-3.0) کپی نشده است؛ این
پیاده‌سازیِ مستقلِ Marble است، همان‌گونه که در فصلِ V250 آمده است.

| # | گزارش | پاسخ |
|---|---|---|
| ۱ | «نوار تب ریپلِ متریال دیزاین ۳ دارد که منطبق بر خطوط اشکال نیست.» | دو لایهٔ کلیپ: ردیفِ کلِ نوار با `PillOutlineShape` (PR #257) و **هر اسلات** با `RoundedCornerShape(innerRadiusDp)` *قبل از* `selectable` — همان قاعدهٔ Lumen. بال‌ها (جزیره، دکمهٔ power، تریگرِ مرکزی) همگی کلیپ را قبل از interaction دارند |
| ۲ | «نوار تبِ پایین باید کشیدنی باشد؛ راحت بشود انگشت را روی آن کشید و حرکت داد.» | درگِ افقی روی ردیف، انتخاب را به اسلاتِ زیرِ انگشت می‌برد (یک تغییر به ازای هر اسلات) و **لنز را زیرِ انگشت نگه می‌دارد** (`heldLensLeftPx` از همان clampِ `LiquidLens.lensLeftPx`)؛ با رها کردن، لنز با اسپرینگ روی اسلاتِ انتخاب‌شده می‌نشیند |

---

## ۱. ریپلِ منطبق بر خطوطِ شکل

ریپلِ `ripple(bounded = true)` (که `MarbleTheme` برای همهٔ کنترل‌ها می‌گذارد) یک مستطیلِGraphic است؛
بدون کلیپ، گوشه‌هایش از منحنیِ کپسول — و از منحنیِ خودِ اسلات — بیرون می‌زنند.

| لایه | فایل | تغییر |تضمین |
|---|---|---|---|
| ردیفِ کلِ نوار | `LiquidLensNavigationBar.kt` | `.clip(pillClip)` با `PillOutlineShape` (outlineِ کپسول در مختصاتِ ردیف) | ریپلِ اسلات‌هایِ کناری از منحنیِ stadium بیرون نمی‌زند |
| هر اسلاتِ تب | `NavigationItemView.kt` | `.clip(RoundedCornerShape(DockStudio.innerRadiusDp(size).dp))` **قبل از** `.selectable(...)` | ریپلِ هر تب شکلِ خودش را دنبال می‌کند، در هر جای ردیف — نه فقط در دو اسلاتِ کناری |
| تزئینات | همان فایل | شمارنده و نورِ اتصال **خارج** از کلیپِ اسلات کشیده می‌شوند | یک badge که روی لبهٔ اسلات می‌نشیند بریده نمی‌شود |
| بالِ جزیره | `MarbleDockWings.kt` | `.clip(islandShape)` قبل از `.clickable(...)` | ریپل داخلِ کپسولِ جزیره می‌ماند |
| تریگرِ مرکزی | همان فایل | `.clip(grooveShape)` قبل از `.combinedClickable(...)` | ریپل داخلِ شیار می‌ماند |
| دکمهٔ power | همان فایل | `FilledIconButton(shape = RoundedCornerShape(innerRadiusDp(size).dp))` | M3 خودش ریپل را داخلِ shape نقاشی می‌کند |

قاعدهٔ گوشه‌ها همان قاعدهٔ V260 است: `innerRadiusDp = cornerRadius − 6dp`؛ پس ریپل با همان
انحنایی که خودِ نوار دارد پایان می‌یابد.

## ۲. نوارِ کشیدنی

`detectHorizontalDragGestures` روی ردیفِ نوار:

- `onDragStart` → نزدیک‌ترین اسلات انتخاب می‌شود و لنز زیرِ انگشت «گرفته» می‌شود (`heldLensLeftPx`)؛
- `onHorizontalDrag` → به ازای هر اسلاتِ crossed یک `onSelect`؛ و هر حرکت، موقعیتِ لنز را هم با همان
  `LiquidLens.lensLeftPx` (clampِ واحدِ کلِ محصول) به‌روز می‌کند — پس **لنز با انگشت حرکت می‌کند**، نه
  اینکه بینِ تب‌ها بپرد؛
- `onDragEnd` / `onDragCancel` → مقدارِ held پاک می‌شود و لنز با اسپرینگ از همان‌جا روی اسلاتِ
  انتخاب‌شده می‌نشیند.

درگ روی ردیف است و بال‌ها فرزندِ ردیف‌اند، پس کشیدن از روی جزیره یا دکمهٔ اتصال هم کار می‌کند —
در هر سه استایلِ دکمهٔ اتصال (`DOCK_ISLAND`، `CENTER_MORPH`، `HERO_CARD`).

## ۳. هر سه حالتِ دکمهٔ اتصال

سه استایلِ widget × سه فازِ اتصال (قطع / در حال اتصال / متصل) — نه ترکیب — با تستِ Robolectric
پین شده‌اند (`LumenDockV265UiTest`): در هر ترکیب، widgetِ همان استایل (dock-island و dock-power،
یا dock-center، یا هیچ‌کدام) کشیده می‌شود، چهار تب و لنز هستند، شمارنده و نورِ اتصال حفظ می‌شوند، و یک
swipeِ کاملِ نوار آخرین تب را انتخاب می‌کند؛ یک درگ که وسطِ نوار تمام شود، تبِ وسطی را انتخاب می‌کند.

تست‌هایِ سورس هم pin شده‌اند: کلیپِ اسلات قبل از `selectable`؛ کلیپِ ردیف با `PillOutlineShape`؛
کلیپِ بال‌ها قبل از interaction؛ و held/seatِ لنز در درگ.

| تست | چه چیزی را pin می‌کند |
|---|---|
| `LumenDockV265UiTest.theSlotClipsItsOwnRippleToItsShapeBeforeSelectable` | ترتیبِ `.clip` قبل از `.selectable` در `NavigationItemView.kt` |
| `...theRowAndEveryWingClipTheirRipplesToTheirShapes` | `pillClip`، `islandShape`، `grooveShape`، shapeِ دکمهٔ power |
| `...theDragHoldsTheLensUnderTheFingerAndSeatsItOnRelease` | `heldLensLeftPx`، `lensLeftFor`، `onDragEnd`/`onDragCancel` |
| ۹ تستِ style×phase | widget + ۴ تب + لنز + swipe در هر ترکیب |
| `...aDragThatStopsMidBarSelectsTheTabUnderTheFinger` | درگِ جزئی → تبِ زیرِ انگشت |
| `...aTapOnATabStillSelectsIt` | tapِ معمولی هنوز کار می‌کند |
