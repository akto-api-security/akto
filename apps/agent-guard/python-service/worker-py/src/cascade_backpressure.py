"""Adaptive backpressure for LLM cascade scans when Vertex latency is elevated.

Recent cascade latencies are averaged over a rolling, *time-bounded* window. When
that average meets or exceeds AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS (env
override), new cascade scans fail-open (is_valid=True) so guardrails traffic is
not blocked.

Recovery: samples older than AGW_CASCADE_BACKPRESSURE_TTL_SECONDS are evicted on
every read/write, *independently of new traffic*. This is what makes backpressure
self-healing. While skipping, no cascade runs, so no new latencies are recorded —
a purely count-based window would therefore stay full of stale high latencies and
latch ON forever. With time eviction the stale samples age out; once fewer than
min_samples remain, should_skip_cascade() returns False, the next cascade runs as
a natural probe, and fresh latencies decide whether to re-trip. So a transient
Vertex slowdown self-clears within ~TTL instead of requiring a process restart.
"""

from __future__ import annotations

import os
import threading
import time
from collections import deque
from dataclasses import dataclass
from typing import Deque, Dict, Optional, Tuple

_DEFAULT_WINDOW = 50
_DEFAULT_MIN_SAMPLES = 5
_DEFAULT_AVG_LATENCY_MS = 8000.0
_DEFAULT_TTL_SECONDS = 30.0
_DEFAULT_ENABLED = True

_lock = threading.Lock()
# Each entry is (monotonic_timestamp_seconds, latency_ms). maxlen bounds memory
# under bursts; the TTL bounds staleness so backpressure can recover.
_samples: Deque[Tuple[float, float]] = deque(maxlen=_DEFAULT_WINDOW)


def _now() -> float:
    """Monotonic clock (seconds). Indirected so tests can control time."""
    return time.monotonic()


@dataclass(frozen=True)
class BackpressureConfig:
    enabled: bool = _DEFAULT_ENABLED
    min_samples: int = _DEFAULT_MIN_SAMPLES
    threshold_ms: float = _DEFAULT_AVG_LATENCY_MS
    window: int = _DEFAULT_WINDOW
    ttl_seconds: float = _DEFAULT_TTL_SECONDS


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
    """Load threshold/window/ttl from env; resize the rolling latency window."""
    global _config, _samples
    window = _env_int("AGW_CASCADE_BACKPRESSURE_WINDOW", _DEFAULT_WINDOW)
    _config = BackpressureConfig(
        enabled=_env_bool("AGW_CASCADE_BACKPRESSURE_ENABLED", _DEFAULT_ENABLED),
        min_samples=_env_int("AGW_CASCADE_BACKPRESSURE_MIN_SAMPLES", _DEFAULT_MIN_SAMPLES),
        threshold_ms=_env_float("AGW_CASCADE_BACKPRESSURE_AVG_LATENCY_MS", _DEFAULT_AVG_LATENCY_MS),
        window=window,
        ttl_seconds=_env_float("AGW_CASCADE_BACKPRESSURE_TTL_SECONDS", _DEFAULT_TTL_SECONDS),
    )
    with _lock:
        if window != _samples.maxlen:
            _samples = deque(list(_samples)[-window:], maxlen=window)


def _prune_locked(now: float) -> None:
    """Drop samples older than the TTL. Caller must hold _lock."""
    ttl = _config.ttl_seconds
    if ttl <= 0:
        return
    cutoff = now - ttl
    while _samples and _samples[0][0] < cutoff:
        _samples.popleft()


def record_cascade_latency(elapsed_ms: float) -> None:
    if elapsed_ms <= 0:
        return
    now = _now()
    with _lock:
        _samples.append((now, elapsed_ms))
        _prune_locked(now)


def recent_avg_latency_ms() -> Optional[float]:
    """Rolling average of non-expired cascade latencies, or None when empty."""
    with _lock:
        _prune_locked(_now())
        if not _samples:
            return None
        return sum(lat for _, lat in _samples) / len(_samples)


def recent_sample_count() -> int:
    with _lock:
        _prune_locked(_now())
        return len(_samples)


def should_skip_cascade() -> bool:
    """Return True when the average of non-expired cascade latencies meets the env
    threshold. Stale samples are evicted first so this recovers on its own."""
    cfg = _config
    if not cfg.enabled:
        return False

    with _lock:
        _prune_locked(_now())
        count = len(_samples)
        if count < cfg.min_samples:
            return False
        avg = sum(lat for _, lat in _samples) / count

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
    global _config, _samples
    _config = BackpressureConfig()
    with _lock:
        _samples.clear()
