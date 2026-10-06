# Play Store release automation

## Maintainer flow

1. On `main`, run **Prepare release PR** in GitHub Actions. Choose patch, minor, or major. Enter up to 500 characters of release notes for each of English, Spanish, German, French, Italian, and Portuguese. Blank fields use translated generic bug-fix/improvement text. These are Play “What's new” notes, not full listing descriptions; review generic claims for accuracy.
2. Review the generated `codex/release/…` PR. It increments `versionCode` by one, updates the semantic version, and commits all six notes to `release/current.json`. For example, starting at 1.7.0 / 9, patch becomes 1.7.1 / 10, minor 1.8.0 / 10, and major 2.0.0 / 10. One open release PR is allowed. Approve the final commit and merge manually; no auto-merge is configured. Changed commits require a fresh approval. GitHub may require approval to run CI on bot-created PRs.
3. After **Android CI** succeeds for that merge commit on `main`, **Release closed testing** verifies the approved PR, rebuilds the exact commit, reruns tests/lint/Detekt and APK security inspection, signs its AAB with the registered Play upload key, and submits it to the configured closed-testing track. Ordinary merges do not publish. A stale version bump fails. API failures fail the workflow; no automatic production publication occurs.
4. Test the release and wait for required Play review/access requirements. Then, on `main`, run **Promote release to production** with the successful closed-testing workflow's numeric run ID (from its URL). The pipeline verifies the successful publication receipt, original approved merge, and exact completed version on the closed track. It promotes the already uploaded version to production at 100%, with the same notes; it does not rebuild or sign again. Versions at or below the existing production version are rejected.

Google review and managed publishing can delay availability even after a successful submission. API track status does not prove that testing, policy review, or account-specific production access requirements have been satisfied. The production operator must check these in Play Console. Publishing fails while other changes are in review rather than cancelling that review. Avoid concurrent manual Play Console edits while these workflows run.

## One-time setup (required before publishing)

These workflows are inactive until committed to `main` and configured. This change does not create keys, upload credentials, modify repository settings, or publish a release.

### Play Console

- Set up `org.screenconsume.app`, enroll in Play App Signing, complete listing/policy declarations and any required initial manual upload, and create the closed-testing track and tester group. Obtain its **API track ID**, which may be `alpha` or a custom ID; a display name is not necessarily the ID. The pipeline rejects production, open-testing (`beta`), and internal-testing (`qa`/`internal`) track IDs, including form-factor variants.
- Use a **separate upload key** registered with Play. The existing local release signing identity and its passwords must never enter GitHub Actions. Do not replace or regenerate that identity. If the existing upload certificate is the local identity, first arrange a separate upload key registration/reset in Play Console as an explicitly approved maintainer operation. Google continues to sign delivered apps with the established app signing key. The workflows only sign upload bundles.
- Enable the Google Play Android Developer API in a Google Cloud project. Grant a dedicated service account app-scoped testing release permissions; use a separate account with production release permission for production. Do not grant access to usage data or unrelated apps. Keep account JSON private and rotate it under your credential policy.

### GitHub

Enable **Allow GitHub Actions to create and approve pull requests** in repository Actions settings. The prepare workflow uses `GITHUB_TOKEN` to open PRs, never to approve or merge them. `main` requires PRs, all five Android CI checks, an up-to-date branch, and resolved review conversations, including for administrators. Force pushes and deletion are blocked. Ordinary PRs do not require another reviewer, so a solo maintainer can merge their own changes; the release gate separately requires human approval of the final release PR commit. Stale approvals are dismissed. Protect workflow/script changes with maintainer review. An approval on the final PR commit from the human repository owner (`cdmngz` for this personal repository) is also checked at deployment time; do not dismiss that review after publication if you intend to promote the release.

Create environments **play-closed** and **play-production** restricted to `main`. Closed testing should have no required environment reviewer if it must run automatically after merge. Production may have a required reviewer as an extra safeguard; dispatch is already manual. Configure these environment variables/secrets:

| Setting | Kind | Environment | Value |
| --- | --- | --- | --- |
| `PLAY_CLOSED_TRACK` | Variable | Both | Existing closed track API ID |
| `PLAY_UPLOAD_CERT_SHA256` | Variable | play-closed | Registered upload certificate SHA-256 fingerprint |
| `PLAY_UPLOAD_KEYSTORE_BASE64` | Secret | play-closed | Base64 of the separate upload keystore |
| `PLAY_UPLOAD_KEY_ALIAS` | Secret | play-closed | Upload key alias |
| `PLAY_UPLOAD_STORE_PASSWORD` | Secret | play-closed | Keystore password |
| `PLAY_UPLOAD_KEY_PASSWORD` | Secret | play-closed | Key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Secret | Both | Dedicated environment's Google service account JSON |

Never paste secrets into workflow inputs, commits, logs, issues, or PRs. Upload keys are decoded into owner-readable temporary files only during the signing step and removed afterwards. Passwords are passed to Java tools through environment lookup, not command-line values. Builds and ordinary PR CI do not receive release credentials. The upload certificate fingerprint and signed bundle certificate are checked before upload. CI signing leaves Gradle's normal unsigned release configuration unchanged.

All release workflows serialize Play operations and disable cancellation of active publication. Successful closed publication records a GitHub deployment receipt tied to the exact release SHA and run ID; production checks that receipt and a successful publication job. Rerunning a completed closed submission can recover a missing receipt without uploading the same version again, provided Play still has the same version and notes. Failed pre-publication runs cannot authorize production. Retain workflow runs and deployment records for versions you intend to promote. Ordinary CI can be retried to retry a failed automatic release gate.

The six release-note locales are `en-US`, `es-ES`, `de-DE`, `fr-FR`, `it-IT`, and `pt-PT`. Change the Portuguese locale deliberately if your Play listing uses Brazil instead of Portugal. Release notes do not create or translate full store listings.

## Verification and limits

Run `python3 -B -m unittest discover -s scripts -p 'test_*.py'`, workflow linting, and the repository's Android checks before merging workflow changes. Real signing/API delivery requires the configured upload key and Play account and must be verified with a closed-testing release. No connected-device tests run in these pipelines. Google API errors are reported by HTTP status without printing credential-bearing request/response data.

References: [Play tracks and release notes](https://developers.google.com/android-publisher/tracks), [Play App Signing and upload keys](https://support.google.com/googleplay/android-developer/answer/9842756), [edit commit/review behavior](https://developers.google.com/android-publisher/api-ref/rest/v3/edits/commit), and [GitHub token-triggered workflow behavior](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/trigger-a-workflow).
