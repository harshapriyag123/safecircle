import asyncio
import os
import time
import uuid
from contextlib import asynccontextmanager
from typing import Any
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException
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
        "privacy_mode": privacy,
        "escalation_stage_minutes": stage,
        "role": role,
    }

    lat = session.get("latitude")
    lon = session.get("longitude")
    can_reveal_precise = state in {"CONCERN", "ESCALATED"} or (stage is not None and stage >= 15)

    if privacy == "APPROXIMATE" and lat is not None and lon is not None:
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

    if stage is not None and stage >= 15:
        result["safety_capsule"] = session.get("capsule")
    else:
        result["safety_capsule"] = None

    return result


async def escalation_loop(stop: asyncio.Event) -> None:
    while not stop.is_set():
        current = now_ms()
        for session in db.active_sessions():
            stage = stage_for_session(session, current)
            if stage is None:
                continue

            created = db.append_event(
                session["id"],
                "ESCALATION_STAGE",
                {
                    "state": session["state"],
                    "stage_minutes": stage,
                    "expected_end_at": session["expected_end_at"],
                },
                stage_minutes=stage,
            )
            if created and stage >= 5:
                results = await asyncio.to_thread(deliver_escalation, session, stage)
                if not results:
                    db.append_event(
                        session["id"],
                        "GUARDIAN_DELIVERY_PENDING",
                        {"stage_minutes": stage, "channel": "provider_not_configured"},
                        stage_minutes=stage,
                    )
                for index, result in enumerate(results):
                    db.append_event(
                        session["id"],
                        "GUARDIAN_DELIVERY_" + result.channel.upper() + "_" + str(index),
                        {
                            "stage_minutes": stage,
                            "accepted": result.accepted,
                            "provider_message_id": result.provider_message_id,
                            "error": result.error,
                        },
                    )

        try:
            await asyncio.wait_for(stop.wait(), timeout=15)
        except asyncio.TimeoutError:
            pass


@asynccontextmanager
async def lifespan(app: FastAPI):
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
    db.upsert_session(body.model_dump())
    db.append_event(body.id, "SESSION_UPSERTED", {"state": body.state})
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

    updates = body.model_dump(exclude_none=True)
    current.update(updates)
    db.upsert_session(current)
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

    session["resolved"] = True
    session["resolved_at"] = now_ms()
    session["state"] = "RESOLVED"
    db.upsert_session(session)
    db.append_event(session_id, "SESSION_RESOLVED", {"source": "owner"})
    return {"ok": True}


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
    auth_or_401(authorization)
    acknowledged: list[str] = []

    for event in body.events[:500]:
        db.append_event(
            event.session_id,
            "CLIENT_" + event.event_type,
            {
                "client_event_id": event.id,
                "payload": event.payload,
                "client_created_at": event.created_at,
            },
        )
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

    if not app_user_id:
        raise HTTPException(status_code=400, detail="RevenueCat event is missing app_user_id")

    active = "safecircle_pro" in entitlement_ids and event_type not in {
        "EXPIRATION",
        "CANCELLATION",
        "BILLING_ISSUE",
    }
    db.upsert_subscription(
        app_user_id,
        "safecircle_pro",
        active,
        product_id,
        expiration_at_ms,
    )
    return {"ok": True, "active": active, "event_type": event_type}


@app.get("/v1/subscriptions/{app_user_id}")
def subscription_status(
    app_user_id: str,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    auth_or_401(authorization)
    status = db.get_subscription(app_user_id)
    if status is None:
        status = {
            "app_user_id": app_user_id,
            "entitlement_id": "safecircle_pro",
            "is_active": False,
            "product_id": None,
            "expiration_at_ms": None,
        }
    return {"subscription": status}
