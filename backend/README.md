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
uvicorn app.main:app --reload --port 8080
```

The API documentation is then available at `/docs`.

## Authentication

Owner endpoints use a hackathon bearer secret:

```http
Authorization: Bearer <SAFECIRCLE_API_SECRET>
```

This is deliberately simple for the demo. A production deployment should replace it with per-user authenticated identity, device/session revocation, and authorization checks.

Guardian access uses signed, expiring tokens. The database stores only the SHA-256 token hash.

## Privacy behavior

Guardian responses are redacted according to `privacy_mode`:

- `STATUS_ONLY` — never returns location
- `APPROXIMATE` — rounds coordinates
- `PRECISE_ON_ESCALATION` — exact coordinates only after the configured escalation state/threshold

Safety Capsule data is returned only after the 15-minute escalation stage in this reference policy.

## RevenueCat

Configure `REVENUECAT_WEBHOOK_SECRET`, then point RevenueCat webhooks at:

```text
POST /webhooks/revenuecat
Authorization: Bearer <REVENUECAT_WEBHOOK_SECRET>
```

The backend mirrors whether `safecircle_pro` is active for demo/server-side feature checks. Client entitlement truth still comes from RevenueCat CustomerInfo.

## Production hardening

Before real-world use:

- replace shared owner bearer token with real auth;
- move SQLite to managed PostgreSQL;
- encrypt sensitive server-side fields;
- run escalation in a durable job system;
- integrate real push/SMS provider;
- add delivery acknowledgement;
- rate-limit public and authenticated routes;
- rotate signing keys;
- add monitoring and alerting;
- complete privacy/legal/security reviews.
