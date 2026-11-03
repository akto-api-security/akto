"""Image OCR for POST /ocr: the image goes to the providers in OCR_MODEL_CONFIG_JSON, in order.

OCR_MODEL_CONFIG_JSON is a JSON list of entries, e.g.
    [{"provider": "azure_vision", "baseUrl": "https://<resource>.cognitiveservices.azure.com", "timeoutMs": 30000}]
The first entry that answers wins; one that fails or times out falls through to the next. Unset, it is a single
"azure_vision" entry when AZURE_VISION_BASE_URL and AZURE_VISION_API_KEY are both set. As with modelConfigs, an
entry may set its own baseUrl, but keys are env-only. It is deployment config: callers can't pick the provider.

Providers:
  azure_vision  Azure Computer Vision OCR v3.2, the call guardrails-service makes directly today.
"""

import asyncio
import base64
import binascii
import json
import logging
import time
from typing import Any

import alerts
import http_client
import metrics_push
from settings import settings

logger = logging.getLogger(__name__)

MAX_IMAGE_BYTES = 4 * 1024 * 1024  # bounds what reaches a provider; guardrails-service already caps images at 2 MB
_DEFAULT_TIMEOUT_MS = 30000
_IDENTITY = {"Accept-Encoding": "identity"}  # Pyodide double-gunzip fix, as in providers.py


class AzureVisionOCR:
    name = "azure_vision"

    def __init__(self, base_url: str, api_key: str):
        self.base_url = base_url.strip().rstrip("/")
        self.api_key = api_key

    async def extract_text(self, image: bytes) -> str:
        resp = await http_client.get_client().post(
            f"{self.base_url}/vision/v3.2/ocr?detectOrientation=true&language=unk",
            headers={
                **_IDENTITY,
                "Content-Type": "application/octet-stream",
                "Ocp-Apim-Subscription-Key": self.api_key,
            },
            content=image,
        )
        if resp.is_error:
            logger.warning(f"[OCR] {self.name} HTTP {resp.status_code}: {resp.text[:500]!r}")
        resp.raise_for_status()
        return parse_azure_ocr(resp.json())


def parse_azure_ocr(body: dict[str, Any]) -> str:
    """One line of output per OCR line, its words joined by spaces."""
    lines = []
    for region in body.get("regions") or []:
        for line in region.get("lines") or []:
            words = [word.get("text", "") for word in line.get("words") or []]
            if words:
                lines.append(" ".join(words))
    return "\n".join(lines)


def _entries() -> list[dict[str, Any]]:
    raw = (settings.OCR_MODEL_CONFIG_JSON or "").strip()
    if not raw:
        configured = settings.AZURE_VISION_BASE_URL and settings.AZURE_VISION_API_KEY
        return [{"provider": "azure_vision"}] if configured else []
    try:
        entries = json.loads(raw)
    except ValueError:
        entries = None
    if isinstance(entries, list) and all(isinstance(entry, dict) for entry in entries):
        return entries
    logger.error("[OCR] OCR_MODEL_CONFIG_JSON is not a JSON list of objects; OCR is off")
    return []


def build_ocr_provider(entry: dict[str, Any]) -> AzureVisionOCR | None:
    name = str(entry.get("provider") or "").strip().lower()
    if name == "azure_vision":
        base_url = str(entry.get("baseUrl") or "").strip() or settings.AZURE_VISION_BASE_URL
        if base_url and settings.AZURE_VISION_API_KEY:
            return AzureVisionOCR(base_url, settings.AZURE_VISION_API_KEY)
        logger.warning("[OCR] azure_vision needs a baseUrl (or AZURE_VISION_BASE_URL) and AZURE_VISION_API_KEY")
        return None
    logger.warning(f"[OCR] unknown provider {name!r}, skipped")
    return None


async def ocr_payload(payload: Any) -> tuple[int, dict[str, Any]]:
    """Run one /ocr request; returns (HTTP status, body).

    200 {"text", "provider"}; 400 / 413 for a missing, invalid or oversized image (checked before any provider
    call); 503 when no provider is configured; 502 when every provider failed (with each one's reason only).
    """
    image_b64 = payload.get("image_b64") if isinstance(payload, dict) else None
    if not isinstance(image_b64, str) or not image_b64:
        return 400, {"error": "image_b64 (a base64 string) is required"}
    if len(image_b64) > MAX_IMAGE_BYTES * 4 // 3 + 4:  # cheap bound before decoding
        return 413, {"error": f"image is over {MAX_IMAGE_BYTES} bytes"}
    try:
        image = base64.b64decode(image_b64, validate=True)
    except (binascii.Error, ValueError):
        return 400, {"error": "image_b64 is not valid base64"}
    if not image:
        return 400, {"error": "image is empty"}
    if len(image) > MAX_IMAGE_BYTES:
        return 413, {"error": f"image is over {MAX_IMAGE_BYTES} bytes"}

    chain = [(entry, provider) for entry in _entries() if (provider := build_ocr_provider(entry)) is not None]
    if not chain:
        return 503, {"error": "OCR not configured"}

    failures = []
    for entry, provider in chain:
        started = time.perf_counter()
        try:  # a bad timeoutMs fails this entry like any other error, rather than the whole request
            timeout_s = float(entry.get("timeoutMs") or _DEFAULT_TIMEOUT_MS) / 1000.0
            text = await asyncio.wait_for(provider.extract_text(image), timeout_s)
        except Exception as exc:
            reason = alerts.fallback_reason(exc)
            metrics_push.COUNTS["provider_errors"].increment(f"{provider.name}:{reason}")
            logger.warning(f"[OCR] {provider.name} failed ({reason}): {exc!r}")
            failures.append({"provider": provider.name, "reason": reason})
            continue
        elapsed_ms = (time.perf_counter() - started) * 1000
        metrics_push.SAMPLES["provider"].record(provider.name, elapsed_ms)
        logger.info(f"[OCR] provider={provider.name} image_bytes={len(image)} chars={len(text)} ms={elapsed_ms:.0f}")
        return 200, {"text": text, "provider": provider.name}
    return 502, {"error": "every OCR provider failed", "failures": failures}
