"""Durable cross-pod training corpus via database-abstractor — now keyed on
extracted instruction UNITS, not whole prompts.

Every time the LLM cascade produces a GOOD verdict (ESCALATE → cascade →
is_valid=True), each instruction unit extracted from that request (see
intent/segmenter.py) is queued here as its own row: {agentHost, unitText,
vector, taskIntent, riskCategory, scopeBucket, isValid, extractionMethod,
sourceKey}. taskIntent/riskCategory start empty — they're filled in later,
OFFLINE, by the separate LLM-labeling service that reads this corpus from
Mongo, clusters/labels each unit into a fine-grained intent + risk category
(capped at ~50 per agent), and upserts the label back in place. Live cascade
verdicts never carry that granularity, so this module never trains anything
directly; it only feeds Mongo and, on warmup/refresh, pulls whatever's been
labeled so far back into the per-agent multi-class classifier
(intent/trainer.py + embedder-container's /train).

The offline service ALSO does its own from-scratch instruction/data breakdown
of the request (real LLM understanding, not the worker's regex) and, once
processed, upserts a `breakdown` field onto the row:
    breakdown: {
      rawText:                    the full original request text (duplicated
                                   across every sibling unit-row from the same
                                   request — acceptable, keeps the schema simple),
      groundTruthSourceKey:        the LLM's own determination of which
                                   key/chat-role held this instruction, same
                                   flat-key convention as sourceKey (e.g.
                                   "instruction", "userAsk", "role:user"),
      groundTruthInstructionText:  the LLM's own extracted instruction text,
                                   which may differ from the worker's unitText,
    }
This exists because the worker's own sourceKey/unitText are just a regex
guess — without ground truth to check them against, a learned "structure
profile" built purely from the worker's own guesses only reinforces its own
blind spots. See build_structure_profile(): rows carrying `breakdown` are
LLM-verified and trusted regardless of the worker's extractionMethod tier;
rows without it yet fall back to the worker's own guess, but only when that
guess came from the confident tier-1 (structured) path — unverified bootstrap
signal, not confirmed truth. That's the whole feedback loop: regex bootstraps
a cold-start guess, the LLM corrects/confirms it over time.

Also, on every warmup()/refresh, this module aggregates the agent's raw
corpus rows (ground-truth-preferred, regex-guess-as-fallback) into a small
learned "structure profile" — which source keys and leading verbs reliably
produce a confident instruction unit for THIS agent — and caches it in Redis
for intent/segmenter.py to use as an addition to its generic key/verb lexicon
(see build_structure_profile() and intent/prefilter.py's get_structure_profile()).

Design:
  - Zero per-request synchronous overhead: queue() is pure in-process.
  - flush() is fired via schedule_fn (fire-and-forget coroutine), same as
    cache.observe() — it runs after the response has been returned.
  - Fail-open: any HTTP error logs a warning and the batch is dropped rather
    than blocking traffic or retrying forever.
  - warmup()/refresh is repeatable (cold-start priming AND the count-based
    periodic refresh in scan_handler.py both call it), guarded by an in-flight
    set so concurrent requests from the same agent never trigger redundant
    concurrent Mongo loads + retrains.
"""

import asyncio
import json
import logging
import re
from collections import Counter, deque
from typing import Any, Dict, List, Optional

from settings import settings

logger = logging.getLogger(__name__)

_BATCH_SIZE = 20      # flush when buffer reaches this many unit-rows
_MAX_BUFFER = 2000    # hard cap: deque drops oldest when full
_ENDPOINT = "/api/bulkInsertCorpusExamples"
_LOAD_ENDPOINT = "/api/loadCorpusForAgent"
_DEFAULT_REFRESH_EVERY_N = 200  # coarser than the old per-pod retrain cadence:
                                # this now triggers a Mongo round-trip, not a
                                # cheap in-memory fit.

# Structure-profile learning (see build_structure_profile()).
_STRUCTURE_CACHE_PREFIX = "agentstruct:"  # must match intent/prefilter.py's read side
_STRUCTURE_TTL_SECONDS = 24 * 3600
_MIN_OBSERVATIONS = 3    # a key/verb must recur at least this often before we
                         # trust it enough to add to the lexicon — guards
                         # against a one-off fluke promoting a bad signal.
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


