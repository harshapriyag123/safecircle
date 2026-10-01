import json
import os
import sqlite3
import threading
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

from .server_crypto import decrypt_json, encrypt_json

DB_PATH = Path(os.getenv("SAFECIRCLE_DB_PATH", "/tmp/safecircle.db"))
_lock = threading.Lock()


@contextmanager
def connect() -> Iterator[sqlite3.Connection]:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    try:
        yield conn
        conn.commit()
    finally:
        conn.close()


def init_db() -> None:
    with _lock, connect() as conn:
        conn.executescript(
            """
            CREATE TABLE IF NOT EXISTS sessions (
                id TEXT PRIMARY KEY,
                owner_id TEXT NOT NULL,
                mode TEXT NOT NULL,
                destination TEXT,
                started_at INTEGER NOT NULL,
                expected_end_at INTEGER NOT NULL,
                last_check_in_at INTEGER NOT NULL,
                state TEXT NOT NULL,
                battery_percent INTEGER,
                latitude REAL,
                longitude REAL,
                location_accuracy REAL,
                privacy_mode TEXT NOT NULL,
                capsule_json TEXT,
                resolved INTEGER NOT NULL DEFAULT 0,
                resolved_at INTEGER,
                updated_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id TEXT,
                event_type TEXT NOT NULL,
                stage_minutes INTEGER,
                payload_json TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                UNIQUE(session_id, event_type, stage_minutes)
            );

            CREATE TABLE IF NOT EXISTS delivery_jobs (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                deadline INTEGER NOT NULL,
                stage INTEGER NOT NULL,
                role TEXT NOT NULL,
                channel TEXT NOT NULL,
                payload_json TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'queued',
                attempts INTEGER NOT NULL DEFAULT 0,
                next_attempt_at INTEGER NOT NULL,
                lease_until INTEGER,
                provider_message_id TEXT,
                error TEXT,
                updated_at INTEGER NOT NULL,
                UNIQUE(session_id,deadline,stage,role,channel)
            );
            CREATE INDEX IF NOT EXISTS delivery_jobs_due ON delivery_jobs(status,next_attempt_at);
            CREATE TABLE IF NOT EXISTS subscription_reconcile_jobs (
                app_user_id TEXT PRIMARY KEY, generation INTEGER NOT NULL, next_attempt_at INTEGER NOT NULL
            );
            CREATE TABLE IF NOT EXISTS revenuecat_receipts (
                event_id TEXT PRIMARY KEY,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS client_event_receipts (
                owner_id TEXT NOT NULL,
                client_event_id TEXT NOT NULL,
                PRIMARY KEY(owner_id, client_event_id)
            );

            CREATE TABLE IF NOT EXISTS guardian_invites (
                id TEXT PRIMARY KEY,
                session_id TEXT NOT NULL,
                owner_id TEXT NOT NULL,
                role TEXT NOT NULL,
                token_hash TEXT NOT NULL UNIQUE,
                expires_at INTEGER NOT NULL,
                revoked INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS users (
                id TEXT PRIMARY KEY,
                email TEXT NOT NULL UNIQUE,
                password_hash TEXT NOT NULL,
                created_at INTEGER NOT NULL
            );

            CREATE TABLE IF NOT EXISTS subscriptions (
                app_user_id TEXT PRIMARY KEY,
                entitlement_id TEXT NOT NULL,
                is_active INTEGER NOT NULL,
                product_id TEXT,
                expiration_at_ms INTEGER,
                updated_at INTEGER NOT NULL
            );
            """
        )

        session_columns = {row["name"] for row in conn.execute("PRAGMA table_info(sessions)")}
        if "contacts_json" not in session_columns:
            conn.execute("ALTER TABLE sessions ADD COLUMN contacts_json TEXT")
        columns = {row["name"] for row in conn.execute("PRAGMA table_info(subscriptions)")}
        if "event_timestamp_ms" not in columns:
            conn.execute("ALTER TABLE subscriptions ADD COLUMN event_timestamp_ms INTEGER NOT NULL DEFAULT 0")
        if "last_event_id" not in columns:
            conn.execute("ALTER TABLE subscriptions ADD COLUMN last_event_id TEXT")


