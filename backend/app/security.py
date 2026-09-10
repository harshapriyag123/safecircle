import base64
import hashlib
import hmac
import json
import os
import time
from typing import Any

API_SECRET = os.getenv("SAFECIRCLE_API_SECRET", "dev-only-change-me")
GUARDIAN_SIGNING_SECRET = os.getenv("GUARDIAN_SIGNING_SECRET", "dev-guardian-change-me")
REVENUECAT_WEBHOOK_SECRET = os.getenv("REVENUECAT_WEBHOOK_SECRET", "")


def require_bearer(authorization: str | None) -> str:
    if not authorization or not authorization.startswith("Bearer "):
        raise PermissionError("Missing bearer token")
    token = authorization.removeprefix("Bearer ").strip()
    if not hmac.compare_digest(token, API_SECRET):
        raise PermissionError("Invalid bearer token")
    return token


def _b64_encode(raw: bytes) -> str:
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def _b64_decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))


def sign_guardian_token(payload: dict[str, Any]) -> str:
    body = _b64_encode(
        json.dumps(payload, separators=(",", ":"), sort_keys=True).encode()
    )
    signature = hmac.new(
        GUARDIAN_SIGNING_SECRET.encode(),
        body.encode(),
        hashlib.sha256,
    ).digest()
    return body + "." + _b64_encode(signature)


def verify_guardian_token(token: str) -> dict[str, Any]:
    body, signature = token.split(".", 1)
    expected = hmac.new(
        GUARDIAN_SIGNING_SECRET.encode(),
        body.encode(),
        hashlib.sha256,
    ).digest()
    supplied = _b64_decode(signature)
    if not hmac.compare_digest(expected, supplied):
        raise PermissionError("Invalid Guardian token")

    payload = json.loads(_b64_decode(body))
    if int(payload["exp"]) < int(time.time() * 1000):
        raise PermissionError("Guardian token expired")
    return payload


def token_hash(token: str) -> str:
    return hashlib.sha256(token.encode()).hexdigest()


def verify_revenuecat_webhook(authorization: str | None) -> None:
    if not REVENUECAT_WEBHOOK_SECRET:
        raise PermissionError("RevenueCat webhook secret is not configured")
    expected = "Bearer " + REVENUECAT_WEBHOOK_SECRET
    if authorization is None or not hmac.compare_digest(authorization, expected):
        raise PermissionError("Invalid RevenueCat webhook secret")
