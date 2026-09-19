# SafeCircle Backend

FastAPI reference backend for the SafeCircle hackathon build.

## What it provides

- Safety Session synchronization
- owner-side check-in / resolve endpoints
- privacy-scoped Guardian session views
- signed, expiring Guardian access tokens
- server-side escalation stage evaluation
- idempotent escalation event storage
- offline Android event reconciliation endpoint
- RevenueCat webhook ingestion
- subscription mirror endpoint
- SQLite persistence for the demo deployment
- Docker deployment support

## Run locally

```bash
cd backend
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.entrypoint:app --reload --port 8080
```

## Run with Docker

Build from the **repository root** so the Guardian web portal can be packaged with the API:

```bash
docker build -f backend/Dockerfile -t safecircle-api .
docker run --rm -p 8080:8080 \
  -e SAFECIRCLE_API_SECRET=change-me \
  -e GUARDIAN_SIGNING_SECRET=change-me-too \
  -e REVENUECAT_WEBHOOK_SECRET=webhook-secret \
  -e SAFECIRCLE_PUBLIC_BASE_URL=http://localhost:8080 \
  safecircle-api
```

The Guardian web experience is then served from `/guardian/`.

The API documentation is then available at `/docs`.

## Authentication

Owner endpoints accept signed per-user access tokens issued by `/v1/auth/register` and `/v1/auth/login`. A shared hackathon bearer secret can be enabled explicitly for local demos with `SAFECIRCLE_ALLOW_DEMO_TOKEN=true`:

```http
Authorization: Bearer <SAFECIRCLE_API_SECRET>
```

Production hardening still requires refresh-token rotation, device/session revocation, rate limiting, and an external identity/security review.

Guardian access uses signed, expiring tokens. The database stores only the SHA-256 token hash.

## Privacy behavior

Guardian responses are redacted according to `privacy_mode`:

- `STATUS_ONLY` — never returns location
- `APPROXIMATE` — rounds coordinates
- `PRECISE_ON_ESCALATION` — exact coordinates only after the configured escalation state/threshold

Safety Capsule data is returned only after the 15-minute escalation stage in this reference policy.
After session resolution, Guardian responses always remove location and Safety Capsule data. Guardian Live polls only while visible, prevents overlapping requests, and stops permanently when the session resolves or the signed link expires or is revoked.

## RevenueCat

Configure `REVENUECAT_WEBHOOK_SECRET`, then point RevenueCat webhooks at:

```text
POST /webhooks/revenuecat
Authorization: Bearer <REVENUECAT_WEBHOOK_SECRET>
```

The backend mirrors whether `safecircle_pro` is active for demo/server-side feature checks. Client entitlement truth still comes from RevenueCat CustomerInfo.

## Production hardening

Before real-world use:

- add refresh-token rotation and device/session revocation to the signed user authentication flow;
- move SQLite to managed PostgreSQL;
- encrypt sensitive server-side fields;
- run escalation in a durable job system;
- integrate real push/SMS provider;
- connect production push/SMS delivery receipts to the Guardian timeline;
- rate-limit public and authenticated routes;
- rotate signing keys;
- add monitoring and alerting;
- complete privacy/legal/security reviews.