def upsert_session(data: dict[str, Any]) -> None:
    now = int(time.time() * 1000)
    capsule = data.get('capsule')
    if isinstance(capsule, dict):
        capsule = dict(capsule)
        limit = now + 24 * 60 * 60_000
        expiry = capsule.get('expiresAt')
        capsule['expiresAt'] = min(expiry, limit) if isinstance(expiry, (int, float)) else limit
    with _lock, connect() as conn:
        conn.execute(
            """
            INSERT INTO sessions(
                id, owner_id, mode, destination, started_at, expected_end_at,
                last_check_in_at, state, battery_percent, latitude, longitude,
                location_accuracy, privacy_mode, capsule_json, resolved, resolved_at, updated_at
            ) VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            ON CONFLICT(id) DO UPDATE SET
                destination=excluded.destination,
                expected_end_at=excluded.expected_end_at,
                last_check_in_at=excluded.last_check_in_at,
                state=excluded.state,
                battery_percent=excluded.battery_percent,
                latitude=excluded.latitude,
                longitude=excluded.longitude,
                location_accuracy=excluded.location_accuracy,
                privacy_mode=excluded.privacy_mode,
                capsule_json=excluded.capsule_json,
                resolved=excluded.resolved,
                resolved_at=excluded.resolved_at,
                updated_at=excluded.updated_at
            """,
            (
                data["id"],
                data["owner_id"],
                data["mode"],
                data.get("destination"),
                data["started_at"],
                data["expected_end_at"],
                data["last_check_in_at"],
                data.get("state", "NORMAL"),
                data.get("battery_percent"),
                data.get("latitude"),
                data.get("longitude"),
                data.get("location_accuracy"),
                data.get("privacy_mode", "PRECISE_ON_ESCALATION"),
                encrypt_json(capsule),
                1 if data.get("resolved") else 0,
                data.get("resolved_at"),
                now,
            ),
        )
        if "guardian_contacts" in data:
            conn.execute("UPDATE sessions SET contacts_json=? WHERE id=?",
                         (encrypt_json({"contacts": data["guardian_contacts"]}), data["id"]))
        if 'guardian_contacts' in data:
            contacts = {item['role']: item for item in data['guardian_contacts'] if item.get('consented')}
            jobs = conn.execute("SELECT id,role FROM delivery_jobs WHERE session_id=? AND channel='twilio_sms' AND status='queued'", (data['id'],)).fetchall()
            for job in jobs:
                contact = contacts.get(job['role'])
                if contact:
                    conn.execute('UPDATE delivery_jobs SET payload_json=? WHERE id=?', (encrypt_json({'phone': contact['phone']}), job['id']))
                else:
                    conn.execute("UPDATE delivery_jobs SET status='cancelled',updated_at=? WHERE id=?", (now, job['id']))
        conn.execute("UPDATE delivery_jobs SET status='cancelled',updated_at=? WHERE session_id=? "
                     "AND status IN ('queued','sending') AND (?=1 OR deadline!=?)",
                     (now, data["id"], int(bool(data.get("resolved"))), data["expected_end_at"]))


def get_session(session_id: str) -> dict[str, Any] | None:
    with connect() as conn:
        row = conn.execute("SELECT * FROM sessions WHERE id=?", (session_id,)).fetchone()
    if row is None:
        return None
    result = dict(row)
    result["resolved"] = bool(result["resolved"])
    result["capsule"] = decrypt_json(result.pop("capsule_json", None))
    result["guardian_contacts"] = (decrypt_json(result.pop("contacts_json", None)) or {}).get("contacts", [])
    return result


def active_sessions() -> list[dict[str, Any]]:
    with connect() as conn:
        rows = conn.execute(
            "SELECT * FROM sessions WHERE resolved=0 ORDER BY expected_end_at ASC"
        ).fetchall()
    result = []
    for row in rows:
        item = dict(row)
        item["capsule"] = decrypt_json(item.pop("capsule_json", None))
        item["guardian_contacts"] = (decrypt_json(item.pop("contacts_json", None)) or {}).get("contacts", [])
        result.append(item)
    return result


def append_client_event(owner_id: str, event: dict[str, Any]) -> None:
    """Persist a retry receipt and its event in the same transaction."""
    with _lock, connect() as conn:
        receipt = conn.execute(
            "INSERT OR IGNORE INTO client_event_receipts(owner_id,client_event_id) VALUES(?,?)",
            (owner_id, event["id"]),
        )
        if receipt.rowcount == 0:
            return
        conn.execute(
            "INSERT INTO events(session_id,event_type,payload_json,created_at) VALUES(?,?,?,?)",
            (
                event["session_id"],
                "CLIENT_" + event["event_type"],
                json.dumps({
                    "client_event_id": event["id"],
                    "payload": event["payload"],
                    "client_created_at": event["created_at"],
                }),
                int(time.time() * 1000),
            ),
        )


