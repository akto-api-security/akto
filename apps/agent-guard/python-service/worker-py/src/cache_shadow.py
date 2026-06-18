"""Per-scanner semantic cache — shadow mode.

Observe-only: for each cascade (LLM) scan we compute what a semantic cache
*would* have answered and Slack-alert how it compares to the real verdict, but
we never serve the cached answer. The caller (guardrails-service) is oblivious —
`/scan` returns the real scanner result exactly as before.

Flow (all fire-and-forget, scheduled via entry._schedule so it never blocks the
scan response, and fail-open so a cache/embedder/Vectorize outage is harmless):

    text + scanner + config
      → scanner_key = config_hash(scanner, type, config)   # per-scanner partition
      → vec = embed(text)                                  # embedder via ANONYMIZER_WORKER binding
      → match = Vectorize KNN(vec, filter scanner_key)     # nearest prior verdict
      → outcome = miss | hit_match | hit_mismatch          # vs the real verdict
      → Slack alert
      → Vectorize upsert(vec, real verdict)                # warm the cache

Entries expire after CACHE_TTL_SECONDS (6h default). Vectorize has no native TTL,
so it's enforced on read — a match older than the TTL counts as a miss — and the
entry id is deterministic per (scanner, text), so a re-scan overwrites and
refreshes the same vector instead of growing the index.

When we later flip this to serve traffic, the only change is: on a fresh hit
within threshold, return the cached verdict instead of running the cascade.
"""

import hashlib
import json
import logging
import time
from typing import Any, Dict, List, Optional

import httpx
from js import Object
from pyodide.ffi import to_js

import alerts
from settings import settings

logger = logging.getLogger(__name__)

_DEFAULT_DISTANCE_THRESHOLD = 0.15
_DEFAULT_TTL_SECONDS = 6 * 3600  # 6h; Vectorize has no native TTL, enforced on read
_EMBED_TIMEOUT_S = 10.0
# Config keys that don't change the verdict and so must not change the cache key.
_NON_VERDICT_CONFIG_KEYS = {"storeAllResults"}


# --------------------------------------------------------------------------- #
# Settings helpers (worker env values arrive as strings)
# --------------------------------------------------------------------------- #
def enabled() -> bool:
    return str(getattr(settings, "CACHE_SHADOW_ENABLED", "")).strip().lower() in ("1", "true", "yes")


def _threshold() -> float:
    raw = str(getattr(settings, "CACHE_DISTANCE_THRESHOLD", "")).strip()
    try:
        return float(raw) if raw else _DEFAULT_DISTANCE_THRESHOLD
    except ValueError:
        return _DEFAULT_DISTANCE_THRESHOLD


def _ttl_seconds() -> float:
    raw = str(getattr(settings, "CACHE_TTL_SECONDS", "")).strip()
    try:
        return float(raw) if raw else _DEFAULT_TTL_SECONDS
    except ValueError:
        return _DEFAULT_TTL_SECONDS


# --------------------------------------------------------------------------- #
# Per-scanner key
# --------------------------------------------------------------------------- #
def config_hash(scanner_name: str, scanner_type: str, config: Dict[str, Any]) -> str:
    """16-char fingerprint of a single scanner + its verdict-affecting config.

    Two requests share cache entries only when the scanner, type, and config
    (model lineup, thresholds, ban lists, …) all match. modelConfigs order is
    preserved because cascade order can change the verdict; noise like
    storeAllResults is dropped so it never fragments the cache.
    """
    cfg = {k: v for k, v in (config or {}).items() if k not in _NON_VERDICT_CONFIG_KEYS}
    canonical = json.dumps(cfg, sort_keys=True, separators=(",", ":"), default=str)
    content = f"{scanner_name}|{scanner_type}|{canonical}"
    return hashlib.sha256(content.encode("utf-8")).hexdigest()[:16]


