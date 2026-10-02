import pytest
from fastapi.testclient import TestClient
from app import db
from app.entrypoint import app
from app.security import sign_access_token
from app.account_controls import allow_request

@pytest.fixture
def client(tmp_path, monkeypatch):
    monkeypatch.setattr(db, 'DB_PATH', tmp_path / 'account.db')
    with TestClient(app) as client:
        yield client


def test_logout_revokes_only_current_unique_token(client):
    token1, token2 = sign_access_token('owner'), sign_access_token('owner')
    assert token1 != token2
    headers = {'Authorization': 'Bearer ' + token1}
    assert client.post('/v1/auth/logout', headers=headers).status_code == 200
    assert client.get('/v1/subscriptions/owner', headers=headers).status_code == 401
    assert client.get('/v1/subscriptions/owner', headers={'Authorization': 'Bearer ' + token2}).status_code == 200


def test_account_deletion_requires_password_and_revokes_old_tokens(client):
    data = {'email': 'delete@example.test', 'password': 'SafeCircle123!'}
    account = client.post('/v1/auth/register', json=data).json()
    owner = account['user_id']
    headers = {'Authorization': 'Bearer ' + account['access_token']}
    assert client.request('DELETE', '/v1/account', headers=headers, json={**data, 'password': 'WrongPassword123'}).status_code == 401
    assert db.get_user(owner)
    assert client.request('DELETE', '/v1/account', headers=headers, json=data).status_code == 200
    assert db.get_user(owner) is None
    assert client.get('/v1/subscriptions/' + owner, headers=headers).status_code == 401
    # Other accounts remain untouched.
    other = client.post('/v1/auth/register', json={**data, 'email': 'other@example.test'}).json()
    assert db.get_user(other['user_id'])


def test_rate_limit_is_persistent_and_recovers_next_window(client):
    for _ in range(10): assert allow_request('ip:auth', 6000, 10)
    db.init_db()
    assert not allow_request('ip:auth', 6000, 10)
    assert allow_request('ip:auth', 6060, 10)


def test_deleted_customer_is_not_recreated_by_billing(client):
    from app.account_controls import delete_account
    from app.revenuecat import enqueue_reconciliation
    delete_account('deleted-owner')
    db.upsert_subscription('deleted-owner', 'safecircle_pro', True, 'monthly', None)
    enqueue_reconciliation('deleted-owner', 1)
    assert db.get_subscription('deleted-owner') is None
    with db.connect() as conn:
        assert conn.execute('SELECT * FROM subscription_reconcile_jobs WHERE app_user_id=?', ('deleted-owner',)).fetchone() is None