def append_event(
    session_id: str | None,
    event_type: str,
    payload: dict[str, Any],
    stage_minutes: int | None = None,
) -> bool:
    try:
        with _lock, connect() as conn:
            conn.execute(
                """
                INSERT INTO events(session_id,event_type,stage_minutes,payload_json,created_at)
                VALUES(?,?,?,?,?)
                """,
                (
                    session_id,
                    event_type,
                    stage_minutes,
                    json.dumps(payload),
                    int(time.time() * 1000),
                ),
            )
        return True
    except sqlite3.IntegrityError:
        return False


def list_events(session_id: str, limit: int = 100) -> list[dict[str, Any]]:
    with connect() as conn:
        rows = conn.execute(
            """
            SELECT * FROM events WHERE session_id=?
            ORDER BY created_at DESC, id DESC LIMIT ?
            """,
            (session_id, max(1, min(limit, 500))),
        ).fetchall()
    result = []
    for row in rows:
        item = dict(row)
        item["payload"] = json.loads(item.pop("payload_json"))
        result.append(item)
    return result


def save_invite(
    invite_id: str,
    session_id: str,
    owner_id: str,
    role: str,
    token_hash: str,
    expires_at: int,
) -> None:
    with _lock, connect() as conn:
        conn.execute(
            """
            INSERT INTO guardian_invites(
                id,session_id,owner_id,role,token_hash,expires_at,created_at
            ) VALUES(?,?,?,?,?,?,?)
            """,
            (
                invite_id,
                session_id,
                owner_id,
                role,
                token_hash,
                expires_at,
                int(time.time() * 1000),
            ),
        )


def get_invite_by_hash(token_hash: str) -> dict[str, Any] | None:
    with connect() as conn:
        row = conn.execute(
            "SELECT * FROM guardian_invites WHERE token_hash=?",
            (token_hash,),
        ).fetchone()
    return dict(row) if row else None


def revoke_invite(invite_id: str, owner_id: str) -> bool:
    with _lock, connect() as conn:
        cur = conn.execute(
            "UPDATE guardian_invites SET revoked=1 WHERE id=? AND owner_id=?",
            (invite_id, owner_id),
        )
    return cur.rowcount > 0


def upsert_subscription(
    app_user_id: str,
    entitlement_id: str,
    is_active: bool,
    product_id: str | None,
    expiration_at_ms: int | None,
    event_timestamp_ms: int = 0,
    event_id: str | None = None,
) -> None:
    with _lock, connect() as conn:
        conn.execute(
            """
            INSERT INTO subscriptions(
                app_user_id,entitlement_id,is_active,product_id,expiration_at_ms,updated_at,event_timestamp_ms,last_event_id
            ) VALUES(?,?,?,?,?,?,?,?)
            ON CONFLICT(app_user_id) DO UPDATE SET
                entitlement_id=excluded.entitlement_id,
                is_active=excluded.is_active,
                product_id=excluded.product_id,
                expiration_at_ms=excluded.expiration_at_ms,
                updated_at=excluded.updated_at,
                event_timestamp_ms=excluded.event_timestamp_ms,
                last_event_id=excluded.last_event_id
            WHERE excluded.event_timestamp_ms >= subscriptions.event_timestamp_ms
                AND (excluded.last_event_id IS NULL OR subscriptions.last_event_id IS NULL
                     OR excluded.last_event_id != subscriptions.last_event_id)
            """,
            (
                app_user_id,
                entitlement_id,
                1 if is_active else 0,
                product_id,
                expiration_at_ms,
                int(time.time() * 1000),
                event_timestamp_ms,
                event_id,
            ),
        )


def get_subscription(app_user_id: str) -> dict[str, Any] | None:
    with connect() as conn:
        row = conn.execute(
            "SELECT * FROM subscriptions WHERE app_user_id=?",
            (app_user_id,),
        ).fetchone()
    if row is None:
        return None
    item = dict(row)
    item["is_active"] = bool(item["is_active"])
    return item


def create_user(user_id: str, email: str, password_hash: str) -> bool:
    try:
        with _lock, connect() as conn:
            conn.execute(
                "INSERT INTO users(id,email,password_hash,created_at) VALUES(?,?,?,?)",
                (user_id, email.lower().strip(), password_hash, int(time.time() * 1000)),
            )
        return True
    except sqlite3.IntegrityError:
        return False


