# بازسازی اساسی مسیریابی و بازخوانی بقیهٔ تنظیمات (V254)

شناسهٔ این فصل: `MARBLE_ROUTING_REBUILD_V254`.

خواستهٔ گزارش سه بخش داشت: **۱)** بخش مسیریابی Geo در تنظیمات شلوغ، پرت و بی‌معنی است و باید از
صفر بازسازی شود؛ **۲)** هیچ چیز الکی نباید در آن بماند — یعنی هر کنترل و هر جمله باید به کدی وصل
باشد که واقعاً اجرا می‌شود؛ **۳)** بقیهٔ صفحات تنظیمات هم با همین معیار بازخوانی شوند. این فصل همان
کار است، و دلیل هر تغییر در آن نوشته شده تا دفعهٔ بعد کسی حدس نزند.

---

## ۱. مسئله چه بود؟ صفحهٔ مسیریابی «تصمیم» را نشان نمی‌داد، «حافظه» را نشان می‌داد

صفحهٔ قدیمی چهار مکانیزم مستقل را هم‌زمان در خود داشت که همه‌شان قانون مسیریابی می‌ساختند:

| لایه | جایی که ذخیره می‌شد | در صفحه دیده می‌شد؟ |
| --- | --- | --- |
| حالت مسیریابی | `routingMode` | بله، به‌عنوان چهار ردیف انتخاب |
| فهرست‌های کشوری ضمنی (تبلیغات، `ir`) | `routeGeoIpTags` / `routeGeoSiteTags` / `routeAdsTag` | نه، فقط در کد |
| فهرست‌های «متخصص» (پنج جعبهٔ متنی) | `routeDirectDomains` و چهار فیلد دیگر | **هیچ صفحه‌ای در تنظیمات آن‌ها را نداشت** |
| قانون‌های دستی | `routingRulesJson` + `customRoutingEnabled` | بله، ولی بدون توجه به حالت |

نتیجه سه تناقض قابل لمس بود:

1. **حالت و کلید قانون دستی با هم نمی‌خواندند.** «Custom rules only» به‌عنوان حالت و «Enable Custom
   Routing» به‌عنوان کلید، هر چهار ترکیبشان قابل دسترسی بود؛ در دو ترکیب از آن‌ها صفحه فهرستی را
   نشان می‌داد که هسته هرگز اجرا نمی‌کرد (قانون‌های دستی در حالت `PROXY_ALL` بعد از قاعدهٔ
   catch-all نوشته می‌شدند و هیچ‌وقت به آن‌ها نمی‌رسید).
2. **چیزی که دیده نمی‌شد کار می‌کرد.** فهرست‌های متخصص از رابط کاربری حذف شده بودند ولی موتور
   مسیریابی و `SingBoxConfigBuilder` هنوز آن‌ها را می‌خواندند؛ کاربر فقط از طریق بازیابی پشتیبان
   می‌توانست به آن‌ها برسد.
3. **عنوان‌ها نام مکانیزم بودند، نه نتیجه.** «Rule Presets & Domain Strategy»، «GeoIP & GeoSite
   Databases»، «Multi-Source Geo Databases» — سه سرفصل که هیچ‌کدام به سؤال «ترافیک من کجا می‌رود؟»
   جواب نمی‌دادند. علاوه بر این «Geo Direct» به‌عنوان یک حالت، از واژه‌ای استفاده می‌کرد که خود
   `PlainVoice.JARGON` آن را ممنوع کرده بود.

---

## ۲. ساختار تازه: از نتیجه به عقب

`app/src/main/java/com/marbleng/app/core/RoutingOverview.kt` تنها جایی است که این لایه‌ها به زبان
آدم ترجمه می‌شوند. صفحه فقط از این فایل می‌خواند و فقط از طریق آن می‌نویسد:

- `decide(settings, mode)` — تنها راه تغییر حالت. `routingMode` و `customRoutingEnabled` را با هم
  می‌نویسد، پس دیگر ممکن نیست صفحه بگوید «فقط قانون‌های من» و هسته بگوید «فهرست خاموش است».
