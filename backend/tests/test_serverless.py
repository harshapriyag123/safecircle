import asyncio
import importlib.util
import os
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor

import pytest
from fastapi.testclient import TestClient
from app import db


def test_worker_heartbeat_expires_and_future_time_is_not_ready():
    assert not db.worker_is_current(1_800_000_000_000)
    db.record_worker_tick(1_800_000_000_000)
    assert db.worker_is_current(1_800_000_060_000)
    assert not db.worker_is_current(1_800_000_090_001)
    assert not db.worker_is_current(1_799_999_999_999)


def test_vercel_without_configuration_serves_pages_but_refuses_accounts(monkeypatch):
    monkeypatch.delenv('DATABASE_URL', raising=False)
    monkeypatch.setenv('SAFECIRCLE_ENV', 'production')
    monkeypatch.setenv('SAFECIRCLE_WORKER_MODE', 'external')
    path = Path(__file__).resolve().parents[2] / 'app.py'
    spec = importlib.util.spec_from_file_location('vercel_entry', path)
    module = importlib.util.module_from_spec(spec)
    # Repository root is needed for the backend namespace import.
    monkeypatch.syspath_prepend(str(path.parent))
    spec.loader.exec_module(module)
    with TestClient(module.app) as client:
        assert client.get('/site/').status_code == 200
        assert client.get('/app/v4.html').status_code == 200
        assert client.get('/guardian/').status_code == 200
        health = client.get('/health')
        assert health.status_code == 503
        assert not health.json()['backend_ready']
        assert client.post('/v1/auth/register', json={'email':'test@example.test','password':'SafeCircle123!'}).status_code == 503
        assert client.post('/internal/worker/tick').status_code == 503


@pytest.mark.skipif(not os.getenv('SAFECIRCLE_TEST_DATABASE_URL'), reason='Needs isolated PostgreSQL test database')
def test_postgres_claim_is_atomic_across_independent_connections():
    import time
    from app.delivery import schedule_escalations
    current = int(time.time() * 1000)
    db.upsert_session({'id':'concurrent', 'owner_id':'owner', 'mode':'WALK_HOME',
                      'started_at':current-3_600_000, 'expected_end_at':current-300_000,
                      'last_check_in_at':current-3_600_000})
    s = db.get_session('concurrent')
    db.enqueue_delivery(s, 5, 'primary', 'push_webhook', {}, current)
    # claim_delivery's Python lock must not be the source of correctness here.
    from contextlib import nullcontext
    saved = db._lock
    db._lock = nullcontext()
    try:
        with ThreadPoolExecutor(max_workers=8) as pool:
            claims = list(pool.map(lambda _: db.claim_delivery(current), range(8)))
        assert len([job for job in claims if job is not None]) == 1
    finally:
        db._lock = saved
