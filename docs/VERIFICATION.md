# Build and test evidence

Checks and reviewable commits: [PR #2](https://github.com/harshapriyag123/safecircle/pull/2). PR #1 work is preserved.

Verified implementation commit: `23d38ce582f2dcf38d47a784488e2ec7c69434f3` (Actions checks synthetic PR merge `c5a024b7f8a01f01df3193c586a7d9b33be57b95`). All four workflows passed, including Android device-smoke. The historical evidence below predates the current Vercel changes; current evidence is recorded further down.

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

## Current Vercel implementation verification — 2026-10-02 UTC

All four workflows passed on commit `24a5a2877c8d0924e4e9f503e9a9bd3f86c51ee1`:

| Check | Evidence |
|---|---|
| Backend | [Run 36947929899](https://github.com/harshapriyag123/safecircle/actions/runs/36947929899): 51 SQLite tests passed (2 PostgreSQL-only skips), 53 PostgreSQL tests passed |
| Android | [Run 36947929871](https://github.com/harshapriyag123/safecircle/actions/runs/36947929871): unit tests, lint, assembleDebug and Android 15 emulator safety workflow passed |
| Website | [Run 36947930430](https://github.com/harshapriyag123/safecircle/actions/runs/36947930430): actual desktop/mobile landing/Guardian checks plus companion registration, failed-request state preservation, canonical check-in, ETA, role labels, server history and logout isolation |
| iOS | [Run 36947929885](https://github.com/harshapriyag123/safecircle/actions/runs/36947929885): updated server-backed basic screens compiled for the simulator with RevenueCat/RevenueCatUI |
| Scheduler adapter | Three Node regression tests passed: one authenticated tick, rejected failures, invalid configuration/redirect protection |

[Updated APK artifact](https://github.com/harshapriyag123/safecircle/actions/runs/36947929871/artifacts/11202692715)
uses `https://safecircle-site.vercel.app` as the native API default. It is a debug
build with no configured store billing key. GitHub sign-in is required for Actions
artifacts; unzip `app-debug.apk`. This is not a public store release.

Local checks after the partial-snapshot privacy regression was added: **52 tests
passed, 2 PostgreSQL-only tests skipped**. This regression ensures omitted fields
cannot reset privacy or erase capsules/telemetry; explicit null still clears them.
The latest commit's CI is linked through [PR #2](https://github.com/harshapriyag123/safecircle/pull/2);
check its head SHA rather than treating historical passes as current evidence.

The existing Vercel Hobby project deploys the full website/backend, using a
dedicated Neon Free database and privately configured independent secrets.
Controlled production API verification passed registration/login, invalid password,
worker authorization and a manual tick, owned sessions, Primary/Backup signed
links/actions, check-in/ETA, escalated privacy redaction, terminal resolution,
server history and logout revocation. Account/session/history persistence across
redeployment, another owner denied access, expired capsule cleanup and all stage
queues (zero attempts) with ETA cancellation also passed. Fictional accounts/coordinates and no
notification contacts were used. See [deployment](VERCEL_DEPLOYMENT.md).

Continuous monitoring remains blocked on an authorized free scheduler account;
manual ticks are not ongoing monitoring. No live provider receipt, RevenueCat/store
purchase/restore, physical-device suspension/reconnect, public store eligibility
or complete native premium demo video is claimed. The project ID screenshot
establishes administrative metadata `proj5f7132ef` only. The obsolete Railway
service is not used or required for this deployment.

## Provider safeguards follow-up — 2026-10-02 UTC

Local backend: 70 passed, 2 PostgreSQL-only skips. Tests exercise missing adapter authentication, unsafe URLs, redirect refusal, response/error redaction, malformed receipts and independent production secrets alongside the existing lifecycle/privacy/outbox suite. Three scheduler adapter tests passed; these also run in backend CI. No real providers were contacted. Removed unused legacy direct SMS code that read capsule contacts without current Guardian consent; durable stage-specific jobs remain the sole escalation path. Final commit CI and deployment evidence are recorded on PR #2 after checks finish.
