# Security policy and model

Screen Consume is an independent proof of concept that handles sensitive behavioral information. This document describes the reviewed source implementation and its limits; it is not a claim that the application is completely secure, production-hardened, or endorsed by Google or Android.

## Project status

There is currently no formal supported-version or security-fix commitment. Security reports concerning the latest revision are welcome, but response times and release timelines are not guaranteed for this proof of concept.

## Reporting a vulnerability

Prefer a private GitHub security advisory for this repository. Do not include personal usage exports, passwords, private keys, or signing material in a public issue. If private reporting is unavailable, open a minimal public issue requesting a private contact channel without publishing exploit details or sensitive data.

## Current security boundaries

- The release variant is configured with `android:debuggable=false`, code shrinking, resource shrinking, and the optimized default ProGuard rules.
- Compose tooling is a `debugImplementation`; no preview activity is declared in the application manifest.
- The app declares `android.permission.PACKAGE_USAGE_STATS`. WorkManager adds `WAKE_LOCK` and `RECEIVE_BOOT_COMPLETED` so periodic aggregation can run and be rescheduled after reboot; AndroidX also adds an app-scoped signature permission for non-exported dynamic receivers. The app does not request `INTERNET` or broad file access. WorkManager's unused network-state and foreground-service permissions are explicitly removed during manifest merging.
- Android automatic application backup is disabled with `android:allowBackup="false"`, legacy full-backup exclusions, and Android 12+ cloud/device-transfer exclusions.
- The only app-defined exported component is the launcher `MainActivity`, required for launching from the home screen. It exposes no custom deep link or intent API. Merged AndroidX manifests also expose WorkManager's job service behind the system-only `BIND_JOB_SERVICE` permission and diagnostics/profile receivers behind the system-only `DUMP` permission; ordinary applications cannot invoke them.
- Room and DataStore live in the application's private Android sandbox. No content provider, WebView, native/JNI library, dynamic code loader, shell execution, APK installer, accessibility service, or external network service is implemented.
- No online integration is exposed in the interface. No authentication, API client, credential storage, token handling, sync worker, or `SyncProvider` implementation ships. An integration must not be represented as functional until those boundaries are deliberately designed and reviewed.
- Raw `UsageEvents`, exact timestamps, and individual foreground intervals are held only while aggregating. The persisted records are daily per-app aggregates.
- Restore reads only a document explicitly selected through the system picker, applies a 25 MB input limit and 250,000-record limit, validates field lengths and usage totals, and writes through a Room transaction.

These statements describe the reviewed source configuration. Device firmware, the Android operating system, installed document providers, build host, and third-party dependency integrity remain outside the application's direct control.

The first-run **No internet connection** statement is accurate for the reviewed app itself. It does not prevent a user-selected Storage Access Framework provider from copying an explicitly exported file to cloud storage under that provider's own permissions and behavior.

## Sensitive data

The private Room database stores package name, display name, optional Android app category, date, usage duration, foreground-resume count, and morning/afternoon/evening/night totals. DataStore records onboarding completion and the timestamp of the last successful aggregation. Together, these can reveal habits, interests, schedules, and installed/used applications.

The Room database is not application-level encrypted. Its primary protections are the Android application sandbox, device lock, and platform storage protections. A rooted or compromised device, privileged malware, unlocked-device access, debugging of a debug build, or compromise of the operating system may expose it.

## Usage Access

The user must explicitly grant Android Usage Access in system settings. That access lets Screen Consume observe usage events containing package names and event timestamps retained by Android. Screen Consume does not use Accessibility services and does not capture screen content, typed text, notification contents, camera, microphone, contacts, SMS, calls, location, clipboard, or device identifiers.

Revoking Usage Access stops new collection but does not delete aggregates already stored. Application storage can be cleared through Android Settings, or the app can be uninstalled, to remove local records.

## Exports, restore, and backups

- CSV and JSON exports are plaintext. Anyone or any service with access to those destination files can read them.
- Files are selected through Android's Storage Access Framework. A user-selected document provider may be cloud-backed; choosing it can cause data to leave the device under that provider's behavior.
- `.scb` backups use AES-256-GCM with a random 16-byte salt and 12-byte IV. Their key is derived with PBKDF2-HMAC-SHA256 using 210,000 iterations. Authentication detects modification or an incorrect password.
- Backup security depends on password strength. The password is not stored and cannot be recovered. UI password text may remain in process memory until garbage collection even though repository-level character arrays are cleared after use.
- Import validation reduces accidental or hostile resource use but does not make untrusted files risk-free. Import only files from a trusted source.
- Automatic Android backup remains disabled; exports happen only after a user selects a destination. The application has no implemented automatic remote synchronization.

## Signing and release requirements

Android updates must be signed by the same release identity. Private signing keys and credentials are maintained outside Git and must not be committed, uploaded, regenerated, replaced, printed, or exposed. Release signing is not wired into Gradle or GitHub Actions; `assembleRelease` produces an unsigned APK and signing is a separate authorized maintainer operation.

Before distributing or installing a release:

1. Run unit tests, release lint, and `assembleRelease`.
2. Sign with the existing identity without placing passwords on the command line or in tracked files.
3. Verify the final APK certificate, `android:debuggable` state, permissions, exported components, and absence of debug-only components.
4. Record the APK SHA-256 through a trusted release channel.

Never expose the signing identity to pull-request automation or sign untrusted code.

## Threat assumptions and recommendations

The current model assumes a normally secured, non-rooted Android device; a trusted OS and build machine; explicit user control of Usage Access and document destinations; and review of dependencies and release artifacts. It does not defend against a compromised OS, root-level access, malicious accessibility software, physical access to an unlocked device, weak backup passwords, or disclosure after plaintext export.

Recommended ongoing controls—not current guarantees—include enabling GitHub Dependabot alerts/security updates and branch protection, reviewing every dependency update, periodically auditing the final release APK, protecting and separately backing up the release signing identity, and considering Room encryption only after evaluating key management and recovery tradeoffs.
