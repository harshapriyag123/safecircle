import base64
import hashlib
import json
import os
from typing import Any

from cryptography.fernet import Fernet, InvalidToken


def _key() -> bytes:
    configured = os.getenv("SAFECIRCLE_DATA_ENCRYPTION_KEY", "").strip()
    if configured:
        raw = configured.encode()
        # Validate eagerly so bad deployment config fails clearly.
        Fernet(raw)
        return raw

    # Hackathon/development fallback. Production must set an independent random key.
    seed = os.getenv("SAFECIRCLE_DATA_ENCRYPTION_SECRET", "dev-data-encryption-change-me")
    digest = hashlib.sha256(seed.encode()).digest()
    return base64.urlsafe_b64encode(digest)


def encrypt_json(value: dict[str, Any] | None) -> str | None:
    if value is None:
        return None
    payload = json.dumps(value, separators=(",", ":"), sort_keys=True).encode()
    return Fernet(_key()).encrypt(payload).decode()


def decrypt_json(value: str | None) -> dict[str, Any] | None:
    if not value:
        return None
    try:
        raw = Fernet(_key()).decrypt(value.encode())
    except InvalidToken as exc:
        raise ValueError("Unable to decrypt stored Safety Capsule") from exc
    return json.loads(raw)
