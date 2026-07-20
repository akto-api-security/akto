"""Opt-in scan diagnostics for load-test and backpressure verification.

Set AGW_SCAN_DIAGNOSTICS=true to log every /scan outcome and in-flight concurrency.
Set AGW_LOG_LEVEL=info (or LOG_LEVEL) so application loggers reach docker logs.
Cascade backpressure skips are always logged at INFO (low volume when tripped).
"""

from __future__ import annotations

import contextlib
import logging
import os
import sys
import time
from collections.abc import Iterator
from typing import Any

import cascade_backpressure

logger = logging.getLogger(__name__)

_LEVELS = {
    "DEBUG": logging.DEBUG,
    "INFO": logging.INFO,
    "WARNING": logging.WARNING,
    "ERROR": logging.ERROR,
}


def resolve_log_level() -> int:
    raw = (os.environ.get("AGW_LOG_LEVEL") or os.environ.get("LOG_LEVEL") or "INFO").strip().upper()
    return _LEVELS.get(raw, logging.INFO)


def configure_process_logging() -> None:
    """Route app loggers (scan_diag, model_map, …) to stdout for docker logs.

    Uvicorn only configures its own loggers by default; without this, logger.info
    calls in application code are dropped (root stays at WARNING + LastResort).
    """
    level = resolve_log_level()
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
        stream=sys.stdout,
        force=True,
    )


def enabled() -> bool:
    return os.environ.get("AGW_SCAN_DIAGNOSTICS", "").strip().lower() in ("1", "true", "yes", "on")


def log_startup_banner() -> None:
    """Emit once per uvicorn worker so docker logs confirm env + logging."""
    bp = cascade_backpressure.status_snapshot()
    logger.info(
        "[scan-diag] worker_ready pid=%s diagnostics=%s log_level=%s "
        "backpressure_enabled=%s threshold_ms=%s min_samples=%s",
        os.getpid(),
        enabled(),
        logging.getLevelName(resolve_log_level()),
        bp.get("enabled"),
        bp.get("threshold_ms"),
        bp.get("min_samples"),
    )


def log_backpressure_skip(scanner_name: str) -> None:
    snap = cascade_backpressure.status_snapshot()
    logger.info(
        "[scan-diag] path=cascade_backpressure_skip scanner=%s pid=%s recent_avg_ms=%s sample_count=%s threshold_ms=%s",
        scanner_name,
        os.getpid(),
        snap.get("recent_avg_latency_ms"),
        snap.get("recent_sample_count"),
        snap.get("threshold_ms"),
    )


def log_backpressure_proceeding(scanner_name: str) -> None:
    if not enabled():
        return
    snap = cascade_backpressure.status_snapshot()
    logger.info(
        "[scan-diag] path=cascade_proceeding scanner=%s pid=%s "
        "recent_avg_ms=%s sample_count=%s threshold_ms=%s min_samples=%s",
        scanner_name,
        os.getpid(),
        snap.get("recent_avg_latency_ms"),
        snap.get("recent_sample_count"),
        snap.get("threshold_ms"),
        snap.get("min_samples"),
    )


def log_scan_outcome(
    path: str,
    scanner_name: str,
    elapsed_ms: float,
    *,
    extra: dict[str, Any] | None = None,
    always: bool = False,
) -> None:
    """Log a completed scan path. Skips and errors use always=True."""
    if not always and not enabled():
        return
    parts = [
        f"path={path}",
        f"scanner={scanner_name}",
        f"pid={os.getpid()}",
        f"elapsed_ms={round(elapsed_ms, 2)}",
    ]
    if extra:
        for key, value in extra.items():
            parts.append(f"{key}={value}")
    logger.info("[scan-diag] %s", " ".join(parts))


class StageTimer:
    """Accumulate per-stage wall times (ms) for one request so the intent path can
    report where time went and which stage dominated (the bottleneck).

    Usage:
        timer = StageTimer()
        with timer.span("embed"):
            ...
        timer.record("knn", elapsed_ms)   # or record directly
    """

    __slots__ = ("stages",)

    def __init__(self) -> None:
        self.stages: dict[str, float] = {}

    def record(self, name: str, ms: float) -> None:
        self.stages[name] = self.stages.get(name, 0.0) + ms

    @contextlib.contextmanager
    def span(self, name: str) -> Iterator[None]:
        t0 = time.perf_counter()
        try:
            yield
        finally:
            self.record(name, (time.perf_counter() - t0) * 1000.0)

    def total_ms(self) -> float:
        return round(sum(self.stages.values()), 2)

    def bottleneck(self) -> str | None:
        return max(self.stages, key=self.stages.get) if self.stages else None

    def as_fields(self) -> str:
        return " ".join(f"{k}_ms={round(v, 2)}" for k, v in self.stages.items())


def log_intent_decision(
    path: str,
    agent: str,
    scanner_name: str,
    decision: str,
    timer: StageTimer | None = None,
    *,
    extra: dict[str, Any] | None = None,
    always: bool = False,
) -> None:
    """Print one intent-prefilter outcome with per-stage timings + bottleneck.

    Verbose for every request under AGW_SCAN_DIAGNOSTICS; otherwise only when
    `always=True` (non-ALLOW / escalate / error — the low-volume, interesting paths).
    Designed so a load/test script can read exactly what happened and why.
    """
    if not always and not enabled():
        return
    parts = [
        f"path=intent_{path}",
        f"agent={agent or '-'}",
        f"scanner={scanner_name}",
        f"decision={decision}",
        f"pid={os.getpid()}",
    ]
    if timer is not None:
        parts.append(f"total_ms={timer.total_ms()}")
        bn = timer.bottleneck()
        if bn:
            parts.append(f"bottleneck={bn}")
        if timer.stages:
            parts.append(timer.as_fields())
    if extra:
        for key, value in extra.items():
            parts.append(f"{key}={value}")
    logger.info("[scan-diag] %s", " ".join(parts))


def log_inflight(inflight: int, *, entering: bool) -> None:
    if not enabled() and inflight < 4:
        return
    action = "enter" if entering else "exit"
    logger.info(
        "[scan-diag] inflight=%s action=%s pid=%s",
        inflight,
        action,
        os.getpid(),
    )
