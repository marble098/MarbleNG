# MARBLE_PERSIAN_CALENDAR_TRUTH_V275 — the calendar the product lied about, rebuilt on a recorded table

> The code lives in `app/src/main/java/com/marbleng/app/model/PersianCalendar.kt` (the truth),
> `PlanStatus.kt` (`JalaliCalendar`, the arithmetic), `app/src/main/java/com/marbleng/app/ui/
> PersianCalendarUi.kt` (the today line and the sheet) and is pinned by
> `app/src/test/java/com/marbleng/app/model/PersianCalendarV275Test.kt`.

## ۱. شکایت (the complaint, answered literally)

Two sentences, both tested by name in the regression test:

1. **«تعطیل رسمی اعلام کردن ۱۳ آبان که واقعاً تعطیل رسمی نیست»** — the calendar declared
   13 Aban an official holiday. It never was one: it is **Student Day**, a commemoration.
   In this build, `isOfficialHoliday(1405, 8, 13)` is `false` for *every* year the table
   covers, while `occasionsOn(1405, 8, 13)` still answers `Student Day` — the day is
   neither hidden nor lied about.
2. **«نبود همچنان برخی از مناسبت ها»** — occasions were missing. The non-holiday list now
   carries the national days a Persian reader expects, and the holidays come from a
   recorded official-calendar table instead of an invented rule.

And a third defect the complaint could not have seen, because it corrupts the *arithmetic*
rather than the labels: the Jalali converter folded **Esfand 30 of every leap year onto
12-29** (`ofJdn` checked the leap flag one year past the year it had stepped back from), so
even a correct table could not have been displayed. 1408-12-30 and 1412-12-30 now
round-trip.

## ۲. قانون (the rules the calendar answers to)