# --------------------------------------------------------------------------- #
# Embedding (embedder container; same worker that hosts the anonymizer)
# --------------------------------------------------------------------------- #
async def _embed_via_binding(text: str, env) -> Optional[List[float]]:
    """Embed through the ANONYMIZER_WORKER service binding.

    The sibling worker hosts both containers and routes `/embed` to the embedder.
    This is the path that works on a *deployed* worker — a Worker can't fetch
    another Worker over its public `workers.dev` URL, only via a service binding.
    """
    binding = getattr(env, "ANONYMIZER_WORKER", None)
    if binding is None:
        return None
    init = _js({"method": "POST",
                "headers": {"content-type": "application/json"},
                "body": json.dumps({"text": text})})
    try:
        resp = await binding.fetch("https://embedder/embed", init)
        if resp.status < 200 or resp.status >= 300:
            logger.warning(f"[cache_shadow] embedder binding returned {resp.status}")
            return None
        return json.loads(await resp.text()).get("vector") or None
    except Exception as exc:
        logger.warning(f"[cache_shadow] embed via binding failed: {exc}")
        return None


async def _embed_via_http(text: str) -> Optional[List[float]]:
    """Local-dev fallback: hit EMBEDDER_URL (e.g. http://localhost:8094) directly.

    Not usable from a deployed worker (can't reach localhost, and worker ->
    workers.dev is blocked) — that's what the service binding is for.
    """
    base = (settings.EMBEDDER_URL or "").strip().rstrip("/")
    if not base:
        return None
    try:
        async with httpx.AsyncClient(timeout=_EMBED_TIMEOUT_S) as client:
            resp = await client.post(f"{base}/embed", json={"text": text},
                                     headers={"Accept-Encoding": "identity"})
        if resp.status_code >= 400:
            logger.warning(f"[cache_shadow] embedder returned {resp.status_code}")
            return None
        return resp.json().get("vector") or None
    except Exception as exc:
        logger.warning(f"[cache_shadow] embed via http failed: {exc}")
        return None


async def _embed(text: str, env) -> Optional[List[float]]:
    """Embed text → 384-dim vector, or None (fail-open).

    Service binding first (works deployed), then EMBEDDER_URL over HTTP for local
    dev. Either path returning None surfaces as outcome=error/embed_unavailable.
    """
    vec = await _embed_via_binding(text, env)
    if vec is not None:
        return vec
    return await _embed_via_http(text)


# --------------------------------------------------------------------------- #
# Vector store (Cloudflare Vectorize binding)
# --------------------------------------------------------------------------- #
def _js(obj: Any):
    """Convert a Python value to JS, turning dicts into plain JS objects."""
    return to_js(obj, dict_converter=Object.fromEntries)


async def _vector_query(env, vec: List[float], scanner_key: str) -> Optional[Dict[str, Any]]:
    """Return the nearest stored verdict for scanner_key as a dict, or None.

    The dict carries {is_valid, risk_score, reason, distance, inserted_at}.
    distance is cosine distance (1 - similarity) and inserted_at is the entry's
    epoch seconds; the caller applies the distance threshold and the TTL.
    """
    index = getattr(env, "VECTORIZE", None)
    if index is None:
        logger.warning("[cache_shadow] VECTORIZE binding not set; treating as miss")
        return None
    try:
        opts = _js({
            "topK": 1,
            "filter": {"scanner_key": {"$eq": scanner_key}},
            "returnMetadata": "all",
        })
        res = await index.query(to_js(vec), opts)
        matches = getattr(res, "matches", None)
        if matches is None or len(matches) == 0:
            return None
        top = matches[0]
        score = float(getattr(top, "score", 0.0))  # cosine similarity in [-1, 1]
        meta = getattr(top, "metadata", None)
        meta = meta.to_py() if meta is not None and hasattr(meta, "to_py") else (meta or {})
        return {
            "is_valid": bool(meta.get("is_valid", True)),
            "risk_score": float(meta.get("risk_score", 0.0) or 0.0),
            "reason": str(meta.get("reason", "") or ""),
            "distance": 1.0 - score,
            "inserted_at": float(meta.get("inserted_at", 0) or 0),
        }
    except Exception as exc:
        logger.warning(f"[cache_shadow] Vectorize query failed: {exc}")
        return None


