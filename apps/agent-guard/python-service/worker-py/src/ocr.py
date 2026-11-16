"""Image OCR for POST /ocr: the image goes to the providers in OCR_MODEL_CONFIG_JSON, in order.

A provider is configured, not coded. OCR_MODEL_CONFIG_JSON is a JSON list; each item is a built-in preset by name
("gemma", "azure_vision") or an "http" entry describing one call, e.g.

    {"provider": "http", "name": "google_vision",
     "url": "https://vision.googleapis.com/v1/images:annotate",
     "headers": {"X-Goog-Api-Key": "${OCR_GOOGLE_KEY}"},
     "body": {"requests": [{"image": {"content": "${image_b64}"}, "features": [{"type": "TEXT_DETECTION"}]}]},
     "text": {"path": "responses[0].fullTextAnnotation.text"},
     "timeoutMs": 30000}

The first entry that answers wins; one that fails or times out falls through to the next. Unset, the list is
["gemma"]. Only an HTTP 200 counts as an answer.

Placeholders, in the url, header values and body strings:
  ${image_bytes}  the raw image, as the whole body (the default body)
  ${image_b64}    the image, base64
  ${image_mime}   its type from its first bytes, e.g. image/png
  ${OCR_...}      that environment variable. Only OCR_-prefixed names, so a config can't send other agent-guard
                  secrets (model keys) to the URL it names. An entry naming an unset variable is skipped.

"text" says where the text is in a JSON reply: a "path" (a.b, [0] an index, [] every item of a list), "join" for a
list (default newline) and "each", a nested spec applied to every item first. Without "text" the reply body is the
text. A preset is just such an entry; it is data, not code.
"""

import asyncio
import base64
import binascii
import copy
import json
import logging
import math
import re
import time
from collections.abc import Awaitable, Callable
from typing import Any

import httpx

import alerts
import http_client
import metrics_push
from settings import settings

logger = logging.getLogger(__name__)

MAX_IMAGE_BYTES = 4 * 1024 * 1024  # bounds what reaches a provider; guardrails-service caps images at 2 MB by default
MAX_BODY_BYTES = MAX_IMAGE_BYTES * 4 // 3 + 1024  # the base64 image plus JSON framing
_DEFAULT_TIMEOUT_MS = 30000
_IDENTITY = {"Accept-Encoding": "identity"}  # Pyodide double-gunzip fix, as in providers.py
_ENV_PREFIX = "OCR_"
_IMAGE_KEYS = ("image_bytes", "image_b64", "image_mime")
_PLACEHOLDER = re.compile(r"\$\{([A-Za-z0-9_]+)\}")
_PATH_TOKEN = re.compile(r"[^.\[\]]+|\[\d*\]")

_GEMMA_INSTRUCTION = (
    "Transcribe all text in this image exactly as written, line by line. "
    "Output only the text; if there is none, output nothing."
)
PRESETS: dict[str, dict[str, Any]] = {
    # Gemma on an OpenAI-compatible chat endpoint (vLLM / Foundry) that accepts image input.
    "gemma": {
        "provider": "http",
        "name": "gemma",
        "url": "${OCR_GEMMA_URL}/chat/completions",
        "headers": {"Authorization": "Bearer ${OCR_GEMMA_KEY}"},
        "body": {
            "model": "${OCR_GEMMA_MODEL}",
            "temperature": 0,
            "max_tokens": 4096,
            "messages": [
                {
                    "role": "user",
                    "content": [
                        {"type": "text", "text": _GEMMA_INSTRUCTION},
                        {"type": "image_url", "image_url": {"url": "data:${image_mime};base64,${image_b64}"}},
                    ],
                }
            ],
        },
        "text": {"path": "choices[0].message.content"},
    },
    # Azure Computer Vision OCR v3.2, the call guardrails-service makes directly today (azure_vision.go).
    "azure_vision": {
        "provider": "http",
        "name": "azure_vision",
        "url": "${OCR_AZURE_URL}/vision/v3.2/ocr?detectOrientation=true&language=unk",
        "headers": {"Ocp-Apim-Subscription-Key": "${OCR_AZURE_KEY}", "Content-Type": "application/octet-stream"},
        "body": "${image_bytes}",
        "text": {"path": "regions[].lines[]", "join": "\n", "each": {"path": "words[].text", "join": " "}},
    },
}
_DEFAULT_LIST: list[Any] = ["gemma"]


class NotConfigured(Exception):
    """An entry names an OCR_ variable that isn't set."""


_MAGIC = (
    (b"\x89PNG\r\n\x1a\n", "image/png"),
    (b"\xff\xd8\xff", "image/jpeg"),
    (b"GIF8", "image/gif"),
    (b"BM", "image/bmp"),
    (b"II*\x00", "image/tiff"),
    (b"MM\x00*", "image/tiff"),
)


