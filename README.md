# ScreenConsume

ScreenConsume is a privacy-first, open-source Android screen-time analytics app. It reads Android's Usage Access data, converts events into daily aggregates, and stores only those aggregates locally. The MVP has dashboards for today, seven days, and 30 days, plus app, trend, and settings views.

## Privacy by design

- Usage data stays in an on-device Room database.
- No account, backend, Firebase, analytics SDK, ads, or Internet permission.
- Exact app-open timestamps and individual interactions are processed in memory and are never persisted.
- Data will leave the device only after a user explicitly exports it or enables a future connection.

## Platform limitations

Usage Access is special access granted from Android Settings, not a runtime permission. Android/OEM event retention and delivery can vary. ScreenConsume uses public `UsageEvents` activity resume/pause events for foreground intervals. “Launches” means distinct foreground resumes observed for a package inside the aggregation window; it is not a process-launch count. ScreenConsume does **not** store notification counts or unlock counts in the MVP: notification counts are not reliably available from UsageStats APIs, and device unlock events vary across Android versions and OEMs. App categories come from `ApplicationInfo` when supplied by the installed app and may be absent.

An unfinished foreground session at the end of a collection window is capped at that window. A missing start event cannot be reconstructed and is not silently estimated.

## Architecture

The single `app` module keeps platform usage reads, Room persistence, repositories, pure domain analytics, UI, workers, and sync abstractions in separate packages. A small manual application container avoids dependency-injection framework overhead. `DailyAggregationWorker` reprocesses the last three days every six hours; a transaction replaces each day's records, and the `(date, appId)` primary key makes retries idempotent.

`SyncProvider` is an intentionally unimplemented extension point for future user-authorized destinations such as Google Sheets. CSV/JSON exports are also deferred and kept conceptually separate from persistent connections.

## Setup

1. Install Android Studio with Android SDK 36 and Java 17.
2. Open this repository and let Gradle sync.
3. Run the `app` configuration on an Android 8.0 (API 26) or newer device/emulator.
4. In ScreenConsume, tap **Grant Usage Access**, enable ScreenConsume, then return to the app.

Command-line checks:

```sh
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Instrumented Room tests require a connected device or emulator:

```sh
./gradlew connectedDebugAndroidTest
```

## Development status

Milestone 1 MVP. Collection, local daily persistence, period dashboards, per-app usage, a simple daily trend, background aggregation, privacy messaging, and domain tests are present. Custom ranges, monthly/yearly views, exports, richer charts, and opt-in integrations remain future work.

No license has been selected yet.
