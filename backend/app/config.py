import os
from pathlib import Path


def validate_production_config() -> None:
    if os.getenv('SAFECIRCLE_ENV', 'development') != 'production':
        return
    required = ('SAFECIRCLE_API_SECRET', 'SAFECIRCLE_ACCESS_TOKEN_SECRET', 'GUARDIAN_SIGNING_SECRET',
                'REVENUECAT_WEBHOOK_SECRET', 'SAFECIRCLE_DATA_ENCRYPTION_KEY')
    missing = [key for key in required if not os.getenv(key) or 'change-me' in os.getenv(key, '')
               or 'replace-with' in os.getenv(key, '')]
    if missing:
        raise RuntimeError('Production requires independent configured secrets: ' + ', '.join(missing))
    if any(len(os.environ[key]) < 32 for key in required):
        raise RuntimeError("Production secrets must have at least 32 characters")
    if os.getenv("SAFECIRCLE_PUSH_WEBHOOK_URL") and (not os.getenv("SAFECIRCLE_PUSH_WEBHOOK_URL", "").startswith("https://") or len(os.getenv("SAFECIRCLE_DELIVERY_RECEIPT_SECRET", "")) < 32):
        raise RuntimeError("Push requires HTTPS and an independent receipt secret")
    if "*" in os.getenv("SAFECIRCLE_ALLOWED_ORIGINS", ""):
        raise RuntimeError("Production CORS origins must be explicit")
    secrets = [os.environ[key] for key in required]
    if os.getenv('SAFECIRCLE_WORKER_MODE') == 'external' or os.getenv('VERCEL'):
        worker_secret = os.getenv('SAFECIRCLE_WORKER_SECRET', '')
        if len(worker_secret) < 32:
            raise RuntimeError('External worker requires independent SAFECIRCLE_WORKER_SECRET')
        secrets.append(worker_secret)
    if os.getenv("SAFECIRCLE_PUSH_WEBHOOK_URL"):
        secrets.append(os.environ["SAFECIRCLE_DELIVERY_RECEIPT_SECRET"])
    if len(set(secrets)) != len(secrets):
        raise RuntimeError('Production secrets must be independent')
    if os.getenv('SAFECIRCLE_ALLOW_DEMO_TOKEN', 'false').lower() == 'true':
        raise RuntimeError('Shared demo access must be disabled in production')
    database_url = os.getenv('DATABASE_URL', '').strip()
    if database_url:
        from urllib.parse import urlparse, parse_qs
        parsed = urlparse(database_url)
        if parsed.scheme not in {'postgres', 'postgresql'} or not parsed.hostname:
            raise RuntimeError('DATABASE_URL must be a PostgreSQL connection URL')
        if parse_qs(parsed.query).get('sslmode', [''])[0] not in {'require', 'verify-ca', 'verify-full'}:
            raise RuntimeError('Production PostgreSQL requires TLS via sslmode')
    else:
        if os.getenv('VERCEL'):
            raise RuntimeError('Vercel requires persistent PostgreSQL DATABASE_URL; SQLite is unsupported')
        path = Path(os.getenv('SAFECIRCLE_DB_PATH', '/tmp/safecircle.db'))
        if not path.is_absolute() or str(path).startswith('/tmp/'):
            raise RuntimeError('Set SAFECIRCLE_DB_PATH to an absolute path on a persistent mounted volume')
    from .server_crypto import _key
    _key()
    if not os.getenv('SAFECIRCLE_PUBLIC_BASE_URL', '').startswith('https://'):
        raise RuntimeError('Production requires an HTTPS public base URL')
