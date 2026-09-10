# Security policy and model

Screen Consume handles sensitive usage history. This document describes source-level safeguards and known limits, not an independent security certification. User-facing data practices are in [PRIVACY.md](PRIVACY.md).

## Reporting a vulnerability

If GitHub offers **Report a vulnerability** on this repository’s Security tab, use that private channel. Otherwise, open a minimal [issue](https://github.com/cdmngz/screen-consume/issues) requesting a private contact channel. Do not publish exploit details, usage exports, passwords, or signing material.

The project has no formal supported-version or response-time commitment. Reports about the latest revision are welcome.

## Current safeguards

- No `INTERNET` permission or implemented network client, analytics, advertising, account system, or synchronization provider.
- Usage Access is explicitly granted in Android Settings. WorkManager contributes wake-lock and boot-completed permissions; unused network-state and foreground-service permissions are removed during manifest merging.
- Android automatic backup is disabled with `allowBackup="false"` and backup/device-transfer exclusion rules.
- Room, DataStore, and WorkManager state use app-private storage. Raw usage events and activity timestamps are processed in memory; persisted usage records are daily aggregates.
- The release variant is non-debuggable, minified, and resource-shrunk. Compose tooling is a debug-only dependency.
- The only app-defined exported component is the launcher activity, with no custom deep-link API. AndroidX contributes a non-exported initialization provider and other library components. Exported job/diagnostic/profile components are protected by `BIND_JOB_SERVICE` or `DUMP`; inspect the merged release manifest after dependency changes.

The authoritative source locations are [AndroidManifest.xml](app/src/main/AndroidManifest.xml), [backup rules](app/src/main/res/xml/backup_rules.xml), [data-extraction rules](app/src/main/res/xml/data_extraction_rules.xml), and [build configuration](app/build.gradle.kts). Source configuration does not replace inspection of the final distributable artifact.

## Exports and restore limits

CSV and JSON exports are plaintext and support an inclusive date range or all history. They stream one day of database rows at a time on the I/O dispatcher, avoiding a complete in-memory plaintext file. Encrypted backups still assemble the full payload in memory. A selected document provider can be cloud-backed. The app’s no-network permission does not constrain that provider or the external browser used to open the privacy policy.

Encrypted `.scb` files use AES-256-GCM with a 128-bit authentication tag, random 16-byte salt, and random 12-byte IV. PBKDF2-HMAC-SHA256 derives a 256-bit key using 210,000 iterations. Incorrect passwords or modified ciphertext fail authentication. Password strength remains important; there is no recovery mechanism. Repository password arrays are cleared after use, but UI strings and plaintext buffers are not guaranteed to be erased from process memory immediately.

Restore reads a user-selected file, limits input to 25 MiB and 250,000 records, validates string lengths, dates, nonnegative usage/count values and matching time-of-day totals, and applies changes transactionally. Matching app/date records are overwritten; repeated restores do not add duplicate daily rows. See [UsageRepository.kt](app/src/main/java/org/screenconsume/app/data/repository/UsageRepository.kt) and [DataPortability.kt](app/src/main/java/org/screenconsume/app/data/export/DataPortability.kt).

Known portability limits:

- Export has no corresponding size/record cap. A sufficiently large exported backup can exceed the current restore limits.
- All-time export and backup end at today. Future-dated records accepted from an imported file are not included.
- JSON validation does not establish the file’s origin or prove that its usage history is accurate.
- CSV quoting preserves CSV syntax but does not neutralize spreadsheet formulas in app labels or other imported text. Treat those fields as untrusted when opening CSV in a spreadsheet.

## Protection limits

The local database is not application-level encrypted. The design relies on Android’s sandbox, device storage protections, and a trusted operating system. It does not protect history against root access, a compromised OS, debugging of a debug build, or someone using an unlocked device. Exported copies are outside the app’s control and must be deleted separately.

A collection timestamp is a freshness indicator, not a completeness guarantee. Android event retention, revoked access, and delayed background work can leave gaps. The project has not undergone a broad independent security or device-manufacturer audit.

## Signing and release

For a manual source commit, review `git status --short` and the complete diff, including new files. Stage only the intended source, tests, translations, documentation, and store copy. Review `git diff --cached` and run `git diff --cached --check` before committing. Keep local configuration, signing material, generated builds, usage exports, and personal captures out of the index; ignore rules do not protect files already tracked by Git.

Use the established release identity so updates remain compatible. Never commit, upload, print, replace, or regenerate signing keys or credentials as part of routine work. Detailed repository rules are in [AGENTS.md](AGENTS.md#signing-and-secrets).

`assembleRelease` produces an unsigned APK. Release signing is a separate authorized maintainer step; CI does not sign or publish.

Before distribution:

1. Run the applicable checks in [AGENTS.md](AGENTS.md#build-and-verification), including release lint and assembly.
2. Sign with the existing identity without placing passwords in command-line arguments or tracked configuration.
3. Verify the final certificate, permissions, exported components, non-debuggable state, and absence of debug-only components.
4. Record the final APK SHA-256 through the release channel.

Dependency updates require human review. CI actions are pinned to commit SHAs and use explicit minimal permissions; do not expose signing material or other secrets to untrusted code.