- `summary(mode)` / `note(mode)` — یک جمله برای هر حالت، به زبان نتیجه.
- `sections(settings)` — فهرست لایه‌هایی که **واقعاً** در این لحظه قانون تولید می‌کنند، با تعداد و
  مقصد هرکدام. لایهٔ خاموش حذف می‌شود، نه اینکه با عدد صفر نشان داده شود.
- `duplicateMatchers` / `regexRules` / `activePresetId` — سه اشتباهی که قبلاً بی‌صدا بود.

صفحهٔ نو (از بالا به پایین):

| بخش | چه چیزی نشان می‌دهد | چرا این‌جاست |
| --- | --- | --- |
| «How traffic is sorted» | چهار حالت، هر کدام یک جمله | تنها تصمیم سطح‌بالا، بدون اصطلاح فنی |
| «What this reads today» | لایه‌های فعال + تعداد | چیزی که قبلاً نامرئی بود |
| «Country lists» | آمادگی دو فایل + یک دکمهٔ دانلود | کاربر دقیقاً می‌داند چه چیزی کم است |
| «Where the lists come from» | یک انتخاب‌گر منبع + ترکیب چند منبع | به‌جای شش ردیف با شناسهٔ داخلی |
| «How much domestic traffic stays direct» | سه سطح دقت داخلی با تعداد واقعی | عدد از `IranPrecisionPack` خوانده می‌شود |
| «My own rules» | یک کلید + فهرست قانون‌ها با توقف/جابه‌جایی/حذف | قانون‌ها دیده می‌شوند و مدیریت می‌شوند |
| «Start from a ready set» | پیش‌تنظیم‌ها با تعداد قانون و انتخاب واقعی | پیش‌تنظیم، حالت را هم روشن می‌کند |
| «Try a site» | ورودی + حکم + مرحلهٔ برنده | شبیه‌ساز قبلاً بود ولی خروجی‌اش ناخوانا بود |
| «Housekeeping» | به‌روزرسانی، بررسی با هسته، تنظیمات پیشرفته | کارهای گاه‌به‌گاه، جدا از تصمیم |

### چه چیزی حذف شد

- ردیف `GeoIP · <bytes>` و `GeoSite · <bytes>` که نام فایل خام را به‌عنوان عنوان ردیف چاپ می‌کرد.
- شش ردیف «Multi-Source Geo Databases» که `spec.id` را زیر عنوان نشان می‌داد و منطق کلیک آن
  (انتخاب یا ترکیب، بر اساس اینکه کدام ردیف اول است) قابل پیش‌بینی نبود.
- جعبهٔ شبیه‌ساز که فقط `verdict.name` را به‌صورت `PROXY` چاپ می‌کرد.
- دکمه‌های «Verify Policy» و «Update Geo Files» که در دو جای صفحه تکرار می‌شدند.
- `RoutingOverview.handMadeRuleCount`، `presetRuleCount`، `usesRegex` و `Repo.geoPrecisionSummary()` که
  هیچ خواننده‌ای نداشتند — در همانPass که نوشته شدند، حذف شدند تا کد مرده نماند.

### چه چیزی اضافه شد

- **قانون‌ها حالا مدیریت می‌شوند**: `setRoutingRuleEnabled`، `moveRoutingRule`، `duplicateRoutingRule`
  در `AppRepository` — قبلاً تنها راه «جابه‌جایی» حذف و بازنویسی بود.
- **ویرایشگر قانون پیشنهاد می‌دهد**: از `GeoAssetIndex.suggest` استفاده می‌کند و برچسب‌های موجود در
  فایل دانلودشده را با تعدادشان نشان می‌دهد. قبلاً کاربر یک برچسب را از حافظه می‌نوشت و بعد پیام
  «در geosite.dat نیست» می‌گرفت.
