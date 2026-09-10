# Screen Consume

Screen Consume is a native Kotlin Android app that turns Android Usage Access events into daily app-usage summaries stored on your device. It requires no account and has no Internet permission, analytics, advertising, or telemetry.

This is an independent, early-stage open-source project, not affiliated with or endorsed by Google, Android, or Digital Wellbeing.

## Features

- Day, Week, Month, and Year dashboard views with previous/next controls, horizontal swipes, and **Back to today**.
- Recorded period totals and comparisons against the immediately preceding range of equal length. Comparisons identify the dates and flag partial current days; missing prior usage is not treated as a reliable baseline.
- Stacked usage charts and app shares, with selectable bars and morning/afternoon/evening/night totals.
- App search, sorting by usage or name, and an option to include usage below one minute.
- Tap an app to highlight its chart segments; use Details to open its history. Chart selections show dates, totals, and the leading apps.
- Dashboard period, sort order, and brief-usage preference are remembered locally.
- Per-app trends, an interactive calendar, all-time and period totals, active days, and consecutive-use streaks. Long charts scroll horizontally and bring selected points into view.
- Collection freshness and refresh/retry status.
- **Export a date range or all history** as plaintext CSV or JSON, plus password-encrypted `.scb` backup and JSON/backup restore.
- English, Spanish, Italian, French, Portuguese, and German; automatic light and dark themes.

Dashboard and app details share calendar periods: Week runs Monday–Sunday; Month and Year use complete calendar boundaries. Opening app details preserves the dashboard period and offset. The headline **Total Day Time** refers to the selected range’s final day, capped at today; **Average in {month year}** includes every calendar day in that month, through today for the current month.

## Privacy and data limits

Usage Access is granted and revoked in Android Settings. Raw events, activity names, and exact timestamps are processed in memory; only daily per-app aggregates are persisted. Reports may be incomplete because Android retains limited events and background collection can be delayed. A fresh collection timestamp does not prove complete historical coverage.

History has no automatic expiration. Clear app storage or uninstall to remove local records; revoking Usage Access alone does not delete them. The database relies on Android’s sandbox and storage protections, not separate database encryption. Automatic Android backup is disabled in the app configuration.

CSV/JSON export defaults to the dashboard period, capped at today, and supports a custom inclusive date range or all history. Plaintext exports stream daily batches off the main thread. Encrypted backups always include all recorded history through today. Android’s document picker can offer cloud-backed destinations. The privacy-policy button opens GitHub in an external browser; the app sends no usage history with that link.

See [PRIVACY.md](PRIVACY.md) for the data lifecycle and [SECURITY.md](SECURITY.md) for security reporting, technical boundaries, and restore limitations.

## Build and verification

Use Android Studio’s bundled JBR for local Gradle runs and set the IDE Gradle JDK and terminal `JAVA_HOME` to the same installation. Compile SDK is 37, target SDK is 36, minimum SDK is 26, and Java/Kotlin bytecode targets are 17. Build configuration lives in [app/build.gradle.kts](app/build.gradle.kts); contributor requirements and the full check matrix live in [AGENTS.md](AGENTS.md).

```sh
./gradlew testDebugUnitTest lintDebug :app:detektDebug :app:detektRelease assembleDebug
```

The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. Install it on an API 26+ device or emulator and grant Usage Access from Android Settings.

```sh
# Requires a connected API 26+ device or emulator.
./gradlew connectedDebugAndroidTest

# Release verification; this does not sign the APK.
./gradlew lintRelease assembleRelease
```

The release APK is `app/build/outputs/apk/release/app-release-unsigned.apk`. Follow [SECURITY.md](SECURITY.md#signing-and-release) before distribution.

JVM tests cover aggregation, analytics, portability, and chart/range calculations. Connected tests cover Room behavior. These do not constitute a complete UI, accessibility, OEM, or security audit. Optional coverage generation uses `./gradlew createDebugUnitTestCoverageReport`; reports are under `app/build/reports/`.

[Android CI](.github/workflows/android-ci.yml) runs JVM tests, lint, debug/release builds, a release bundle, and patch formatting checks on pushes and pull requests to `main`. Its runner uses Temurin 17; local validation uses Android Studio’s JBR. CI does not run connected tests or Detekt, sign releases, or publish the app. [Dependabot](.github/dependabot.yml) proposes dependency updates for human review.

## Architecture

A single Android application module uses Compose/Material 3, Room, Coroutines/Flow, ViewModel, WorkManager, DataStore, and a small manual `AppContainer`.

| Location under `app/src/main/java/org/screenconsume/app/` | Responsibility |
| --- | --- |
| `data/usage/` | Android usage-event access and session tracking |
| `data/database/` | Room entities and queries |
| `data/repository/` | Collection, persistence, export and restore |
| `data/export/` | Serialization, validation and backup encryption |
| `data/preferences/` | Collection timestamp and dashboard preferences; reserved onboarding flag |
| `data/sync/` | Unimplemented provider interface; no integration ships |
| `domain/` | Models, ranges, aggregation and analytics |
| `ui/` | Compose screens and ViewModel |
| `workers/` | Periodic collection |

The worker requests execution every six hours and reaggregates today and the previous two days. Android controls actual timing. Each date is replaced transactionally; the `(date, appId)` key prevents duplicate daily records. Derived dashboards are calculated from stored aggregates.

## Documentation and store assets

- [PRIVACY.md](PRIVACY.md): user-facing data practices and deletion instructions.
- [SECURITY.md](SECURITY.md): vulnerability reporting, safeguards and known limits.
- [AGENTS.md](AGENTS.md): repository change and verification rules.
- [Store listing](store-listing/README.md): listing copy, graphics and screenshot preparation.

Existing screenshots are working assets, not evidence that every current screen has been reviewed. Check them against the current build before publishing.

## License

[Apache License 2.0](LICENSE).
