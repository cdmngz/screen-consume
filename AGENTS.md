# ScreenConsume repository instructions

This is the authoritative guidance for Codex and other AI agents working in this repository. Follow it for every change, in addition to the user's current request.

## Product boundaries

ScreenConsume is a native Kotlin Android application that turns Android Usage Access events into local daily aggregates. Privacy and security are product requirements, not optional polish.

- Do not add `INTERNET`, analytics, telemetry, advertising, tracking, accounts, remote services, or data transmission unless the user explicitly requests it.
- Do not add Android permissions unless necessary for an explicitly requested feature. Explain and obtain confirmation before materially changing permissions, networking, privacy, security, storage, import/export, Android backup, or signing.
- Keep usage history on-device unless the user explicitly approves an export or integration. Treat package names, app labels, usage duration, launch counts, time-of-day totals, and exported files as sensitive data.
- Keep Android automatic backup disabled unless explicitly requested. Preserve `android:allowBackup="false"`.
- Release builds must remain non-debuggable, minified, and resource-shrunk. Debug-only dependencies and components, including Compose `PreviewActivity`, must never enter release artifacts.
- Do not silently weaken import limits, backup encryption, data validation, component exposure, or the no-network design.

## Architecture

The project has one Android application module and uses a small manual `AppContainer`; do not introduce a dependency-injection framework without a demonstrated need.

- `app/src/main/java/org/screenconsume/app/data/usage/`: Android `UsageStatsManager`/`UsageEvents` access behind `UsageDataSource`.
- `data/database/`: Room entities, DAO, database, and portable query rows. Daily usage is uniquely keyed by `(date, appId)`.
- `data/repository/`: collection, transactional persistence, dashboards, exports, and restore.
- `data/export/`: pure CSV/JSON serialization, validation, and encrypted backup primitives.
- `data/preferences/`: DataStore preferences for onboarding and collection health only.
- `data/sync/`: unimplemented provider abstraction; no network provider currently ships.
- `domain/model/` and `domain/analytics/`: framework-light models, date ranges, aggregation, and derived analytics.
- `ui/`: Compose/Material 3 screens and `MainViewModel`.
- `workers/`: idempotent periodic aggregation through WorkManager.
- `app/src/test/`: JVM tests for analytics, aggregation, and portability.
- `app/src/androidTest/`: device tests for Room behavior and duplicate prevention.
- `app/schemas/`: committed Room schemas; review schema and migration implications together.

Preserve separation between platform collection, persistence, pure analytics, UI, and future integrations. Persist daily aggregates, not individual usage events or exact app-open timestamps. Derived dashboards should normally be calculated from stored daily data.

## Build and verification

Use Java 17 and Android SDK 36. Prefer the checked-in wrapper.

```sh
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
./gradlew connectedDebugAndroidTest       # connected API 26+ device/emulator required
./gradlew lintRelease assembleRelease
```

Run checks proportional to the change:

- Pure domain/export changes: relevant JVM tests, then `testDebugUnitTest`.
- Room changes: JVM tests, connected tests, and deliberate schema/migration review.
- UI or Android integration changes: unit tests, lint, debug build, and relevant device checks.
- Manifest, build, shrinking, or release changes: lint and assemble both affected variants; inspect the merged release manifest and APK when security boundaries are involved.
- Before handing off any code change, run `git diff --check` and report checks that could not run.

`assembleRelease` currently creates an unsigned release artifact. Signing is an explicit local release step; do not claim an unsigned APK is installable.

## Signing and secrets

- `.signing/` must remain Git-ignored. Never commit signing keys, passwords, certificates, credentials, tokens, secret properties, personal data, or generated keystores.
- Never print, inspect unnecessarily, log, transmit, or expose private signing material or passwords.
- Never delete, replace, regenerate, convert, or modify the existing release signing identity unless the user explicitly requests it. Preserving that identity is required for future APKs to update an installed application.
- Using the existing identity to produce an explicitly authorized release build is allowed. Prefer tools that prompt for passwords rather than command-line arguments or tracked configuration.
- Warn the user before changing release signing configuration. Never put the local signing identity or its passwords into GitHub Actions.

## Dependencies and GitHub security

- Keep dependency changes narrow and human-reviewed; never enable automatic merging.
- Review release notes, advisories, and the resolved graph. Coordinate Gradle, Android Gradle Plugin, Kotlin, KSP, and Compose tooling upgrades.
- Keep the wrapper distribution checksum synchronized with Gradle's official checksum. Do not casually generate dependency locks or verification metadata; introduce them only in a dedicated reviewed change.
- GitHub Actions are not required for Dependabot. Any future workflow must have minimal explicit `permissions`, pin third-party actions to full commit SHAs, avoid secrets for untrusted code, and never sign or publish using the local identity.

Do not clean, rewrite, or discard unrelated user changes in a dirty worktree.
