"""Authoritative subscriber refresh; jobs survive webhook/provider failures."""
import datetime
import json
import os
import urllib.parse
import urllib.request
from . import db

PROJECT_ID = 'proj5f7132ef'  # Administrative identifier, never used as a credential.
ENTITLEMENT = 'safecircle_pro'


def enqueue_reconciliation(customer: str, current: int) -> None:
    with db._lock, db.connect() as conn:
        conn.execute('INSERT INTO subscription_reconcile_jobs VALUES(?,?,?) ON CONFLICT(app_user_id) '
                     'DO UPDATE SET generation=subscription_reconcile_jobs.generation+1,next_attempt_at=excluded.next_attempt_at',
                     (customer, 1, current))


def fetch_subscriber(customer: str, key: str) -> dict:
    url = 'https://api.revenuecat.com/v1/subscribers/' + urllib.parse.quote(customer, safe='')
    request = urllib.request.Request(url, headers={'Authorization': 'Bearer ' + key, 'Accept': 'application/json'})
    with urllib.request.urlopen(request, timeout=8) as response:
        return json.load(response)['subscriber']


def reconcile_pending(current: int) -> int:
    key = os.getenv('REVENUECAT_SECRET_API_KEY', '')
    if not key:
        return 0
    with db.connect() as conn:
        jobs = conn.execute('SELECT * FROM subscription_reconcile_jobs WHERE next_attempt_at<=? LIMIT 10', (current,)).fetchall()
    refreshed = 0
    for job in jobs:
        try:
            subscriber = fetch_subscriber(job['app_user_id'], key)
            entitlement = subscriber.get('entitlements', {}).get(ENTITLEMENT)
            expiry = None
            active = bool(entitlement)
            if entitlement and entitlement.get('expires_date'):
                expiry = int(datetime.datetime.fromisoformat(entitlement['expires_date'].replace('Z', '+00:00')).timestamp() * 1000)
                active = expiry > current
            # Sandbox state is kept separate from production backend entitlement access.
            product = entitlement.get('product_identifier') if entitlement else None
            store_record = subscriber.get('subscriptions', {}).get(product, {})
            if os.getenv('SAFECIRCLE_ENV') == 'production' and store_record.get('is_sandbox'):
                active = False
            db.upsert_subscription(job['app_user_id'], ENTITLEMENT, active, product, expiry, event_timestamp_ms=current)
            with db._lock, db.connect() as conn:
                conn.execute('DELETE FROM subscription_reconcile_jobs WHERE app_user_id=? AND generation=?',
                             (job['app_user_id'], job['generation']))
            refreshed += 1
        except Exception:
            # No credential, provider response, or customer data is logged.
            with db._lock, db.connect() as conn:
                conn.execute('UPDATE subscription_reconcile_jobs SET next_attempt_at=? WHERE app_user_id=? AND generation=?',
                             (current + 60_000, job['app_user_id'], job['generation']))
    return refreshed
