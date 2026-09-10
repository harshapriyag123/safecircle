from fastapi import Request
from fastapi.responses import JSONResponse, RedirectResponse

from . import db
from .main import app, guardian_token_or_401, public_snapshot


@app.middleware("http")
async def lifecycle_consistency_guard(request: Request, call_next):
    """Protect lifecycle invariants even when an older client sends stale state."""
    path = request.url.path

    if request.method == "POST" and path.startswith("/v1/sessions/") and path.endswith("/check-in"):
        session_id = path.removeprefix("/v1/sessions/").removesuffix("/check-in").strip("/")
        session = db.get_session(session_id)
        if session is not None and bool(session.get("resolved")):
            return JSONResponse(
                status_code=409,
                content={"detail": "Resolved Safety Sessions are read-only"},
            )

    if request.method == "GET" and path.startswith("/v1/public/guardian/"):
        token = path.removeprefix("/v1/public/guardian/")
        try:
            payload = guardian_token_or_401(token)
            session = db.get_session(payload["session_id"])
            if session is None:
                return JSONResponse(status_code=404, content={"detail": "Session not found"})
            snapshot = public_snapshot(session, payload.get("role", "guardian"))
            stage = snapshot.get("escalation_stage_minutes")
            if snapshot.get("resolved"):
                snapshot["state"] = "RESOLVED"
            elif stage is not None and int(stage) >= 15:
                snapshot["state"] = "ESCALATED"
            elif stage is not None and int(stage) >= 5:
                snapshot["state"] = "CONCERN"
            elif stage is not None and int(stage) >= 0:
                snapshot["state"] = "CHECK_IN"
            return JSONResponse(content=snapshot)
        except Exception as exc:
            status_code = getattr(exc, "status_code", 401)
            detail = getattr(exc, "detail", str(exc) or "Guardian link unavailable")
            return JSONResponse(status_code=status_code, content={"detail": detail})

    return await call_next(request)


@app.get("/", include_in_schema=False)
def root() -> RedirectResponse:
    """Send the public Railway domain to the functional SafeCircle command center."""
    return RedirectResponse(url="/app/v3.html", status_code=307)
