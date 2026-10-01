# Deployment and delivery contract

The existing root Dockerfile runs `app.entrypoint:app` and serves API, landing site, web companion and Guardian routes. Deploy the reviewed `shipaton/completion` commit to the existing Railway service, then verify `/health`, root redirect, `/site/privacy.html`, signed Guardian flow and a restart preserving the database. The known origin is `https://safecircle-production-5a32.up.railway.app`; new-branch deployment is not yet verified.

## Required production configuration

Set `SAFECIRCLE_ENV=production`. Startup fails closed unless the following are configured:

- Independent random `SAFECIRCLE_API_SECRET`, `SAFECIRCLE_ACCESS_TOKEN_SECRET`, `GUARDIAN_SIGNING_SECRET`, and `REVENUECAT_WEBHOOK_SECRET`, each at least 32 characters.
- Independent `SAFECIRCLE_DATA_ENCRYPTION_KEY`, generated as a valid Fernet key. Preserve it across restarts; changing it without migration makes stored capsules/contacts unreadable.
- `SAFECIRCLE_DB_PATH=/data/safecircle.db` on an actual persistent Railway volume mounted at `/data`. Path validation cannot prove the volume is persistent: inspect the deployment, then restart and verify data remains.
- `SAFECIRCLE_PUBLIC_BASE_URL` set to the actual HTTPS origin; explicit CORS origins; `SAFECIRCLE_ALLOW_DEMO_TOKEN=false`.

A writable directory and a single service replica are required for this SQLite architecture. For multiple hosts/replicas, migrate to a shared transactional database and use cross-worker queue leases. Back up SQLite with its online backup API or stop the worker before copying; include encryption key recovery under separate access controls. Set and publish backup retention. Do not put secrets in GitHub, logs, screenshots or browser bundles.

## Notification adapters

For SMS set `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`. Guardians must consent and numbers must be E.164. The provider must permit the test destinations and use the appropriate messaging registration for the launch region. No real SMS was sent in the test suite.

For push set `SAFECIRCLE_PUSH_WEBHOOK_URL` (HTTPS), `SAFECIRCLE_PUSH_WEBHOOK_SECRET`, and independent `SAFECIRCLE_DELIVERY_RECEIPT_SECRET` (32+ characters). The adapter must map `owner_id` plus `guardian_role` to opted-in registered Guardian devices; SafeCircle does not yet include a first-party push registration service. It MUST deduplicate by `Idempotency-Key`/`delivery_id`, including after network timeout, and authenticate receipt calls.

Outbox statuses: `queued`, `sending`, `accepted`, `delivered`, `failed`, `cancelled`, `uncertain`. `accepted` only confirms an API response. SMS delivery receipts are validated using Twilio's signature and message SID. Push receipts use the independent bearer secret and the push job type. A lease lasts 60 seconds; missing providers wait without consuming attempts; rejected attempts retry exponentially, up to five attempts.

An ambiguous SMS timeout or interrupted worker changes the job to `uncertain`, not to delivered and not to a blind retry. Await its signed receipt and investigate provider logs. This favors avoiding duplicate messages over assuming a retry is harmless. Repeated scheduling does not repeat accepted/delivered jobs. New ETA cycles intentionally create new stage alerts. Resolution cannot recall already accepted messages. A provider response may race with resolution; this is not an exactly-once physical-delivery guarantee.

Consent removal cancels queued SMS jobs and is rechecked immediately before sending. Contacts changed before sending use the current consented destination. Capsule/contact content is absent from generic push/SMS messages. Expired capsules are cleared every worker iteration; completed/obsolete delivery payloads clear after 24 hours.

## Remaining operational safeguards

Production account deletion, refresh-token rotation, device token revocation and application-level distributed rate limiting are not implemented. The current access tokens expire and Guardian links can be revoked. Before public account onboarding, add operator/gateway rate limits and a private deletion/support process; do not mistake production configuration validation for a security certification. Device background/permission tests, actual signed receipt tests and store purchase tests remain required.
