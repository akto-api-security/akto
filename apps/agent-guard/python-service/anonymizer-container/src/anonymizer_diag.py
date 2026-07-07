"""Diagnostics for the anonymizer service (load-test / latency verification).

Set ANONYMIZER_DIAGNOSTICS=true for every /anonymize timing line.
Set ANONYMIZER_LOG_LEVEL=info (or LOG_LEVEL) so application loggers reach docker logs.

Slow requests (>= ANONYMIZER_SLOW_MS, default 200) are always logged at INFO.
In-flight enter/exit is logged when diagnostics are on or inflight >= 4.
"""

from __future__ import annotations

import logging
import os
import sys
import threading
from typing import Optional

logger = logging.getLogger(__name__)

_LEVELS = {
    "DEBUG": logging.DEBUG,
    "INFO": logging.INFO,
    "WARNING": logging.WARNING,
    "ERROR": logging.ERROR,
}

_inflight = 0
_inflight_lock = threading.Lock()


def resolve_log_level() -> int:
    raw = (
        os.environ.get("ANONYMIZER_LOG_LEVEL")
        or os.environ.get("LOG_LEVEL")
        or "INFO"
    ).strip().upper()
    return _LEVELS.get(raw, logging.INFO)


def configure_process_logging() -> None:
    level = resolve_log_level()
    logging.basicConfig(
        level=level,
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
        stream=sys.stdout,
        force=True,
    )


def diagnostics_enabled() -> bool:
    return os.environ.get("ANONYMIZER_DIAGNOSTICS", "").strip().lower() in (
        "1",
        "true",
        "yes",
        "on",
    )


def slow_threshold_ms() -> float:
    raw = os.environ.get("ANONYMIZER_SLOW_MS", "").strip()
    try:
        return float(raw) if raw else 200.0
    except ValueError:
        return 200.0


def inflight_count() -> int:
    with _inflight_lock:
        return _inflight


def track_inflight(*, entering: bool) -> int:
    global _inflight
    with _inflight_lock:
        if entering:
            _inflight += 1
        else:
            _inflight = max(0, _inflight - 1)
        count = _inflight
    log_inflight(count, entering=entering)
    return count


def log_startup_banner(*, spacy_model: str) -> None:
    logger.info(
        "[anon-diag] worker_ready pid=%s spacy_model=%s diagnostics=%s "
        "log_level=%s slow_ms=%s",
        os.getpid(),
        spacy_model,
        diagnostics_enabled(),
        logging.getLevelName(resolve_log_level()),
        slow_threshold_ms(),
    )


def log_inflight(inflight: int, *, entering: bool) -> None:
    if not diagnostics_enabled() and inflight < 4:
        return
    action = "enter" if entering else "exit"
    logger.info(
        "[anon-diag] inflight=%s action=%s pid=%s",
        inflight,
        action,
        os.getpid(),
    )


def log_anonymize_complete(
    *,
    text_len: int,
    entity_count: int,
    analyze_ms: float,
    anonymize_ms: float,
    total_ms: float,
    client: Optional[str] = None,
) -> None:
    slow = total_ms >= slow_threshold_ms()
    if not diagnostics_enabled() and not slow:
        return
    parts = [
        f"text_len={text_len}",
        f"entities={entity_count}",
        f"analyze_ms={round(analyze_ms, 2)}",
        f"anonymize_ms={round(anonymize_ms, 2)}",
        f"total_ms={round(total_ms, 2)}",
        f"inflight={inflight_count()}",
        f"pid={os.getpid()}",
    ]
    if client:
        parts.append(f"client={client}")
    tag = "slow" if slow else "complete"
    logger.info("[anon-diag] %s %s", tag, " ".join(parts))
