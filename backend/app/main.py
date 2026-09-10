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
)
from .security import (
    require_bearer,
    sign_guardian_token,
    token_hash,
    verify_guardian_token,
    verify_revenuecat_webhook,
)

ESCALATION_STAGES = (0, 5, 10, 15)
PUBLIC_BASE_URL = os.getenv("SAFECIRCLE_PUBLIC_BASE_URL", "http://localhost:8080").rstrip("/")


def now_ms() -> int:
    return int(time.time() * 1000)


def auth_or_401(authorization: str | None) -> None:
    try:
        require_bearer(authorization)
    except PermissionError as exc:
        raise HTTPException(status_code=401, detail=str(exc)) from exc


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
    state = session["state"]
    stage = stage_for_session(session, now_ms())
    privacy = session.get("privacy_mode") or "PRECISE_ON_ESCALATION"

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
                # Production deployments attach a push/SMS worker to these queued events.
                db.append_event(
                    session["id"],
                    "GUARDIAN_DELIVERY_PENDING",
                    {
                        "stage_minutes": stage,
                        "channel": "provider_adapter_required",
                    },
                    stage_minutes=stage,
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
    auth_or_401(authorization)
    db.upsert_session(body.model_dump())
    db.append_event(body.id, "SESSION_UPSERTED", {"state": body.state})
    return {"ok": True, "session_id": body.id}


@app.get("/v1/sessions/{session_id}")
def owner_get_session(
    session_id: str,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    auth_or_401(authorization)
    session = db.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")
    return session


@app.patch("/v1/sessions/{session_id}")
def patch_session(
    session_id: str,
    body: SessionPatch,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    auth_or_401(authorization)
    current = db.get_session(session_id)
    if current is None:
        raise HTTPException(status_code=404, detail="Session not found")

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
    auth_or_401(authorization)
    session = db.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")

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
    auth_or_401(authorization)
    session = db.get_session(session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")

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
    auth_or_401(authorization)
    return {"events": db.list_events(session_id)}


@app.post("/v1/events/batch")
def ingest_events(
    body: ClientEventBatch,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    auth_or_401(authorization)
    acknowledged: list[str] = []

    for event in body.events[:500]:
        created = db.append_event(
            event.session_id,
            "CLIENT_" + event.event_type,
            {
                "client_event_id": event.id,
                "payload": event.payload,
                "client_created_at": event.created_at,
            },
        )
        if created:
            acknowledged.append(event.id)
        else:
            # Duplicate server inserts are still considered acknowledged.
            acknowledged.append(event.id)

    return {"acknowledged_event_ids": acknowledged}


@app.post("/v1/guardian-invites")
def create_guardian_invite(
    body: GuardianInviteCreate,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    auth_or_401(authorization)
    session = db.get_session(body.session_id)
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")
    if session["owner_id"] != body.owner_id:
        raise HTTPException(status_code=403, detail="Owner mismatch")

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
    auth_or_401(authorization)
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
        raise HTTPException(status_code=400, detail="RevenueCat event missing app_user_id")

    active_types = {
        "INITIAL_PURCHASE",
        "RENEWAL",
        "UNCANCELLATION",
        "PRODUCT_CHANGE",
        "NON_RENEWING_PURCHASE",
    }
    inactive_types = {"EXPIRATION"}

    current_active = event_type in active_types
    if event_type not in active_types | inactive_types:
        current_active = bool(expiration_at_ms and int(expiration_at_ms) > now_ms())

    entitlement = "safecircle_pro" if "safecircle_pro" in entitlement_ids else (
        entitlement_ids[0] if entitlement_ids else "safecircle_pro"
    )

    db.upsert_subscription(
        app_user_id=app_user_id,
        entitlement_id=entitlement,
        is_active=current_active,
        product_id=product_id,
        expiration_at_ms=expiration_at_ms,
    )
    db.append_event(
        None,
        "REVENUECAT_" + event_type,
        {
            "app_user_id": app_user_id,
            "entitlement": entitlement,
            "active": current_active,
        },
    )
    return {"ok": True}


@app.get("/v1/subscriptions/{app_user_id}")
def subscription_status(
    app_user_id: str,
    authorization: str | None = Header(default=None),
) -> dict[str, Any]:
    auth_or_401(authorization)
    return {
        "app_user_id": app_user_id,
        "subscription": db.get_subscription(app_user_id),
    }
