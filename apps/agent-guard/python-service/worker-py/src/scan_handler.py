"""Cloud-agnostic scan routing shared by the Cloudflare Worker and FastAPI app."""

import time
from collections.abc import Callable, Coroutine
from typing import Any

import alerts
import cascade_backpressure
import metrics_push
import scan_diag
from constants import (
    CASCADE_SCANNERS,
    FORCE_GEMMA_ONLY_SCANNERS,
    GEMMA_ONLY_SCANNERS,
    LOCAL_SCANNERS,
    REMOTE_SCANNERS,
    SUPPORTED_SCANNERS,
    canonical_scanner,
    force_gemma_only,
    get_default_config,
    strip_qwen_tier,
)
from llm_scanner import scan_with_model_map
from remote_scanner import scan_anonymize
from scanners import scan_local
from settings import settings

ScheduleFn = Callable[[Coroutine[Any, Any, None]], None]


def _noop_schedule(_: Coroutine[Any, Any, None]) -> None:
    pass


def shape_response(
    scanner_name: str,
    is_valid: bool,
    risk_score: float,
    sanitized_text: str,
    details: dict[str, Any],
) -> dict:
    return {
        "scanner_name": scanner_name,
        "is_valid": is_valid,
        "risk_score": risk_score,
        "sanitized_text": sanitized_text,
        "details": details,
    }


def scanners_metadata() -> dict:
    return {
        "supported": sorted(SUPPORTED_SCANNERS),
        "cascade": sorted(CASCADE_SCANNERS),
        "local": sorted(LOCAL_SCANNERS),
    }


async def scan_payload(
    payload: dict,
    env=None,
    schedule_fn: ScheduleFn | None = None,
) -> dict:
    """Run one scan and return the ScanResponse-shaped dict."""
    started = time.perf_counter()
    schedule = schedule_fn or _noop_schedule
    scanner_name = canonical_scanner(payload.get("scanner_name", ""))
    scanner_type = payload.get("scanner_type", "prompt")
    text = payload.get("text", "")
    config = payload.get("config") or {}

    def _elapsed_ms() -> float:
        return (time.perf_counter() - started) * 1000.0

    def _record(status: str) -> None:
        elapsed_ms = _elapsed_ms()
        metrics_push.COUNTS["scan"].increment(f"{scanner_name}:{status}")
        metrics_push.SAMPLES["scan"].record(scanner_name, elapsed_ms)

    if scanner_name not in SUPPORTED_SCANNERS:
        scan_diag.log_scan_outcome(
            "unsupported_scanner",
            scanner_name,
            _elapsed_ms(),
            always=True,
        )
        _record("error")
        return shape_response(scanner_name, True, 0.0, text, {"error": f"unsupported scanner: {scanner_name}"})

    if scanner_name in REMOTE_SCANNERS:
        if scanner_name == "Anonymize":
            r = await scan_anonymize(text, config, env)
            _record("success" if r["is_valid"] else "blocked")
            return shape_response(scanner_name, r["is_valid"], r["risk_score"], r["sanitized_text"], r["details"])

    if scanner_name in CASCADE_SCANNERS:
        if not config.get("modelConfigs"):
            default_cfg = get_default_config(settings.DEFAULT_MODEL_CONFIG_JSON)
            config = {**default_cfg, **config, "modelConfigs": default_cfg["modelConfigs"]}
        if scanner_name in FORCE_GEMMA_ONLY_SCANNERS:
            config = {**config, "modelConfigs": force_gemma_only(config.get("modelConfigs"))}
        elif scanner_name in GEMMA_ONLY_SCANNERS:
            config = {**config, "modelConfigs": strip_qwen_tier(config.get("modelConfigs"))}
        store_fn = None
        if config.get("storeAllResults"):
            store_fn = lambda completed, name: schedule(alerts.store_results(completed, name))

        # Backpressure fail-open to avoid paying for the slow cascade while
        # Vertex latency is elevated.
        if cascade_backpressure.should_skip_cascade():
            scan_diag.log_backpressure_skip(scanner_name)
            metrics_push.COUNTS["backpressure_trips"].increment("cascade")
            _record("skipped")
            return shape_response(
                scanner_name,
                True,
                0.0,
                text,
                {**cascade_backpressure.cascade_skip_details(), "scanner_type": scanner_type},
            )
        scan_diag.log_backpressure_proceeding(scanner_name)
        try:
            result = await scan_with_model_map(scanner_name, scanner_type, text, config, store_fn=store_fn)
            elapsed = result.get("execution_time_ms")
            if isinstance(elapsed, (int, float)) and elapsed > 0:
                cascade_backpressure.record_cascade_latency(float(elapsed))
            schedule(alerts.post_slack(scanner_name, scanner_type, text, result))
            scan_diag.log_scan_outcome(
                "cascade_run",
                scanner_name,
                _elapsed_ms(),
                extra={
                    "is_valid": result.get("is_valid"),
                    "cascade_ms": elapsed,
                },
            )
            _record("success" if result["is_valid"] else "blocked")
            return shape_response(
                scanner_name,
                result["is_valid"],
                result["risk_score"],
                text,
                result.get("details", {}),
            )
        except Exception as exc:
            scan_diag.log_scan_outcome(
                "cascade_error",
                scanner_name,
                _elapsed_ms(),
                extra={"error": str(exc)},
                always=True,
            )
            _record("error")
            return shape_response(
                scanner_name,
                True,
                0.0,
                text,
                {"scanner_type": scanner_type, "error": f"cascade failed: {exc}"},
            )

    if scanner_name in LOCAL_SCANNERS:
        try:
            r = scan_local(scanner_name, scanner_type, text, config)
            scan_diag.log_scan_outcome(
                "local_scan",
                scanner_name,
                _elapsed_ms(),
                extra={"is_valid": r["is_valid"]},
            )
            _record("success" if r["is_valid"] else "blocked")
            return shape_response(scanner_name, r["is_valid"], r["risk_score"], r["sanitized_text"], r["details"])
        except ValueError:
            _record("not_implemented")
            return shape_response(
                scanner_name,
                True,
                0.0,
                text,
                {
                    "scanner_type": scanner_type,
                    "status": "not_implemented",
                    "would_route_to": "local",
                },
            )

    _record("error")
    return shape_response(scanner_name, True, 0.0, text, {"error": "unroutable"})
