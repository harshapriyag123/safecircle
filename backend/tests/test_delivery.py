import time
import uuid

import pytest
from fastapi.testclient import TestClient

from app import db
from app.delivery import process_delivery, schedule_escalations
from app.entrypoint import app
from app.main import public_snapshot
from app.providers import DeliveryResult
from app.security import sign_access_token


@pytest.fixture
def isolated_db(tmp_path, monkeypatch):
    monkeypatch.setattr(db, 'DB_PATH', tmp_path / 'delivery.db')
    monkeypatch.delenv('SAFECIRCLE_PUSH_WEBHOOK_URL', raising=False)
    monkeypatch.delenv('TWILIO_ACCOUNT_SID', raising=False)
    db.init_db()
    return db


def make_session():
    now = int(time.time() * 1000)
    s = {'id': uuid.uuid4().hex, 'owner_id': 'sc_delivery_owner', 'mode': 'WALK_HOME',
         'started_at': now - 3_600_000, 'expected_end_at': now - 6 * 60_000,
         'last_check_in_at': now - 3_600_000, 'privacy_mode': 'STATUS_ONLY',
         'guardian_contacts': [
             {'role': 'primary', 'phone': '+15555550111', 'consented': True},
             {'role': 'backup', 'phone': '+15555550222', 'consented': True}],
         'capsule': {'expiresAt': now + 60_000, 'instruction': 'Call first',
                     'latitude': 32.123456, 'primaryContact': '+15555550111'}}
    db.upsert_session(s)
    return db.get_session(s['id']), now


def test_stage_routing_and_deduplication(isolated_db):
    s, now = make_session()
    schedule_escalations(s, now)
    schedule_escalations(s, now)
    jobs = db.delivery_status(s['id'])
    assert {(j['stage'], j['role'], j['channel']) for j in jobs} == {
        (5, 'primary', 'twilio_sms'), (5, 'primary', 'push_webhook')}
    schedule_escalations(s, now + 5 * 60_000)
    assert len(db.delivery_status(s['id'])) == 4
    assert any(j['role'] == 'backup' and j['stage'] == 10 for j in db.delivery_status(s['id']))


def test_missing_configuration_waits_without_using_attempts(isolated_db):
    s, now = make_session()
    schedule_escalations(s, now)
    while process_delivery(now):
        pass
    jobs = db.delivery_status(s['id'])
    assert all(j['status'] == 'queued' and j['attempts'] == 0 for j in jobs)
    assert not process_delivery(now + 1)


def test_retry_then_acceptance_is_not_delivery(isolated_db, monkeypatch):
    s, now = make_session()
    # Isolate one webhook job so scheduling order is deterministic.
    db.enqueue_delivery(s, 5, 'primary', 'push_webhook', {}, now)
    monkeypatch.setenv('SAFECIRCLE_PUSH_WEBHOOK_URL', 'https://example.com/push')
    responses = iter([DeliveryResult('push_webhook', False), DeliveryResult('push_webhook', True, 'provider-1')])
    seen = []
    def send(self, payload):
        seen.append(payload['delivery_id'])
        return next(responses)
    monkeypatch.setattr('app.providers.PushWebhookProvider.send', send)
    assert process_delivery(now)
    assert db.delivery_status(s['id'])[0]['status'] == 'queued'
    assert not process_delivery(now + 1)
    assert process_delivery(now + 60_000)
    job = db.delivery_status(s['id'])[0]
    assert job['status'] == 'accepted' and job['attempts'] == 2
    assert seen[0] == seen[1]
    assert not process_delivery(now + 120_000)
    assert db.mark_delivery_receipt(job['id'], True, now + 120_000)
    assert db.delivery_status(s['id'])[0]['status'] == 'delivered'


