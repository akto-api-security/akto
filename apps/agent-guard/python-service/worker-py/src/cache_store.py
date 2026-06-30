"""Redis-backed vector store for the per-scanner semantic cache.

This replaces the Cloudflare Vectorize binding from the upstream shadow-cache PR
with a portable Redis vector index, so the cache works on the containerised
(Azure / docker-compose) deployment where there is no Cloudflare.

Requires Redis with the **RediSearch** module (e.g. `redis/redis-stack-server`,
or Azure Cache for Redis Enterprise with the search module) for KNN vector
search. Everything here is **fail-open**: if `REDIS_URL` is unset or Redis is
unreachable, every call no-ops/returns None and the caller falls back to running
the real scan. That also makes this module a clean no-op on the Cloudflare path,
which never sets `REDIS_URL`.

Layout (one hash per entry):
    key   = gcache:{scanner_key}:{text_hash}      # deterministic → re-scan overwrites
    fields= vec(FLOAT32[384] bytes), scanner_key(TAG), is_valid, risk_score,
            reason, inserted_at
TTL is enforced natively via EXPIRE (Vectorize had no TTL; Redis does), so an
expired entry simply disappears and reads as a miss. inserted_at is still stored
so the shadow alert can show the matched entry's age.
"""

import logging
from array import array
from typing import Any, Dict, List, Optional

from settings import settings

logger = logging.getLogger(__name__)

EMBEDDING_DIM = 384  # all-MiniLM-L6-v2
_KEY_PREFIX = "gcache:"
_DEFAULT_INDEX = "guardrails_shadow_cache"
_RETURN_FIELDS = (
    "dist", "is_valid", "risk_score", "reason", "inserted_at",
    "task_intent", "risk_intent", "scope_bucket",
)

# Lazily-created singletons (one Redis connection pool per process).
_client = None
_client_init = False
_index_ready = False


def _index_name() -> str:
    return (getattr(settings, "CACHE_REDIS_INDEX", "") or "").strip() or _DEFAULT_INDEX


def _get_client():
    """Return an async Redis client, or None when REDIS_URL is unset/unusable.

    decode_responses is left False so vector payloads stay as raw bytes; string
    metadata is decoded explicitly on read.
    """
    global _client, _client_init
    if _client_init:
        return _client
    _client_init = True
    url = (getattr(settings, "REDIS_URL", "") or "").strip()
    if not url:
        return None
    try:
        import redis.asyncio as redis  # imported lazily so the dep is optional
        _client = redis.from_url(url, decode_responses=False)
    except Exception as exc:
        logger.warning(f"[cache_store] redis client init failed: {exc}")
        _client = None
    return _client


def _pack(vec: List[float]) -> bytes:
    return array("f", vec).tobytes()


async def _ensure_index(client) -> bool:
    """Create the RediSearch index once per process (idempotent). Fail-open."""
    global _index_ready
    if _index_ready:
        return True
    try:
        await client.execute_command(
            "FT.CREATE", _index_name(),
            "ON", "HASH",
            "PREFIX", "1", _KEY_PREFIX,
            "SCHEMA",
            "scanner_key", "TAG",
            "vec", "VECTOR", "FLAT", "6",
            "TYPE", "FLOAT32",
            "DIM", str(EMBEDDING_DIM),
            "DISTANCE_METRIC", "COSINE",
        )
        _index_ready = True
    except Exception as exc:
        # "Index already exists" is the common, expected case across restarts.
        if "already exists" in str(exc).lower():
            _index_ready = True
        else:
            logger.warning(f"[cache_store] FT.CREATE failed: {exc}")
    return _index_ready


def _decode(v: Any) -> str:
    return v.decode("utf-8") if isinstance(v, (bytes, bytearray)) else str(v)


def _bget(d: Dict[Any, Any], key: str) -> Any:
    """Fetch from a dict whose keys may be str or bytes (decode_responses=False)."""
    if key in d:
        return d[key]
    return d.get(key.encode("utf-8"))


def _shape_match(flat: Dict[str, Any]) -> Dict[str, Any]:
    return {
        "is_valid": _decode(flat.get("is_valid", "1")) in ("1", "true", "True"),
        "risk_score": float(_decode(flat.get("risk_score", "0")) or 0.0),
        "reason": _decode(flat.get("reason", "")),
        "distance": float(_decode(flat.get("dist", "0")) or 0.0),
        "inserted_at": float(_decode(flat.get("inserted_at", "0")) or 0.0),
        # Intent triple — empty on entries written before the intent layer existed.
        "task_intent": _decode(flat.get("task_intent", "")),
        "risk_intent": _decode(flat.get("risk_intent", "")),
        "scope_bucket": _decode(flat.get("scope_bucket", "")),
    }


