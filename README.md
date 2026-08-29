# Screen Consume

Screen Consume is a privacy-first Android Digital Wellbeing proof of concept. It explores how richer long-term usage insights, historical comparisons, and user-controlled data portability could complement the digital wellbeing experience on Android.

The project is backed by a working, fully native Android implementation built with Kotlin and Jetpack Compose. It is intended as a concrete design and engineering exploration—not as a competitor to, or replacement for, Android's Digital Wellbeing features.

> **Independent project:** Screen Consume is not affiliated with, endorsed by, sponsored by, or an official product of Google, Android, or the Digital Wellbeing team. Android, Google, and Digital Wellbeing are trademarks of their respective owners.

## The problem being explored

Digital wellbeing tools can help people understand and adjust their relationship with technology. Screen Consume began with a narrower question: what additional value could emerge if people had access to more durable, inspectable, and portable summaries of their own app usage?

The proof of concept explores several ideas:

- Swipeable daily, weekly, calendar-month, and calendar-year dashboard views, with custom ranges available for export.
- A stable headline summary for today's total screen time and the current calendar month's daily average, with both compared directly against the previous month's daily average.
- App, category, launch-count, interactive daily-trend, calendar, streak, usage-pattern, and broad time-of-day perspectives.
- Indefinite local retention of compact daily aggregates, controlled by the device owner.
- Transparent CSV/JSON export and restorable encrypted backups.
- A local-first architecture that does not require an account, backend, or network permission.

These are proposed concepts demonstrated by this repository. They should not be interpreted as statements about the current capabilities, priorities, or future plans of Google's Digital Wellbeing product.

## What the prototype demonstrates

The current app includes:

- A dashboard period dropdown for day, week, calendar month, and calendar year, with horizontal gestures for moving between adjacent periods.
- An interactive stacked usage chart: time-of-day buckets for today, daily buckets for a week, weekly buckets for a month, and monthly buckets for a year. Each bucket ranks the top three apps and groups the remainder as **Other**; tapping a bar reveals exact app names, durations, and totals.
- Today's total screen time and the current calendar month's daily average, each with a plain-language definition and a direct percentage/duration comparison against the previous month's daily average. The selected breakdown period does not change these headline definitions.
- Daily per-app aggregation with morning, afternoon, evening, and night totals.
- A dedicated per-app detail view that opens to 7 days and offers 7-day, 30-day, and calendar-year presets. Swiping across a calendar-year chart explores earlier or newer years, while navigation remains bounded at the current year so future dates are never exposed.
- A scrubbable per-app daily trend chart in a Material card with labeled time and date axes, plus an independently pageable calendar. One finger inspects dates in either visualization; as on the dashboard, a longer swipe right shows an older period and a longer swipe left returns toward newer periods. Calendar-year charts begin at January and show quarterly month markers for orientation. The calendar includes date numbers, weekday labels, month markers, usage-intensity shading, and navigation back to 2010 even when the selected analytics preset is shorter.
- Ranked consecutive-use streaks with compact date labels inside their progress bars.
- Distinct per-app usage-pattern summaries for the most-used day and weekday/weekend usage share.
- Screen-reader previous/next-date actions for the interactive trend and calendar, plus text summaries of the selected values.
- Explicit messaging when a blank day means only “no recorded usage” or collection health suggests that part of a period may be incomplete.
- Periodic, idempotent reaggregation of recent days with collection-health status.
- Plaintext CSV and JSON export for a selected date range.
- Idempotent JSON restore with input validation and resource limits.
- Password-encrypted full-history backup and restore.
- English, Spanish, Italian, French, Portuguese, and German interfaces selected automatically from the device language.
- Automatic light and dark themes based on the device setting, including theme-aware chart and system-bar colors.
- A dashboard with period analytics and app search/expansion, full-screen per-app analytics, and a separate settings view.

No online integration is exposed in the interface. The source retains only a provider-neutral future extension interface; no provider, authentication flow, API client, upload job, or other online connection is implemented or shipped.

## Screenshots

Screenshots are not yet included. Before a public release, add captures made with synthetic or non-personal usage data.

| Dashboard | App detail | Settings |
| --- | --- | --- |
| _Screenshot pending_ | _Screenshot pending_ | _Screenshot pending_ |

