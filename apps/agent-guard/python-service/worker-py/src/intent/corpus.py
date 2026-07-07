import asyncio
import json
import logging
import re
from collections import Counter, deque
import time
from typing import Any, Dict, List, Optional

from settings import settings

logger = logging.getLogger(__name__)

_BATCH_SIZE = 20      # flush when buffer reaches this many unit-rows
_MAX_BUFFER = 2000    # hard cap: deque drops oldest when full
_ENDPOINT = "/api/bulkInsertCorpusExamples"
_LOAD_ENDPOINT = "/api/loadCorpusForAgent"
_DEFAULT_REFRESH_EVERY_N = 200 

# Structure-profile learning (see build_structure_profile()).
_STRUCTURE_CACHE_PREFIX = "agentstruct:"  # must match intent/prefilter.py's read side
_STRUCTURE_TTL_SECONDS = 24 * 3600
_MIN_OBSERVATIONS = 3    # a key/verb must recur at least this often before we
_MAX_LEARNED_KEYS = 25
_MAX_LEARNED_VERBS = 25
_LEADING_WORD_RE = re.compile(r"[A-Za-z']+")

# In-process buffer shared across all async tasks on this pod.
_buffer: "deque[Dict[str, Any]]" = deque(maxlen=_MAX_BUFFER)
_flush_lock: Optional[asyncio.Lock] = None
_refreshing: set = set()               # agent_hosts currently mid-warmup/refresh
_good_since_refresh: Dict[str, int] = {}  # per-agent GOOD-verdict counter


def _get_lock() -> asyncio.Lock:
    global _flush_lock
    if _flush_lock is None:
        _flush_lock = asyncio.Lock()
    return _flush_lock


def _url() -> Optional[str]:
    base = (getattr(settings, "DATABASE_ABSTRACTOR_SERVICE_URL", "") or "").rstrip("/")
    return f"{base}{_ENDPOINT}" if base else None


def _refresh_every_n() -> int:
    raw = str(getattr(settings, "INTENT_REFRESH_EVERY_N", "")).strip()
    try:
        return int(raw) if raw else _DEFAULT_REFRESH_EVERY_N
    except ValueError:
        return _DEFAULT_REFRESH_EVERY_N


def queue(agent_host: str, units: List[Dict[str, Any]]) -> bool:
    crossed = False
    for u in units:
        text = (u.get("text") or "").strip()
        if not text:
            continue
        _buffer.append({
            "agentHost": agent_host,
            "unitText": text,  # (see AgentGuardCorpusLabelingCron), not here
            "createdAt": int(time.time() * 1000),
        }) # in milliseconds
        if len(_buffer) >= _BATCH_SIZE:
            crossed = True
    return crossed


def record_good(agent_host: str) -> bool:
    """Increment the per-agent GOOD-verdict counter. Returns True once
    INTENT_REFRESH_EVERY_N is crossed, so the caller can schedule warmup()
    (a fresh pull of whatever the offline service has labeled so far)."""
    if not agent_host:
        return False
    n = _good_since_refresh.get(agent_host, 0) + 1
    if n >= _refresh_every_n():
        _good_since_refresh[agent_host] = 0
        return True
    _good_since_refresh[agent_host] = n
    return False


async def _fetch_raw(agent_host: str) -> List[Dict[str, Any]]:
    """Fetch this agent's full corpus rows from the database-abstractor,
    unfiltered. Shared by load() (offline-labeled examples, for classifier
    training) and build_structure_profile() (raw structural signal, for the
    per-agent key/verb lexicon) — one Mongo round-trip serves both. Fail-open:
    [] on any error or when the URL is unconfigured.
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
            json={"agentHost": agent_host},
            headers={"Accept-Encoding": "identity"},
            timeout=10.0,
        )
        if resp.status_code >= 400:
            logger.warning(f"[corpus] load returned {resp.status_code} for agent={agent_host!r}")
            return []
        return resp.json().get("examples") or []
    except Exception as exc:
        logger.warning(f"[corpus] load failed for agent={agent_host!r}: {exc}")
        return []


def _labeled_examples(raw_rows: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Filter+shape raw corpus rows into offline-labeled training examples,
    skipping rows the offline service hasn't labeled yet (empty taskIntent).

    Prefers breakdown.groundTruthInstructionText (the LLM-corrected
    instruction) over the worker's own unitText guess when present — training
    should learn from the verified text, not a possibly-wrong regex guess.
    Returns text, not a vector — load() embeds it right before training,
    since Mongo never stores embeddings for this corpus (see module docstring).
    """
    out: List[Dict[str, Any]] = []
    for ex in raw_rows:
        task_intent = ex.get("taskIntent")
        if not task_intent:
            continue  # not yet labeled offline — skip
        breakdown = ex.get("breakdown") or {}
        text = breakdown.get("groundTruthInstructionText") or ex.get("unitText")
        if not text:
            continue
        out.append({
            "text": text,
            "task_intent": task_intent,
            "risk_category": ex.get("riskCategory") or ex.get("risk_category") or "unknown",
        })
    return out


