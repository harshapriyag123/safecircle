import asyncio
import os
import time
import uuid
from contextlib import asynccontextmanager
from typing import Any
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from . import db
from .models import (
    ClientEventBatch,
    GuardianInviteCreate,
    RevenueCatWebhookEnvelope,
    SessionPatch,
    SessionUpsert,
    RegisterRequest,
    LoginRequest,
)
from .providers import deliver_escalation
from .security import (
    require_bearer,
    sign_guardian_token,
    hash_password,
    verify_password,
    sign_access_token,
    token_hash,
    verify_guardian_token,
    verify_revenuecat_webhook,
)

ESCALATION_STAGES = (0, 5, 10, 15)
PUBLIC_BASE_URL = os.getenv("SAFECIRCLE_PUBLIC_BASE_URL", "http://localhost:8080").rstrip("/")


def now_ms() -> int:
    return int(time.time() * 1000)


def auth_or_401(authorization: str | None) -> str:
    try:
        return require_bearer(authorization)
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc


def assert_owner(user_id: str, owner_id: str) -> None:
    if user_id != "demo-owner" and user_id != owner_id:
        raise HTTPException(status_code=403, detail="Owner authorization failed")


def guardian_token_or_401(token: str) -> dict[str, Any]:
    try:
        payload = verify_guardian_token(token)
    except (PermissionError, ValueError, KeyError) as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc

    invite = db.get_invite_by_hash(token_hash(token))
    if invite is None or invite["revoked"]:
        raise HTTPException(status_code=401, detail="Guardian invite is revoked or unknown")
    if invite["expires_at"] < now_ms():
        raise HTTPException(status_code=401, detail="Guardian invite expired")
    return payload


def stage_for_session(session: dict[str, Any], current_ms: int) -> int | None:
    if session["resolved"]:
        return None
    delta = current_ms - int(session["expected_end_at"])
    if delta < 0:
        return None
    overdue_minutes = delta // 60_000
    reached = [stage for stage in ESCALATION_STAGES if overdue_minutes >= stage]
    return max(reached) if reached else None


