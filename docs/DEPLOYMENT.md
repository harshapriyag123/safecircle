# Deployment and delivery contract

The existing Vercel Hobby project serves the website, web companion, Guardian routes and FastAPI backend at https://safecircle-site.vercel.app/site/. It uses the dedicated Neon Free PostgreSQL database. Reuse this project/domain; do not deploy to Railway. See [Vercel setup](VERCEL_DEPLOYMENT.md) and [free scheduler adapter](../scheduler/cloudflare/README.md). Continuous monitoring remains blocked until actual platform-scheduled ticks are verified.

## Required production configuration

Set `SAFECIRCLE_ENV=production`. Startup fails closed unless the following are configured:

- Independent random `SAFECIRCLE_API_SECRET`, `SAFECIRCLE_ACCESS_TOKEN_SECRET`, `GUARDIAN_SIGNING_SECRET`, and `REVENUECAT_WEBHOOK_SECRET`, each at least 32 characters.
- Independent `SAFECIRCLE_DATA_ENCRYPTION_KEY`, generated as a valid Fernet key. Preserve it across restarts; changing it without migration makes stored capsules/contacts unreadable.
- `DATABASE_URL` privately configured for the existing Neon PostgreSQL database with TLS (`sslmode=require` or stronger). Vercel ephemeral SQLite is unsupported.
- `SAFECIRCLE_PUBLIC_BASE_URL` set to the actual HTTPS origin; explicit CORS origins; `SAFECIRCLE_ALLOW_DEMO_TOKEN=false`.

PostgreSQL transactions and queue leases coordinate multiple function invocations. Retain the encryption key across deployments and manage database backup retention/recovery separately. A non-Vercel single-host SQLite deployment needs an actual persistent volume and online backup procedure; it is not the current deployment. Never put secrets in GitHub, logs, screenshots or browser bundles.

## Notification adapters

For SMS set `TWILIO_ACCOUNT_SID`, `TWILIO_AUTH_TOKEN`, `TWILIO_FROM_NUMBER`. Guardians must consent and numbers must be E.164. The provider must permit the test destinations and use the appropriate messaging registration for the launch region. No real SMS was sent in the test suite.

For push set `SAFECIRCLE_PUSH_WEBHOOK_URL` (HTTPS, no embedded credentials/query/fragment), `SAFECIRCLE_PUSH_WEBHOOK_SECRET` (32+ characters), and independent `SAFECIRCLE_DELIVERY_RECEIPT_SECRET` (32+ characters). The adapter must map `owner_id` plus `guardian_role` to opted-in registered Guardian devices; SafeCircle does not yet include a first-party push registration service. It MUST deduplicate by `Idempotency-Key`/`delivery_id`, including after network timeout, and authenticate receipt calls.

The backend refuses redirects for authenticated push requests. Successful 2xx responses mean accepted only; an optional JSON `message_id` (ASCII letters/digits and `._:-`, at most 120 characters) is retained. Raw response bodies and exception text are not persisted. Empty or non-JSON successful responses still require an authenticated delivery receipt. Adapter and receipt secrets must be independent from one another and application secrets.

Outbox statuses: `queued`, `sending`, `accepted`, `delivered`, `failed`, `cancelled`, `uncertain`. `accepted` only confirms an API response. SMS delivery receipts are validated using Twilio's signature and message SID. Push receipts use the independent bearer secret and the push job type. A lease lasts 60 seconds; missing providers wait without consuming attempts; rejected attempts retry exponentially, up to five attempts.

An ambiguous SMS timeout or interrupted worker changes the job to `uncertain`, not to delivered and not to a blind retry. Await its signed receipt and investigate provider logs. This favors avoiding duplicate messages over assuming a retry is harmless. Repeated scheduling does not repeat accepted/delivered jobs. New ETA cycles intentionally create new stage alerts. Resolution cannot recall already accepted messages. A provider response may race with resolution; this is not an exactly-once physical-delivery guarantee.

Consent removal cancels queued SMS jobs and is rechecked immediately before sending. Contacts changed before sending use the current consented destination. Capsule/contact content is absent from generic push/SMS messages. Expired capsules are cleared every worker iteration; completed/obsolete delivery payloads clear after 24 hours.

## Remaining operational safeguards

Password-confirmed account deletion removes owned sessions, jobs, invites, events and mirror data, and tombstones the random account identifier to revoke all old tokens. Logout revokes the current token; access tokens have unique IDs. Persisted counters bound authentication to 10 and Guardian API requests to 120 per source address per minute. Only trusted reverse-proxy settings may establish source addresses; verify shared-proxy behavior in Vercel. Refresh-token rotation and distributed multi-host rate limiting remain future hardening. Guardian links can be revoked. Before public account onboarding, verify gateway rate limits and establish a private provider/backup deletion support process; do not mistake production configuration validation for a security certification. Device background/permission tests, actual signed receipt tests and store purchase tests remain required.

Android cloud backup is disabled to keep account tokens, Guardian contacts and local session data out of automatic device backups.