- **هشدار تکرار و regex**: دو خط که قبلاً وجود نداشتند و هر دو هزینهٔ واقعی دارند.
- **جعبهٔ «تنظیمات پیشرفته»**: پنج فهرست متخصص و دو آدرس دانلود که هیچ صفحه‌ای آن‌ها را نشان نمی‌داد،
  حالا در یک شیت با توضیح قالب، به‌جای اینکه پنهان یا در وسط صفحهٔ اصلی باشند.

---

## ۳. بازخوانی بقیهٔ تنظیمات

| صفحه | چه چیزی اصلاح شد |
| --- | --- |
| **مسیریابی** | بازسازی کامل (بالا) |
| **DNS و نسخهٔ آی‌پی** | پنج ردیف خانوادهٔ آدرس هرکدام یک جمله گرفتند؛ رزولور دستی از یک کارت همیشه‌باز به یک کلید تبدیل شد؛ سرفصل‌ها از مکانیزم به کار («Encrypted DNS»، «DNS protection») تغییر کردند |
| **کدام برنامه‌ها از تونل** | ابتدا «انتخاب‌شده‌ها» و بعد «افزودن برنامه»؛ برنامه‌های انتخاب‌شده دیگر ته فهرست الفبایی گم نمی‌شوند؛ برنامهٔ حذف‌شده که هنوز در رشتهٔ ذخیره است نشان داده می‌شود؛ حالت خالی توضیح داده می‌شود |
| **ایران مود و تست بلاک** | ترتیب خواندن: «چه چیزی خراب است» → «چه کاری کنم» → «شاهدها» (تاشو)؛ «DPI Countermeasures» به «Work around filtering» و اعداد خام `latencyMs` به یک خط تاشو تبدیل شدند |
| **مسیر تونل** | سرفصل «Packet Datapath» (واژهٔ ممنوعه) به «Which tunnel driver carries the traffic» تغییر کرد |
| **پینگ** | «Custom Signal Weights» → «What the pick weighs»؛ انتخاب دکمهٔ خانه حالا هر سه فیلد `homePing*Action` را هم‌زمان می‌نویسد و آنها را از هم جدا نمی‌کند |
| **حریم آی‌پی** | «Exit Intelligence & Country Flags» و «Public Exit IP Intelligence» به دو جملهٔ روشن تبدیل شدند |
| **اعلان‌ها** | «Live Telemetry in Ongoing Notification» → «Live ping in the notification» |
| **ظاهر** | درصد اندازهٔ متن در چیپ‌ها حالا با ارقام فارسی نمایش داده می‌شود (`faDigits`) |
| **جست‌وجو/هاب** | بدون تغییر ساختاری — بازچینش V253 حفظ شد |

---

## ۴. جدول اجرا

| فایل | تغییر |
| --- | --- |
| `core/RoutingOverview.kt` | **فایل جدید.** `decide`، `summary`، `note`، `sections`، `duplicateMatchers`، `regexRules`، `activePresetId`، `DEFAULT_MODE` |
| `AppRepository.kt` | `setRoutingMode`، `setCustomRoutingEnabled`، `setRoutingRuleEnabled`، `moveRoutingRule`، `duplicateRoutingRule`؛ `applyRoutingPreset` و `applyIranRoutingPreset` از همان دروازه رد می‌شوند؛ حذف دو تابع بدون خواننده؛ خلاصهٔ دانلود به زبان فایل‌ها |
| `ui/SettingsRoutingPages.kt` | بازنویسی کامل سه صفحه (مسیریابی، DNS/آی‌پی، تفکیک برنامه‌ها) و بازچینش صفحهٔ ایران مود؛ شیت ویرایشگر قانون با پیشنهاد، شیت «تنظیمات پیشرفته» |
| `ui/SettingsConnectionPages.kt` | سرفصل‌های تونل و توان، زیرنویس صفحهٔ اتصال |
| `ui/SettingsTestingPages.kt` | سرفصل‌های پینگ، هم‌زمان‌نویسی سه فیلد دکمهٔ خانه |
| `ui/SettingsSystemPages.kt` | «Live ping in the notification»، «Permissions» |
| `ui/SettingsAppearancePages.kt` | ارقام فارسی در چیپ‌های اندازهٔ متن |
| `ui/MarblePersianLexicon.kt` | ۱۹۹ کلید تازه + هفت الگوی زمان‌اجرا برای پیام‌های اعتبارسنجی که مقدار دارند |
| `test/RoutingOverviewV254Test.kt` | **فایل جدید.** ۱۳ تست برای قواعد بالا |

