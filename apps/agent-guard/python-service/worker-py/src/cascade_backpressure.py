"""Adaptive, self-healing backpressure for the slow Vertex cascade dependency.

A single circuit breaker over a rolling, *time-bounded* latency window:

  MODEL  — Vertex cascade latency. When it trips the caller fail-opens the
           cascade (is_valid=True) so guardrails traffic is not blocked while
           Vertex is slow. Env prefix AGW_CASCADE_BACKPRESSURE_* (threshold 8000ms).

Recovery: samples older than the TTL are evicted on every read/write,
*independently of new traffic*. While tripped, the protected work doesn't run, so
no fresh latency is recorded — a purely count-based window would stay full of
stale high samples and latch ON forever. Time eviction ages them out; once fewer
than min_samples remain, should_skip() returns False, the next call runs as a
natural probe, and fresh latencies decide whether to re-trip. So a transient
slowdown self-clears within ~TTL instead of requiring a process restart.

State is per-process (per uvicorn worker).
"""

from __future__ import annotations

import os
import threading
import time
from collections import deque
from dataclasses import dataclass
from typing import Deque, Dict, Optional, Tuple


def _now() -> float:
    """Monotonic clock (seconds). Indirected so tests can control time."""
    return time.monotonic()


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


@dataclass(frozen=True)
class BackpressureConfig:
    enabled: bool = False
    min_samples: int = 5
    threshold_ms: float = 8000.0
    window: int = 50
    ttl_seconds: float = 30.0


class LatencyBreaker:
    """Self-healing, time-bounded rolling-average latency circuit breaker.

    `env_prefix` namespaces the env overrides (e.g. AGW_CASCADE_BACKPRESSURE);
    `reason` labels the skip details so callers/diagnostics can tell the breakers
    apart. The defaults seed both the initial config and reset_for_tests().
    """

    def __init__(self, env_prefix: str, reason: str, defaults: BackpressureConfig):
        self._env_prefix = env_prefix
        self._reason = reason
        self._defaults = defaults
        self._config = defaults
        self._lock = threading.Lock()
        # Each entry is (monotonic_timestamp_seconds, latency_ms). maxlen bounds
        # memory under bursts; the TTL bounds staleness so the breaker recovers.
        self._samples: Deque[Tuple[float, float]] = deque(maxlen=defaults.window)

    # -- config ---------------------------------------------------------------
    def configure_from_env(self) -> None:
        """Load enabled/threshold/window/ttl from env; resize the window."""
        p, d = self._env_prefix, self._defaults
        window = _env_int(f"{p}_WINDOW", d.window)
        self._config = BackpressureConfig(
            enabled=_env_bool(f"{p}_ENABLED", d.enabled),
            min_samples=_env_int(f"{p}_MIN_SAMPLES", d.min_samples),
            threshold_ms=_env_float(f"{p}_AVG_LATENCY_MS", d.threshold_ms),
            window=window,
            ttl_seconds=_env_float(f"{p}_TTL_SECONDS", d.ttl_seconds),
        )
        with self._lock:
            if window != self._samples.maxlen:
                self._samples = deque(list(self._samples)[-window:], maxlen=window)

    def get_config(self) -> BackpressureConfig:
        return self._config

    # -- window ---------------------------------------------------------------
    def _prune_locked(self, now: float) -> None:
        """Drop samples older than the TTL. Caller must hold self._lock."""
        ttl = self._config.ttl_seconds
        if ttl <= 0:
            return
        cutoff = now - ttl
        while self._samples and self._samples[0][0] < cutoff:
            self._samples.popleft()

    def record(self, elapsed_ms: float) -> None:
        if elapsed_ms <= 0:
            return
        now = _now()
        with self._lock:
            self._samples.append((now, elapsed_ms))
            self._prune_locked(now)

    def recent_avg_latency_ms(self) -> Optional[float]:
        """Rolling average of non-expired latencies, or None when empty."""
        with self._lock:
            self._prune_locked(_now())
            if not self._samples:
                return None
            return sum(lat for _, lat in self._samples) / len(self._samples)

    def recent_sample_count(self) -> int:
        with self._lock:
            self._prune_locked(_now())
            return len(self._samples)

    def should_skip(self) -> bool:
        """True when the average of non-expired latencies meets the threshold.
        Stale samples are evicted first so this recovers on its own."""
        cfg = self._config
        if not cfg.enabled:
            return False
        with self._lock:
            self._prune_locked(_now())
            count = len(self._samples)
            if count < cfg.min_samples:
                return False
            avg = sum(lat for _, lat in self._samples) / count
        return avg >= cfg.threshold_ms

    def skip_details(self) -> Dict[str, object]:
        """Details dict for a fail-open / skip response."""
        cfg = self._config
        avg = self.recent_avg_latency_ms()
        return {
            "cascade_skipped": True,
            "reason": self._reason,
            "recent_avg_latency_ms": round(avg, 2) if avg is not None else 0.0,
            "recent_sample_count": self.recent_sample_count(),
            "threshold_ms": cfg.threshold_ms,
        }

    def status_snapshot(self) -> Dict[str, object]:
        """Current window for diagnostics (per uvicorn worker process)."""
        cfg = self._config
        with self._lock:
            self._prune_locked(_now())
            count = len(self._samples)
            avg = (sum(lat for _, lat in self._samples) / count) if count else None
        return {
            "reason": self._reason,
            "enabled": cfg.enabled,
            "recent_sample_count": count,
            "recent_avg_latency_ms": round(avg, 2) if avg is not None else None,
            "threshold_ms": cfg.threshold_ms,
            "min_samples": cfg.min_samples,
            "ttl_seconds": cfg.ttl_seconds,
            "pid": os.getpid(),
        }

    def reset_for_tests(self) -> None:
        self._config = self._defaults
        with self._lock:
            self._samples.clear()


# --------------------------------------------------------------------------- #
# The cascade breaker (per-process singleton).
# --------------------------------------------------------------------------- #
MODEL = LatencyBreaker(
    "AGW_CASCADE_BACKPRESSURE", "cascade_backpressure",
    BackpressureConfig(threshold_ms=8000.0),
)


def configure_from_env() -> None:
    """Configure the breaker from env (called once at startup)."""
    MODEL.configure_from_env()


def reset_for_tests() -> None:
    MODEL.reset_for_tests()


# --------------------------------------------------------------------------- #
# Back-compat module-level API — delegates to the MODEL (cascade) breaker so
# existing call sites (app.py, scan_handler.py, tests) keep working unchanged.
# --------------------------------------------------------------------------- #
def get_config() -> BackpressureConfig:
    return MODEL.get_config()


def record_cascade_latency(elapsed_ms: float) -> None:
    MODEL.record(elapsed_ms)


def recent_avg_latency_ms() -> Optional[float]:
    return MODEL.recent_avg_latency_ms()


def recent_sample_count() -> int:
    return MODEL.recent_sample_count()


def should_skip_cascade() -> bool:
    return MODEL.should_skip()


def cascade_skip_details() -> Dict[str, object]:
    return MODEL.skip_details()


def status_snapshot() -> Dict[str, object]:
    return MODEL.status_snapshot()
