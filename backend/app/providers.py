import base64
import json
import os
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Any


@dataclass
class DeliveryResult:
    channel: str
    accepted: bool
    provider_message_id: str | None = None
    error: str | None = None


class PushWebhookProvider:
    def __init__(self) -> None:
        self.url = os.getenv("SAFECIRCLE_PUSH_WEBHOOK_URL", "").strip()
        self.secret = os.getenv("SAFECIRCLE_PUSH_WEBHOOK_SECRET", "").strip()

    @property
    def configured(self) -> bool:
        return bool(self.url)

    def send(self, payload: dict[str, Any]) -> DeliveryResult:
        if not self.configured:
            return DeliveryResult("push_webhook", False, error="not configured")
        body = json.dumps(payload).encode()
        request = urllib.request.Request(self.url, data=body, method="POST")
        request.add_header("Content-Type", "application/json")
        if self.secret:
            request.add_header("Authorization", "Bearer " + self.secret)
        try:
            with urllib.request.urlopen(request, timeout=8) as response:
                raw = response.read().decode(errors="ignore")
                return DeliveryResult(
                    "push_webhook",
                    200 <= response.status < 300,
                    provider_message_id=raw[:120] or None,
                )
        except Exception as exc:
            return DeliveryResult("push_webhook", False, error=str(exc))


class TwilioSmsProvider:
    def __init__(self) -> None:
        self.sid = os.getenv("TWILIO_ACCOUNT_SID", "").strip()
        self.token = os.getenv("TWILIO_AUTH_TOKEN", "").strip()
        self.from_number = os.getenv("TWILIO_FROM_NUMBER", "").strip()

    @property
    def configured(self) -> bool:
        return bool(self.sid and self.token and self.from_number)

    def send(self, to_number: str, message: str) -> DeliveryResult:
        if not self.configured:
            return DeliveryResult("twilio_sms", False, error="not configured")

        url = f"https://api.twilio.com/2010-04-01/Accounts/{self.sid}/Messages.json"
        data = urllib.parse.urlencode({
            "From": self.from_number,
            "To": to_number,
            "Body": message,
        }).encode()
        request = urllib.request.Request(url, data=data, method="POST")
        basic = base64.b64encode(f"{self.sid}:{self.token}".encode()).decode()
        request.add_header("Authorization", "Basic " + basic)
        request.add_header("Content-Type", "application/x-www-form-urlencoded")

        try:
            with urllib.request.urlopen(request, timeout=8) as response:
                payload = json.loads(response.read().decode())
                return DeliveryResult(
                    "twilio_sms",
                    200 <= response.status < 300,
                    provider_message_id=payload.get("sid"),
                )
        except Exception as exc:
            return DeliveryResult("twilio_sms", False, error=str(exc))


def deliver_escalation(session: dict[str, Any], stage: int) -> list[DeliveryResult]:
    message = (
        f"SafeCircle: {session.get('mode','Safety Session')} is unresolved "
        f"{stage} minutes after the expected-safe time."
    )
    payload = {
        "event": "safecircle_escalation",
        "session_id": session["id"],
        "owner_id": session["owner_id"],
        "stage_minutes": stage,
        "state": session.get("state", "CONCERN"),
        "message": message,
    }

    results: list[DeliveryResult] = []
    push = PushWebhookProvider()
    if push.configured:
        results.append(push.send(payload))

    capsule = session.get("capsule") or {}
    phone = capsule.get("primaryContact") if isinstance(capsule, dict) else None
    sms = TwilioSmsProvider()
    if phone and sms.configured:
        results.append(sms.send(str(phone), message))

    return results
