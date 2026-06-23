"""Akto Agent Guard — single Python Worker entrypoint.

Routes each scan to either the LLM cascade (PromptInjection / BanTopics /
Toxicity / Gibberish) or a local in-Worker scanner (BanSubstrings / TokenLimit
/ Secrets). Preserves the existing FastAPI ScanRequest/ScanResponse contract so
callers need no change.
"""

import asyncio
import json
import logging
from urllib.parse import urlparse

from pyodide.ffi import create_proxy
from workers import Response, waitUntil

import alerts
from constants import CASCADE_SCANNERS, GEMMA_ONLY_SCANNERS, LOCAL_SCANNERS, REMOTE_SCANNERS, SUPPORTED_SCANNERS, canonical_scanner, get_default_config, strip_qwen_tier
from scanners import scan_local
from settings import settings
from llm_scanner import scan_with_model_map
from remote_scanner import scan_anonymize

logger = logging.getLogger(__name__)


def _json(obj, status: int = 200) -> Response:
    return Response(json.dumps(obj), status=status, headers={"content-type": "application/json"})


def _schedule(coro) -> None:
    """Fire-and-forget a coroutine that outlives the response.

    waitUntil (from `cloudflare:workers`) is the raw JS function and needs a
    JS-thenable, so we wrap the coroutine in a create_proxy PyProxy (awaitable
    in JS, and not auto-destroyed at the end of this call). The proxy destroys
    itself once the coroutine settles. waitUntil keeps the isolate alive until
    then (up to 30s after the response).
    """
    async def _run_and_cleanup(c, proxy_box):
        try:
            await c
        finally:
            proxy_box[0].destroy()

    box = [None]
    box[0] = create_proxy(_run_and_cleanup(coro, box))
    try:
        waitUntil(box[0])
    except Exception as exc:  # pragma: no cover - runtime-specific
        logger.warning(f"[alerts] waitUntil unavailable: {exc}")


def _shape(scanner_name, is_valid, risk_score, sanitized_text, details) -> dict:
    return {
        "scanner_name": scanner_name,
        "is_valid": is_valid,
        "risk_score": risk_score,
        "sanitized_text": sanitized_text,
        "details": details,
    }


async def _scan(payload: dict, env=None) -> dict:
    """Run one scan and return the ScanResponse-shaped dict."""
    scanner_name = canonical_scanner(payload.get("scanner_name", ""))
    scanner_type = payload.get("scanner_type", "prompt")
    text = payload.get("text", "")
    config = payload.get("config") or {}

    if scanner_name not in SUPPORTED_SCANNERS:
        return _shape(scanner_name, True, 0.0, text,
                      {"error": f"unsupported scanner: {scanner_name}"})

    if scanner_name in REMOTE_SCANNERS:
        if scanner_name == "Anonymize":
            r = await scan_anonymize(text, config, env)
            return _shape(scanner_name, r["is_valid"], r["risk_score"],
                          r["sanitized_text"], r["details"])

    if scanner_name in CASCADE_SCANNERS:
        if not config.get("modelConfigs"):
            default_cfg = get_default_config(settings.DEFAULT_MODEL_CONFIG_JSON)
            config = {**default_cfg, **config, "modelConfigs": default_cfg["modelConfigs"]}
        # Qwen3Guard can't judge code (safety verdict only); strip its tier so the
        # Gemma arbiter decides instead of fast-passing benign code as "safe".
        if scanner_name in GEMMA_ONLY_SCANNERS:
            config = {**config, "modelConfigs": strip_qwen_tier(config.get("modelConfigs"))}
        # Persist every model's output when asked (scheduled, never blocks).
        store_fn = None
        if config.get("storeAllResults"):
            store_fn = lambda completed, name: _schedule(alerts.store_results(completed, name))
        try:
            result = await scan_with_model_map(scanner_name, scanner_type, text, config,
                                               store_fn=store_fn)
            _schedule(alerts.post_slack(scanner_name, scanner_type, text, result))
            return _shape(scanner_name, result["is_valid"], result["risk_score"],
                          text, result.get("details", {}))
        except Exception as exc:  # fail open — never block traffic on cascade failure
            return _shape(scanner_name, True, 0.0, text,
                          {"scanner_type": scanner_type, "error": f"cascade failed: {exc}"})

    if scanner_name in LOCAL_SCANNERS:
        try:
            r = scan_local(scanner_name, scanner_type, text, config)
            return _shape(scanner_name, r["is_valid"], r["risk_score"],
                          r["sanitized_text"], r["details"])
        except ValueError:
            # TokenLimit / Secrets land in milestone 3 — stub for now.
            return _shape(scanner_name, True, 0.0, text,
                          {"scanner_type": scanner_type, "status": "not_implemented",
                           "would_route_to": "local"})

    return _shape(scanner_name, True, 0.0, text, {"error": "unroutable"})


async def on_fetch(request, env, ctx):
    settings.init(env)  # idempotent; populates provider creds from Worker bindings
    path = urlparse(request.url).path

    if path == "/health":
        return _json({"status": "healthy", "service": "agent-guard-worker"})

    if path == "/scanners":
        return _json({
            "supported": sorted(SUPPORTED_SCANNERS),
            "cascade": sorted(CASCADE_SCANNERS),
            "local": sorted(LOCAL_SCANNERS),
        })

    if path == "/scan" and request.method == "POST":
        payload = json.loads(await request.text())
        return _json(await _scan(payload, env))

    if path == "/scan/batch" and request.method == "POST":
        payloads = json.loads(await request.text())
        return _json([await _scan(p, env) for p in payloads])

    return _json({"error": "not found", "path": path}, status=404)