def public_snapshot(session: dict[str, Any], role: str) -> dict[str, Any]:
    stage = stage_for_session(session, now_ms())
    privacy = session.get("privacy_mode") or "PRECISE_ON_ESCALATION"

    # Guardian state must be derived from the canonical persisted session plus
    # the server-side escalation clock. Never show a resolved session as live.
    if bool(session["resolved"]):
        state = "RESOLVED"
    elif stage is not None and stage >= 15:
        state = "ESCALATED"
    elif stage is not None and stage >= 5 and session["state"] == "NORMAL":
        state = "CONCERN"
    else:
        state = session["state"]

    result: dict[str, Any] = {
        "session_id": session["id"],
        "mode": session["mode"],
        "destination": session.get("destination"),
        "expected_end_at": session["expected_end_at"],
        "last_check_in_at": session["last_check_in_at"],
        "state": state,
        "battery_percent": session.get("battery_percent"),
        "resolved": bool(session["resolved"]),
        "resolved_at": session.get("resolved_at"),
        "privacy_mode": privacy,
        "escalation_stage_minutes": stage,
        "role": role,
    }

    allow_details = not session["resolved"] and (state == "ESCALATED" or (stage is not None and stage >= 15))
    if not allow_details or not session.get("share_battery_on_escalation"):
        result["battery_percent"] = None
    if not allow_details or not session.get("share_destination_on_escalation"):
        result["destination"] = None

    lat = session.get("latitude")
    lon = session.get("longitude")
    can_reveal_precise = state == "ESCALATED" or (stage is not None and stage >= 15)

    if bool(session["resolved"]):
        result["location"] = None
    elif privacy == "APPROXIMATE" and lat is not None and lon is not None:
        result["location"] = {
            "latitude": round(float(lat), 2),
            "longitude": round(float(lon), 2),
            "precision": "approximate",
        }
    elif privacy == "PRECISE_ON_ESCALATION" and can_reveal_precise and lat is not None and lon is not None:
        result["location"] = {
            "latitude": lat,
            "longitude": lon,
            "accuracy_meters": session.get("location_accuracy"),
            "precision": "precise_on_escalation",
        }
    else:
        result["location"] = None

    capsule = session.get("capsule")
    capsule_expiry = capsule.get("expiresAt") if isinstance(capsule, dict) else None
    capsule_current = capsule_expiry is None or (
        isinstance(capsule_expiry, (int, float)) and capsule_expiry > now_ms()
    )
    if stage is not None and stage >= 15 and capsule_current:
        allowed = {"sessionId", "createdAt", "expiresAt", "destinationLabel", "batteryPercent", "destination", "battery", "instructions",
                   "guardianInstructions", "instruction"}
        import math
        numeric_fields = {'createdAt', 'expiresAt', 'battery', 'batteryPercent'}
        result['safety_capsule'] = {
            key: value for key, value in (capsule or {}).items() if key in allowed and (
                (key in numeric_fields and isinstance(value, (int, float)) and not isinstance(value, bool) and math.isfinite(value))
                or (key not in numeric_fields and isinstance(value, str) and len(value) <= 2048)
            )
        }
    else:
        result["safety_capsule"] = None

    if isinstance(result.get('safety_capsule'), dict):
        for field in ('battery', 'batteryPercent'):
            if not session.get('share_battery_on_escalation'):
                result['safety_capsule'].pop(field, None)
        for field in ('destination', 'destinationLabel'):
            if not session.get('share_destination_on_escalation'):
                result['safety_capsule'].pop(field, None)

    guardian_events = [
        event
        for event in db.list_events(session["id"], limit=50)
        if event["event_type"] in {"GUARDIAN_ACKNOWLEDGED", "GUARDIAN_CHECK_IN_REQUESTED"}
    ]
    result["guardian_acknowledged_at"] = next(
        (event["created_at"] for event in guardian_events if event["event_type"] == "GUARDIAN_ACKNOWLEDGED"),
        None,
    )
    result["guardian_check_in_requested_at"] = next(
        (event["created_at"] for event in guardian_events if event["event_type"] == "GUARDIAN_CHECK_IN_REQUESTED"),
        None,
    )

    return result


async def escalation_loop(stop: asyncio.Event) -> None:
    from .delivery import schedule_escalations, process_delivery
    import logging
    while not stop.is_set():
        try:
            current = now_ms()
            for session in db.active_sessions():
                schedule_escalations(session, current)
            for _ in range(50):
                if not await asyncio.to_thread(process_delivery, now_ms()):
                    break
            db.purge_expired_capsules(current)
            from .revenuecat import reconcile_pending
            await asyncio.to_thread(reconcile_pending, current)
        except Exception:
            # A failed provider or malformed record must not kill the scheduler.
            logging.getLogger(__name__).exception("Escalation worker iteration failed")
        try:
            await asyncio.wait_for(stop.wait(), timeout=15)
        except asyncio.TimeoutError:
            pass


@asynccontextmanager
async def lifespan(app: FastAPI):
    from .config import validate_production_config
    validate_production_config()
    db.init_db()
    stop = asyncio.Event()
    task = asyncio.create_task(escalation_loop(stop))
    try:
        yield
    finally:
        stop.set()
        await task


app = FastAPI(
    title="SafeCircle API",
    version="0.1.0",
    description="Hackathon backend for privacy-scoped Safety Sessions and Guardian views.",
    lifespan=lifespan,
)

allowed_origins = [
    value.strip()
    for value in os.getenv("SAFECIRCLE_ALLOWED_ORIGINS", "http://localhost:3000,http://localhost:8080").split(",")
    if value.strip()
]

app.add_middleware(
    CORSMiddleware,
    allow_origins=allowed_origins,
    allow_credentials=False,
    allow_methods=["GET", "POST", "PATCH", "DELETE"],
    allow_headers=["Authorization", "Content-Type"],
)

guardian_candidates = [
    Path(__file__).resolve().parent.parent / "web" / "guardian",
    Path(__file__).resolve().parent.parent.parent / "web" / "guardian",
]
guardian_static = next((path for path in guardian_candidates if path.exists()), None)
if guardian_static is not None:
    app.mount("/guardian", StaticFiles(directory=guardian_static, html=True), name="guardian")

