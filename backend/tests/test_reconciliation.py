import time
import pytest
from fastapi.testclient import TestClient
from app import db, revenuecat, security
from app.entrypoint import app
from app.config import validate_production_config
from app.security import sign_access_token

@pytest.fixture
def isolated(tmp_path, monkeypatch):
    monkeypatch.setattr(db, 'DB_PATH', tmp_path / 'billing.db')
    monkeypatch.setattr(security, 'REVENUECAT_WEBHOOK_SECRET', 'billing-test-secret')
    monkeypatch.delenv('REVENUECAT_SECRET_API_KEY', raising=False)
    db.init_db()


def test_transfer_queues_both_customers_and_reconciles(isolated, monkeypatch):
    now = int(time.time() * 1000)
    with TestClient(app) as client:
        r = client.post('/webhooks/revenuecat', json={'event': {'type': 'TRANSFER', 'id': 'transfer-1',
                        'transferred_from': ['old'], 'transferred_to': ['new']}},
                        headers={'Authorization': 'Bearer billing-test-secret'})
        assert r.status_code == 200
    assert revenuecat.reconcile_pending(now + 10) == 0
    db.upsert_subscription('old', 'safecircle_pro', True, 'monthly', None)
    monkeypatch.setenv('REVENUECAT_SECRET_API_KEY', 'mock-server-only-key')
    monkeypatch.setattr(revenuecat, 'fetch_subscriber', lambda user, key: {'entitlements': {
        'safecircle_pro': {'product_identifier': 'monthly', 'expires_date': None}} if user == 'new' else {}})
    assert revenuecat.reconcile_pending(now + 1000) == 2
    assert not db.get_subscription('old')['is_active']
    assert db.get_subscription('new')['is_active']


def test_refresh_failure_preserves_state_and_retries(isolated, monkeypatch):
    revenuecat.enqueue_reconciliation('owner', 1000)
    db.upsert_subscription('owner', 'safecircle_pro', True, 'monthly', None)
    monkeypatch.setenv('REVENUECAT_SECRET_API_KEY', 'mock-key')
    def failure(*args): raise OSError('provider unavailable')
    monkeypatch.setattr(revenuecat, 'fetch_subscriber', failure)
    assert revenuecat.reconcile_pending(1000) == 0
    assert db.get_subscription('owner')['is_active']
    with db.connect() as conn:
        assert conn.execute('SELECT next_attempt_at FROM subscription_reconcile_jobs').fetchone()[0] == 61000


def test_new_event_during_refresh_is_not_lost(isolated, monkeypatch):
    revenuecat.enqueue_reconciliation('owner', 1000)
    monkeypatch.setenv('REVENUECAT_SECRET_API_KEY', 'mock-key')
    def fetch(user, key):
        revenuecat.enqueue_reconciliation(user, 1001)
        return {'entitlements': {}}
    monkeypatch.setattr(revenuecat, 'fetch_subscriber', fetch)
    revenuecat.reconcile_pending(1000)
    with db.connect() as conn:
        assert conn.execute('SELECT generation FROM subscription_reconcile_jobs').fetchone()[0] == 2


def test_refresh_endpoint_requires_owner(isolated):
    with TestClient(app) as client:
        assert client.post('/v1/subscriptions/owner/reconcile', headers={
            'Authorization': 'Bearer ' + sign_access_token('different')}).status_code == 403


def test_production_fails_closed_for_missing_secrets(monkeypatch):
    monkeypatch.setenv('SAFECIRCLE_ENV', 'production')
    monkeypatch.delenv('SAFECIRCLE_DATA_ENCRYPTION_KEY', raising=False)
    with pytest.raises(RuntimeError, match='configured secrets'):
        validate_production_config()
