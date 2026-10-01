# Full SafeCircle deployment on Vercel

Target project: `harsha-hacks/safecircle-site`, Hobby plan.
Public website: https://safecircle-site.vercel.app/site

Use the repository root and `shipaton/completion` branch, with the FastAPI preset.
The root `app.py` exports the Vercel application; root `requirements.txt` installs
backend dependencies. Existing native Android/iOS code remains in the repository;
native apps are installed on devices and cannot execute as a hosted webpage.

## Persistent storage

Configure a **dedicated** PostgreSQL database with the server-only `DATABASE_URL`
(pooled URL recommended, TLS required via `sslmode=require` or stronger). Never
put it in frontend code or a public variable. The application retains SQLite for
local/self-hosted deployments, but rejects SQLite on Vercel. PostgreSQL timestamps
use BIGINT. Short storage transactions take a database advisory lock to serialize
claims/deletion/deduplication across instances. This favors correctness over high
throughput; load/performance evaluation remains required before broader use.

An existing SQLite database is **not** automatically copied into PostgreSQL.
Use a fresh dedicated database or review and back up a migration separately.

## Required server configuration

- `SAFECIRCLE_ENV=production`
- `SAFECIRCLE_PUBLIC_BASE_URL=https://safecircle-site.vercel.app`
- `SAFECIRCLE_ALLOWED_ORIGINS=https://safecircle-site.vercel.app`
- `SAFECIRCLE_ALLOW_DEMO_TOKEN=false`
- `SAFECIRCLE_WORKER_MODE=external`
- Independent random secrets of at least 32 characters: `SAFECIRCLE_API_SECRET`,
  `SAFECIRCLE_ACCESS_TOKEN_SECRET`, `GUARDIAN_SIGNING_SECRET`,
  `REVENUECAT_WEBHOOK_SECRET`, `SAFECIRCLE_WORKER_SECRET`.
- Valid random Fernet `SAFECIRCLE_DATA_ENCRYPTION_KEY`.
- RevenueCat administrative project metadata: `REVENUECAT_PROJECT_ID=proj5f7132ef`.
  This is not an SDK key. Supply actual public SDK keys in native build
  configuration and server-only reconciliation credentials separately.

Missing configuration keeps static website/client pages available while all
backend routes return 503. `/health` reports separate backend/worker readiness
without exposing configuration values. New sessions cannot start if the worker
has not completed a tick within 90 seconds. The status banner reports that state.

## Alert execution

An endless background task inside a Vercel request is **not** a durable scheduler.
The Vercel entrypoint disables that loop. `/internal/worker/tick` requires the
independent worker bearer secret and processes persisted jobs, reconciliation and
expiry cleanup. A user-controlled always-on host can run:

```sh
python backend/run_external_worker.py
```

Set `SAFECIRCLE_PUBLIC_BASE_URL` and `SAFECIRCLE_WORKER_SECRET` in its private
process environment. Run under a process supervisor. The runner calls the server
every 15 seconds, retries with bounded backoff, and omits credentials from logs.
No always-on worker has yet been provisioned. Free Vercel daily cron is insufficient
for 5/10/15-minute alerts. A native durable workflow alternative requires a tested
scheduler integration; it is not claimed as implemented here.

If the worker stops, existing jobs remain stored but execution is delayed. Readiness
and session creation safeguards do not themselves deliver an alert. Providers,
receipt callbacks and consenting test contacts remain required for live delivery.

## Verification

The backend CI matrix runs the full suite against SQLite and PostgreSQL 17 in an
isolated disposable schema per test. The PostgreSQL test also disables the Python
lock and races eight independent delivery claims; only one may claim a job.

Deployment must additionally verify actual registration/login, session creation,
signed Primary/Backup links, privacy/check-in/ETA/resolution, persistence across
redeployment, current worker heartbeat, and configured provider receipts. Missing
credentials or a green build are not evidence that those live flows passed.