def test_resolve_and_eta_change_cancel_pending_jobs(isolated_db):
    s, now = make_session()
    schedule_escalations(s, now)
    s['expected_end_at'] = now + 600_000
    db.upsert_session(s)
    assert all(j['status'] == 'cancelled' for j in db.delivery_status(s['id']))
    schedule_escalations(db.get_session(s['id']), now + 16 * 60_000)
    s['resolved'] = True
    db.upsert_session(s)
    assert not process_delivery(now + 30 * 60_000)
    assert all(j['status'] == 'cancelled' for j in db.delivery_status(s['id']))


def test_worker_lease_recovers_after_restart(isolated_db):
    s, now = make_session()
    job_id = db.enqueue_delivery(s, 5, 'primary', 'push_webhook', {}, now)
    assert db.claim_delivery(now)['id'] == job_id
    assert db.claim_delivery(now + 1) is None
    db.init_db()  # restart must preserve the queue
    assert db.claim_delivery(now + 60_001)['id'] == job_id


def test_capsule_purge_and_privacy_allowlist(isolated_db):
    s, now = make_session()
    s['expected_end_at'] = now - 16 * 60_000
    db.upsert_session(s)
    snapshot = public_snapshot(db.get_session(s['id']), 'guardian')
    assert snapshot['location'] is None
    assert snapshot['safety_capsule'] == {'expiresAt': now + 60_000, 'instruction': 'Call first'}
    assert db.purge_expired_capsules(now + 60_001) == 1
    assert db.get_session(s['id'])['capsule'] is None


def test_late_checkin_gives_five_minute_grace_and_cancels_jobs(isolated_db):
    s, now = make_session()
    schedule_escalations(s, now)
    with TestClient(app) as client:
        headers = {'Authorization': 'Bearer ' + sign_access_token(s['owner_id'])}
        response = client.post('/v1/sessions/' + s['id'] + '/check-in', headers=headers)
        assert response.status_code == 200
        assert response.json()['session']['expected_end_at'] >= now + 5 * 60_000
        assert all(j['status'] == 'cancelled' for j in db.delivery_status(s['id']))
        s.update(state='ESCALATED', latitude=32.12345, longitude=-97.12345, privacy_mode='PRECISE_ON_ESCALATION')
        stale = client.post('/v1/sessions', json=s, headers=headers)
        assert stale.status_code == 200
        assert db.get_session(s['id'])['expected_end_at'] >= now + 5 * 60_000
        canonical = db.get_session(s['id'])
        assert canonical['state'] == 'NORMAL'
        assert public_snapshot(canonical, 'primary')['location'] is None


def test_sms_contacts_require_explicit_consent(isolated_db):
    s, now = make_session()
    s['guardian_contacts'][0]['consented'] = False
    schedule_escalations(s, now)
    assert all(j['channel'] != 'twilio_sms' for j in db.delivery_status(s['id']))


def test_receipts_are_authenticated_and_owner_scoped(isolated_db, monkeypatch):
    s, now = make_session()
    job = db.enqueue_delivery(s, 5, 'primary', 'push_webhook', {}, now)
    db.claim_delivery(now)
    db.finish_delivery(job, 'accepted', now)
    monkeypatch.setenv('SAFECIRCLE_DELIVERY_RECEIPT_SECRET', 'test-receipt-secret')
    with TestClient(app) as client:
        assert client.post('/v1/delivery-receipts/push/' + job, json={'status': 'delivered'}).status_code == 401
        assert client.post('/v1/delivery-receipts/push/' + job, json={'status': 'delivered'},
                           headers={'Authorization': 'Bearer test-receipt-secret'}).status_code == 200
        assert db.get_delivery(job)['status'] == 'delivered'
        assert client.get('/v1/sessions/' + s['id'] + '/deliveries',
                          headers={'Authorization': 'Bearer ' + sign_access_token('another_owner')}).status_code == 403


def test_revoking_consent_cancels_pending_sms(isolated_db):
    s, now = make_session()
    schedule_escalations(s, now)
    s['guardian_contacts'] = []
    db.upsert_session(s)
    assert all(j['status'] == 'cancelled' for j in db.delivery_status(s['id']) if j['channel'] == 'twilio_sms')


