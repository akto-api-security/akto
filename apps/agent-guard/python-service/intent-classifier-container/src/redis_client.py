"""Thin read-only Redis client for the model-artifact L2 cache.

mcp-endpoint-shield (Go) is the single writer of model blobs — it persists
whatever intent_classifier.train() returns to Redis under a key it constructs
itself (see mcp/intentguard/store.go). This service never writes to Redis; it
only reads a blob on an L1 (`_models`) miss, so key-format ownership stays in
one place (Go).

Fail-open throughout: if REDIS_URL is unset or Redis is unreachable, get()
returns None and the caller treats the agent as cold (safe — falls through to
ESCALATE upstream), never raises into the request path.
"""

import logging
import os
from typing import Optional

logger = logging.getLogger(__name__)

_client = None
_client_initialized = False


def _get_client():
    global _client, _client_initialized
    if _client_initialized:
        return _client
    _client_initialized = True
    url = os.environ.get("REDIS_URL", "").strip()
    if not url:
        return None
    try:
        import redis
        _client = redis.Redis.from_url(url)
    except Exception as exc:
        logger.warning(f"[intent_classifier] redis init failed: {exc}")
        _client = None
    return _client


def get(key: str) -> Optional[bytes]:
    if not key:
        return None
    client = _get_client()
    if client is None:
        return None
    try:
        return client.get(key)
    except Exception as exc:
        logger.warning(f"[intent_classifier] redis GET failed key={key!r}: {exc}")
        return None