def _parse_search_reply(reply: Any) -> Optional[Dict[str, Any]]:
    """Parse the top match out of an FT.SEARCH reply, or None.

    redis-py normalises FT.SEARCH differently across versions:
      - >=5 / RESP3: a dict {results: [{extra_attributes: {field: val}}], ...}
      - RESP2 raw:   a flat list [count, key1, [f1, v1, ...], key2, [...], ...]
    Both are handled so the cache works regardless of client/protocol.
    """
    try:
        if not reply:
            return None
        if isinstance(reply, dict):  # redis-py >=5 / RESP3 map form
            results = _bget(reply, "results") or []
            if not results:
                return None
            attrs = _bget(results[0], "extra_attributes") or {}
            flat = {_decode(k): v for k, v in attrs.items()}
            return _shape_match(flat)
        # RESP2 raw list form
        if int(reply[0]) == 0 or len(reply) < 3:
            return None
        fields = reply[2]
        flat = {_decode(fields[i]): fields[i + 1] for i in range(0, len(fields) - 1, 2)}
        return _shape_match(flat)
    except Exception as exc:
        logger.warning(f"[cache_store] parse reply failed: {exc}")
        return None


async def query(vec: List[float], scanner_key: str) -> Optional[Dict[str, Any]]:
    """Return the nearest stored verdict for scanner_key, or None.

    distance is RediSearch COSINE distance (1 - cosine similarity), so the
    caller's threshold/TTL logic carries over unchanged from the Vectorize path.
    """
    client = _get_client()
    if client is None or not await _ensure_index(client):
        return None
    try:
        q = f"(@scanner_key:{{{scanner_key}}})=>[KNN 1 @vec $vec AS dist]"
        reply = await client.execute_command(
            "FT.SEARCH", _index_name(), q,
            "PARAMS", "2", "vec", _pack(vec),
            "RETURN", str(len(_RETURN_FIELDS)), *_RETURN_FIELDS,
            "SORTBY", "dist",
            "DIALECT", "2",
        )
        return _parse_search_reply(reply)
    except Exception as exc:
        logger.warning(f"[cache_store] FT.SEARCH failed: {exc}")
        return None


async def exact_get(scanner_key: str, text_hash: str) -> Optional[Dict[str, Any]]:
    """Exact-repeat fast path: a direct HGETALL of the deterministic key — no
    embedding, no vector FT.SEARCH. Returns the stored verdict with distance 0.0,
    or None on miss. Native EXPIRE means an expired entry is already gone (a miss),
    so the caller falls through to the embed + KNN fuzzy path. Cheap enough to run
    on every request; it offloads both the embedder (CPU) and the search index.
    """
    client = _get_client()
    if client is None:
        return None
    try:
        key = f"{_KEY_PREFIX}{scanner_key}:{text_hash}"
        raw = await client.hgetall(key)
        if not raw:
            return None
        flat = {_decode(k): v for k, v in raw.items()}
        match = _shape_match(flat)
        match["distance"] = 0.0  # exact text match
        return match
    except Exception as exc:
        logger.warning(f"[cache_store] exact_get failed: {exc}")
        return None


async def upsert(vec: List[float], scanner_key: str, entry_id: str,
                 is_valid: bool, risk_score: float, reason: str,
                 inserted_at: int, ttl_seconds: int,
                 task_intent: str = "", risk_intent: str = "",
                 scope_bucket: str = "") -> None:
    """Store/refresh one verdict vector. Deterministic key → re-scan overwrites.

    EXPIRE gives the entry a native TTL, so expired matches vanish on their own.
    The intent triple (task/risk/scope) is stored alongside the verdict so a later
    cache hit returns the previously-computed intents without re-classifying.
    """
    client = _get_client()
    if client is None or not await _ensure_index(client):
        return
    try:
        key = f"{_KEY_PREFIX}{scanner_key}:{entry_id}"
        await client.hset(key, mapping={
            "vec": _pack(vec),
            "scanner_key": scanner_key,
            "is_valid": "1" if is_valid else "0",
            "risk_score": repr(float(risk_score or 0.0)),
            "reason": reason or "",
            "inserted_at": str(int(inserted_at)),
            "task_intent": task_intent or "",
            "risk_intent": risk_intent or "",
            "scope_bucket": scope_bucket or "",
        })
        if ttl_seconds and ttl_seconds > 0:
            await client.expire(key, int(ttl_seconds))
    except Exception as exc:
        logger.warning(f"[cache_store] HSET/EXPIRE failed: {exc}")