* **A holiday is a holiday because something recorded says so.** The embedded
  `HOLIDAY_TABLE` is the official Iranian calendar for **1404–1412**, transcribed from
  [Hesam-Nikzad/Iranian-Calendar-Holidays](https://github.com/Hesam-Nikzad/Iranian-Calendar-Holidays)
  (CSV per year; date + event columns; the 1404–1412 extract was cross-checked against the
  source CSV row by row during the pass, and the transcription is pinned by the test).
* **The lunar table *is* the moon-sighting.** Lunar occasions (Eid al-Fitr, Ashura,
  …) appear only on the dates the table records for them. **No lunar date is ever
  approximated, extrapolated or computed** — an approximation is exactly how a calendar
  ends up declaring a holiday on the wrong day, which is the complaint this pass answers.
* **Beyond the table: solar truth only.** For years outside 1404–1412 the calendar answers
  from the solar-fixed rules (Nowruz 1–4 Farvardin, 12 and 13 Farvardin, 14 and 15
  Khordad, 22 Bahman, 29 Esfand) plus the Friday weekend — and `holidayCoverage(year)`
  is `false`, which the sheet prints as its coverage note, so a reader in 1413+ sees a
  calendar that *says* it is not guessing the moon rather than one that quietly does.
* **Friday is the only weekend.** Thursday is a working day (verified against the source
  table and the Iranian labour calendar); a Friday is a holiday even when it carries no
  occasion.
* **Commemorations are never holidays.** The non-official list — 10 Farvardin (Persian
  Gulf), 29 Farvardin (Army), 12 Ordibehesht (Teacher's), 20 Mehr (Hafez), 13 Aban
  (Student Day), 30 Azar (Yalda) — is `official = false`, every one, every year. They get
  their dot on the grid and their name on the day; they do not get the holiday colour.
* **12 Esfand of a leap year** (the last day, 1408 and 1412 within the table) is recorded
  as the dataset records it: an official holiday labelled «آخرین روز سال».
* **Multi-occasion days print everything.** 1405-01-01 is Nowruz *and* Eid al-Fitr;
  1407-03-14/15 carry Tasua/Ashura beside the fixed Khordad holidays; 1411-01-04 is Nowruz
  and Eid al-Adha; 1412-01-01 is Nowruz and Eid al-Ghadir. The UI prints both names.

## ۳. خطاها و استثناها (caveats a reader deserves)

* **The 1404 Safar gap.** In the source dataset, 1404 lacks the row other years carry for
  the last day of Safar (29 Safar 1404 = 1404-06-02 is unlisted, while 28 Safar and
  8 Rabi I are present). The embedded table preserves the source rather than inventing the
  day; before relying on 1404's *late-Safar* dates, cross-check a second source.
* **Coverage is honest about its edges.** `holidayCoverage` is true only for 1404–1412.
  A Persian reader in that range gets the full truth; outside it they get solar-fixed
  holidays, the Friday weekend, the commemorations — and a note saying the lunar table
  ended.
* **Occasions are deliberately few.** Rock-solid national days only. Ferdowsi, Saadi and
  other author-days were considered and excluded — their dates are not uniformly agreed,
  and the whole point of this pass is that a calendar does not print what it cannot verify.

## ۴. حساب (the arithmetic, and the fix that was hiding in it)

* `JalaliCalendar` is the classic JDN-based conversion. `ofJdn` decrements the year while
  walking past January 1st and then adjusts for the day count of the year it stepped back
  **from**; the old code read the leap flag after the decrement (`jalCal(jy).leap == 1`),
  i.e. one year past the leap year, so Esfand 30 of *every* leap year came back as 12-29.
  The fix reads the flag of the year it stepped back from (`jalCal(jy + 1).leap == 1`).
* `JalaliCalendar` gained the three helpers the calendar needs, all delegating to the same
  arithmetic: `daysInMonth(year, month)`, `isLeapYear(year)` (`jalCal(year).leap == 0`
  ⟺ leap: 1399, 1403, 1408, 1412), and `jdnOf(year, month, day)` (Jalali → JDN). The
  pre-existing three-argument `of(year, month, day)` takes **Gregorian** input and is
  unused; do not confuse it with `jdnOf`.
* The week is **Saturday-first**: `weekIndexOf` = `((jdn % 7) + 2) % 7` → 0 = شنبه …
  6 = جمعه. (JDN % 7 counts 0 = Monday.)
* `gregorianLabel` converts through the same JDN ((jdn − 2440588) × 86400000 → UTC
  calendar), so the two calendars the sheet prints can never disagree.

## ۵. رابط (the UI)

* **`MarbleTodayLine`** — one quiet row under the Home heading: the Jalali date, today's
  occasion, a warm dot *only* on a verified official holiday. A day with nothing on it
  prints nothing extra. Tapping opens the sheet.
* **`PersianCalendarSheet`** — the month: a Saturday-first grid whose holiday colour is
  earned only through `isOfficialHoliday`, occasion dots for the commemorations, the
  selected day named with both its occasions and its Gregorian twin, a coming-up strip
  (next 3, with honest day distances), and the coverage note. Month paging mirrors in
  RTL (an Iranian calendar flips the way the language reads).
* A `CALENDAR` glyph was added to `MarbleGlyphArt` (one plate, two hangers, marked days —
  reads at 16 dp on the 24-unit grid).
* Persian copy: `MarblePersianLexicon` carries the six new sentences and two dynamic
  patterns (the coverage note with its years, the day distance).

## ۶. تست (verification)

`PersianCalendarV275Test` (JVM) pins: 13 Aban not a holiday in any covered year but still
Student Day, and 13 Aban 1405 = Wednesday = 2026-11-04; Nowruz a holiday across the table;
the solar rules holding beyond the table with `holidayCoverage` false; the lunar spot
checks (Eid al-Fitr 1404-01-11, Ashura 1404-04-15, Eid al-Adha 1405-03-07, the doubled
Tasua/Ashura Khordad of 1407); the two-name Nowruz of 1405; Friday/Thursday; every table
date a real date; the leap years and Esfand lengths; the Esfand-30 round-trip and its table
entry; the six commemorations as non-holidays; the ordinary day; today/upcoming (day
distances across month boundaries); the month and weekday spellings; the full-sentence
format; the marker.

## ۷. آنچه عمداً انجام نشد (what was deliberately not done)

* **No computed lunar calendar** (no `tabular Islamic calendar`, no Ibn Qurra shift). Every
  approximation is a wrong day waiting for a reader; the table is the answer.
* **No city-level moon sighting**, no khordad-15-vs-16 politics, no arbitration between
  marja' calendars — the dataset records one official calendar and that is what ships.
* **No extension of the table past 1412.** The source publishes to 1430; extending is a
  data edit (append to `HOLIDAY_TABLE`, widen `TABLE_FIRST/LAST_YEAR`, extend the test's
  year loops) and should be done when 1412 approaches, not by extrapolation.
* **No holiday migration into the theme system.** The holiday colour is `error`, the
  occasion dot is `primary`; a dedicated "holiday" palette token was considered and
  dropped — two colours, both already AA-audited.
