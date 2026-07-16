"""Forward `Anonymize` scans to the anonymizer service.

Portable deployments use ANONYMIZER_URL (HTTP). Cloudflare uses the
ANONYMIZER_WORKER service binding on the Worker env object.

Wire (Cloudflare):
    worker-py  ──service binding──▶  anonymizer-worker  ──DO/container──▶  anonymizer-container
Wire (portable):
    worker-py  ──HTTP──▶  anonymizer-container (FastAPI + Presidio)
"""

import json
import logging
import time
from typing import Any

import httpx

import metrics_push
from settings import settings

logger = logging.getLogger(__name__)

_ANONYMIZE_TIMEOUT_S = 30.0
_FAIL_OPEN = {
    "is_valid": True,
    "risk_score": 0.0,
}


def _build_payload(text: str, config: dict[str, Any]) -> dict:
    payload = {"text": text, "language": config.get("language", "en")}
    if config.get("entities"):
        payload["entities"] = config["entities"]
    return payload


def _shape_success(parsed: dict, text: str) -> dict[str, Any]:
    return {
        "is_valid": True,
        "risk_score": 0.0,
        "sanitized_text": parsed.get("sanitized_text", text),
        "details": {"entities_found": parsed.get("entities_found", [])},
    }


def _fail_open(text: str, error: str) -> dict[str, Any]:
    return {**_FAIL_OPEN, "sanitized_text": text, "details": {"error": error}}


async def _scan_anonymize_http(text: str, config: dict[str, Any], base_url: str) -> dict[str, Any]:
    payload = _build_payload(text, config)
    url = f"{base_url.rstrip('/')}/anonymize"
    started = time.perf_counter()
    try:
        async with httpx.AsyncClient(timeout=_ANONYMIZE_TIMEOUT_S) as client:
            response = await client.post(url, json=payload)
    except Exception as exc:
        metrics_push.COUNTS["anonymizer_errors"].increment(type(exc).__name__)
        logger.warning(f"[remote] anonymizer HTTP fetch failed: {exc}")
        return _fail_open(text, f"fetch_failed: {exc}")
    metrics_push.SAMPLES["anonymizer"].record("anonymizer", (time.perf_counter() - started) * 1000.0)

    if response.status_code < 200 or response.status_code >= 300:
        metrics_push.COUNTS["anonymizer_errors"].increment(f"status_{response.status_code}")
        logger.warning(f"[remote] anonymizer returned {response.status_code}")
        return _fail_open(text, f"status_{response.status_code}")

    try:
        return _shape_success(response.json(), text)
    except Exception as exc:
        metrics_push.COUNTS["anonymizer_errors"].increment(type(exc).__name__)
        logger.warning(f"[remote] anonymizer response not JSON: {exc}")
        return _fail_open(text, "bad_response")


async def _scan_anonymize_cf_binding(text: str, config: dict[str, Any], binding) -> dict[str, Any]:
    from js import Object
    from pyodide.ffi import to_js

    body = json.dumps(_build_payload(text, config))
    init = to_js(
        {"method": "POST", "headers": {"content-type": "application/json"}, "body": body},
        dict_converter=Object.fromEntries,
    )

    try:
        response = await binding.fetch("https://anonymizer/anonymize", init)
    except Exception as exc:
        logger.warning(f"[remote] anonymizer fetch failed: {exc}")
        return _fail_open(text, f"fetch_failed: {exc}")

    if response.status < 200 or response.status >= 300:
        logger.warning(f"[remote] anonymizer returned {response.status}")
        return _fail_open(text, f"status_{response.status}")

    try:
        return _shape_success(json.loads(await response.text()), text)
    except Exception as exc:
        logger.warning(f"[remote] anonymizer response not JSON: {exc}")
        return _fail_open(text, "bad_response")


async def scan_anonymize(text: str, config: dict[str, Any], env=None) -> dict[str, Any]:
    """POST text to the anonymizer and shape the response.

    is_valid is always True — Anonymize doesn't reject traffic, it rewrites it.
    """
    base_url = (settings.ANONYMIZER_URL or "").strip()
    if base_url:
        return await _scan_anonymize_http(text, config, base_url)

    binding = getattr(env, "ANONYMIZER_WORKER", None) if env is not None else None
    if binding is not None:
        return await _scan_anonymize_cf_binding(text, config, binding)

    logger.warning("[remote] no ANONYMIZER_URL or ANONYMIZER_WORKER binding; passing text through")
    return _fail_open(text, "anonymizer_unconfigured")
