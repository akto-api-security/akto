"""Opt-in scan diagnostics for load-test and backpressure verification.

Set AGW_SCAN_DIAGNOSTICS=true to log every /scan outcome and in-flight concurrency.
Cascade backpressure skips are always logged at INFO (low volume when tripped).
"""

from __future__ import annotations

import logging
import os
from typing import Any, Dict, Optional

import cascade_backpressure

logger = logging.getLogger(__name__)


def enabled() -> bool:
    return os.environ.get("AGW_SCAN_DIAGNOSTICS", "").strip().lower() in ("1", "true", "yes", "on")


def log_backpressure_skip(scanner_name: str) -> None:
    snap = cascade_backpressure.status_snapshot()
    logger.info(
        "[scan-diag] path=cascade_backpressure_skip scanner=%s pid=%s "
        "recent_avg_ms=%s sample_count=%s threshold_ms=%s",
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
    extra: Optional[Dict[str, Any]] = None,
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
