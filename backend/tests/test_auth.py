import importlib
import os
import uuid

os.environ["SAFECIRCLE_API_SECRET"] = "test-owner-secret"
os.environ["SAFECIRCLE_ACCESS_TOKEN_SECRET"] = "test-access-secret"
os.environ["SAFECIRCLE_ALLOW_DEMO_TOKEN"] = "true"
os.environ["GUARDIAN_SIGNING_SECRET"] = "test-guardian-signing-secret"
os.environ["REVENUECAT_WEBHOOK_SECRET"] = "test-revenuecat-secret"
os.environ["SAFECIRCLE_DB_PATH"] = "/tmp/safecircle-auth-test.db"

from fastapi.testclient import TestClient
from app.main import app


def test_register_login_and_owner_authorization():
    email = "user-" + uuid.uuid4().hex[:8] + "@example.com"
    password = "strong-test-password-123"

    with TestClient(app) as client:
        registered = client.post("/v1/auth/register", json={"email": email, "password": password})
        assert registered.status_code == 200
        body = registered.json()
        assert body["user_id"].startswith("sc_")
        token = body["access_token"]

        logged_in = client.post("/v1/auth/login", json={"email": email, "password": password})
        assert logged_in.status_code == 200
        assert logged_in.json()["user_id"] == body["user_id"]

        now = 1_900_000_000_000
        session = {
            "id": "auth-session-" + uuid.uuid4().hex,
            "owner_id": body["user_id"],
            "mode": "WALK_HOME",
            "started_at": now,
            "expected_end_at": now + 600_000,
            "last_check_in_at": now,
            "state": "NORMAL",
            "privacy_mode": "STATUS_ONLY",
            "resolved": False,
        }
        created = client.post(
            "/v1/sessions",
            json=session,
            headers={"Authorization": "Bearer " + token},
        )
        assert created.status_code == 200

        forbidden = client.post(
            "/v1/sessions",
            json={**session, "id": session["id"] + "-other", "owner_id": "sc_someone_else"},
            headers={"Authorization": "Bearer " + token},
        )
        assert forbidden.status_code == 403