def queue(agent_host: str, units: List[Dict[str, Any]], is_valid: bool, scope_bucket: str) -> bool:
    """Append one row per extracted instruction unit to the in-process buffer.

    Each item in `units` is {"text": str, "vector": List[float] | None,
    "extraction_method": str, "source_key": str} (intent/prefilter.py's
    segmentation output — source_key is the originating JSON/chat-role key for
    a tier-1 unit, "" otherwise). All units from one request share that
    request's cascade verdict — taskIntent/riskCategory are left empty for the
    offline service to fill in; sourceKey/extractionMethod are populated
    immediately and feed build_structure_profile() below.

    Returns True when the batch threshold is crossed so the caller can
    schedule flush() as a fire-and-forget coroutine. Units with no vector are
    skipped (nothing to train on).
    """
    crossed = False
    for u in units:
        vec = u.get("vector")
        if not vec:
            continue
        _buffer.append({
            "agentHost": agent_host,
            "unitText": u.get("text", ""),
            "vector": vec,
            "taskIntent": "",
            "riskCategory": "",
            "scopeBucket": scope_bucket,
            "isValid": is_valid,
            "extractionMethod": u.get("extraction_method", ""),
            "sourceKey": u.get("source_key", ""),
        })
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
    skipping rows the offline service hasn't labeled yet (empty taskIntent)."""
    out: List[Dict[str, Any]] = []
    for ex in raw_rows:
        task_intent = ex.get("taskIntent") or ex.get("task_intent") or ""
        if not task_intent:
            continue  # not yet labeled offline — skip
        vec = ex.get("vector") if ex.get("vector") is not None else ex.get("vectors")
        if isinstance(vec, list) and vec and isinstance(vec[0], list):
            vec = vec[0]  # tolerate a legacy bucketed {"vectors": [[...]]} shape
        if not vec:
            continue
        out.append({
            "vector": vec,
            "task_intent": task_intent,
            "risk_category": ex.get("riskCategory") or ex.get("risk_category") or "unknown",
            "is_valid": bool(ex.get("isValid", ex.get("is_valid", True))),
        })
    return out


async def load(agent_host: str) -> List[Dict[str, Any]]:
    """Fetch offline-labeled per-unit training examples for agent_host.

    Returns [{"vector", "task_intent", "risk_category", "is_valid"}, ...].
    Fail-open: [] on any error or when the URL is unconfigured.
    """
    return _labeled_examples(await _fetch_raw(agent_host))


def _leading_word(text: str) -> str:
    m = _LEADING_WORD_RE.match((text or "").strip())
    return m.group(0).lower() if m else ""


def build_structure_profile(raw_rows: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Aggregate this agent's corpus rows into a small learned lexicon: which
    source keys and leading verbs reliably produce a confident instruction
    unit. Two-tier trust, preferring ground truth over the worker's own guess:

      1. A row carrying `breakdown` (the offline LLM-labeling service's own
         from-scratch instruction/data split) is LLM-verified — counted
         regardless of the worker's extractionMethod tier, since this is
         confirmed truth, not a guess.
      2. A row without `breakdown` yet falls back to the worker's own
         sourceKey/unitText guess, but ONLY when extractionMethod=="structured"
         (unverified bootstrap signal — the worker was confident, but nothing
         has checked it yet).

    This is what lets the profile improve over time instead of just
    reinforcing the regex segmenter's own blind spots: cold-start runs on the
    worker's guess alone, and each row the offline service processes either
    confirms or corrects that guess.
    """
    key_counts: Counter = Counter()
    verb_counts: Counter = Counter()
    for row in raw_rows:
        if not row.get("isValid", True):
            continue
        breakdown = row.get("breakdown") or {}
        ground_truth_key = (breakdown.get("groundTruthSourceKey") or "").strip().lower()
        ground_truth_text = breakdown.get("groundTruthInstructionText") or ""
        if ground_truth_key or ground_truth_text:
            if ground_truth_key:
                key_counts[ground_truth_key] += 1
            verb = _leading_word(ground_truth_text or row.get("unitText", ""))
            if verb:
                verb_counts[verb] += 1
            continue
        if row.get("extractionMethod") != "structured":
            continue
        key = (row.get("sourceKey") or "").strip().lower()
        if key:
            key_counts[key] += 1
        verb = _leading_word(row.get("unitText", ""))
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

        examples = _labeled_examples(raw_rows)
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