def test_missing_capsule_expiry_is_bounded(isolated_db):
    s, now = make_session()
    s['capsule'] = {'instruction': 'Call first'}
    db.upsert_session(s)
    assert db.get_session(s['id'])['capsule']['expiresAt'] <= now + 86_400_100


def test_uncertain_sms_is_not_retried_after_interruption(isolated_db):
    s, now = make_session()
    job = db.enqueue_delivery(s, 5, 'primary', 'twilio_sms', {'phone': '+15555550111'}, now)
    assert db.claim_delivery(now)['id'] == job
    assert db.claim_delivery(now + 60_001) is None
    assert db.get_delivery(job)['status'] == 'uncertain'
    assert db.mark_delivery_receipt(job, True, now + 61_000)
    assert db.get_delivery(job)['status'] == 'delivered'


def test_twilio_receipt_requires_signature_and_matching_sid(isolated_db, monkeypatch):
    import base64, hashlib, hmac
    from urllib.parse import urlencode
    s, now = make_session()
    job = db.enqueue_delivery(s, 5, 'primary', 'twilio_sms', {'phone': '+15555550111'}, now)
    db.claim_delivery(now)
    db.finish_delivery(job, 'accepted', now, provider_message_id='SMexpected')
    monkeypatch.setenv('TWILIO_AUTH_TOKEN', 'test-twilio-token')
    monkeypatch.setenv('SAFECIRCLE_PUBLIC_BASE_URL', 'https://example.test')
    path = '/v1/delivery-receipts/twilio/' + job
    fields = {'MessageSid': 'SMexpected', 'MessageStatus': 'delivered'}
    canonical = 'https://example.test' + path + ''.join(k + fields[k] for k in sorted(fields))
    signature = base64.b64encode(hmac.new(b'test-twilio-token', canonical.encode(), hashlib.sha1).digest()).decode()
    with TestClient(app) as client:
        assert client.post(path, content=urlencode(fields)).status_code == 401
        response = client.post(path, content=urlencode(fields), headers={'X-Twilio-Signature': signature,
                                'Content-Type': 'application/x-www-form-urlencoded'})
        assert response.status_code == 200
        assert db.get_delivery(job)['status'] == 'delivered'


def test_battery_destination_consent_applies_to_all_guardian_fields(isolated_db):
    s, now = make_session()
    s.update(expected_end_at=now - 16 * 60_000, battery_percent=78, destination='Private address',
             capsule={'expiresAt': now + 60_000, 'batteryPercent': 78, 'destinationLabel': 'Private address', 'instruction': 'Call first'})
    db.upsert_session(s)
    redacted = public_snapshot(db.get_session(s['id']), 'primary')
    assert redacted['battery_percent'] is None and redacted['destination'] is None
    assert set(redacted['safety_capsule']) == {'expiresAt', 'instruction'}
    s.update(share_battery_on_escalation=True, share_destination_on_escalation=True)
    db.upsert_session(s)
    allowed = public_snapshot(db.get_session(s['id']), 'backup')
    assert allowed['battery_percent'] == 78 and allowed['destination'] == 'Private address'
    s['resolved'] = True
    db.upsert_session(s)
    closed = public_snapshot(db.get_session(s['id']), 'backup')
    assert closed['battery_percent'] is None and closed['destination'] is None and closed['safety_capsule'] is None


def test_nested_capsule_data_is_not_exposed_through_allowed_key(isolated_db):
    s, now = make_session()
    s.update(expected_end_at=now - 16 * 60_000, capsule={'instruction': {'phone': '+15555550111', 'latitude': 32.12345}})
    db.upsert_session(s)
    snapshot = public_snapshot(db.get_session(s['id']), 'primary')
    assert 'instruction' not in snapshot['safety_capsule']