webapp_candidates = [
    Path(__file__).resolve().parent.parent / "web" / "app",
    Path(__file__).resolve().parent.parent.parent / "web" / "app",
]
webapp_static = next((path for path in webapp_candidates if path.exists()), None)
if webapp_static is not None:
    app.mount("/app", StaticFiles(directory=webapp_static, html=True), name="webapp")


site_static = Path(__file__).resolve().parent.parent.parent / "web" / "site"
if not site_static.exists():
    site_static = Path(__file__).resolve().parent.parent / "web" / "site"
if site_static.exists():
    app.mount("/site", StaticFiles(directory=site_static, html=True), name="site")

@app.post("/v1/auth/register")
def register(body: RegisterRequest) -> dict[str, Any]:
    email = body.email.lower().strip()
    user_id = "sc_" + uuid.uuid4().hex
    try:
        password_hash = hash_password(body.password)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc)) from exc

    if not db.create_user(user_id, email, password_hash):
        raise HTTPException(status_code=409, detail="Account already exists")

    return {
        "user_id": user_id,
        "access_token": sign_access_token(user_id),
        "token_type": "bearer",
    }


@app.post("/v1/auth/login")
def login(body: LoginRequest) -> dict[str, Any]:
    user = db.get_user_by_email(body.email)
    if user is None or not verify_password(body.password, user["password_hash"]):
        raise HTTPException(status_code=401, detail="Invalid email or password")
    return {
        "user_id": user["id"],
        "access_token": sign_access_token(user["id"]),
        "token_type": "bearer",
    }


@app.get("/health")
def health() -> dict[str, Any]:
    return {
        "status": "ok",
        "service": "safecircle-api",
        "time_ms": now_ms(),
    }


