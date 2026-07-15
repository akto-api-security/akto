"""Periodic metrics push to database-abstractor's /api/ingestMetricsData."""

import asyncio
import base64
import json
import logging
import os
import socket
import threading
import time

import httpx
import psutil

import cascade_backpressure
from settings import settings

logger = logging.getLogger(__name__)

MODULE_TYPE = "AKTO_AGENT_GUARD"
_DEFAULT_INTERVAL_S = 60.0
_PUSH_TIMEOUT_S = 10.0
_MAX_RETRIES = 3
_BACKOFF_BASE_S = 1.0
_DEFAULT_DATABASE_ABSTRACTOR_URL = "https://ultron.akto.io"

_instance_id = f"{socket.gethostname()}-{os.getpid()}"
_process = psutil.Process(os.getpid())
_process.cpu_percent(None)  # prime the baseline; first real reading needs one
psutil.cpu_percent(None)  # same priming, for the system-wide (not just this process) reading

_account_id_cache: int | None = None
_account_id_resolved = False


def _account_id_from_token() -> int | None:
    """Best-effort decode of the `accountId` claim from the service JWT.
    AccountIDFromServiceToken, which trusts a token it never signed itself.
    """
    global _account_id_cache, _account_id_resolved
    if _account_id_resolved:
        return _account_id_cache
    _account_id_resolved = True
    parts = settings.DATABASE_ABSTRACTOR_SERVICE_TOKEN.split(".")
    if len(parts) != 3:
        return None
    try:
        padded = parts[1] + "=" * (-len(parts[1]) % 4)
        claims = json.loads(base64.urlsafe_b64decode(padded))
        account_id = claims.get("accountId")
        _account_id_cache = int(account_id) if account_id is not None else None
    except Exception:
        _account_id_cache = None
    return _account_id_cache


