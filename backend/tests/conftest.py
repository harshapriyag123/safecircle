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
    db.init_db()