def image_mime(image: bytes) -> str:
    if image[:4] == b"RIFF" and image[8:12] == b"WEBP":
        return "image/webp"
    return next((mime for magic, mime in _MAGIC if image.startswith(magic)), "application/octet-stream")


def _compile_text(spec: Any) -> dict[str, Any]:
    if not isinstance(spec, dict) or not isinstance(spec.get("path"), str):
        raise ValueError('"text" needs a "path" string')
    path = spec["path"]
    tokens = _PATH_TOKEN.findall(path)
    if (
        not tokens
        or "".join(tokens) != path.replace(".", "")
        or ".." in path
        or path.startswith(".")
        or path.endswith(".")
    ):
        raise ValueError(f"bad text path {path!r}")
    steps: list[str | int | None] = [(int(t[1:-1]) if t[1:-1] else None) if t.startswith("[") else t for t in tokens]
    join = spec.get("join", "\n")
    if not isinstance(join, str):
        raise ValueError('"join" must be a string')
    compiled: dict[str, Any] = {"steps": steps, "join": join}
    if "each" in spec:
        compiled["each"] = _compile_text(spec["each"])
    return compiled


def _walk(value: Any, steps: list[str | int | None]) -> Any:
    """Follow a path; a "[]" step applies the rest of the path to every item and returns them as one flat list."""
    for i, step in enumerate(steps):
        if step is None:
            rest = steps[i + 1 :]
            nested = None in rest
            out: list[Any] = []
            for item in value if isinstance(value, list) else []:
                got = _walk(item, rest)
                if nested:
                    out.extend(got)
                else:
                    out.append(got)
            return out
        if isinstance(step, int):
            value = value[step] if isinstance(value, list) and step < len(value) else None
        else:
            value = value.get(step) if isinstance(value, dict) else None
    return value


def _extract(spec: dict[str, Any], reply: Any) -> str | None:
    """The text a spec selects; None when it selects an empty list, so an enclosing "each" drops that item
    (as azure_vision.go skips a line with no words). A missing or null value reads as empty text."""
    found = _walk(reply, spec["steps"])
    if "each" in spec:
        items = found if isinstance(found, list) else [found]
        found = [text for text in (_extract(spec["each"], item) for item in items) if text is not None]
    if isinstance(found, list):
        return spec["join"].join("" if v is None else str(v) for v in found) if found else None
    return "" if found is None else str(found)


def _placeholders(value: Any) -> set[str]:
    if isinstance(value, str):
        return set(_PLACEHOLDER.findall(value))
    if isinstance(value, dict):
        return set().union(*(_placeholders(v) for v in value.values())) if value else set()
    if isinstance(value, list):
        return set().union(*(_placeholders(v) for v in value)) if value else set()
    return set()


def _fill(value: Any, values: dict[str, str]) -> Any:
    if isinstance(value, str):
        return _PLACEHOLDER.sub(lambda m: values[m.group(1)], value)
    if isinstance(value, dict):
        return {k: _fill(v, values) for k, v in value.items()}
    if isinstance(value, list):
        return [_fill(v, values) for v in value]
    return value


class HttpOCR:
    """One configured OCR call (see the module docstring). Built once per request from its entry."""

    def __init__(self, entry: dict[str, Any]):
        self.name = str(entry.get("name") or "http")
        self.url = entry.get("url")
        self.headers = entry.get("headers") or {}
        self.body = entry.get("body", "${image_bytes}")
        if not isinstance(self.url, str) or not self.url:
            raise ValueError('"url" is required')
        if not isinstance(self.headers, dict) or not all(isinstance(v, str) for v in self.headers.values()):
            raise ValueError('"headers" must map names to strings')
        if isinstance(self.body, str) and "${image_bytes}" in self.body and self.body != "${image_bytes}":
            raise ValueError("${image_bytes} must be the whole body")
        if not isinstance(self.body, str) and "image_bytes" in _placeholders(self.body):
            raise ValueError("${image_bytes} can't go inside a JSON body; use ${image_b64}")
        self.text = _compile_text(entry["text"]) if "text" in entry else None

        self.env: dict[str, str] = {}
        for name in _placeholders([self.url, self.headers, self.body]) - set(_IMAGE_KEYS):
            if not name.startswith(_ENV_PREFIX):
                raise ValueError(f"${{{name}}}: only {_ENV_PREFIX}* variables can be used")
            value = settings.env_value(name)
            if not value:
                raise NotConfigured(f"{name} is not set")
            self.env[name] = value

    def redact(self, message: str) -> str:
        for value in self.env.values():
            if len(value) >= 4:
                message = message.replace(value, "***")
        return message

    async def extract_text(self, image: bytes) -> str:
        values = {**self.env, "image_b64": base64.b64encode(image).decode(), "image_mime": image_mime(image)}
        url = _fill(self.url, {**values, **{k: v.rstrip("/") for k, v in self.env.items()}})
        headers = {**_IDENTITY, **_fill(self.headers, values)}
        request: dict[str, Any]
        if self.body == "${image_bytes}":
            if not any(k.lower() == "content-type" for k in headers):
                headers["Content-Type"] = "application/octet-stream"
            request = {"content": image}
        elif isinstance(self.body, str):
            request = {"content": _fill(self.body, values).encode()}
        else:
            request = {"json": _fill(self.body, values)}
        resp = await http_client.get_client().post(url, headers=headers, **request)
        if resp.status_code != 200:  # as azure_vision.go: anything but 200 is a failure
            logger.warning(f"[OCR] {self.name} HTTP {resp.status_code}: {self.redact(resp.text[:500])!r}")
            raise httpx.HTTPStatusError(f"HTTP {resp.status_code}", request=resp.request, response=resp)
        if self.text is None:
            return resp.text
        return _extract(self.text, resp.json()) or ""


