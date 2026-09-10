import os
import uuid

os.environ["SAFECIRCLE_API_SECRET"] = "test-owner-secret"
os.environ["GUARDIAN_SIGNING_SECRET"] = "test-guardian-signing-secret"
os.environ["REVENUECAT_WEBHOOK_SECRET"] = "test-revenuecat-secret"
os.environ["SAFECIRCLE_DB_PATH"] = "/tmp/safecircle-test.db"

from fastapi.testclient import TestClient

from app.main import app

OWNER_HEADERS = {"Authorization": "Bearer test-owner-secret"}


def new_session(privacy_mode: str = "PRECISE_ON_ESCALATION") -> dict:
    now = 1_800_000_000_000
    return {
        "id": "session-" + str(uuid.uuid4()),
        "owner_id": "sc_test_owner",
        "mode": "WALK_HOME",
        "destination": "Home",
        "started_at": now,
        "expected_end_at": now + 20 * 60_000,
        "last_check_in_at": now,
        "state": "NORMAL",
        "battery_percent": 76,
        "latitude": 32.95,
        "longitude": -97.23,
        "location_accuracy": 15.0,
        "privacy_mode": privacy_mode,
        "capsule": {"instruction": "Call me first"},
        "resolved": False,
    }


def test_health():
    with TestClient(app) as client:
        response = client.get("/health")
        assert response.status_code == 200
        assert response.json()["status"] == "ok"


def test_status_only_guardian_view_never_exposes_location():
    session = new_session("STATUS_ONLY")
    with TestClient(app) as client:
        created = client.post("/v1/sessions", json=session, headers=OWNER_HEADERS)
        assert created.status_code == 200

        invite = client.post(
            "/v1/guardian-invites",
            json={
                "session_id": session["id"],
                "owner_id": session["owner_id"],
                "role": "guardian",
                "ttl_minutes": 60,
            },
            headers=OWNER_HEADERS,
        )
        assert invite.status_code == 200
        token = invite.json()["guardian_token"]

        public = client.get("/v1/public/guardian/" + token)
        assert public.status_code == 200
        body = public.json()
        assert body["location"] is None
        assert body["safety_capsule"] is None


def test_owner_can_check_in_and_resolve():
    session = new_session()
    with TestClient(app) as client:
        assert client.post("/v1/sessions", json=session, headers=OWNER_HEADERS).status_code == 200

        checkin = client.post(
            "/v1/sessions/" + session["id"] + "/check-in",
            headers=OWNER_HEADERS,
        )
        assert checkin.status_code == 200
        assert checkin.json()["session"]["state"] == "NORMAL"

        resolved = client.post(
            "/v1/sessions/" + session["id"] + "/resolve",
            headers=OWNER_HEADERS,
        )
        assert resolved.status_code == 200

        current = client.get("/v1/sessions/" + session["id"], headers=OWNER_HEADERS)
        assert current.json()["resolved"] is True


def test_revenuecat_webhook_updates_subscription_mirror():
    user_id = "sc_rc_test_" + str(uuid.uuid4())
    payload = {
        "event": {
            "type": "INITIAL_PURCHASE",
            "app_user_id": user_id,
            "entitlement_ids": ["safecircle_pro"],
            "product_id": "monthly",
            "expiration_at_ms": 2_000_000_000_000,
        }
    }
    with TestClient(app) as client:
        response = client.post(
            "/webhooks/revenuecat",
            json=payload,
            headers={"Authorization": "Bearer test-revenuecat-secret"},
        )
        assert response.status_code == 200

        status = client.get("/v1/subscriptions/" + user_id, headers=OWNER_HEADERS)
        assert status.status_code == 200
        assert status.json()["subscription"]["is_active"] is True
