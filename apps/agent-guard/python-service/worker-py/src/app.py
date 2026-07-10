"""Standalone FastAPI entrypoint for Docker, Kubernetes, and VM deployments."""

import asyncio
import logging
import os
import time
from contextlib import asynccontextmanager
from typing import Any

from fastapi import FastAPI, Request
from prometheus_fastapi_instrumentator import Instrumentator
from pydantic import BaseModel, Field

import cascade_backpressure
import scan_diag
from metrics import QUEUE_SIZE
from scan_handler import scan_payload, scanners_metadata
from settings import settings

logger = logging.getLogger(__name__)

_scan_inflight = 0


class ScanRequest(BaseModel):
    scanner_name: str
    scanner_type: str = "prompt"
    text: str = ""
    config: dict[str, Any] = Field(default_factory=dict)


@asynccontextmanager
async def lifespan(_: FastAPI):
    scan_diag.configure_process_logging()
    settings.init_from_env()
    cascade_backpressure.configure_from_env()
    scan_diag.log_startup_banner()
    yield


app = FastAPI(title="Akto Agent Guard Executor", version="1.0.0", lifespan=lifespan)
Instrumentator().instrument(app).expose(app)


@app.middleware("http")
async def scan_inflight_middleware(request: Request, call_next):
    """Track concurrent /scan handlers per uvicorn worker (queue saturation signal)."""
    global _scan_inflight
    if request.url.path not in ("/scan", "/scan/batch"):
        return await call_next(request)

    wall_start = time.perf_counter()
    _scan_inflight += 1
    inflight = _scan_inflight
    QUEUE_SIZE.set(inflight)
    scan_diag.log_inflight(inflight, entering=True)
    try:
        response = await call_next(request)
        wall_ms = (time.perf_counter() - wall_start) * 1000.0
        if scan_diag.enabled() or inflight >= 6 or wall_ms >= 500:
            logger.info(
                "[scan-diag] wall_complete path=%s inflight_at_enter=%s wall_ms=%.2f pid=%s",
                request.url.path,
                inflight,
                wall_ms,
                os.getpid(),
            )
        return response
    finally:
        _scan_inflight -= 1
        QUEUE_SIZE.set(_scan_inflight)
        scan_diag.log_inflight(_scan_inflight, entering=False)


def _schedule_background(coro) -> None:
    asyncio.create_task(coro)


def _response_path(details: dict[str, Any]) -> str:
    if details.get("cascade_skipped"):
        return "cascade_backpressure_skip"
    if details.get("error"):
        return "error"
    if "scan_time_ms" in details or "execution_time_ms" in details:
        return "cascade_run"
    return "other"


@app.get("/health")
def health():
    body: dict[str, Any] = {
        "status": "healthy",
        "service": "agent-guard-executor",
        "pid": os.getpid(),
        "scan_diagnostics": scan_diag.enabled(),
        "log_level": logging.getLevelName(scan_diag.resolve_log_level()),
    }
    if scan_diag.enabled():
        body["backpressure"] = cascade_backpressure.status_snapshot()
    return body


@app.get("/scanners")
def scanners():
    return scanners_metadata()


@app.post("/scan")
async def scan(body: ScanRequest):
    started = time.perf_counter()
    result = await scan_payload(body.model_dump(), schedule_fn=_schedule_background)
    total_ms = (time.perf_counter() - started) * 1000.0
    details = result.get("details") or {}
    path = _response_path(details)
    if path == "cascade_backpressure_skip" or scan_diag.enabled() or total_ms >= 3000:
        logger.info(
            "[scan-diag] http_complete scanner=%s path=%s total_ms=%.2f pid=%s",
            body.scanner_name,
            path,
            total_ms,
            os.getpid(),
        )
    return result


@app.post("/scan/batch")
async def scan_batch(body: list[ScanRequest]):
    return [await scan_payload(item.model_dump(), schedule_fn=_schedule_background) for item in body]
