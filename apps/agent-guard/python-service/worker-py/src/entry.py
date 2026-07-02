"""Akto Agent Guard — Cloudflare Python Worker entrypoint."""

import json
import logging
from urllib.parse import urlparse

logging.basicConfig(level=logging.INFO)

from pyodide.ffi import create_proxy
from workers import Response, waitUntil

from scan_handler import scan_payload, scanners_metadata
from settings import settings

logger = logging.getLogger(__name__)


def _json(obj, status: int = 200) -> Response:
    return Response(json.dumps(obj), status=status, headers={"content-type": "application/json"})


def _schedule(coro) -> None:
    """Fire-and-forget a coroutine that outlives the response.

    waitUntil (from `cloudflare:workers`) is the raw JS function and needs a
    JS-thenable, so we wrap the coroutine in a create_proxy PyProxy (awaitable
    in JS, and not auto-destroyed at the end of this call). The proxy destroys
    itself once the coroutine settles. waitUntil keeps the isolate alive until
    then (up to 30s after the response).
    """
    async def _run_and_cleanup(c, proxy_box):
        try:
            await c
        finally:
            proxy_box[0].destroy()

    box = [None]
    box[0] = create_proxy(_run_and_cleanup(coro, box))
    try:
        waitUntil(box[0])
    except Exception as exc:  # pragma: no cover - runtime-specific
        logger.warning(f"[alerts] waitUntil unavailable: {exc}")


async def on_fetch(request, env, ctx):
    settings.init(env)
    path = urlparse(request.url).path

    if path == "/health":
        return _json({"status": "healthy", "service": "agent-guard-worker"})

    if path == "/scanners":
        return _json(scanners_metadata())

    if path == "/scan" and request.method == "POST":
        payload = json.loads(await request.text())
        return _json(await scan_payload(payload, env=env, schedule_fn=_schedule))

    if path == "/scan/batch" and request.method == "POST":
        payloads = json.loads(await request.text())
        results = [await scan_payload(p, env=env, schedule_fn=_schedule) for p in payloads]
        return _json(results)

    return _json({"error": "not found", "path": path}, status=404)
