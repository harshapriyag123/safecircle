from __future__ import annotations

import re
from typing import Any
from urllib.parse import urlparse

from fastapi import APIRouter, Header, HTTPException
from pydantic import BaseModel, Field

from . import db
from .main import auth_or_401, guardian_token_or_401, public_snapshot

router = APIRouter()


class MessageScanRequest(BaseModel):
    text: str = Field(min_length=1, max_length=12000)


def _scan_message(text: str) -> dict[str, Any]:
    lower = text.lower()
    signals: list[dict[str, Any]] = []

    def add(condition: bool, signal: str, weight: int, explanation: str) -> None:
        if condition:
            signals.append({"signal": signal, "weight": weight, "explanation": explanation})

    add(any(x in lower for x in ("urgent", "immediately", "act now", "final warning", "within 24 hours")),
        "urgency_pressure", 18, "The message pressures you to act quickly.")
    add(any(x in lower for x in ("gift card", "bitcoin", "crypto", "wire transfer", "zelle", "cash app", "western union")),
        "unusual_payment", 30, "The message asks for an unusual or hard-to-reverse payment method.")
    add(any(x in lower for x in ("otp", "one-time password", "verification code", "password", "pin number", "security code")),
        "credential_request", 24, "The message asks for a password, PIN, OTP, or verification code.")
    add(any(x in lower for x in ("account suspended", "account locked", "verify your account", "confirm your identity", "unauthorized transaction")),
        "account_threat", 18, "The message uses account-threat or impersonation language.")
    add(any(x in lower for x in ("you won", "prize", "lottery", "refund waiting", "claim reward")),
        "unexpected_reward", 18, "The message promises an unexpected reward, refund, or prize.")
    add(any(x in lower for x in ("do not tell", "keep this confidential", "secret transaction")),
        "secrecy_request", 20, "The sender asks you to keep the request secret.")

    urls = re.findall(r"https?://[^\s]+", text, flags=re.I)
    if urls:
        signals.append({"signal": "contains_link", "weight": 8, "explanation": "The message contains a clickable link."})
    shorteners = {"bit.ly", "tinyurl.com", "t.co", "goo.gl", "rb.gy", "cutt.ly"}
    for raw_url in urls:
        host = (urlparse(raw_url.rstrip(".,)]}" )).hostname or "").lower()
        if host in shorteners:
            signals.append({"signal": "shortened_link", "weight": 20, "explanation": f"The message uses a shortened link ({host})."})
        if "xn--" in host:
            signals.append({"signal": "punycode_domain", "weight": 15, "explanation": "The link uses an internationalized/punycode domain."})
        if host.count("-") >= 3:
            signals.append({"signal": "complex_domain", "weight": 8, "explanation": "The link domain is unusually complex."})

    if text.count("!") >= 3:
        signals.append({"signal": "excessive_punctuation", "weight": 6, "explanation": "Excessive punctuation can be a pressure tactic."})

    # Keep one instance of each signal while preserving order.
    deduped: list[dict[str, Any]] = []
    seen: set[str] = set()
    for item in signals:
        if item["signal"] not in seen:
            seen.add(item["signal"])
            deduped.append(item)

    score = max(0, min(100, sum(int(item["weight"]) for item in deduped)))
    level = "high" if score >= 60 else "medium" if score >= 30 else "low"
    action = (
        "Do not click links, send money, or share codes. Verify through an official channel."
        if level == "high"
        else "Verify the sender and request through a separate trusted channel."
        if level == "medium"
        else "No common scam signals were detected, but this does not prove the message is genuine."
    )
    return {
        "risk_level": level,
        "risk_score": score,
        "signals": deduped,
        "recommended_action": action,
        "disclaimer": "SafeCircle flags common scam indicators. It cannot authenticate a sender or guarantee that a message is genuine.",
    }


@router.post("/v1/message-scan")
def message_scan(body: MessageScanRequest) -> dict[str, Any]:
    return _scan_message(body.text)


@router.get("/v1/auth/me")
def auth_me(authorization: str | None = Header(default=None)) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    user = db.get_user(user_id)
    if user is None:
        raise HTTPException(status_code=404, detail="Account not found")
    return {"user_id": user["id"], "email": user["email"]}


@router.get("/v1/me/active-session")
def my_active_session(authorization: str | None = Header(default=None)) -> dict[str, Any]:
    user_id = auth_or_401(authorization)
    with db.connect() as conn:
        row = conn.execute(
            "SELECT id FROM sessions WHERE owner_id=? AND resolved=0 ORDER BY updated_at DESC LIMIT 1",
            (user_id,),
        ).fetchone()
    if row is None:
        return {"session": None}
    session = db.get_session(str(row["id"]))
    return {"session": session}


@router.post("/v1/public/guardian/{token}/acknowledge")
def guardian_acknowledge(token: str) -> dict[str, Any]:
    payload = guardian_token_or_401(token)
    session = db.get_session(payload["session_id"])
    if session is None:
        raise HTTPException(status_code=404, detail="Session not found")
    db.append_event(
        session["id"],
        "GUARDIAN_ACKNOWLEDGED",
        {"role": payload.get("role", "guardian")},
    )
    return {"ok": True, "session": public_snapshot(session, payload.get("role", "guardian"))}
