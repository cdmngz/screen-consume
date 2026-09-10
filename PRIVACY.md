# Screen Consume privacy policy

**Last updated:** September 10, 2026

**App:** Screen Consume

**Developer:** CD Apps

**Project and support:** [GitHub repository](https://github.com/cdmngz/screen-consume)

This policy describes the current app’s data practices. Screen Consume is an independent project and is not affiliated with Google, Android, or Digital Wellbeing.

## What the app reads

With your explicit permission in Android’s Usage Access settings, Screen Consume reads app activity events to calculate usage summaries. It processes package names, activity class names, foreground resume/pause/stop events, and exact event timestamps. It also looks up app labels, icons, and categories when Android makes them available.

The app does not capture screen contents or read camera, microphone, location, contacts, messages, calls, notification contents, clipboard contents, advertising identifiers, or device identifiers. It does not use an Accessibility service or request broad storage access.

## What stays on your device

The app processes raw events and exact timestamps in memory, including when calculating an hourly app-detail chart. It does not save an individual-event or exact app-open log.

Its private database stores these daily summaries:

- App package name, display name, and optional Android category.
- Calendar date and total foreground usage seconds.
- Observed foreground-resume count, called a launch count in exported data.
- Usage seconds grouped into morning, afternoon, evening, and night.

The app also stores the last successful collection time and your dashboard period, sort order, and brief-usage preferences. An onboarding preference exists in the source but is not written by the current UI. Android libraries maintain local operational state, such as scheduled background work.

Usage history can reveal routines and interests. The database is not separately encrypted by Screen Consume; it relies on Android’s app sandbox and device storage protections. The app configuration disables automatic Android backup and excludes app data from cloud backup and device transfer.

## Retention and deletion

Recorded history has no automatic expiration. Android event retention and collection delays can leave gaps; installing the app cannot recover events Android no longer retains.

- **Stop collection:** revoke Usage Access for Screen Consume in Android Settings. Existing summaries remain stored.
- **Delete local data:** clear Screen Consume’s app storage in Android Settings, or uninstall it. There is currently no in-app selective deletion control.
- **Delete exported copies:** remove them separately from the destination you chose, including any cloud copies or provider backups. Clearing app storage does not remove those files.

Deleting Screen Consume’s records does not delete the usage events retained independently by Android.

## Exports, backups, and restore

Export and restore are initiated by you through Android’s document picker. CSV/JSON export writes your selected date range or all recorded history through today in plaintext. Encrypted `.scb` backups always cover all recorded history through today and protect it with AES-256-GCM using a password-derived key. The password is not saved persistently and cannot be recovered by the app. Backup encryption does not encrypt the app’s local database.

The destination may be local storage, removable media, or a cloud-backed document provider. That provider may transmit or retain the file under its own practices. Screen Consume does not automatically upload it. Anyone with access to a plaintext export can read it.

Restore reads the JSON or encrypted backup you select into the local database. Matching app/date records are updated; other stored records remain. CSV cannot be restored. Restore does not transmit the selected file to the developer. See [SECURITY.md](SECURITY.md#exports-and-restore-limits) for file limits and backup limitations.

## Network access and external services

Screen Consume has no Android `INTERNET` permission, accounts, analytics, telemetry, advertising, or implemented online synchronization. The developer does not receive usage history through the app.

**Open privacy policy** opens a fixed GitHub page in an external browser. No usage history is attached to the link. The browser and GitHub may process ordinary web-request information under their own practices. This is separate from the app’s no-network design, as are cloud-backed document providers you select for exports.

If you submit a support issue on GitHub, the information you choose to submit is handled by GitHub and is normally public. Do not post usage exports, passwords, signing material, or other private information. For security concerns, follow [SECURITY.md](SECURITY.md#reporting-a-vulnerability).

## Contact and changes

For general privacy questions, use [GitHub Issues](https://github.com/cdmngz/screen-consume/issues) without including personal data. If a private discussion is needed, request a private contact channel first.

This policy will be updated when data practices change. New networking or data-sharing features require a separate privacy review and clear user disclosure before release. This document describes the repository’s implementation; modified builds and third-party services may behave differently.