class SampleAccumulator:
    """Per-key duration samples (ms) since the last drain — feeds avg/p50/p95/p99."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._samples: dict[str, list[float]] = {}

    def record(self, key: str, value_ms: float) -> None:
        with self._lock:
            self._samples.setdefault(key, []).append(value_ms)

    def drain_all(self) -> dict[str, list[float]]:
        with self._lock:
            samples, self._samples = self._samples, {}
        return samples


class CountAccumulator:
    """Per-key counters since the last drain — feeds request/error/hit counts."""

    def __init__(self) -> None:
        self._lock = threading.Lock()
        self._counts: dict[str, int] = {}

    def increment(self, key: str, by: int = 1) -> None:
        with self._lock:
            self._counts[key] = self._counts.get(key, 0) + by

    def drain_all(self) -> dict[str, int]:
        with self._lock:
            counts, self._counts = self._counts, {}
        return counts


# Duration-sample accumulators, keyed by name)
SAMPLES: dict[str, SampleAccumulator] = {
    name: SampleAccumulator() for name in ("scan", "provider", "anonymizer", "alert")
}

# Counter accumulators, keyed by name
COUNTS: dict[str, CountAccumulator] = {
    name: CountAccumulator()
    for name in (
        "scan",
        "provider_errors",
        "anonymizer_errors",
        "cache_hits",
        "cache_misses",
        "backpressure_trips",
        "arbiter_escalations",
        "arbiter_errors",
        "alert_errors",
    )
}

_queue_size_lock = threading.Lock()
_queue_size = 0
_cache_size_lock = threading.Lock()
_cache_sizes: dict[str, int] = {}


def set_queue_size(n: int) -> None:
    global _queue_size
    with _queue_size_lock:
        _queue_size = n


def _current_queue_size() -> int:
    with _queue_size_lock:
        return _queue_size


def set_cache_size(name: str, n: int) -> None:
    """Record the current entry count of a named in-process cache ("cache load")."""
    with _cache_size_lock:
        _cache_sizes[name] = n


def _current_cache_sizes() -> dict[str, int]:
    with _cache_size_lock:
        return dict(_cache_sizes)


def _percentile(sorted_samples: list[float], pct: float) -> float:
    """Nearest-rank percentile. sorted_samples must already be sorted ascending."""
    if not sorted_samples:
        return 0.0
    idx = max(0, min(len(sorted_samples) - 1, round(pct / 100 * (len(sorted_samples) - 1))))
    return sorted_samples[idx]


def _metric(metric_id: str, value: float, metric_type: str, account_id: int | None, now: int) -> dict:
    m: dict = {
        "metricId": metric_id,
        "value": value,
        "timestamp": now,
        "metricType": metric_type,
        "moduleType": MODULE_TYPE,
        "instanceId": _instance_id,
    }
    if account_id is not None:
        m["accountId"] = account_id
    return m


def _latency_batch(prefix: str, samples: list[float], account_id: int | None, now: int) -> list[dict]:
    if not samples:
        return []
    s = sorted(samples)
    avg = sum(s) / len(s)
    return [
        _metric(f"{prefix}_LATENCY_AVG", avg, "LATENCY", account_id, now),
        _metric(f"{prefix}_LATENCY_P50", _percentile(s, 50), "LATENCY", account_id, now),
        _metric(f"{prefix}_LATENCY_P95", _percentile(s, 95), "LATENCY", account_id, now),
        _metric(f"{prefix}_LATENCY_P99", _percentile(s, 99), "LATENCY", account_id, now),
    ]


_last_flush_monotonic = time.monotonic()


def _collect_batch() -> list[dict]:
    global _last_flush_monotonic
    now = int(time.time())
    now_monotonic = time.monotonic()
    elapsed_s = max(1e-6, now_monotonic - _last_flush_monotonic)
    _last_flush_monotonic = now_monotonic
    account_id = _account_id_from_token()
    batch: list[dict] = []

    # --- overall request latency + RPS + request/error/blocked counts ---
    all_scan_samples: list[float] = []
    for samples in SAMPLES["scan"].drain_all().values():
        all_scan_samples.extend(samples)
    batch.extend(_latency_batch("AGENT_GUARD_REQUEST", all_scan_samples, account_id, now))

    scan_count_by_key = COUNTS["scan"].drain_all()
    total_requests = sum(scan_count_by_key.values())
    error_count = sum(v for k, v in scan_count_by_key.items() if k.endswith(":error"))
    blocked_count = sum(v for k, v in scan_count_by_key.items() if k.endswith(":blocked"))
    if total_requests:
        batch.append(_metric("AGENT_GUARD_REQUEST_COUNT", float(total_requests), "SUM", account_id, now))
        batch.append(_metric("AGENT_GUARD_RPS", total_requests / elapsed_s, "GAUGE", account_id, now))
    if error_count:
        batch.append(_metric("AGENT_GUARD_ERROR_COUNT", float(error_count), "SUM", account_id, now))
    if blocked_count:
        batch.append(_metric("AGENT_GUARD_BLOCKED_COUNT", float(blocked_count), "SUM", account_id, now))

    # --- failures/blocks broken down by guardrail category (scanner), bounded ~9 scanners ---
    for key, count in scan_count_by_key.items():
        scanner_name, _, status = key.partition(":")
        if status == "error":
            batch.append(
                _metric(f"AGENT_GUARD_SCAN_ERROR_{scanner_name.upper()}", float(count), "SUM", account_id, now)
            )
        elif status == "blocked":
            batch.append(
                _metric(f"AGENT_GUARD_SCAN_BLOCKED_{scanner_name.upper()}", float(count), "SUM", account_id, now)
            )

    # --- per-provider latency + errors (bounded: known LLM providers) ---
    for provider, samples in SAMPLES["provider"].drain_all().items():
        batch.extend(_latency_batch(f"AGENT_GUARD_PROVIDER_{provider.upper()}", samples, account_id, now))
    for key, count in COUNTS["provider_errors"].drain_all().items():
        provider, _, reason = key.partition(":")
        batch.append(
            _metric(f"AGENT_GUARD_PROVIDER_{provider.upper()}_ERROR_COUNT", float(count), "SUM", account_id, now)
        )

    # --- anonymizer ---
    anon_samples: list[float] = []
    for samples in SAMPLES["anonymizer"].drain_all().values():
        anon_samples.extend(samples)
    batch.extend(_latency_batch("AGENT_GUARD_ANONYMIZER", anon_samples, account_id, now))
    anon_error_total = sum(COUNTS["anonymizer_errors"].drain_all().values())
    if anon_error_total:
        batch.append(_metric("AGENT_GUARD_ANONYMIZER_ERROR_COUNT", float(anon_error_total), "SUM", account_id, now))

    # --- backpressure (trip count + live circuit-breaker state) + queue depth ---
    trips = sum(COUNTS["backpressure_trips"].drain_all().values())
    if trips:
        batch.append(_metric("AGENT_GUARD_BACKPRESSURE_TRIPS", float(trips), "SUM", account_id, now))
    batch.append(
        _metric(
            "AGENT_GUARD_BACKPRESSURE_ACTIVE",
            float(cascade_backpressure.should_skip_cascade()),
            "GAUGE",
            account_id,
            now,
        )
    )
    recent_avg = cascade_backpressure.recent_avg_latency_ms()
    if recent_avg is not None:
        batch.append(_metric("AGENT_GUARD_BACKPRESSURE_RECENT_AVG_LATENCY_MS", recent_avg, "GAUGE", account_id, now))
    batch.append(
        _metric(
            "AGENT_GUARD_BACKPRESSURE_RECENT_SAMPLE_COUNT",
            float(cascade_backpressure.recent_sample_count()),
            "GAUGE",
            account_id,
            now,
        )
    )
    batch.append(_metric("AGENT_GUARD_QUEUE_SIZE", float(_current_queue_size()), "GAUGE", account_id, now))

    # --- arbiter escalation + failure counts (tier1/tier2 disagreement, config errors) ---
    escalations = sum(COUNTS["arbiter_escalations"].drain_all().values())
    if escalations:
        batch.append(_metric("AGENT_GUARD_ARBITER_ESCALATIONS", float(escalations), "SUM", account_id, now))
    for reason, count in COUNTS["arbiter_errors"].drain_all().items():
        batch.append(_metric(f"AGENT_GUARD_ARBITER_ERROR_{reason.upper()}", float(count), "SUM", account_id, now))

    # --- alert sinks (database-abstractor store) ---
    for key, samples in SAMPLES["alert"].drain_all().items():
        batch.extend(_latency_batch(f"AGENT_GUARD_ALERT_{key.upper()}", samples, account_id, now))
    for key, count in COUNTS["alert_errors"].drain_all().items():
        sink, _, reason = key.partition(":")
        batch.append(_metric(f"AGENT_GUARD_ALERT_{sink.upper()}_ERROR_COUNT", float(count), "SUM", account_id, now))

    # --- cache hit rate + current size ("load"), per cache name ---
    hits = COUNTS["cache_hits"].drain_all()
    misses = COUNTS["cache_misses"].drain_all()
    for cache_name in set(hits) | set(misses):
        h, m = hits.get(cache_name, 0), misses.get(cache_name, 0)
        if h + m:
            batch.append(
                _metric(
                    f"AGENT_GUARD_CACHE_HIT_RATE_{cache_name.upper()}",
                    100.0 * h / (h + m),
                    "GAUGE",
                    account_id,
                    now,
                )
            )
    for cache_name, size in _current_cache_sizes().items():
        batch.append(_metric(f"AGENT_GUARD_CACHE_SIZE_{cache_name.upper()}", float(size), "GAUGE", account_id, now))

    # --- instance-level (this process) CPU/memory ---
    try:
        batch.append(_metric("AGENT_GUARD_CPU_USAGE", _process.cpu_percent(None), "GAUGE", account_id, now))
    except Exception as exc:
        logger.warning(f"[metrics-push] cpu_percent failed: {exc}")
    try:
        rss_mb = _process.memory_info().rss / (1024 * 1024)
        batch.append(_metric("AGENT_GUARD_MEMORY_USAGE", rss_mb, "GAUGE", account_id, now))
    except Exception as exc:
        logger.warning(f"[metrics-push] memory_info failed: {exc}")

    # --- host-wide (not just this process) CPU/memory ---
    try:
        batch.append(_metric("AGENT_GUARD_SYSTEM_CPU_PERCENT", psutil.cpu_percent(None), "GAUGE", account_id, now))
    except Exception as exc:
        logger.warning(f"[metrics-push] system cpu_percent failed: {exc}")
    try:
        batch.append(
            _metric("AGENT_GUARD_SYSTEM_MEMORY_PERCENT", psutil.virtual_memory().percent, "GAUGE", account_id, now)
        )
    except Exception as exc:
        logger.warning(f"[metrics-push] system virtual_memory failed: {exc}")

    return batch


async def _push(batch: list[dict]) -> None:
    base = (settings.DATABASE_ABSTRACTOR_SERVICE_URL or "").strip().rstrip("/") or _DEFAULT_DATABASE_ABSTRACTOR_URL
    token = settings.DATABASE_ABSTRACTOR_SERVICE_TOKEN
    if not token:
        return
    url = f"{base}/api/ingestMetricsData"
    headers = {"Authorization": token, "Content-Type": "application/json"}
    payload = {"metricData": batch}

    for attempt in range(_MAX_RETRIES + 1):
        try:
            async with httpx.AsyncClient(timeout=_PUSH_TIMEOUT_S) as client:
                resp = await client.post(url, headers=headers, json=payload)
        except httpx.RequestError as exc:
            if attempt == _MAX_RETRIES:
                logger.warning(f"[metrics-push] push failed after {attempt + 1} attempts: {exc}")
                return
            await asyncio.sleep(_BACKOFF_BASE_S * (2**attempt))
            continue

        if resp.status_code < 400:
            return
        if resp.status_code < 500:
            logger.warning(f"[metrics-push] ingestMetricsData returned {resp.status_code}: {resp.text[:500]}")
            return
        # 5xx: transient server-side failure, worth retrying.
        if attempt == _MAX_RETRIES:
            logger.warning(
                f"[metrics-push] ingestMetricsData returned {resp.status_code} after {attempt + 1} "
                f"attempts: {resp.text[:500]}"
            )
            return
        await asyncio.sleep(_BACKOFF_BASE_S * (2**attempt))


async def run_forever() -> None:
    """Background task: flush accumulated metrics on a ticker. Runs forever."""
    try:
        interval = float(settings.METRICS_PUSH_INTERVAL_SEC or _DEFAULT_INTERVAL_S)
    except ValueError:
        interval = _DEFAULT_INTERVAL_S
    if interval <= 0:
        interval = _DEFAULT_INTERVAL_S

    while True:
        await asyncio.sleep(interval)
        try:
            batch = _collect_batch()
            if batch:
                await _push(batch)
        except Exception as exc:
            logger.warning(f"[metrics-push] tick failed, will retry next interval: {exc}")
