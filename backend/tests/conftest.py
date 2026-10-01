import os
# All test modules import application modules after these deterministic test settings.
os.environ.setdefault('SAFECIRCLE_API_SECRET', 'test-owner-secret')
os.environ.setdefault('GUARDIAN_SIGNING_SECRET', 'test-guardian-secret')
os.environ.setdefault('REVENUECAT_WEBHOOK_SECRET', 'test-revenuecat-secret')
os.environ.setdefault('SAFECIRCLE_ALLOW_DEMO_TOKEN', 'true')

import pytest

@pytest.fixture(autouse=True)
def fresh_database(tmp_path, monkeypatch):
    from app import db
    monkeypatch.setattr(db, 'DB_PATH', tmp_path / 'test.db')
    test_url = os.getenv('SAFECIRCLE_TEST_DATABASE_URL')
    if test_url:
        import uuid
        import psycopg
        from psycopg import sql
        schema = 'sc_test_' + uuid.uuid4().hex
        with psycopg.connect(test_url, autocommit=True) as conn:
            conn.execute(sql.SQL('CREATE SCHEMA {}').format(sql.Identifier(schema)))
        monkeypatch.setenv('DATABASE_URL', test_url)
        monkeypatch.setenv('SAFECIRCLE_DB_SCHEMA', schema)
        try:
            db.init_db()
            yield
        finally:
            with psycopg.connect(test_url, autocommit=True) as conn:
                conn.execute(sql.SQL('DROP SCHEMA {} CASCADE').format(sql.Identifier(schema)))
        return
    db.init_db()
    yield
