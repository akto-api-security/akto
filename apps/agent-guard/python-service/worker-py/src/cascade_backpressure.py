"""Adaptive backpressure for LLM cascade scans when Vertex latency is elevated.

Recent cascade latencies are averaged over a rolling window. When that average
meets or exceeds AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS (env override), new
cascade scans fail-open (is_valid=True) so guardrails traffic is not blocked.
"""

from __future__ import annotations

import os
import threading
from collections import deque
from dataclasses import dataclass
from typing import Deque, Dict, Optional

_DEFAULT_WINDOW = 50
_DEFAULT_MIN_SAMPLES = 5
_DEFAULT_AVG_LATENCY_MS = 8000.0
_DEFAULT_ENABLED = True

_lock = threading.Lock()
_latencies_ms: Deque[float] = deque(maxlen=_DEFAULT_WINDOW)


@dataclass(frozen=True)
class BackpressureConfig:
    enabled: bool = _DEFAULT_ENABLED
    min_samples: int = _DEFAULT_MIN_SAMPLES
    threshold_ms: float = _DEFAULT_AVG_LATENCY_MS
    window: int = _DEFAULT_WINDOW


_config = BackpressureConfig()


def _env_bool(name: str, default: bool) -> bool:
    raw = os.environ.get(name, "")
    if not raw:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


def _env_int(name: str, default: int) -> int:
    raw = os.environ.get(name, "")
    if not raw:
        return default
    try:
        return max(1, int(raw))
    except ValueError:
        return default


def _env_float(name: str, default: float) -> float:
    raw = os.environ.get(name, "")
    if not raw:
        return default
    try:
        return float(raw)
    except ValueError:
        return default


def get_config() -> BackpressureConfig:
    return _config


def configure_from_env() -> None:
    """Load threshold/window from env; resize the rolling latency window."""
    global _config, _latencies_ms
    window = _env_int("AGW_CASCADE_BACKPRESSURE_WINDOW", _DEFAULT_WINDOW)
    _config = BackpressureConfig(
        enabled=_env_bool("AGW_CASCADE_BACKPRESSURE_ENABLED", _DEFAULT_ENABLED),
        min_samples=_env_int("AGW_CASCADE_BACKPRESSURE_MIN_SAMPLES", _DEFAULT_MIN_SAMPLES),
        threshold_ms=_env_float("AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS", _DEFAULT_AVG_LATENCY_MS),
        window=window,
    )
    with _lock:
        if window != _latencies_ms.maxlen:
            _latencies_ms = deque(list(_latencies_ms)[-window:], maxlen=window)


def record_cascade_latency(elapsed_ms: float) -> None:
    if elapsed_ms <= 0:
        return
    with _lock:
        _latencies_ms.append(elapsed_ms)


def recent_avg_latency_ms() -> Optional[float]:
    """Rolling average of recorded cascade latencies, or None when empty."""
    with _lock:
        if not _latencies_ms:
            return None
        return sum(_latencies_ms) / len(_latencies_ms)


def recent_sample_count() -> int:
    with _lock:
        return len(_latencies_ms)


def should_skip_cascade() -> bool:
    """Return True when computed avg latency exceeds the env threshold."""
    cfg = _config
    if not cfg.enabled:
        return False

    with _lock:
        count = len(_latencies_ms)
        if count < cfg.min_samples:
            return False
        avg = sum(_latencies_ms) / count

    return avg >= cfg.threshold_ms


def cascade_skip_details() -> Dict[str, object]:
    """Details dict for a fail-open skip response."""
    cfg = _config
    avg = recent_avg_latency_ms()
    return {
        "cascade_skipped": True,
        "reason": "cascade_backpressure",
        "recent_avg_latency_ms": round(avg, 2) if avg is not None else 0.0,
        "recent_sample_count": recent_sample_count(),
        "threshold_ms": cfg.threshold_ms,
    }


def reset_for_tests() -> None:
    global _config, _latencies_ms
    _config = BackpressureConfig()
    with _lock:
        _latencies_ms.clear()
