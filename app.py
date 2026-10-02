"""Vercel entrypoint: full web clients plus the existing SafeCircle backend.

Without configured persistent storage and secrets, keep public pages available
but refuse every backend request. Never create an ephemeral safety database.
"""
import asyncio
import os
import logging
import time
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse, RedirectResponse

from backend.app.config import validate_production_config
from backend.app import db
from backend.app.entrypoint import app as backend

os.environ.setdefault('SAFECIRCLE_ENV', 'production')
os.environ.setdefault('SAFECIRCLE_WORKER_MODE', 'external')


@asynccontextmanager
async def lifespan(application):
    application.state.backend_ready = False
    phase = 'configuration'
    try:
        if not os.getenv('DATABASE_URL'):
            raise RuntimeError('Persistent database not configured')
        validate_production_config()
        phase = 'database'
        db.init_db()
        application.state.backend_ready = True
    except Exception as exc:
        logging.getLogger('safecircle').error('Backend initialization failed at %s (%s)', phase, type(exc).__name__)
        # Don't disclose connection strings, credentials, or database errors.
        application.state.backend_ready = False
    yield


app = FastAPI(lifespan=lifespan, docs_url=None, redoc_url=None, openapi_url=None)


async def worker_current():
    try:
        return await asyncio.to_thread(db.worker_is_current, time.time_ns() // 1_000_000)
    except Exception:
        return False


@app.middleware('http')
async def readiness_guard(request: Request, call_next):
    path = request.url.path
    ready = getattr(app.state, 'backend_ready', False)
    if path == '/health':
        worker_ready = ready and await worker_current()
        return JSONResponse({'status': 'ok' if worker_ready else 'not_ready',
                             'backend_ready': ready, 'worker_ready': bool(worker_ready)},
                            status_code=200 if worker_ready else 503,
                            headers={'Cache-Control': 'no-store'})
    if path.startswith(('/v1/', '/internal/')):
        if not ready:
            return JSONResponse({'detail': 'Backend configuration pending: persistent database and production secrets'},
                                status_code=503, headers={'Cache-Control': 'no-store'})
        if request.method == 'POST' and path == '/v1/sessions' and not await worker_current():
            return JSONResponse({'detail': 'Alert worker unavailable; a monitored session cannot start'}, status_code=503)
    return await call_next(request)


@app.post('/internal/worker/tick')
async def external_tick(request: Request):
    import hmac
    secret = os.getenv('SAFECIRCLE_WORKER_SECRET', '')
    if len(secret) < 32 or not hmac.compare_digest(request.headers.get('Authorization', ''), 'Bearer ' + secret):
        return JSONResponse({'detail': 'Unauthorized'}, status_code=401)
    from backend.app.main import worker_tick
    await worker_tick()
    return {'status': 'ok'}


@app.get('/app/')
@app.get('/app/index.html')
@app.get('/app/v2.html')
@app.get('/app/v3.html')
def canonical_companion():
    return RedirectResponse('/app/v4.html', status_code=307)


app.mount('/', backend)