def get_user_by_email(email: str) -> dict[str, Any] | None:
    with connect() as conn:
        row = conn.execute(
            "SELECT * FROM users WHERE email=?",
            (email.lower().strip(),),
        ).fetchone()
    return dict(row) if row else None


def get_user(user_id: str) -> dict[str, Any] | None:
    with connect() as conn:
        row = conn.execute("SELECT * FROM users WHERE id=?", (user_id,)).fetchone()
    return dict(row) if row else None


def enqueue_delivery(session: dict[str, Any], stage: int, role: str, channel: str,
                     payload: dict[str, Any], current: int) -> str:
    import hashlib
    identity = f"{session['id']}:{session['expected_end_at']}:{stage}:{role}:{channel}"
    job_id = hashlib.sha256(identity.encode()).hexdigest()
    with _lock, connect() as conn:
        conn.execute("INSERT OR IGNORE INTO delivery_jobs(id,session_id,deadline,stage,role,channel,"
                     "payload_json,next_attempt_at,updated_at) VALUES(?,?,?,?,?,?,?,?,?)",
                     (job_id, session['id'], session['expected_end_at'], stage, role, channel,
                      encrypt_json(payload), current, current))
    return job_id


def claim_delivery(current: int) -> dict[str, Any] | None:
    with _lock, connect() as conn:
        conn.execute("BEGIN IMMEDIATE")
        row = conn.execute("SELECT j.* FROM delivery_jobs j JOIN sessions s ON s.id=j.session_id "
                           "WHERE s.resolved=0 AND s.expected_end_at=j.deadline AND "
                           "((j.status='queued' AND j.next_attempt_at<=?) OR "
                           "(j.status='sending' AND j.lease_until<=?)) "
                           "ORDER BY j.next_attempt_at LIMIT 1", (current, current)).fetchone()
        if row is None:
            return None
        job = dict(row)
        conn.execute("UPDATE delivery_jobs SET status='sending',lease_until=?,updated_at=? WHERE id=?",
                     (current + 60_000, current, job['id']))
    job['payload'] = decrypt_json(job.pop('payload_json'))
    return job


def finish_delivery(job_id: str, status: str, current: int, *, error: str | None = None,
                    provider_message_id: str | None = None, next_attempt_at: int | None = None,
                    attempted: bool = True) -> None:
    with _lock, connect() as conn:
        conn.execute("UPDATE delivery_jobs SET status=?,error=?,provider_message_id=?,"
                     "next_attempt_at=?,lease_until=NULL,attempts=attempts+?,updated_at=? "
                     "WHERE id=? AND status='sending'",
                     (status, error, provider_message_id, next_attempt_at or current,
                      int(attempted), current, job_id))


def mark_delivery_receipt(job_id: str, delivered: bool, current: int) -> bool:
    with _lock, connect() as conn:
        cur = conn.execute("UPDATE delivery_jobs SET status=?,updated_at=? WHERE id=? AND status IN ('accepted','sending')",
                           ('delivered' if delivered else 'failed', current, job_id))
    return cur.rowcount > 0


def delivery_status(session_id: str) -> list[dict[str, Any]]:
    with connect() as conn:
        return [dict(row) for row in conn.execute(
            "SELECT id,stage,role,channel,status,attempts,provider_message_id,error,updated_at "
            "FROM delivery_jobs WHERE session_id=? ORDER BY deadline,stage,role", (session_id,))]


def purge_expired_capsules(current: int) -> int:
    count = 0
    with _lock, connect() as conn:
        rows = conn.execute("SELECT id,capsule_json FROM sessions WHERE capsule_json IS NOT NULL").fetchall()
        for row in rows:
            capsule = decrypt_json(row['capsule_json']) or {}
            expiry = capsule.get('expiresAt')
            if isinstance(expiry, (int, float)) and expiry <= current:
                conn.execute("UPDATE sessions SET capsule_json=NULL WHERE id=?", (row['id'],))
                count += 1
        # Payloads include contacts. Erase them from completed/obsolete jobs after 24h.
        conn.execute("UPDATE delivery_jobs SET payload_json=? WHERE status IN "
                     "('delivered','failed','cancelled','accepted') AND updated_at<?",
                     (encrypt_json({}), current - 86_400_000))
    return count


def get_delivery(job_id: str) -> dict[str, Any] | None:
    with connect() as conn:
        row = conn.execute("SELECT id,channel,status,provider_message_id FROM delivery_jobs WHERE id=?", (job_id,)).fetchone()
    return dict(row) if row else None
