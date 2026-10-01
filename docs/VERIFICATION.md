# Build and test evidence

Checks and reviewable commits: [PR #2](https://github.com/harshapriyag123/safecircle/pull/2). PR #1 work is preserved.

Verified implementation commit: `23d38ce582f2dcf38d47a784488e2ec7c69434f3` (Actions checks synthetic PR merge `c5a024b7f8a01f01df3193c586a7d9b33be57b95`). All four workflows passed, including Android device-smoke. Subsequent README/screenshots/evidence changes do not change application code.

| Check | Actual result |
| --- | --- |
| Backend local and [CI](https://github.com/harshapriyag123/safecircle/actions/runs/36904874265) | 48 tests passed; local Starlette/AnyIO dependency deprecation warning. Providers mocked; no purchase or message sent. |
| Android [build job](https://github.com/harshapriyag123/safecircle/actions/runs/36904874195/job/110512775164) | Genuine wrapper; 11 unit tests passed; lintDebug: 0 errors, 0 fatal, 414 warnings; assembleDebug passed. No baseline or error suppression. |
| iOS [CI](https://github.com/harshapriyag123/safecircle/actions/runs/36904874193) | XcodeGen and simulator build passed with RevenueCat/RevenueCatUI 5.92.0. Device purchases unverified. |
| Website [CI](https://github.com/harshapriyag123/safecircle/actions/runs/36904874232) | Desktop/mobile render, no overflow, simulated interactions, root redirect, privacy, signed Guardian acknowledgement and resolution, Status Only redaction and account cleanup passed. |
| Native emulator [run](https://github.com/harshapriyag123/safecircle/actions/runs/36904874195) | APK installed on Android 15; startup WorkManager registration and offline start/check-in/ETA extension/mark-safe passed; genuine native screenshots captured. No external alert or purchase attempted. |

Backend tests cover stage routing/deduplication, retries, acceptance vs delivery, signed receipts, leases, uncertain SMS, late check-in grace, ETA/resolution cancellation, consent withdrawal, capsule expiry/privacy, stale-snapshot protection, subscriber transfers/reconciliation/provider failure recovery, account deletion, logout revocation and persisted request limits.

## Downloadable Android build

[APK artifact](https://github.com/harshapriyag123/safecircle/actions/runs/36904874195/artifacts/11182798938): unzip `app-debug.apk`. Requires GitHub sign-in; expires October 31, 2026. Debug build, not a public store release. The build has no configured RevenueCat key or real provider credentials.

APK SHA-256: `3f3dfa27da5b3baef0d26a0200321204ee573d8a5150535de959eb3c72bead6b`.

[Android test/lint reports](https://github.com/harshapriyag123/safecircle/actions/runs/36904874195/artifacts/11183920520). [Website screenshots and 1024px icon](https://github.com/harshapriyag123/safecircle/actions/runs/36903508183/artifacts/11182926985). The icon is exported from the existing native vector; website Guardian screenshots depict a fictional demo and must not be presented as native screenshots.

`git diff --check` passed locally. Wrapper generated using checksum-verified official Gradle tooling. Local Android tasks could not reach the environment's Java network proxy; Android verification above uses GitHub-hosted CI. Xcode is unavailable locally.

## Live verification still required

No live RevenueCat/store purchase, production provider delivery, Railway deployment, persistent-volume restart, public store eligibility or complete native premium demo video has been verified. The supplied screenshot establishes administrative project ID `proj5f7132ef` only.

The configured Railway origin `https://safecircle-production-5a32.up.railway.app` returned HTTP 404 “Application not found” at `/health` and `/site/` on October 1. It is not a verified product website. Railway is connected, but no callable Railway tools are exposed in this session. Deployment requires access to the actual service/configuration and persistent storage, followed by HTTPS health/Guardian/provider tests.

[Native screenshots and result](https://github.com/harshapriyag123/safecircle/actions/runs/36904874195/artifacts/11183706603), also embedded in README. Offline test identity only. Captures show real app UI; no provider or purchase claim. Backup disabled; headings respect system bars in the final tested build.
