import base64
import json
import os
import urllib.parse
import urllib.request
import urllib.error
from dataclasses import dataclass
from typing import Any


@dataclass
class DeliveryResult:
    channel: str
    accepted: bool
    provider_message_id: str | None = None
    error: str | None = None
    ambiguous: bool = False


def valid_push_url(url: str) -> bool:
    try:
        parsed = urllib.parse.urlsplit(url)
        return bool(parsed.scheme == 'https' and parsed.hostname and
                    not parsed.username and not parsed.password and
                    not parsed.query and not parsed.fragment)
    except ValueError:
        return False


class NoProviderRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward authorization or Guardian routing data to another URL.
        return None


class PushWebhookProvider:
    def __init__(self) -> None:
        self.url = os.getenv("SAFECIRCLE_PUSH_WEBHOOK_URL", "").strip()
        self.secret = os.getenv("SAFECIRCLE_PUSH_WEBHOOK_SECRET", "").strip()

    @property
    def configured(self) -> bool:
        return valid_push_url(self.url) and len(self.secret) >= 32

    def send(self, payload: dict[str, Any]) -> DeliveryResult:
        if not self.configured:
            return DeliveryResult("push_webhook", False, error="not configured")
        body = json.dumps(payload).encode()
        request = urllib.request.Request(self.url, data=body, method="POST")
        request.add_header("Content-Type", "application/json")
        if payload.get("delivery_id"):
            request.add_header("Idempotency-Key", str(payload["delivery_id"]))
        request.add_header("Authorization", "Bearer " + self.secret)
        try:
            with urllib.request.build_opener(NoProviderRedirect()).open(request, timeout=8) as response:
                raw = response.read(4097)
                message_id = None
                # Persist only the adapter's explicitly named identifier, never
                # arbitrary response bodies that may contain private diagnostics.
                if len(raw) <= 4096:
                    try:
                        result = json.loads(raw)
                        candidate = result.get('message_id') if isinstance(result, dict) else None
                        if isinstance(candidate, str) and 0 < len(candidate) <= 120 and all(
                                ch.isascii() and (ch.isalnum() or ch in '._:-') for ch in candidate):
                            message_id = candidate
                    except (ValueError, UnicodeDecodeError):
                        pass
                return DeliveryResult(
                    "push_webhook",
                    200 <= response.status < 300,
                    provider_message_id=message_id,
                )
        except urllib.error.HTTPError as exc:
            return DeliveryResult("push_webhook", False, error="http_" + str(exc.code))
        except Exception:
            return DeliveryResult("push_webhook", False, error="adapter_unavailable")


class TwilioSmsProvider:
    def __init__(self) -> None:
        self.sid = os.getenv("TWILIO_ACCOUNT_SID", "").strip()
        self.token = os.getenv("TWILIO_AUTH_TOKEN", "").strip()
        self.from_number = os.getenv("TWILIO_FROM_NUMBER", "").strip()

    @property
    def configured(self) -> bool:
        return bool(self.sid and self.token and self.from_number)

    def send(self, to_number: str, message: str, callback_url: str | None = None) -> DeliveryResult:
        if not self.configured:
            return DeliveryResult("twilio_sms", False, error="not configured")

        url = f"https://api.twilio.com/2010-04-01/Accounts/{self.sid}/Messages.json"
        fields = {"From": self.from_number, "To": to_number, "Body": message}
        if callback_url:
            fields["StatusCallback"] = callback_url
        data = urllib.parse.urlencode(fields).encode()
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
        except urllib.error.HTTPError as exc:
            return DeliveryResult("twilio_sms", False, error="http_" + str(exc.code))
        except Exception:
            return DeliveryResult("twilio_sms", False, error="outcome_unknown", ambiguous=True)