async def _vector_upsert(env, vec: List[float], scanner_key: str, text: str,
                         real: Dict[str, Any]) -> None:
    index = getattr(env, "VECTORIZE", None)
    if index is None:
        return
    try:
        details = real.get("details") or {}
        # Deterministic id (scanner + text): an identical re-scan overwrites this
        # same vector and refreshes inserted_at, rather than accreting a new
        # vector per scan. inserted_at drives the read-side TTL.
        text_hash = hashlib.sha256((text or "").encode("utf-8")).hexdigest()[:16]
        entry = {
            "id": f"{scanner_key}:{text_hash}",
            "values": vec,
            "metadata": {
                "scanner_key": scanner_key,
                "is_valid": bool(real.get("is_valid", True)),
                "risk_score": float(real.get("risk_score", 0.0) or 0.0),
                "reason": str(details.get("reason", "") or ""),
                "inserted_at": int(time.time()),
            },
        }
        await index.upsert(_js([entry]))
    except Exception as exc:
        logger.warning(f"[cache_shadow] Vectorize upsert failed: {exc}")


# --------------------------------------------------------------------------- #
# Orchestration
# --------------------------------------------------------------------------- #
def _classify(cached: Optional[Dict[str, Any]], threshold: float, ttl: float,
              now: float, real_valid: bool) -> str:
    if cached is None or cached["distance"] > threshold:
        return "miss"
    if now - cached["inserted_at"] > ttl:  # expired: Vectorize has no native TTL
        return "miss"
    return "hit_match" if cached["is_valid"] == real_valid else "hit_mismatch"


async def observe(scanner_name: str, scanner_type: str, text: str,
                  config: Dict[str, Any], real_result: Dict[str, Any], env) -> None:
    """Shadow-check one cascade scan and Slack-alert the comparison.

    Must be scheduled fire-and-forget (entry._schedule). Never raises; never
    affects the response. Stores the real verdict afterwards to warm the cache.
    """
    if not enabled():
        return

    real_valid = bool(real_result.get("is_valid", True))
    real_details = real_result.get("details") or {}
    info: Dict[str, Any] = {
        "scanner_name": scanner_name,
        "scanner_type": scanner_type,
        "text": text,
        "real_is_valid": real_valid,
        "real_reason": str(real_details.get("reason", "") or ""),
        "real_risk": real_result.get("risk_score", 0.0),
        "threshold": _threshold(),
        "ttl_s": _ttl_seconds(),
    }

    t0 = time.time()
    try:
        scanner_key = config_hash(scanner_name, scanner_type, config)
        info["scanner_key"] = scanner_key

        vec = await _embed(text, env)
        if vec is None:
            info["outcome"] = "error"
            info["error"] = "embed_unavailable"
            info["latency_ms"] = round((time.time() - t0) * 1000, 1)
            await alerts.post_cache_shadow(info)
            return

        now = time.time()
        cached = await _vector_query(env, vec, scanner_key)
        outcome = _classify(cached, info["threshold"], info["ttl_s"], now, real_valid)

        info["outcome"] = outcome
        info["latency_ms"] = round((time.time() - t0) * 1000, 1)
        if cached is not None:
            info["cached_is_valid"] = cached["is_valid"]
            info["cached_reason"] = cached["reason"]
            info["distance"] = cached["distance"]
            age = now - cached["inserted_at"]
            info["age_s"] = round(age, 1)
            if age > info["ttl_s"]:  # nearest neighbour existed but expired → miss
                info["stale"] = True

        await alerts.post_cache_shadow(info)

        # Warm the cache for future requests (always, regardless of outcome). The
        # deterministic id means a re-scan overwrites this entry and refreshes its
        # TTL instead of growing the index.
        await _vector_upsert(env, vec, scanner_key, text, real_result)
    except Exception as exc:  # absolute backstop — shadow must never surface
        logger.warning(f"[cache_shadow] observe failed for {scanner_name}: {exc}")
