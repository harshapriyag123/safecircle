"""Persistent access revocation, owner deletion and bounded public request counters."""
import hashlib
import time
from . import db


def init_controls():
    with db.connect() as conn:
        conn.executescript('''
        CREATE TABLE IF NOT EXISTS revoked_tokens(token_hash TEXT PRIMARY KEY, expires_at INTEGER NOT NULL);
        CREATE TABLE IF NOT EXISTS deleted_accounts(user_id TEXT PRIMARY KEY);
        CREATE TABLE IF NOT EXISTS request_limits(identity TEXT, bucket INTEGER, count INTEGER NOT NULL,
                                                 PRIMARY KEY(identity,bucket));
        ''')


def denied(token: str, user_id: str) -> bool:
    with db.connect() as conn:
        return bool(conn.execute('SELECT 1 FROM deleted_accounts WHERE user_id=?', (user_id,)).fetchone()
                    or conn.execute('SELECT 1 FROM revoked_tokens WHERE token_hash=?', (hashlib.sha256(token.encode()).hexdigest(),)).fetchone())


def revoke(token: str, expiry: int):
    with db._lock, db.connect() as conn:
        conn.execute('DELETE FROM revoked_tokens WHERE expires_at<?', (int(time.time()),))
        conn.execute('INSERT OR IGNORE INTO revoked_tokens VALUES(?,?)', (hashlib.sha256(token.encode()).hexdigest(), expiry))


def delete_account(user_id: str):
    with db._lock, db.connect() as conn:
        conn.execute('BEGIN IMMEDIATE')
        sessions = 'SELECT id FROM sessions WHERE owner_id=?'
        for table in ('events', 'delivery_jobs', 'guardian_invites'):
            conn.execute(f'DELETE FROM {table} WHERE session_id IN ({sessions})', (user_id,))
        conn.execute('DELETE FROM events WHERE owner_id=?', (user_id,))
        conn.execute('DELETE FROM sessions WHERE owner_id=?', (user_id,))
        conn.execute('DELETE FROM client_event_receipts WHERE owner_id=?', (user_id,))
        conn.execute('DELETE FROM subscriptions WHERE app_user_id=?', (user_id,))
        conn.execute('DELETE FROM subscription_reconcile_jobs WHERE app_user_id=?', (user_id,))
        conn.execute('DELETE FROM users WHERE id=?', (user_id,))
        # Minimal random identifier tombstone prevents old access tokens recreating data.
        conn.execute('INSERT OR IGNORE INTO deleted_accounts VALUES(?)', (user_id,))


def allow_request(identity: str, current: int, limit: int) -> bool:
    bucket = current // 60
    digest = hashlib.sha256(identity.encode()).hexdigest()
    with db._lock, db.connect() as conn:
        conn.execute('DELETE FROM request_limits WHERE bucket<?', (bucket - 2,))
        conn.execute('INSERT INTO request_limits VALUES(?,?,1) ON CONFLICT(identity,bucket) DO UPDATE SET count=request_limits.count+1', (digest, bucket))
        count = conn.execute('SELECT count FROM request_limits WHERE identity=? AND bucket=?', (digest, bucket)).fetchone()[0]
    return count <= limit
