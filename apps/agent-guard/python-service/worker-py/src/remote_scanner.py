"""Forward `Anonymize` scans to the anonymizer-worker service binding.

The anonymizer-worker is a thin JS Worker (sibling deployment) that owns the
Cloudflare Container running Presidio. We can't bind the container directly
here because Cloudflare's Containers SDK is JS-only — Python Workers can't
declare the Durable Object class the binding requires.

Wire:
    worker-py  ──service binding──▶  anonymizer-worker  ──DO/container──▶  anonymizer-container (FastAPI + Presidio)
"""

import json
import logging
from typing import Any, Dict

from js import Object
from pyodide.ffi import to_js

logger = logging.getLogger(__name__)


def _js_init(method: str, headers: Dict[str, str], body: str):
    """Build a JS RequestInit object from Python values.

    binding.fetch on Python Workers does NOT marshal Python kwargs into a JS
    RequestInit. Passing body= as a kwarg silently drops the body, which is
    what makes FastAPI return 422. We have to construct the JS object
    explicitly with `to_js` so headers and body actually travel.
    """
    return to_js(
        {"method": method, "headers": headers, "body": body},
        dict_converter=Object.fromEntries,
    )


async def scan_anonymize(text: str, config: Dict[str, Any], env) -> Dict[str, Any]:
    """POST text to anonymizer-worker /anonymize and shape the response.

    Returns a dict matching the scanner contract: keys is_valid, risk_score,
    sanitized_text, details. is_valid is always True — Anonymize doesn't reject
    traffic, it just rewrites it.
    """
    binding = getattr(env, "ANONYMIZER_WORKER", None)
    if binding is None:
        logger.warning("[remote] ANONYMIZER_WORKER binding not set; passing text through")
        return {
            "is_valid": True,
            "risk_score": 0.0,
            "sanitized_text": text,
            "details": {"error": "binding_missing"},
        }

    payload = {"text": text, "language": config.get("language", "en")}
    if config.get("entities"):
        payload["entities"] = config["entities"]
    body = json.dumps(payload)

    init = _js_init(
        method="POST",
        headers={"content-type": "application/json"},
        body=body,
    )

    try:
        response = await binding.fetch("https://anonymizer/anonymize", init)
    except Exception as exc:
        # Fail-open: if the container is unreachable, return the original text
        # so the caller can still ship a (less-redacted) threat report.
        logger.warning(f"[remote] anonymizer fetch failed: {exc}")
        return {
            "is_valid": True,
            "risk_score": 0.0,
            "sanitized_text": text,
            "details": {"error": f"fetch_failed: {exc}"},
        }

    if response.status < 200 or response.status >= 300:
        logger.warning(f"[remote] anonymizer returned {response.status}")
        return {
            "is_valid": True,
            "risk_score": 0.0,
            "sanitized_text": text,
            "details": {"error": f"status_{response.status}"},
        }

    try:
        parsed = json.loads(await response.text())
    except Exception as exc:
        logger.warning(f"[remote] anonymizer response not JSON: {exc}")
        return {
            "is_valid": True,
            "risk_score": 0.0,
            "sanitized_text": text,
            "details": {"error": "bad_response"},
        }

    return {
        "is_valid": True,
        "risk_score": 0.0,
        "sanitized_text": parsed.get("sanitized_text", text),
        "details": {"entities_found": parsed.get("entities_found", [])},
    }
