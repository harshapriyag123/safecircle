# SafeCircle implementation gap audit

Reviewed against commit `834d91c` on 2026-10-01. This is a source-code audit, not a certification of the live deployment or store accounts.

## Fixed in this change

| Gap | Result |
| --- | --- |
| Any signed-in user could upload events to another owner's session | Validate ownership for every referenced session before writing a batch. Missing sessions return 404. |
| Offline retries inserted duplicate events | Transactional receipts deduplicate by account and client event ID; retries are acknowledged so devices can clear the queue. |
| Large batches were silently truncated | API validates the 500-event limit; Android flush retains excess events for the next sync. |
| Subscription endpoint exposed other users' billing status | Require account ownership. Explicit local demo-token behavior remains supported. |
| Cancellation/billing issues removed access before paid/grace periods ended | Keep access through the effective expiration, remove it on expiration/refund, support lifetime purchases, and ignore unrelated event types/entitlements. |
| Delayed or repeated billing events could overwrite newer subscription state | Persist event timestamp and last event ID; ignore older/repeated updates atomically. Read-time expiration also handles delayed expiration webhooks. |
| SMS escalation could not find the encrypted capsule contact | Decrypt active-session capsules before provider dispatch; remove raw database ciphertext from API records. |
| PATCH ignored explicit null values | Allow owners to clear optional sensitive fields while rejecting nulls for required session fields. |
| CONCERN released precise coordinates before escalation | Withhold precise coordinates until ESCALATED or the 15-minute server threshold. |
| Expired capsules remained visible to Guardians | Withhold capsules whose expiresAt has passed. This does not yet delete the database record. |
| Environment template contained literal backslash-n sequences | Restore separate configuration lines for auth and provider settings. |

## Remaining work, ordered by impact

| Priority | Gap and source evidence | Required follow-up |
| --- | --- | --- |
| P0 | Escalation delivery only happens on the first persisted stage event (`backend/app/main.py`). Provider failures and missing credentials are recorded but are not retried. | Durable outbox/jobs with retry, resolution cancellation, provider idempotency, and delivery callbacks. |
| P0 | Provider SMS selects only capsule.primaryContact regardless of stage (`backend/app/providers.py`). | Model verified Primary/Backup Guardian destinations and route each stage explicitly. Test real device delivery with consent and configured credentials. |
| P0 | SQLite defaults to /tmp; encryption/signing have development fallback secrets. | Configure persistent storage, independent encryption/signing secrets, backups, and disable shared demo access on the deployment. Verify Railway settings directly. |
| P1 | Late check-in resets the stored state but does not extend the overdue server escalation clock. Extended ETA uses the same per-session stage deduplication keys. | Define check-in grace and escalation generation/reset policy; test ETA extension and later missed check-ins. |
| P1 | Capsule expiry redaction is implemented, but server retention/deletion is not. | Scheduled purge and explicit schema for capsule fields, privacy redaction, and expiration. |
| P1 | Backend billing mirror is a single entitlement/product snapshot, not a complete subscriber history. Transfers, aliases, overlapping subscriptions, and equal-timestamp conflicting events need reconciliation. | Fetch authoritative RevenueCat subscriber state server-side when configured; retain event receipts/history and test these cases. |
| P1 | iOS README explicitly leaves RevenueCat iOS wiring for later; some web app surfaces are local simulations. | Complete store-specific billing and exercise native/API flows on devices. Label simulated web features clearly. |
| P1 | Auth has no refresh rotation, device revocation, or route rate limiting. | Add account/session controls and bounded authentication/public endpoint rate limits. |
| P1 | No Gradle wrapper is checked in; this environment also has no Gradle/Android SDK. | Generate and commit a wrapper from trusted Gradle tooling; run Android CI and real-device background/permission tests. |
| Launch | Public store listings, final pricing, beta usage, purchases, and campaign metrics are not established by the supplied evidence. | Supply real store/account evidence and measure adoption; do not describe simulated purchases as revenue. |

## Verification

Backend: `SAFECIRCLE_ALLOW_DEMO_TOKEN=true PYTHONPATH=. python -m pytest -q` from backend. 24 tests pass, including 16 new regression cases. Provider tests replace SMS delivery with a stub and send no real messages.

`git diff --check` passes. Android execution could not run locally: no gradlew, Gradle installation, or Android SDK is available. Existing Android CI runs unit tests and assembles debug with its installed Gradle.

The backend initializes new receipt storage and adds subscription event metadata to existing SQLite databases without deleting data. Deploy code and restart to apply this migration. These changes have not been applied to the live service.
