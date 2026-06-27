"""Cloud-agnostic scan routing shared by the Cloudflare Worker and FastAPI app."""

from typing import Any, Callable, Coroutine, Dict, Optional

import alerts
import cache
from constants import (
    CASCADE_SCANNERS,
    GEMMA_ONLY_SCANNERS,
    LOCAL_SCANNERS,
    REMOTE_SCANNERS,
    SUPPORTED_SCANNERS,
    canonical_scanner,
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
    details: Dict[str, Any],
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
    schedule_fn: Optional[ScheduleFn] = None,
) -> dict:
    """Run one scan and return the ScanResponse-shaped dict."""
    schedule = schedule_fn or _noop_schedule
    scanner_name = canonical_scanner(payload.get("scanner_name", ""))
    scanner_type = payload.get("scanner_type", "prompt")
    text = payload.get("text", "")
    config = payload.get("config") or {}

    if scanner_name not in SUPPORTED_SCANNERS:
        return shape_response(
            scanner_name, True, 0.0, text, {"error": f"unsupported scanner: {scanner_name}"}
        )

    if scanner_name in REMOTE_SCANNERS:
        if scanner_name == "Anonymize":
            r = await scan_anonymize(text, config, env)
            return shape_response(
                scanner_name, r["is_valid"], r["risk_score"], r["sanitized_text"], r["details"]
            )

    if scanner_name in CASCADE_SCANNERS:
        if not config.get("modelConfigs"):
            default_cfg = get_default_config(settings.DEFAULT_MODEL_CONFIG_JSON)
            config = {**default_cfg, **config, "modelConfigs": default_cfg["modelConfigs"]}
        if scanner_name in GEMMA_ONLY_SCANNERS:
            config = {**config, "modelConfigs": strip_qwen_tier(config.get("modelConfigs"))}
        store_fn = None
        if config.get("storeAllResults"):
            store_fn = lambda completed, name: schedule(alerts.store_results(completed, name))

        # Semantic cache, decide mode: embed + lookup once before paying for the
        # cascade. A fresh within-threshold hit short-circuits — safe verdicts on
        # a fuzzy match, blocks only on a (near-)exact repeat (see cache.try_serve);
        # a miss/error falls through. The served verdict (is_valid) is honoured so
        # cached blocks still block. `prep` is reused by observe() so a miss embeds
        # only once.
        prep = None
        if cache.serving():
            prep = await cache.prepare(scanner_name, scanner_type, text, config)
            served = cache.try_serve(prep, scanner_name, scanner_type, text)
            if served is not None:
                schedule(alerts.post_cache_shadow(served["alert"]))
                return shape_response(
                    scanner_name, served["is_valid"], served["risk_score"], text, served["details"]
                )
        try:
            result = await scan_with_model_map(
                scanner_name, scanner_type, text, config, store_fn=store_fn
            )
            schedule(alerts.post_slack(scanner_name, scanner_type, text, result))
            # Per-scanner semantic cache: observe + warm (shadow in observe mode;
            # cache-warm + miss-comparison in decide mode). Fire-and-forget.
            if cache.enabled():
                schedule(cache.observe(scanner_name, scanner_type, text, config, result, prep=prep))
            return shape_response(
                scanner_name,
                result["is_valid"],
                result["risk_score"],
                text,
                result.get("details", {}),
            )
        except Exception as exc:
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
            return shape_response(
                scanner_name, r["is_valid"], r["risk_score"], r["sanitized_text"], r["details"]
            )
        except ValueError:
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

    return shape_response(scanner_name, True, 0.0, text, {"error": "unroutable"})
