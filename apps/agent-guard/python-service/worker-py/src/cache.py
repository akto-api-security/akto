"""Per-scanner semantic cache (Redis vector store + embedder service).

Ported from the upstream Cloudflare shadow-cache PR, with the Vectorize binding
swapped for Redis (see cache_store.py) and the service-binding embedder swapped
for a plain HTTP call to EMBEDDER_URL — so it runs on the containerised
(Azure / docker-compose) deployment.

Three modes, via CACHE_MODE (default `observe`; CACHE_SHADOW_ENABLED is a
back-compat alias where true → observe):

  off      - cache never runs.
  observe  - shadow only: after the real scan, fire-and-forget embed → KNN
             lookup → compare → Slack-alert → warm. Never affects /scan, never
             adds request latency.
  decide   - serve from cache: before the cascade, embed → KNN lookup; on a
             fresh, within-threshold hit whose cached verdict is is_valid=True,
             short-circuit and return it (skipping the cascade) and Slack-alert
             the served hit. A cached *block*, a miss, or any error always falls
             through to the real cascade — we never let the cache block traffic.

Everything is fail-open: a missing/unreachable embedder or Redis degrades to a
miss (observe) or a fall-through (decide), never an error to the caller.

Flow:
    scanner_key = config_hash(scanner, type, config)   # per-scanner partition
    vec         = embed(text)                           # EMBEDDER_URL /embed
    cached      = redis KNN(vec, filter scanner_key)    # nearest prior verdict
    distance ≤ threshold and fresh (≤ TTL) → hit, else miss
"""

import hashlib
import json
import logging
import time
from typing import Any, Dict, List, Optional

import httpx

import alerts
import cache_store
from settings import settings

logger = logging.getLogger(__name__)

_DEFAULT_DISTANCE_THRESHOLD = 0.15
# Blocked verdicts are served only on a (near-)exact repeat. Default 0.0 keeps
# blocks unserved (COSINE distance of identical text is ~1e-7, never ≤ 0); set
# CACHE_BLOCK_DISTANCE_THRESHOLD to a small epsilon (e.g. 1e-4) to serve exact
# repeats. Kept far below the safe threshold so semantically-similar (but not
# identical) prompts are never blocked from a cached neighbour.
_DEFAULT_BLOCK_DISTANCE_THRESHOLD = 0.0
_DEFAULT_TTL_SECONDS = 6 * 3600  # 6h
_EMBED_TIMEOUT_S = 10.0
_MODES = ("off", "observe", "decide")
# Config keys that don't change the verdict and so must not change the cache key.
_NON_VERDICT_CONFIG_KEYS = {"storeAllResults"}


# --------------------------------------------------------------------------- #
# Mode / settings helpers (worker env values arrive as strings)
# --------------------------------------------------------------------------- #
def mode() -> str:
    raw = str(getattr(settings, "CACHE_MODE", "")).strip().lower()
    if raw in _MODES:
        return raw
    alias = str(getattr(settings, "CACHE_SHADOW_ENABLED", "")).strip().lower()
    if alias in ("1", "true", "yes"):
        return "observe"
    if alias in ("0", "false", "no"):
        return "off"
    return "observe"  # default


def enabled() -> bool:
    """True in observe or decide mode (cache work should run at all)."""
    return mode() in ("observe", "decide")


def serving() -> bool:
    """True only in decide mode (cache may short-circuit the cascade)."""
    return mode() == "decide"


def _threshold() -> float:
    raw = str(getattr(settings, "CACHE_DISTANCE_THRESHOLD", "")).strip()
    try:
        return float(raw) if raw else _DEFAULT_DISTANCE_THRESHOLD
    except ValueError:
        return _DEFAULT_DISTANCE_THRESHOLD


def _block_threshold() -> float:
    raw = str(getattr(settings, "CACHE_BLOCK_DISTANCE_THRESHOLD", "")).strip()
    try:
        return float(raw) if raw else _DEFAULT_BLOCK_DISTANCE_THRESHOLD
    except ValueError:
        return _DEFAULT_BLOCK_DISTANCE_THRESHOLD


def _threshold_for(is_valid: bool) -> float:
    """Per-verdict match tolerance: safe uses the fuzzy threshold, blocked uses
    the strict (exact-repeat) one."""
    return _threshold() if is_valid else _block_threshold()


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


def _text_hash(text: str) -> str:
    return hashlib.sha256((text or "").encode("utf-8")).hexdigest()[:16]


# --------------------------------------------------------------------------- #
# Embedding (embedder container over HTTP)
# --------------------------------------------------------------------------- #
async def _embed(text: str) -> Optional[List[float]]:
    """Embed text → 384-dim vector via EMBEDDER_URL /embed, or None (fail-open)."""
    base = (settings.EMBEDDER_URL or "").strip().rstrip("/")
    if not base:
        return None
    try:
        async with httpx.AsyncClient(timeout=_EMBED_TIMEOUT_S) as client:
            resp = await client.post(f"{base}/embed", json={"text": text},
                                     headers={"Accept-Encoding": "identity"})
        if resp.status_code >= 400:
            logger.warning(f"[cache] embedder returned {resp.status_code}")
            return None
        return resp.json().get("vector") or None
    except Exception as exc:
        logger.warning(f"[cache] embed failed: {exc}")
        return None