## Privacy-first and local-first

Screen Consume is designed so the core experience works entirely on the device:

- No account or backend is required.
- No Firebase, analytics, telemetry, advertising, or tracking SDK is included.
- The application does not request Android's `INTERNET` permission.
- The first-run **No internet connection** badge describes the current app boundary: no network permission, network client, account system, automatic upload, or online provider.
- WorkManager contributes wake-lock and boot-completed permissions for periodic aggregation and rescheduling; neither provides network access or user-data access.
- Raw usage events and exact event timestamps are processed in memory and are not persisted.
- Compact daily aggregates are stored in the app's private Room database with no application-level expiration.
- Android automatic application backup is disabled.
- Data leaves the app's private storage only when the user explicitly chooses an export or backup destination. Android's document picker may offer third-party or cloud-backed destinations; those providers are outside Screen Consume and do not mean the app itself has a network connection.

See the public [privacy policy](https://github.com/cdmngz/screen-consume/blob/main/PRIVACY.md) for the implementation-specific data description and [SECURITY.md](SECURITY.md) for security boundaries and limitations. The same privacy-policy URL is available from the app's Settings screen.

## Usage Access

Screen Consume requires Android's Usage Access special access (`android.permission.PACKAGE_USAGE_STATS`). The user grants it explicitly from Android Settings; it is not a normal runtime permission dialog.

Usage Access is needed to read `UsageEvents` activity resume, pause, and stop events for apps used on the device. Screen Consume processes those events into daily summaries. Without Usage Access, the app shows an explanatory empty state and cannot collect new usage information.

Because Usage Access exposes package names and event timestamps retained by Android, it is sensitive. It can be revoked at any time in Android Settings. Revoking access stops new collection but does not delete aggregates already stored.

## Data model

For an app observed in usage events, Screen Consume stores:

- Package name, display label, and optional Android-provided category.
- Calendar date.
- Total foreground usage seconds.
- Count of observed foreground-resume events.
- Usage seconds grouped into morning, afternoon, evening, and night.

DataStore separately holds onboarding completion and the timestamp of the last successful aggregation. The app does not store individual interaction records, exact app-open timestamps, notification contents/counts, unlock counts, screen contents, typed text, location, contacts, messages, calls, clipboard content, or device identifiers.

“Launch count” means foreground-resume events observed in the aggregation window, not operating-system process launches. Android and device-manufacturer retention behavior can produce missing events or incomplete history; Screen Consume does not silently invent missing data.

## Export and encrypted backup

Exports use Android's system document picker, so the app needs no broad storage permission.

- CSV and JSON exports contain the selected date range in plaintext.
- JSON restore upserts records by app/date rather than creating duplicates.
- Encrypted `.scb` backups contain all stored history and use AES-256-GCM with a key derived from the user's password using PBKDF2-HMAC-SHA256.
- Restore input is limited to 25 MB and 250,000 records, with field-length and usage-value validation.

A selected document provider may be cloud-backed. In that case, the user's explicit choice of destination can cause the file to leave the device. Screen Consume itself has no network client and performs no automatic upload. Exported files remain sensitive and are outside the app's control after creation.

## Security considerations

Usage history can reveal routines and interests. The Room database is not application-level encrypted; it relies on Android's application sandbox, device lock, and platform storage protections. Root access, a compromised operating system, privileged malware, physical access to an unlocked device, or disclosure of an exported file are outside the app's protection boundary.

The release variant is configured as non-debuggable, minified, and resource-shrunk. Compose tooling is debug-only, Android automatic backup is disabled, and the source manifest declares no network permission. A final distributable APK should still be audited after signing because source configuration alone does not prove the contents of a built artifact.

Please report security issues using the process in [SECURITY.md](SECURITY.md). Do not place personal usage data or secrets in a public issue.

## Architecture

Screen Consume is a single-module native Android application using Kotlin, Jetpack Compose/Material 3, Room, Coroutines and Flow, ViewModel, WorkManager, and DataStore. A small manual `AppContainer` keeps dependency wiring explicit.

```text
app/src/main/java/org/screenconsume/app/
├── data/
│   ├── usage/       Android UsageStatsManager/UsageEvents access
│   ├── database/    Room entities, DAO, and database
│   ├── repository/  Collection, persistence, analytics queries, export/restore
│   ├── export/      CSV, JSON, validation, and encrypted backup primitives
│   ├── preferences/ DataStore preferences
│   └── sync/        Future integration interface; no provider implemented
├── domain/
│   ├── model/       Framework-light models and date ranges
│   └── analytics/   Aggregation and derived calculations
├── ui/              Compose UI and ViewModel
└── workers/         Periodic recent-day aggregation
```

The worker reprocesses the current and previous two days every six hours. A transaction replaces each date's snapshot, and the `(date, appId)` key prevents duplicate daily records. Dashboards derive results from these stored aggregates rather than persisting redundant analytics.

## Build and run

Prerequisites:

- Android Studio with Android SDK 36 and current SDK build tools.
- Java 17.
- An Android 8.0/API 26 or newer device or emulator.

Open the repository in Android Studio and allow Gradle to sync, or use the checked-in wrapper:

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

The debug APK is generated under `app/build/outputs/apk/debug/`. Install it from Android Studio or with ADB, then launch Screen Consume and select **Grant Usage Access**.

Room instrumentation tests require a connected device or emulator:

```sh
./gradlew connectedDebugAndroidTest
```

## Tests and coverage

The repository currently includes 34 JVM unit tests and one connected Android test. The JVM suite covers usage aggregation, calendar-day clipping and time-of-day boundaries, dashboard analytics, CSV/JSON portability and validation, encrypted-backup handling, and app-detail analytics calculations. Detail tests verify empty-day handling, calendar date placement and intensity, consecutive-use streak ranking, weekday/weekend pattern totals, history bucketing primitives, visible-week limits, calendar-year boundaries, protection against future-year navigation, dashboard-consistent paging direction, and Y-axis labels. The connected Room test verifies that repeated daily upserts do not create duplicate records.

Run the JVM suite and generate the JaCoCo HTML report with:

```sh
./gradlew testDebugUnitTest createDebugUnitTestCoverageReport
```

The report is written to `app/build/reports/coverage/test/debug/index.html`. The current JVM report shows high coverage for the extracted chart calculations, but low project-wide coverage because Compose rendering, ViewModel flows, repositories, workers, and Android platform collection remain largely untested. Coverage output is a diagnostic baseline rather than a release-quality gate.

To build the hardened release variant:

```sh
./gradlew testDebugUnitTest lintRelease assembleRelease
```

`assembleRelease` currently produces `app/build/outputs/apk/release/app-release-unsigned.apk`. A maintainer must sign it with the project's established release identity before installation or distribution. Signing credentials and private keys must never be committed.

## Current status

Screen Consume is an early proof of concept, not a production service or an official Digital Wellbeing proposal. The local collection, aggregation, swipeable historical dashboard, full-screen app details, scrubbable trend and calendar views, streak and usage-pattern insights, light/dark themes, export, restore, encrypted backup, background work, unit-test coverage reporting, and core tests are implemented. Interactive detail charts expose text state and screen-reader date navigation, but a broader accessibility audit is still needed. Broader ViewModel/repository/UI coverage, device and visual-regression testing, data-deletion controls, migration strategy, and potential opt-in integrations also need further work.

The repository currently has no continuous-integration workflow. Dependabot is configured for weekly, human-reviewed Gradle dependency updates.

## Limitations

- Available history is limited by Android/OEM event retention and by how often the app can aggregate while Usage Access remains granted.
- A new installation cannot reconstruct events Android no longer retains.
- Background execution timing is controlled by WorkManager and the operating system; six hours is a requested interval, not an exact schedule.
- Foreground events can be missing, duplicated, delayed, or behave differently across Android versions and manufacturers.
- An unfinished foreground session is capped at the collection-window boundary; a missing start event is not estimated.
- App categories are optional metadata supplied by Android and may be absent or broad.
- The application database is not independently encrypted.
- Plaintext exports must be protected by the user after creation.
- Importing restores records but does not prove their origin or correctness.
- There is no implemented online synchronization provider.
- The project has not yet undergone broad accessibility, OEM, performance, or independent security testing.

## License

Screen Consume is open-source software licensed under the [Apache License 2.0](LICENSE). This permissive license allows use, modification, and redistribution while preserving attribution and providing an explicit patent grant.
