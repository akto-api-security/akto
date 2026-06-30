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
from typing import Any, Dict, List, Optional, Tuple

from settings import settings

logger = logging.getLogger(__name__)

_BATCH_SIZE = 20      # flush when buffer reaches this many examples
_MAX_BUFFER  = 2000   # hard cap: deque drops oldest when full
_ENDPOINT      = "/api/bulkInsertCorpusExamples"
_LOAD_ENDPOINT = "/api/loadCorpusForAgent"

# In-process buffer shared across all async tasks on this pod.
_buffer: "deque[Dict[str, Any]]" = deque(maxlen=_MAX_BUFFER)
_flush_lock: Optional[asyncio.Lock] = None
_warmed: set = set()


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


async def load(agent_host: str) -> List[Tuple[List[float], bool]]:
    """Fetch stored training examples for agent_host from the database-abstractor.

    Returns a list of (vector, is_valid) pairs ready to feed into trainer.record().
    Returns [] on any error or when the URL is not configured — fail-open.
    """
    base = (getattr(settings, "DATABASE_ABSTRACTOR_SERVICE_URL", "") or "").rstrip("/")
    if not base:
        return []
    url = f"{base}{_LOAD_ENDPOINT}"
    try:
        import http_client
        client = http_client.get_client()
        resp = await client.post(
            url,
            json={"agent_host": agent_host},
            headers={"Accept-Encoding": "identity"},
            timeout=10.0,
        )
        if resp.status_code >= 400:
            logger.warning(f"[corpus] load returned {resp.status_code} for agent={agent_host!r}")
            return []
        data = resp.json()
        examples = data.get("examples") or []
        # Each item is a bucket: {vectors: [[384 floats], ...], is_valid: bool}.
        # Flatten all vectors across all buckets into (vector, is_valid) pairs.
        return [
            (vec, bool(ex["is_valid"]))
            for ex in examples
            if "vectors" in ex and "is_valid" in ex
            for vec in ex["vectors"]
            if vec
        ]
    except Exception as exc:
        logger.warning(f"[corpus] load failed for agent={agent_host!r}: {exc}")
        return []


async def warmup(agent_host: str) -> None:
    """Lazy-load prior corpus for agent_host and warm the per-agent LogReg classifier.

    Called fire-and-forget on the first cold-start ESCALATE for an agent.
    Runs once per agent per pod lifetime — the _warmed guard prevents duplicate
    loads even when multiple requests race to the same cold agent simultaneously
    (asyncio is single-threaded so the add happens before the first await).
    """
    if not agent_host or agent_host in _warmed:
        return
    _warmed.add(agent_host)

    examples = await load(agent_host)
    if not examples:
        logger.debug(f"[corpus] no prior examples for agent={agent_host!r} — staying cold")
        return

    from intent import trainer as _trainer  # local import avoids circular dependency
    crossed = False
    for vec, is_valid in examples:
        if _trainer.record(agent_host, vec, is_valid):
            crossed = True

    if crossed:
        await _trainer.train_now(agent_host)
        logger.info(f"[corpus] warmed agent={agent_host!r} classifier with {len(examples)} examples")
    else:
        logger.debug(
            f"[corpus] loaded {len(examples)} examples for agent={agent_host!r}"
            f" — below train threshold, classifier still cold"
        )


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
