"""Durable cross-pod training corpus via database-abstractor.

Every time the LLM cascade produces a verdict (ESCALATE → cascade → result),
the embedding vector + intent triple is queued here. When the buffer reaches
BATCH_SIZE we fire one async POST to DATABASE_ABSTRACTOR_SERVICE_URL which
writes the batch to MongoDB.

This closes the loop that trainer.py's in-memory-only approach cannot: pod
restarts keep all labelled examples, and every pod shares the same training
corpus. trainer.py still drives the per-pod LogReg retraining cycle from its
own in-memory buffer (fast, no DB hop); corpus.py just ensures nothing is lost.

Design:
  - Zero per-request synchronous overhead: queue() is pure in-process.
  - flush() is fired via schedule_fn (fire-and-forget coroutine), same as
    cache.observe() — it runs after the response has been returned.
  - Fail-open: any HTTP error logs a warning and the batch is dropped rather
    than blocking traffic or retrying forever.
  - The DB-abstractor endpoint is /api/intent-corpus/bulk (POST, JSON body
    {"examples": [...]}). Add the corresponding handler on the abstractor side.
"""

import asyncio
import logging
from collections import deque
from typing import Any, Dict, List, Optional

from settings import settings

logger = logging.getLogger(__name__)

_BATCH_SIZE = 50      # flush when buffer reaches this many examples
_MAX_BUFFER  = 2000   # hard cap: deque drops oldest when full
_ENDPOINT    = "/api/intent-corpus/bulk"

# In-process buffer shared across all async tasks on this pod.
_buffer: "deque[Dict[str, Any]]" = deque(maxlen=_MAX_BUFFER)
# Prevent concurrent flushes from sending the same examples twice.
_flush_lock: Optional[asyncio.Lock] = None


def _get_lock() -> asyncio.Lock:
    global _flush_lock
    if _flush_lock is None:
        _flush_lock = asyncio.Lock()
    return _flush_lock


def _url() -> Optional[str]:
    base = (getattr(settings, "DATABASE_ABSTRACTOR_SERVICE_URL", "") or "").rstrip("/")
    return f"{base}{_ENDPOINT}" if base else None


def queue(
    agent_host: str,
    vec: Optional[List[float]],
    is_valid: bool,
    triple: Dict[str, str],
    confidence: float = 0.0,
) -> bool:
    """Append one learned example to the in-process buffer.

    Returns True when the batch threshold is crossed so the caller can
    schedule flush() as a fire-and-forget coroutine.

    vec=None is a no-op (no embedding → nothing to train on).
    """
    if not vec:
        return False
    _buffer.append({
        "agent_host":   agent_host,
        "vector":       vec,
        "is_valid":     is_valid,
        "task_intent":  triple.get("task_intent", ""),
        "risk_intent":  triple.get("risk_intent", ""),
        "scope_bucket": triple.get("scope_bucket", ""),
        "confidence":   round(float(confidence), 4),
    })
    return len(_buffer) >= _BATCH_SIZE


async def flush() -> None:
    """POST the current buffer to the database-abstractor service.

    Fail-open: errors are logged and the batch is discarded rather than
    retried synchronously. The lock ensures only one flush runs at a time;
    if a second flush fires while one is in flight it exits immediately.
    """
    url = _url()
    if not url:
        logger.debug("[corpus] DATABASE_ABSTRACTOR_SERVICE_URL not configured — skipping flush")
        return

    lock = _get_lock()
    if lock.locked():
        return  # a flush is already in flight; this batch will go next time
    async with lock:
        if not _buffer:
            return
        batch = list(_buffer)
        _buffer.clear()

    try:
        import http_client
        client = http_client.get_client()
        resp = await client.post(
            url,
            json={"examples": batch},
            headers={"Accept-Encoding": "identity"},
            timeout=15.0,
        )
        if resp.status_code >= 400:
            logger.warning(f"[corpus] db-abstractor returned {resp.status_code} — {len(batch)} examples lost")
        else:
            logger.info(f"[corpus] flushed {len(batch)} examples to db-abstractor")
    except Exception as exc:
        logger.warning(f"[corpus] flush failed ({len(batch)} examples dropped): {exc}")