def _entries() -> list[Any]:
    raw = settings.env_value("OCR_MODEL_CONFIG_JSON").strip()
    if not raw:
        return _DEFAULT_LIST
    try:
        entries = json.loads(raw)
    except ValueError:
        entries = None
    if isinstance(entries, list) and all(isinstance(entry, (dict, str)) for entry in entries):
        return entries
    logger.error("[OCR] OCR_MODEL_CONFIG_JSON is not a JSON list of entries and preset names; OCR is off")
    return []


def build_ocr_provider(entry: Any) -> HttpOCR | None:
    if isinstance(entry, str):
        if entry not in PRESETS:
            logger.warning(f"[OCR] unknown preset {entry!r} (known: {sorted(PRESETS)}), skipped")
            return None
        entry = copy.deepcopy(PRESETS[entry])
    name = str(entry.get("name") or "http")
    provider = str(entry.get("provider") or "").strip().lower()
    if provider != "http":
        logger.warning(f"[OCR] {name}: unknown provider {provider!r}, skipped")
        return None
    try:
        return HttpOCR(entry)
    except NotConfigured as exc:
        logger.warning(f"[OCR] {name} not configured ({exc}), skipped")
    except (ValueError, TypeError) as exc:
        logger.error(f"[OCR] {name}: bad entry ({exc}), skipped")
    return None


async def handle_ocr(content_length: str | None, read_body: Callable[[], Awaitable[Any]]) -> tuple[int, dict[str, Any]]:
    """POST /ocr on both runtimes. A declared body too large to hold an accepted image is refused before it is
    read; a body that isn't JSON (or nests too deep to parse) is a 400, not a 500."""
    try:
        too_large = int(content_length or 0) > MAX_BODY_BYTES
    except ValueError:
        too_large = False
    if too_large:
        return 413, {"error": f"request body is over {MAX_BODY_BYTES} bytes"}
    try:
        payload = json.loads(await read_body())
    except (ValueError, RecursionError):
        payload = None
    return await ocr_payload(payload)


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
    if len(image) > MAX_IMAGE_BYTES:
        return 413, {"error": f"image is over {MAX_IMAGE_BYTES} bytes"}

    chain = [(entry, provider) for entry in _entries() if (provider := build_ocr_provider(entry)) is not None]
    if not chain:
        return 503, {"error": "OCR not configured"}

    failures = []
    for entry, provider in chain:
        started = time.perf_counter()
        try:  # a bad timeoutMs fails this entry like any other error, rather than the whole request
            raw_timeout = entry.get("timeoutMs") if isinstance(entry, dict) else None
            timeout_s = float(raw_timeout or _DEFAULT_TIMEOUT_MS) / 1000.0
            if not (timeout_s > 0 and math.isfinite(timeout_s)):
                raise ValueError(f"timeoutMs must be a positive number, got {raw_timeout!r}")
            text = await asyncio.wait_for(provider.extract_text(image), timeout_s)
        except Exception as exc:
            reason = alerts.fallback_reason(exc)
            metrics_push.COUNTS["provider_errors"].increment(f"{provider.name}:{reason}")
            logger.warning(f"[OCR] {provider.name} failed ({reason}): {provider.redact(repr(exc))}")
            failures.append({"provider": provider.name, "reason": reason})
            continue
        elapsed_ms = (time.perf_counter() - started) * 1000
        metrics_push.SAMPLES["provider"].record(provider.name, elapsed_ms)
        logger.info(f"[OCR] provider={provider.name} image_bytes={len(image)} chars={len(text)} ms={elapsed_ms:.0f}")
        return 200, {"text": text, "provider": provider.name}
    return 502, {"error": "every OCR provider failed", "failures": failures}
