from fastapi.testclient import TestClient
from app.entrypoint import app
from app import db
from app.security import sign_access_token


def test_history_is_authenticated_owner_scoped_and_redacted():
    with TestClient(app) as client:
        for owner, resolved in [('mine', True), ('mine', False), ('other', True)]:
            identifier = owner + str(resolved)
            db.upsert_session({'id': identifier, 'owner_id': owner, 'mode': 'WALK_HOME',
                'started_at': 1, 'expected_end_at': 2, 'last_check_in_at': 1,
                'resolved': resolved, 'resolved_at': 3 if resolved else None,
                'capsule': {'instruction': 'private'}, 'guardian_contacts': []})
        assert client.get('/v1/me/history').status_code == 401
        result = client.get('/v1/me/history', headers={'Authorization': 'Bearer ' + sign_access_token('mine')})
        assert result.status_code == 200
        rows = result.json()['sessions']
        assert [r['id'] for r in rows] == ['mineTrue']
        assert 'capsule' not in rows[0] and 'guardian_contacts' not in rows[0]


def test_partial_client_snapshot_preserves_privacy_capsule_and_telemetry():
    with TestClient(app) as client:
        headers = {'Authorization': 'Bearer ' + sign_access_token('mine')}
        base = {'id': 'snapshot', 'owner_id': 'mine', 'mode': 'WALK_HOME', 'started_at': 1,
                'expected_end_at': 2, 'last_check_in_at': 1}
        assert client.post('/v1/sessions', headers=headers, json={**base,
            'privacy_mode': 'STATUS_ONLY', 'state': 'CONCERN', 'latitude': 1.2,
            'capsule': {'instruction': 'keep this', 'expiresAt': 9_000_000_000_000}}).status_code == 200
        assert client.post('/v1/sessions', headers=headers, json=base).status_code == 200
        current = client.get('/v1/sessions/snapshot', headers=headers).json()
        assert current['privacy_mode'] == 'STATUS_ONLY' and current['state'] == 'CONCERN'
        assert current['capsule']['instruction'] == 'keep this' and current['latitude'] == 1.2
        assert client.post('/v1/sessions', headers=headers, json={**base, 'capsule': None, 'latitude': None}).status_code == 200
        current = client.get('/v1/sessions/snapshot', headers=headers).json()
        assert current['capsule'] is None and current['latitude'] is None
