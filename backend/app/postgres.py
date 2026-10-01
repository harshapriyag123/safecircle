"""Postgres storage preserving the repository's small SQLite query interface.

Every transaction takes the same database advisory lock. This deliberately
serializes short storage operations across serverless instances, including
delivery claims and account deletion. No provider network calls hold the lock.
Use a dedicated database/schema and a pooled connection URL for deployment.
"""
import re
import sqlite3

import psycopg
from psycopg.rows import dict_row


class Row(dict):
    def __getitem__(self, key):
        return list(self.values())[key] if isinstance(key, int) else super().__getitem__(key)


class Cursor:
    def __init__(self, cursor):
        self.cursor = cursor

    @property
    def rowcount(self):
        return self.cursor.rowcount

    def fetchone(self):
        value = self.cursor.fetchone()
        return Row(value) if value is not None else None

    def fetchall(self):
        return [Row(value) for value in self.cursor.fetchall()]

    def __iter__(self):
        return iter(self.fetchall())


class Connection:
    def __init__(self, connection):
        self.connection = connection

    def execute(self, query, params=()):
        # These are application-owned SQL statements, never user-supplied SQL.
        if query.strip().upper() == 'BEGIN IMMEDIATE':
            return Cursor(self.connection.execute('SELECT 1'))
        pragma = re.fullmatch(r'PRAGMA table_info\((\w+)\)', query.strip())
        if pragma:
            return Cursor(self.connection.execute(
                'SELECT column_name AS name FROM information_schema.columns '
                'WHERE table_schema=current_schema() AND table_name=%s', (pragma[1],)))
        query = query.replace('INTEGER PRIMARY KEY AUTOINCREMENT', 'BIGSERIAL PRIMARY KEY')
        query = re.sub(r'\bINTEGER\b', 'BIGINT', query)
        query = query.replace("json_extract(events.payload_json, '$.client_event_id')",
                              "(events.payload_json::jsonb ->> 'client_event_id')")
        ignore = 'INSERT OR IGNORE' in query
        query = query.replace('INSERT OR IGNORE', 'INSERT')
        if ignore:
            query = query.rstrip().rstrip(';') + ' ON CONFLICT DO NOTHING'
        query = query.replace('?', '%s')
        try:
            return Cursor(self.connection.execute(query, params))
        except psycopg.IntegrityError as exc:
            # Existing business logic handles deduplication/unique email this way.
            # The owning context manager rolls the failed transaction back.
            raise sqlite3.IntegrityError('Database constraint violation') from exc

    def executescript(self, script):
        for statement in script.split(';'):
            if statement.strip():
                self.execute(statement)


def open_connection(url, schema='public'):
    if not re.fullmatch(r'[a-z_][a-z0-9_]*', schema):
        raise RuntimeError('Invalid SAFECIRCLE_DB_SCHEMA')
    conn = psycopg.connect(url, row_factory=dict_row, connect_timeout=10,
                           options='-c statement_timeout=15000 -c lock_timeout=10000')
    try:
        from psycopg import sql
        conn.execute(sql.SQL('SET LOCAL search_path TO {}').format(sql.Identifier(schema)))
        conn.execute('SELECT pg_advisory_xact_lock(1979042701)')
        return conn
    except Exception:
        conn.close()
        raise