# --------------------------------------------------------------------------- #
# Prepare / classify
# --------------------------------------------------------------------------- #
async def prepare(scanner_name: str, scanner_type: str, text: str,
                  config: Dict[str, Any]) -> Dict[str, Any]:
    """Embed + KNN lookup once. Returns a reusable {scanner_key, vec, cached}.

    Used on the request path in decide mode; the same result is handed to
    observe() afterwards so a miss embeds only once. Fail-open: vec/cached may
    be None.
    """
    scanner_key = config_hash(scanner_name, scanner_type, config)
    vec = await _embed(text)
    cached = await cache_store.query(vec, scanner_key) if vec is not None else None
    return {"scanner_key": scanner_key, "vec": vec, "cached": cached}


def _fresh_hit(cached: Optional[Dict[str, Any]], threshold: float, ttl: float,
               now: float) -> bool:
    return (
        cached is not None
        and cached["distance"] <= threshold
        and (now - cached["inserted_at"]) <= ttl
    )


def _classify(cached: Optional[Dict[str, Any]], threshold: float, ttl: float,
              now: float, real_valid: bool) -> str:
    if not _fresh_hit(cached, threshold, ttl, now):
        return "miss"
    return "hit_match" if cached["is_valid"] == real_valid else "hit_mismatch"


# --------------------------------------------------------------------------- #
# decide mode: serve a safe hit (pure — operates on a prepared lookup)
# --------------------------------------------------------------------------- #
def try_serve(prep: Dict[str, Any], scanner_name: str, scanner_type: str,
              text: str) -> Optional[Dict[str, Any]]:
    """Return a served response (+ alert info) for a fresh cache hit, else None.

    Per-verdict tolerance: a cached safe verdict (is_valid=True) is served on a
    fuzzy match (CACHE_DISTANCE_THRESHOLD, default 0.15); a cached block
    (is_valid=False) is served only on a (near-)exact repeat
    (CACHE_BLOCK_DISTANCE_THRESHOLD, default 0.0 → blocks unserved). A miss or
    anything stale/over-threshold returns None so the caller runs the real
    cascade. The served verdict (is_valid) is returned so the caller can block.
    """
    cached = prep.get("cached")
    if cached is None:
        return None
    is_valid = bool(cached["is_valid"])
    threshold, ttl, now = _threshold_for(is_valid), _ttl_seconds(), time.time()
    if not _fresh_hit(cached, threshold, ttl, now):
        return None

    details = {
        "scanner_type": scanner_type,
        "reason": cached["reason"],
        "cache": "served",
        "cache_distance": cached["distance"],
    }
    alert_info = {
        "scanner_name": scanner_name,
        "scanner_type": scanner_type,
        "text": text,
        "outcome": "served",
        "scanner_key": prep.get("scanner_key", "—"),
        "real_is_valid": is_valid,
        "cached_is_valid": is_valid,
        "real_reason": cached["reason"],
        "distance": cached["distance"],
        "threshold": threshold,
        "age_s": round(now - cached["inserted_at"], 1),
        "ttl_s": ttl,
    }
    return {"is_valid": is_valid, "risk_score": cached["risk_score"],
            "details": details, "alert": alert_info}


# --------------------------------------------------------------------------- #
# observe: shadow-compare the real verdict and warm the cache (fire-and-forget)
# --------------------------------------------------------------------------- #
async def observe(scanner_name: str, scanner_type: str, text: str,
                  config: Dict[str, Any], real_result: Dict[str, Any],
                  prep: Optional[Dict[str, Any]] = None) -> None:
    """Compare what the cache would have answered to the real verdict, alert,
    and store the real verdict to warm the cache.

    Must be scheduled fire-and-forget. Never raises; never affects the response.
    Reuses `prep` (from decide mode) when given so a miss embeds only once.
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
        if prep is not None:
            scanner_key, vec, cached = prep["scanner_key"], prep.get("vec"), prep.get("cached")
        else:
            scanner_key = config_hash(scanner_name, scanner_type, config)
            vec = await _embed(text)
            cached = await cache_store.query(vec, scanner_key) if vec is not None else None
        info["scanner_key"] = scanner_key

        if vec is None:
            info["outcome"] = "error"
            info["error"] = "embed_unavailable"
            info["latency_ms"] = round((time.time() - t0) * 1000, 1)
            await alerts.post_cache_shadow(info)
            return

        now = time.time()
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
        await cache_store.upsert(
            vec, scanner_key, _text_hash(text),
            is_valid=real_valid,
            risk_score=float(real_result.get("risk_score", 0.0) or 0.0),
            reason=str(real_details.get("reason", "") or ""),
            inserted_at=int(now),
            ttl_seconds=int(info["ttl_s"]),
        )
    except Exception as exc:  # absolute backstop — cache must never surface
        logger.warning(f"[cache] observe failed for {scanner_name}: {exc}")
