import os
import time
import uuid

import pytest
from fastapi.testclient import TestClient

os.environ.setdefault("REVENUECAT_WEBHOOK_SECRET", "test-revenuecat-secret")

from app import db
from app.entrypoint import app
from app.main import public_snapshot
from app.providers import DeliveryResult
from app.security import sign_access_token


def session(owner="sc_gap_owner"):
    now = int(time.time() * 1000)
    return {
        "id": "session-" + uuid.uuid4().hex,
        "owner_id": owner, "mode": "WALK_HOME", "started_at": now,
        "expected_end_at": now + 600_000, "last_check_in_at": now,
        "latitude": 32.954321, "longitude": -97.234567,
        "capsule": {"primaryContact": "+15555550123"},
    }


def headers(owner):
    return {"Authorization": "Bearer " + sign_access_token(owner)}


def event(s, event_id=None):
    return {"id": event_id or uuid.uuid4().hex, "session_id": s["id"],
            "event_type": "CHECK_IN", "payload": {}, "created_at": s["started_at"]}


def test_batch_rejects_other_owners_before_any_writes():
    own, other = session(), session("sc_other_owner")
    with TestClient(app) as client:
        for s in (own, other):
            assert client.post("/v1/sessions", json=s, headers=headers(s["owner_id"])).status_code == 200
        response = client.post("/v1/events/batch", json={"events": [event(own), event(other)]},
                               headers=headers(own["owner_id"]))
        assert response.status_code == 403
        assert not any(e["event_type"] == "CLIENT_CHECK_IN" for e in db.list_events(own["id"]))


def test_batch_retry_acknowledges_without_duplicate_events():
    s = session()
    body = {"events": [event(s)]}
    with TestClient(app) as client:
        client.post("/v1/sessions", json=s, headers=headers(s["owner_id"]))
        for _ in range(2):
            response = client.post("/v1/events/batch", json=body, headers=headers(s["owner_id"]))
            assert response.status_code == 200
            assert response.json()["acknowledged_event_ids"] == [body["events"][0]["id"]]
        assert sum(e["event_type"] == "CLIENT_CHECK_IN" for e in db.list_events(s["id"])) == 1
        assert client.post("/v1/events/batch", json={"events": [event(s)] * 501},
                           headers=headers(s["owner_id"])).status_code == 422


def test_subscription_status_is_owner_scoped():
    with TestClient(app) as client:
        assert client.get("/v1/subscriptions/sc_other", headers=headers("sc_owner")).status_code == 403
        assert client.get("/v1/subscriptions/sc_owner", headers=headers("sc_owner")).status_code == 200


def test_patch_can_clear_sensitive_data_but_not_required_fields():
    s = session()
    with TestClient(app) as client:
        client.post("/v1/sessions", json=s, headers=headers(s["owner_id"]))
        response = client.patch("/v1/sessions/" + s["id"],
            json={"latitude": None, "longitude": None, "capsule": None}, headers=headers(s["owner_id"]))
        assert response.status_code == 200
        current = response.json()["session"]
        assert current["latitude"] is None and current["longitude"] is None and current["capsule"] is None
        assert current["expected_end_at"] == s["expected_end_at"]
        assert "capsule_json" not in current
        assert client.patch("/v1/sessions/" + s["id"], json={"state": None},
                            headers=headers(s["owner_id"])).status_code == 422


def test_concern_does_not_release_precise_location_and_expired_capsule_is_withheld():
    s = session()
    with TestClient(app) as client:
        client.post("/v1/sessions", json={**s, "state": "CONCERN"}, headers=headers(s["owner_id"]))
        current = db.get_session(s["id"])
        assert public_snapshot(current, "guardian")["location"] is None
        current["expected_end_at"] = int(time.time() * 1000) - 16 * 60_000
        current["capsule"]["expiresAt"] = int(time.time() * 1000) - 1
        snapshot = public_snapshot(current, "guardian")
        assert snapshot["location"] is not None
        assert snapshot["safety_capsule"] is None


@pytest.mark.parametrize("event_type,extra,expected", [
    ("CANCELLATION", {"cancel_reason": "UNSUBSCRIBE"}, True),
    ("BILLING_ISSUE", {}, True),
    ("SUBSCRIPTION_PAUSED", {}, True),
    ("EXPIRATION", {}, False),
    ("CANCELLATION", {"cancel_reason": "CUSTOMER_SUPPORT"}, False),
    ("NON_RENEWING_PURCHASE", {"expiration_at_ms": None}, True),
    ("BILLING_ISSUE", {"expiration_at_ms": 1, "grace_period_expiration_at_ms": 2_000_000_000_000}, True),
])
def test_entitlement_lifecycle(event_type, extra, expected):
    owner = "sc_rc_" + uuid.uuid4().hex
    payload = {"event": {"type": event_type, "app_user_id": owner,
        "entitlement_ids": ["safecircle_pro"], "expiration_at_ms": 2_000_000_000_000, **extra}}
    with TestClient(app) as client:
        response = client.post("/webhooks/revenuecat", json=payload,
                               headers={"Authorization": "Bearer test-revenuecat-secret"})
        assert response.status_code == 200
        assert client.get("/v1/subscriptions/" + owner, headers=headers(owner)).json()["subscription"]["is_active"] is expected


def test_subscription_expires_even_when_webhook_is_delayed():
    owner = "sc_expired_" + uuid.uuid4().hex
    with TestClient(app) as client:
        db.upsert_subscription(owner, "safecircle_pro", True, "monthly", 1)
        assert client.get("/v1/subscriptions/" + owner, headers=headers(owner)).json()["subscription"]["is_active"] is False


def test_unrelated_webhooks_do_not_remove_pro_access():
    owner = "sc_unrelated_" + uuid.uuid4().hex
    with TestClient(app) as client:
        db.upsert_subscription(owner, "safecircle_pro", True, "monthly", 2_000_000_000_000)
        for data in ({"type": "TEST", "entitlement_ids": ["safecircle_pro"]},
                     {"type": "EXPIRATION", "entitlement_ids": ["another_entitlement"]}):
            response = client.post("/webhooks/revenuecat", json={"event": {"app_user_id": owner, **data}},
                                   headers={"Authorization": "Bearer test-revenuecat-secret"})
            assert response.json()["ignored"] is True
        assert db.get_subscription(owner)["is_active"] is True


def test_out_of_order_and_duplicate_webhooks_do_not_overwrite_newer_state():
    owner = "sc_order_" + uuid.uuid4().hex
    def payload(kind, event_id, timestamp):
        return {"event": {"type": kind, "id": event_id, "event_timestamp_ms": timestamp,
            "app_user_id": owner, "entitlement_ids": ["safecircle_pro"],
            "expiration_at_ms": 2_000_000_000_000}}
    with TestClient(app) as client:
        auth = {"Authorization": "Bearer test-revenuecat-secret"}
        assert client.post("/webhooks/revenuecat", json=payload("RENEWAL", "new", 200), headers=auth).status_code == 200
        # An old expiration arriving after a renewal must not revoke access.
        client.post("/webhooks/revenuecat", json=payload("EXPIRATION", "old", 100), headers=auth)
        assert db.get_subscription(owner)["is_active"] is True
        client.post("/webhooks/revenuecat", json=payload("EXPIRATION", "expired", 300), headers=auth)
        assert db.get_subscription(owner)["is_active"] is False
        # Replaying the same event ID must not mutate the stored state.
        client.post("/webhooks/revenuecat", json=payload("RENEWAL", "expired", 300), headers=auth)
        assert db.get_subscription(owner)["is_active"] is False