async def _embed_labeled_examples(examples: List[Dict[str, Any]]) -> List[Dict[str, Any]]:
    """Embed each labeled example's text and attach the resulting vector,
    dropping any whose embedding failed. Shared by load() (classifier
    training, called from warmup()) — Mongo never stores embeddings for this
    corpus, so this is always the last step before a vector reaches the
    trainer."""
    if not examples:
        return []
    from intent import client as _client  # local import avoids circular dependency
    vectors = await _client.embed_units([ex["text"] for ex in examples])
    out: List[Dict[str, Any]] = []
    for ex, vec in zip(examples, vectors):
        if not vec:
            continue
        out.append({
            "vector": vec,
            "task_intent": ex["task_intent"],
            "risk_category": ex["risk_category"],
        })
    return out


async def load(agent_host: str) -> List[Dict[str, Any]]:
    examples = _labeled_examples(await _fetch_raw(agent_host))
    return await _embed_labeled_examples(examples)


def _leading_word(text: str) -> str:
    m = _LEADING_WORD_RE.match((text or "").strip())
    return m.group(0).lower() if m else ""


def build_structure_profile(raw_rows: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Aggregate this agent's corpus rows into a small learned lexicon: which
    source keys and leading verbs reliably produce a confident instruction
    unit for this agent. Every row here is LLM ground-truthed by
    construction — the labeling cron only ever writes a row to the final
    corpus once the offline LLM has classified it (see module docstring) —
    so this always reads breakdown.groundTruthSourceKey/
    groundTruthInstructionText, never a worker guess.
    """
    key_counts: Counter = Counter()
    verb_counts: Counter = Counter()
    for row in raw_rows:
        breakdown = row.get("breakdown") or {}
        key = (breakdown.get("groundTruthSourceKey") or "").strip().lower()
        text = breakdown.get("groundTruthInstructionText") or row.get("unitText", "")
        if key:
            key_counts[key] += 1
        verb = _leading_word(text)
        if verb:
            verb_counts[verb] += 1
    return {
        "instruction_keys": [k for k, n in key_counts.most_common(_MAX_LEARNED_KEYS) if n >= _MIN_OBSERVATIONS],
        "instruction_verbs": [v for v, n in verb_counts.most_common(_MAX_LEARNED_VERBS) if n >= _MIN_OBSERVATIONS],
    }


async def _cache_structure_profile(agent_host: str, profile: Dict[str, Any]) -> None:
    """Best-effort: cache the freshly-built structure profile in Redis for
    intent/prefilter.py's get_structure_profile() to read on the hot path.
    Fail-open — a cache-write failure just means segmentation stays
    generic-only for this agent until the next successful warmup()."""
    try:
        import cache_store
        redis_client = cache_store.get_client()
        if redis_client is None:
            return
        await redis_client.set(f"{_STRUCTURE_CACHE_PREFIX}{agent_host}", json.dumps(profile),
                               ex=_STRUCTURE_TTL_SECONDS)
    except Exception as exc:
        logger.debug(f"[corpus] structure profile cache write failed for agent={agent_host!r}: {exc}")


async def warmup(agent_host: str) -> None:
    """Load the latest corpus rows for agent_host, refresh its learned
    structure profile (key/verb lexicon), and refit its multi-class classifier
    from whatever's been offline-labeled so far. Fire-and-forget, fail-open,
    and safe to call repeatedly — the in-flight guard (`_refreshing`) prevents
    concurrent requests from the same agent from firing redundant concurrent
    Mongo loads + retrains. Called both on cold-start (first ESCALATE for a
    never-seen or stale agent) and periodically via the count-based refresh in
    scan_handler.py.
    """
    if not agent_host or agent_host in _refreshing:
        return
    _refreshing.add(agent_host)
    try:
        raw_rows = await _fetch_raw(agent_host)
        if not raw_rows:
            logger.debug(f"[corpus] no corpus rows for agent={agent_host!r} yet — staying cold")
            return

        await _cache_structure_profile(agent_host, build_structure_profile(raw_rows))

        examples = await _embed_labeled_examples(_labeled_examples(raw_rows))
        if not examples:
            logger.debug(f"[corpus] no offline-labeled examples for agent={agent_host!r} yet")
            return
        from intent import trainer as _trainer  # local import avoids circular dependency
        _trainer.load_examples(agent_host, examples)
        await _trainer.train_now(agent_host)
        logger.info(f"[corpus] refreshed agent={agent_host!r} classifier with {len(examples)} examples")
    except Exception as exc:
        logger.warning(f"[corpus] warmup failed for agent={agent_host!r}: {exc}")
    finally:
        _refreshing.discard(agent_host)


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
            logger.info(f"[corpus] flushed {len(batch)} unit-examples to db-abstractor")
    except Exception as exc:
        logger.warning(f"[corpus] flush failed ({len(batch)} examples dropped): {exc}")