@app.post("/v1/sessions")
def upsert_session(
    body: SessionUpsert,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    assert_owner(user_id, body.owner_id)
    existing = db.get_session(body.id)
    if existing is not None and existing["owner_id"] != body.owner_id:
        raise HTTPException(status_code=403, detail="Session owner mismatch")
    if existing is not None and bool(existing["resolved"]):
        if not body.resolved:
            raise HTTPException(status_code=409, detail="Resolved sessions cannot be reopened")
        return {
            "ok": True,
            "session_id": body.id,
            "already_resolved": True,
            "resolved_at": existing.get("resolved_at"),
        }

    data = body.model_dump()
    if existing is not None:
        # Offline snapshots cannot move an acknowledged deadline backwards.
        data["expected_end_at"] = max(data["expected_end_at"], existing["expected_end_at"])
        if body.last_check_in_at > existing["last_check_in_at"] and data["expected_end_at"] <= now_ms():
            data["expected_end_at"] = now_ms() + 5 * 60_000
        for flag in ("share_battery_on_escalation", "share_destination_on_escalation"):
            if flag not in body.model_fields_set:
                data[flag] = existing.get(flag, False)
        if "guardian_contacts" not in body.model_fields_set:
            data["guardian_contacts"] = existing.get("guardian_contacts", [])
    transitioned_to_resolved = body.resolved and not bool(existing and existing["resolved"])
    if body.resolved:
        data["state"] = "RESOLVED"
        data["resolved_at"] = body.resolved_at or now_ms()
    db.upsert_session(data)
    if transitioned_to_resolved:
        db.append_event(body.id, "SESSION_RESOLVED", {"source": "owner_sync"})
    else:
        db.append_event(body.id, "SESSION_UPSERTED", {"state": data["state"]})
    return {"ok": True, "session_id": body.id}


@app.get("/v1/sessions/{session_id}")
def owner_get_session(
    session_id: str,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    session = db.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")
    assert_owner(user_id, session["owner_id"])
    return session


@app.patch("/v1/sessions/{session_id}")
def patch_session(
    session_id: str,
    body: SessionPatch,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    current = db.get_session(session_id)
    if current is None:
        raise HTTPException(status_code=404, detail="Session not found")
    assert_owner(user_id, current["owner_id"])
    if bool(current["resolved"]):
        raise HTTPException(status_code=409, detail="Resolved sessions are read-only")

    updates = body.model_dump(exclude_unset=True)
    required_fields = {"expected_end_at", "last_check_in_at", "state", "privacy_mode", "resolved"}
    if any(updates.get(field) is None for field in required_fields.intersection(updates)):
        raise HTTPException(status_code=422, detail="Required session fields cannot be cleared")
    resolving = updates.get("resolved") is True
    if resolving:
        updates["state"] = "RESOLVED"
        updates["resolved_at"] = updates.get("resolved_at") or now_ms()
    current.update(updates)
    db.upsert_session(current)
    if resolving:
        db.append_event(session_id, "SESSION_RESOLVED", {"source": "owner_patch"})
    else:
        db.append_event(session_id, "SESSION_UPDATED", {"fields": sorted(updates.keys())})
    return {"ok": True, "session": db.get_session(session_id)}


@app.post("/v1/sessions/{session_id}/check-in")
def check_in(
    session_id: str,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    session = db.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")
    assert_owner(user_id, session["owner_id"])
    if bool(session["resolved"]):
        raise HTTPException(status_code=409, detail="Resolved sessions cannot be checked in")

    session["last_check_in_at"] = now_ms()
    session["expected_end_at"] = max(session["expected_end_at"], now_ms() + 5 * 60_000)
    session["state"] = "NORMAL"
    db.upsert_session(session)
    db.append_event(session_id, "CHECK_IN", {"source": "owner"})
    return {"ok": True, "session": db.get_session(session_id)}


@app.post("/v1/sessions/{session_id}/resolve")
def resolve_session(
    session_id: str,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    session = db.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")
    assert_owner(user_id, session["owner_id"])

    if bool(session["resolved"]):
        return {"ok": True, "resolved_at": session.get("resolved_at")}

    session["resolved"] = True
    session["resolved_at"] = now_ms()
    session["state"] = "RESOLVED"
    db.upsert_session(session)
    db.append_event(session_id, "SESSION_RESOLVED", {"source": "owner"})
    return {"ok": True, "resolved_at": session["resolved_at"]}


@app.get("/v1/sessions/{session_id}/events")
def events(
    session_id: str,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    session = db.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")
    assert_owner(user_id, session["owner_id"])
    return {"events": db.list_events(session_id)}


@app.post("/v1/events/batch")
def ingest_events(
    body: ClientEventBatch,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    # Validate the entire batch before writing anything or acknowledging it.
    for event in body.events:
        if event.session_id is not None:
            session = db.get_session(event.session_id)
            if session is None:
                raise HTTPException(status_code=404, detail="Session not found")
            assert_owner(user_id, session["owner_id"])

    acknowledged: list[str] = []
    for event in body.events:
        db.append_client_event(user_id, event.model_dump())
        acknowledged.append(event.id)

    return {"acknowledged_event_ids": acknowledged}


@app.post("/v1/guardian-invites")
def create_guardian_invite(
    body: GuardianInviteCreate,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    session = db.get_session(body.session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")
    if session["owner_id"] != body.owner_id:
        raise HTTPException(status_code=403, detail="Owner mismatch")
    assert_owner(user_id, body.owner_id)
    if bool(session["resolved"]):
        raise HTTPException(status_code=409, detail="Resolved sessions cannot be shared")

    invite_id = str(uuid.uuid4())
    expires_at = now_ms() + body.ttl_minutes * 60_000
    token = sign_guardian_token(
        {
            "invite_id": invite_id,
            "session_id": body.session_id,
            "role": body.role,
            "exp": expires_at,
        }
    )
    db.save_invite(
        invite_id,
        body.session_id,
        body.owner_id,
        body.role,
        token_hash(token),
        expires_at,
    )

    return {
        "invite_id": invite_id,
        "guardian_token": token,
        "expires_at": expires_at,
        "guardian_url": PUBLIC_BASE_URL + "/guardian/?token=" + token,
    }


@app.delete("/v1/guardian-invites/{invite_id}")
def revoke_guardian_invite(
    invite_id: str,
    owner_id: str,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    assert_owner(user_id, owner_id)
    if not db.revoke_invite(invite_id, owner_id):
        raise HTTPException(status_code=404, detail="Invite not found")
    return {"ok": True}


@app.get("/v1/public/guardian/{token}")
def guardian_session(token: str) -> dict[str, Any]:
    payload = guardian_token_or_401(token)
    session = db.get_session(payload["session_id"])
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")
    return public_snapshot(session, payload.get("role", "guardian"))


@app.post("/webhooks/revenuecat")
def revenuecat_webhook(
    body: RevenueCatWebhookEnvelope,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    try:
        verify_revenuecat_webhook(authorization)
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc

    event = body.event
    event_type = str(event.get("type", "")).upper()
    app_user_id = str(event.get("app_user_id", ""))
    entitlement_ids = event.get("entitlement_ids") or []
    product_id = event.get("product_id")
    expiration_at_ms = event.get("expiration_at_ms")

    affected = {app_user_id} if app_user_id else set()
    for field in ('aliases', 'transferred_from', 'transferred_to'):
        values = event.get(field, [])
        if isinstance(values, list):
            affected.update(value for value in values if isinstance(value, str) and value)
    if not affected:
        if event_type == 'TEST':
            return {'ok': True, 'ignored': True}
        raise HTTPException(status_code=400, detail="RevenueCat event is missing customer identifiers")
    from .revenuecat import enqueue_reconciliation
    for customer in affected:
        enqueue_reconciliation(customer, now_ms())
    if not app_user_id:
        return {'ok': True, 'reconciliation_queued': True, 'event_type': event_type}

    if os.getenv("SAFECIRCLE_ENV") == "production" and event.get("environment") == "SANDBOX":
        return {"ok": True, "ignored": True, "reason": "sandbox_event"}

    supported_types = {
        "INITIAL_PURCHASE", "RENEWAL", "NON_RENEWING_PURCHASE", "UNCANCELLATION",
        "CANCELLATION", "BILLING_ISSUE", "EXPIRATION", "SUBSCRIPTION_EXTENDED",
        "SUBSCRIPTION_PAUSED", "REFUND_REVERSED", "TEMPORARY_ENTITLEMENT_GRANT",
    }
    if event_type not in supported_types or "safecircle_pro" not in entitlement_ids:
        return {"ok": True, "ignored": True, "event_type": event_type}

    # Cancellation of renewal does not end the current paid period. Billing
    # grace extends access; expiration and refunds remove it.
    grace_expiration = event.get("grace_period_expiration_at_ms")
    try:
        deadlines = [int(value) for value in (expiration_at_ms, grace_expiration) if value is not None]
    except (TypeError, ValueError) as exc:
        raise HTTPException(status_code=400, detail="Invalid entitlement expiration") from exc
    expiration_at_ms = max(deadlines) if deadlines else None
    refunded = event_type == "CANCELLATION" and event.get("cancel_reason") == "CUSTOMER_SUPPORT"
    active = (
        event_type != "EXPIRATION"
        and not refunded
        and (expiration_at_ms is None or expiration_at_ms > now_ms())
    )
    try:
        event_timestamp_ms = int(event.get("event_timestamp_ms") or now_ms())
    except (TypeError, ValueError) as exc:
        raise HTTPException(status_code=400, detail="Invalid event timestamp") from exc
    db.upsert_subscription(
        app_user_id,
        "safecircle_pro",
        active,
        product_id,
        expiration_at_ms,
        event_timestamp_ms=event_timestamp_ms,
        event_id=str(event["id"]) if event.get("id") else None,
    )
    return {"ok": True, "active": db.get_subscription(app_user_id)["is_active"], "event_type": event_type}


@app.get("/v1/subscriptions/{app_user_id}")
def subscription_status(
    app_user_id: str,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    assert_owner(user_id, app_user_id)
    status = db.get_subscription(app_user_id)
    if status is None:
        status = {
            "app_user_id": app_user_id,
            "entitlement_id": "safecircle_pro",
            "is_active": False,
            "product_id": None,
            "expiration_at_ms": None,
        }
    expiration = status.get("expiration_at_ms")
    if expiration is not None and expiration <= now_ms():
        status["is_active"] = False
    return {"subscription": status}


@app.get("/v1/sessions/{session_id}/deliveries")
def owner_deliveries(session_id: str, authorization: str | None = Header(default=None)) -> dict[str, Any]:
    user = auth_or_401(authorization)
    session = db.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")
    assert_owner(user, session['owner_id'])
    return {"deliveries": db.delivery_status(session_id)}


@app.post("/v1/delivery-receipts/push/{job_id}")
async def push_delivery_receipt(job_id: str, request: Request,
                                authorization: str | None = Header(default=None)) -> dict[str, Any]:
    import hmac
    secret = os.getenv('SAFECIRCLE_DELIVERY_RECEIPT_SECRET', '')
    if not secret or not hmac.compare_digest(authorization or '', 'Bearer ' + secret):
        raise HTTPException(status_code=401, detail='Invalid delivery receipt authentication')
    body = await request.json()
    if body.get('status') not in {'delivered', 'failed'}:
        raise HTTPException(status_code=422, detail='Expected delivered or failed status')
    job = db.get_delivery(job_id)
    if job is None or job['channel'] != 'push_webhook':
        raise HTTPException(status_code=404, detail='Delivery not found')
    db.mark_delivery_receipt(job_id, body['status'] == 'delivered', now_ms())
    return {'ok': True}


@app.post("/v1/delivery-receipts/twilio/{job_id}")
async def twilio_delivery_receipt(job_id: str, request: Request) -> dict[str, Any]:
    import base64
    import hashlib
    import hmac
    from urllib.parse import parse_qs
    token = os.getenv('TWILIO_AUTH_TOKEN', '')
    base = os.getenv('SAFECIRCLE_PUBLIC_BASE_URL', '').rstrip('/')
    fields = parse_qs((await request.body()).decode(), keep_blank_values=True)
    canonical = base + '/v1/delivery-receipts/twilio/' + job_id
    canonical += ''.join(key + value for key in sorted(fields) for value in sorted(set(fields[key])))
    signature = base64.b64encode(hmac.new(token.encode(), canonical.encode(), hashlib.sha1).digest()).decode()
    if not token or not base or not hmac.compare_digest(signature, request.headers.get('X-Twilio-Signature', '')):
        raise HTTPException(status_code=401, detail='Invalid Twilio callback signature')
    job = db.get_delivery(job_id)
    if job is None or job['channel'] != 'twilio_sms':
        raise HTTPException(status_code=404, detail='Delivery not found')
    sid = fields.get('MessageSid', [''])[0]
    if job['provider_message_id'] and sid != job['provider_message_id']:
        raise HTTPException(status_code=409, detail='Provider message mismatch')
    status = fields.get('MessageStatus', [''])[0]
    if status in {'delivered', 'failed', 'undelivered'}:
        db.mark_delivery_receipt(job_id, status == 'delivered', now_ms())
    return {'ok': True}


@app.post('/v1/subscriptions/{app_user_id}/reconcile')
def request_subscription_reconciliation(app_user_id: str, authorization: str | None = Header(default=None)):
    assert_owner(auth_or_401(authorization), app_user_id)
    from .revenuecat import enqueue_reconciliation
    enqueue_reconciliation(app_user_id, now_ms())
    return {'queued': True}


@app.post('/v1/auth/logout')
def logout_account(authorization: str | None = Header(default=None)):
    auth_or_401(authorization)
    from .security import verify_access_token
    from .account_controls import revoke
    token = authorization.removeprefix('Bearer ').strip()
    revoke(token, verify_access_token(token)['exp'])
    return {'ok': True}


@app.delete('/v1/account')
def delete_owner_account(body: LoginRequest, authorization: str | None = Header(default=None)):
    user_id = auth_or_401(authorization)
    user = db.get_user(user_id)
    if not user or user['email'] != body.email.strip().lower() or not verify_password(body.password, user['password_hash']):
        raise HTTPException(status_code=401, detail='Reauthentication required')
    from .account_controls import delete_account
    delete_account(user_id)
    return {'deleted': True, 'subscription_notice': 'Deleting SafeCircle data does not cancel a store subscription. Manage it in the store or Customer Center.'}