---

## ۵. تست

`app/src/test/java/com/marbleng/app/core/RoutingOverviewV254Test.kt` — روی JVM، بدون دستگاه:

- `choosingTheCustomModeIsTheOnlyWayToTurnTheRuleListOn` — حالت و کلید یک تصمیم‌اند و تغییر حالت هیچ
  قانونی را پاک نمی‌کند.
- `theBypassModeIsTheOneThatAddsTheHomeRanges` — «شبکهٔ خانه مستقیم» یک لایه است، نه یک حالت موازی.
- `theSummaryOfEveryModeSaysWhatHappensToOrdinaryTraffic` — هر چهار جمله از `PlainVoice` رد می‌شوند.
- `theGeoModeNamesItsThreeLayersAndNothingElse`، `switchingAdBlockingOffRemovesTheAdLayerInsteadOfShowingZero`،
  `theDomesticPrecisionLayerAppearsOnlyWhenItAddsSomething`، `theExpertListsAreCountedWithTheSameSplitTheCoreUses`،
  `aLiveRuleListIsCountedByItsEnabledRules` — خلاصه همان چیزی را می‌گوید که هسته اجرا می‌کند.
- `theSameMatcherTwiceIsReportedAgainstTheFirstOne`، `aRegexRuleIsFlaggedWhereverItHides`،
  `theActivePresetIsReadFromTheRulesThemselves` — سه اشتباه بی‌صدای قبلی.
- `thePageAndTheCoreAgreeOnTheShipDefault` — صفحه و ذخیره‌سازی روی یک پیش‌فرض توافق دارند.

بازبینی‌های منبع (`scripts/system-integrity-check.py`، `tools/kotlin-structure-check.py`،
`tools/compose-scope-check.py`، `tools/persian-normalize.py`) بدون خطا اجرا می‌شوند: ۳۶۳ بررسی، ۰ خطا.

---

## ۶. آنچه عمداً انجام نشد

- **حذف فیلدهای فهرست متخصص از `AppSettings` انجام نشد.** `routeDirectDomains`، `routeProxyDomains`،
  `routeBlockDomains`، `routeDirectIps`، `routeBlockIps`، `routeDomainMatcher` و `geoAssetSourceId` هیچ
  صفحه‌ای نداشتند و کاندیدای حذف بودند، ولی هر سه موتور (`XrayConfigHardener`، `SingBoxConfigBuilder`،
  `RoutingEngine`) و پشتیبان‌گیری/بازیابی به آن‌ها وابسته‌اند؛ حذفشان یک مهاجرت داده لازم دارد که
  خارج از دامنهٔ این بازسازی است. به‌جای حذف، در شیت «تنظیمات پیشرفته» دیده می‌شوند.
- **حذف `BenchMode` انجام نشد** برای همین دلیل: در `BenchmarkEngine` خوانده می‌شود و فقط صفحه ندارد.
- **تب «تست» در هاب تنظیمات** بدون تغییر ماند؛ کد `SettingsHubCategory` آن را به `NETWORK` نگاشت
  می‌کند و بازچینش دسته‌ها در V253 انجام شده بود.
- **هیچ صفحهٔ جدیدی به فهرست بیست‌وسه مقصد اضافه نشد** تا `SettingsHubLayoutV250Test` و بازبینی
  «هر مقصد دقیقاً یک بار» دست‌نخورده بماند.
