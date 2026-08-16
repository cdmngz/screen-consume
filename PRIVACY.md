# Screen Consume privacy information

Screen Consume is an independent proof of concept and is not affiliated with or endorsed by Google, Android, or the Digital Wellbeing team. This document describes behavior implemented in the reviewed source code. It does not by itself verify a separately distributed APK and does not cover modified builds, the Android operating system, device manufacturers, or third-party document providers selected by the user.

## Data the app accesses

After the user grants Usage Access in Android Settings, Screen Consume reads Android `UsageEvents` for requested time ranges. Events can contain application package names, foreground resume/pause/stop types, and exact event timestamps.

The app also asks Android's package manager for the display label and category of an observed package when available. This is used to make local reports readable. Screen Consume does not enumerate and persist a complete inventory of installed applications.

The app does not access camera, microphone, location, contacts, SMS, phone calls, notification contents, clipboard, accessibility data, advertising identifiers, or other device identifiers. It requests no broad storage permission.

## Data stored on the device

Raw usage events and exact timestamps are processed in memory during aggregation and are not written to the database. The private Room database stores:

- application package name, display name, and optional Android category;
- calendar date;
- total usage seconds and observed foreground-resume count for that app/date;
- usage seconds grouped into morning, afternoon, evening, and night.

DataStore separately records whether onboarding has been seen and the time of the last successful aggregation. Stored daily history has no automatic expiration and can cover years while the application remains installed and its storage is retained.

Android automatic backup is disabled. Local records are removed when the user clears Screen Consume's application storage or uninstalls it. Revoking Usage Access prevents future reads but does not erase existing aggregates.

## Network behavior

The application does not declare Android's `INTERNET` permission and contains no implemented network client, analytics, telemetry, advertising, account, backend, or synchronization provider. The `SyncProvider` interface is only an unused extension point; no provider ships in the current application.

No online integration is shown or enabled in the interface. The unused `SyncProvider` extension point does not connect to any service, request account access, authenticate, or upload data. Implementing a provider would require explicit user consent and a new privacy and security review.

Consequently, the reviewed application cannot directly send usage data to an Internet service. The first-run **No internet connection** badge refers specifically to this application behavior. It does not describe a cloud-backed document provider the user may deliberately choose in Android's system picker. This claim must be reassessed if a future build adds Internet permission or an integration.

## When data can leave the device

Data can leave the app's private storage only through a user-initiated export or backup destination selected with Android's system document picker:

- CSV and JSON exports contain the selected range in plaintext.
- Password-encrypted `.scb` backups contain all stored history.
- A selected document provider may store locally, on removable media, or in a cloud service. Screen Consume does not control that provider after the user selects it.

The application does not automatically upload these files. Once exported, copies are governed by the storage location, other applications with access to it, and the user's sharing choices. Plaintext exports should be handled as sensitive information.

Restore is also user initiated through the document picker. It imports compatible JSON or encrypted Screen Consume data into the local database; it does not upload the selected file.

## Encrypted backups

Encrypted backups use AES-256-GCM and a password-derived key. The password is not saved and cannot be recovered by the application. A strong, unique password and a trusted storage destination are necessary. Encryption protects the backup file, not the unencrypted Room database inside the app's Android sandbox.

## Implications of Usage Access

Usage Access is powerful because application activity can reveal routines and interests. Android controls whether events are available and how long they are retained. Screen Consume aggregates available events locally, but event gaps, OEM behavior, delayed collection, or revoked access can make reports incomplete.

Users can review or revoke Usage Access at any time in Android Settings. They should install only builds they trust and verify that a release retains the documented no-network and permission configuration.
