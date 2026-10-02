# SafeCircle gap audit — October 1, 2026

PR #2 preserves PR #1's authorization, lifecycle, privacy, event retry and billing fixes. This audit distinguishes implementation from live verification.

| Area | Implemented and checked | Remaining verification or work |
| --- | --- | --- |
| Ownership and offline events | Owner-scoped session/event/subscription APIs; transactional event deduplication; bounded batches; explicit-null PATCH handling | Password-confirmed deletion and logout revocation plus persisted request counters are implemented; verify reverse-proxy identity and add refresh rotation for production scale. |
| Durable safety delivery | SQLite/PostgreSQL outbox, per-deadline stage/role/channel deduplication, leased jobs, bounded retries, consented Primary/Backup contacts, resolution/ETA cancellation, authenticated receipts | Configure provider credentials, Guardian push adapter/device registration and production provider verification; exercise real delivery. Uncertain SMS outcomes await receipts to avoid duplicate retries. |
| Check-in and ETA | Android/iOS/server check-ins ensure at least five minutes ahead; stale upserts cannot move deadline backwards; deadline changes cancel obsolete jobs | Real device background, offline/reconnect, permission and clock tests. |
| Privacy | Guardian allowlist excludes raw contact/medical fields, precise coordinates withheld until authorized escalation, Status Only hides location, expired capsules purged, server expiry capped at 24h, completed job payload cleanup, Android cloud backup disabled | Production storage/key/backup verification, private provider/backup deletion support. User account deletion is implemented. |
| Free safety | Startup safety scheduler runs without RevenueCat key; basic privacy/session/multiple-Guardian controls are free | Android 15 emulator free session workflow passed. Physical-device background/reconnect tests and optional billing flows still need verification. |
| RevenueCat | Administrative project ID separated from credentials; native SDK/paywall/restore/Customer Center; authenticated identity; webhook ordering/deduplication; durable authoritative subscriber refresh including transfer/aliases | No account configuration access: actual offerings, store mappings, keys, pricing, purchase/restore/Customer Center, refunds and account-transfer tests remain unverified. |
| Android reproducibility | Official generated Gradle 8.14.3 wrapper with distribution SHA; Java17/AGP8.13.2/Kotlin2.1.20/SDK36; CI unit tests, lint, debug APK/report artifacts | Final commit checks are tracked in VERIFICATION.md. Lint warnings remain; local Java network is blocked, so Android verification uses GitHub Actions. |
| iOS billing | Pinned RevenueCat5.92.0; public Apple key build setting; SwiftUI paywall/restore/Customer Center; simulator build succeeds | Signed device/store testing, credentials and Android feature parity remain unverified/incomplete. iOS is a reference client. |
| Public website | Landing, actual simulated-demo screenshot, Guardian demo clearly labeled, signed Guardian experience, privacy/support/testing instructions; mobile/browser CI | Website and full backend code deployed to the existing Vercel Hobby domain. Dedicated free Neon database and secrets are configured. Continuous scheduler remains unprovisioned; live account/session/Guardian verification and native video pending. |
| Shipaton | Official 2026 requirements reviewed; checklist, category assessment and two-minute native demo script | Native emulator screenshots and 1024px icon exported. No qualifying store release, complete native premium video, product verification or student eligibility evidence supplied. |

Backend regression tests use mocks and send no real notifications or purchases. Android/iOS build success is compilation evidence, not successful store billing or physical alert delivery. Website screenshot is a fictional demo, not a native screenshot or live session result. Do not claim downloads, revenue, customer feedback, publication or Shipaton readiness from these checks.

## Current completion work — 2026-10-02 UTC

- Web companion: server-confirmed start/check-in/ETA/resolve/concern; failure does
  not falsely mark safe. Server history survives reload; account changes clear
  private local session/history/links. Primary/Backup labels are explicit. Status
  distinguishes backend readiness from unavailable monitoring. Keyboard dialog,
  labels and live feedback improved; old companion routes redirect to v4.
- Android Nearby Support: fictional listings removed; explicit external map
  searches and missing-handler feedback implemented.
- Dedicated production Neon Free database and private secrets configured. This is
  configuration evidence, not proof of a live alert or store purchase.
- One-minute Cloudflare Cron adapter and three regression tests implemented;
  **not deployed**, pending authorized Cloudflare account. No daily Vercel cron
  or request background loop is claimed as monitoring.
- Local SQLite backend suite: 51 passed, two PostgreSQL-only tests skipped. Latest
  PostgreSQL/native/browser CI must be inspected before claiming new checks passed.

Controlled production API checks now passed on Vercel with dedicated Neon:
registration/login/invalid password, manual tick authorization, signed roles and
Guardian actions, check-in/ETA, escalation redaction, terminal resolution, server
history and logout revocation. No contacts/purchases were used. A manual tick is
not a live scheduler. The latest iOS basic lifecycle/Vault/Circle/Automate controls
compile in simulator CI; native parity and device/store verification still remain.

A regression additionally preserves omitted privacy, capsule, consent and optional
telemetry fields across client snapshots while retaining explicit-null clearing.
Native battery sync now uses a narrow PATCH. Owner capsule/contact values are
loaded into iOS editors rather than silently replaced by empty controls.
