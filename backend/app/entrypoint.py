from fastapi.responses import RedirectResponse

from .main import app


@app.get("/", include_in_schema=False)
def root() -> RedirectResponse:
    """Send the public Railway domain to the canonical-state SafeCircle product demo."""
    return RedirectResponse(url="/app/v2.html", status_code=307)
