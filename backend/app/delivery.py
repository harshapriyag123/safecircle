"""Persistent escalation outbox. Provider acceptance is not delivery confirmation."""
import os
from typing import Any

from . import db
from .providers import PushWebhookProvider, TwilioSmsProvider


def schedule_escalations(session: dict[str, Any], current: int) -> None:
    if session['resolved']:
        return
    contacts = {item['role']: item for item in session.get('guardian_contacts', []) if item.get('consented')}
    for stage, roles in ((5, ('primary',)), (10, ('backup',)), (15, ('primary', 'backup'))):
        if current < session['expected_end_at'] + stage * 60_000:
            continue
        for role in roles:
            contact = contacts.get(role)
            if contact:
                db.enqueue_delivery(session, stage, role, 'twilio_sms',
                                    {'phone': contact['phone']}, current)
            # Queue webhook deliveries even before provider setup; retries wait for configuration.
            db.enqueue_delivery(session, stage, role, 'push_webhook', {}, current)


def process_delivery(current: int) -> bool:
    job = db.claim_delivery(current)
    if not job:
        return False
    session = db.get_session(job['session_id'])
    if not session or session['resolved'] or session['expected_end_at'] != job['deadline']:
        db.finish_delivery(job['id'], 'cancelled', current, attempted=False)
        return True
    message = f"SafeCircle: {session['mode']} check-in is overdue. Please check on your trusted contact."
    if job['channel'] == 'twilio_sms':
        provider = TwilioSmsProvider()
        base = os.getenv('SAFECIRCLE_PUBLIC_BASE_URL', '').rstrip('/')
        callback = base + '/v1/delivery-receipts/twilio/' + job['id'] if base.startswith('https://') else None
        send = lambda: provider.send(job['payload']['phone'], message, callback_url=callback)
    else:
        provider = PushWebhookProvider()
        send = lambda: provider.send({
            'event': 'safecircle_escalation', 'delivery_id': job['id'],
            'session_id': session['id'], 'owner_id': session['owner_id'],
            'stage_minutes': job['stage'], 'guardian_role': job['role'], 'message': message,
        })
    if not provider.configured:
        db.finish_delivery(job['id'], 'queued', current, attempted=False,
                           error='provider_not_configured', next_attempt_at=current + 60_000)
        return True
    try:
        result = send()
    except Exception:
        db.finish_delivery(job['id'], 'failed' if job['attempts'] >= 4 else 'queued', current,
                           error='provider_exception', next_attempt_at=current + 60_000)
        return True
    if result.accepted:
        db.finish_delivery(job['id'], 'accepted', current, provider_message_id=result.provider_message_id)
    else:
        attempts = job['attempts'] + 1
        db.finish_delivery(job['id'], 'failed' if attempts >= 5 else 'queued', current,
                           error='provider_rejected', next_attempt_at=current + min(900_000, 15_000 * 2**attempts))
    return True
