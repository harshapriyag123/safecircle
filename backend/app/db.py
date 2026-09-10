import json
import os
import sqlite3
import threading
import time
from contextlib import contextmanager
from pathlib import Path
from typing import Any, Iterator

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


def upsert_session(data: dict[str, Any]) -> None:
    now = int(time.time() * 1000)
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
                json.dumps(data.get("capsule")) if data.get("capsule") is not None else None,
                1 if data.get("resolved") else 0,
                data.get("resolved_at"),
                now,
            ),
        )


def get_session(session_id: str) -> dict[str, Any] | None:
    with connect() as conn:
        row = conn.execute("SELECT * FROM sessions WHERE id=?", (session_id,)).fetchone()
    if row is None:
        return None
    result = dict(row)
    result["resolved"] = bool(result["resolved"])
    if result.get("capsule_json"):
        result["capsule"] = json.loads(result["capsule_json"])
    else:
        result["capsule"] = None
    return result


def active_sessions() -> list[dict[str, Any]]:
    with connect() as conn:
        rows = conn.execute(
            "SELECT * FROM sessions WHERE resolved=0 ORDER BY expected_end_at ASC"
        ).fetchall()
    return [dict(row) for row in rows]


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
            ORDER BY created_at DESC LIMIT ?
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
) -> None:
    with _lock, connect() as conn:
        conn.execute(
            """
            INSERT INTO subscriptions(
                app_user_id,entitlement_id,is_active,product_id,expiration_at_ms,updated_at
            ) VALUES(?,?,?,?,?,?)
            ON CONFLICT(app_user_id) DO UPDATE SET
                entitlement_id=excluded.entitlement_id,
                is_active=excluded.is_active,
                product_id=excluded.product_id,
                expiration_at_ms=excluded.expiration_at_ms,
                updated_at=excluded.updated_at
            """,
            (
                app_user_id,
                entitlement_id,
                1 if is_active else 0,
                product_id,
                expiration_at_ms,
                int(time.time() * 1000),
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
