"""Worker-side orchestration of the intent prefilter.

Ties together instruction/data segmentation (intent/segmenter.py), a cached
per-agent capability profile derived from the system prompt, the per-agent
multi-class instruction classifier (via the embedder), and the decision rule.
Pure-Python + a couple of batched HTTP calls; fail-open throughout — any
internal error degrades to ESCALATE, never a crash or a false ALLOW/BLOCK.

Latency shape: segmentation is regex-only (sub-ms); the capability-profile
lookup is a single cached Redis GET; a request's instruction units are
embedded in one batched /embed/batch call and classified in one batched
/classify call — never one HTTP round-trip per unit. Target: ≤10-50ms for
this whole step at 500 req/s (see the design plan for the budget breakdown).
"""

import asyncio
import hashlib
import json
import logging
import re
from typing import Any, Dict, List, Optional, Tuple

from settings import settings

from . import client, decision, payload, segmenter

logger = logging.getLogger(__name__)

_CAPABILITY_CACHE_PREFIX = "agentcap:"
_CAPABILITY_TTL_SECONDS = 24 * 3600
_CAPABILITY_EXCERPT_CHARS = 500
_DEFAULT_MAX_CHUNKS = 16
_STRUCTURE_CACHE_PREFIX = "agentstruct:"  # must match intent/corpus.py's write side


def enabled() -> bool:
    return str(getattr(settings, "INTENT_ENABLED", "")).strip().lower() in ("1", "true", "yes", "on")


def act() -> bool:
    """When False (default), ALLOW is shadow-logged only; request still ESCALATEs."""
    return str(getattr(settings, "INTENT_ACT", "")).strip().lower() in ("1", "true", "yes", "on")


def _float(name: str, default: float) -> float:
    raw = str(getattr(settings, name, "")).strip()
    try:
        return float(raw) if raw else default
    except ValueError:
        return default


def _int(name: str, default: int) -> int:
    raw = str(getattr(settings, name, "")).strip()
    try:
        return int(raw) if raw else default
    except ValueError:
        return default


def normalized_text(text: str) -> Tuple[str, int]:
    """Canonical NL string + chunk count for the semantic verdict CACHE only
    (cache.py) — unrelated to the instruction/data segmentation decide_fast()
    uses for intent classification. Unchanged from before this redesign."""
    chunks = payload.normalize(text, _int("INTENT_MAX_CHUNKS", _DEFAULT_MAX_CHUNKS))
    joined = " ".join(c for c, _ in chunks)
    return joined, len(chunks)


# --------------------------------------------------------------------------- #
# Agent capability profile — a small, cached-per-agent summary of the system
# prompt. Auxiliary context for segmentation only; never a decision-time gate.
# Fail-open: Redis down/miss just proceeds with capability_profile=None.
# --------------------------------------------------------------------------- #
def _build_capability_profile(system_prompt: str) -> Dict[str, Any]:
    """Cheap, no-LLM, no-embedding-call structural summary of the system
    prompt — currently a capped excerpt. Room to grow (e.g. a declared-tools
    list, if the caller passes tool schemas separately) without changing the
    cache contract below."""
    return {"excerpt": system_prompt[:_CAPABILITY_EXCERPT_CHARS]}


async def get_capability_profile(agent_host: str, system_prompt: str) -> Optional[Dict[str, Any]]:
    """Return the cached capability profile for agent_host, building it once.

    Keyed by agent_host + hash(system_prompt), so a system-prompt change
    naturally invalidates (a new key, the old one just expires via TTL)."""
    if not agent_host or not system_prompt:
        return None
    try:
        import cache_store  # local import: keeps this module importable stand-alone
        redis_client = cache_store.get_client()
        if redis_client is None:
            return None
        key_hash = hashlib.sha256(system_prompt.encode("utf-8")).hexdigest()[:16]
        redis_key = f"{_CAPABILITY_CACHE_PREFIX}{agent_host}:{key_hash}"
        raw = await redis_client.get(redis_key)
        if raw:
            return json.loads(raw)
        profile = _build_capability_profile(system_prompt)
        await redis_client.set(redis_key, json.dumps(profile), ex=_CAPABILITY_TTL_SECONDS)
        return profile
    except Exception as exc:
        logger.debug(f"[intent] capability profile unavailable for agent={agent_host!r}: {exc}")
        return None


# --------------------------------------------------------------------------- #
# Structure profile — this agent's own learned instruction key/verb lexicon,
# built by intent/corpus.py's warmup() from the agent's raw corpus history and
# cached in Redis. Read-only here: building it needs a Mongo round-trip, which
# only ever happens in the background (warmup), never on this hot path.
# --------------------------------------------------------------------------- #
async def get_structure_profile(agent_host: str) -> Optional[Dict[str, Any]]:
    """Fail-open: Redis down/miss -> None, segmentation falls back to the
    generic-only key/verb lexicon in intent/segmenter.py."""
    if not agent_host:
        return None
    try:
        import cache_store
        redis_client = cache_store.get_client()
        if redis_client is None:
            return None
        raw = await redis_client.get(f"{_STRUCTURE_CACHE_PREFIX}{agent_host}")
        return json.loads(raw) if raw else None
    except Exception as exc:
        logger.debug(f"[intent] structure profile unavailable for agent={agent_host!r}: {exc}")
        return None


# --------------------------------------------------------------------------- #
# enrich_result — stamps a CASCADE (LLM) verdict's details with a coarse
# task_intent/scope_bucket for audit purposes. The fine-grained per-unit
# intent is assigned later, offline, from the corpus this verdict feeds via
# intent/corpus.py — a live cascade verdict never carries that granularity.
# --------------------------------------------------------------------------- #
def enrich_result(result: Dict[str, Any]) -> None:
    details = result.setdefault("details", {})
    is_valid = bool(result.get("is_valid", True))
    details["task_intent"] = "benign" if is_valid else _extract_task_category(details)
    details["scope_bucket"] = "allow" if is_valid else "blocked"
    conf = result.get("decision_confidence")
    if conf is not None:
        details.setdefault("example_confidence", round(float(conf), 4))


def _extract_task_category(details: Dict[str, Any]) -> str:
    """Return the most specific malicious-intent label the LLM gave us, for
    human-readable audit purposes on a BLOCKED verdict.

    Priority: Qwen3Guard categories → BanTopics matchedTopic → Qwen3Guard
    safety "controversial" → keywords in the LLM's reason text → "malicious".
    """
    cats = (details.get("categories") or "").strip()
    if cats and cats.lower() not in ("none", ""):
        first = cats.split(",")[0].strip()
        slug = _slug(first)
        return slug if slug else "malicious"

    topic = (details.get("matchedTopic") or "").strip()
    if topic:
        slug = _slug(topic)
        return f"banned_{slug}" if slug else "malicious"

    if (details.get("safety") or "").strip().lower() == "controversial":
        return "controversial"

    reason = (details.get("reason") or "").lower()
    for kw, label in (
        ("prompt inject", "prompt_injection"),
        ("injection",     "prompt_injection"),
        ("toxic",         "toxicity"),
        ("harmful",       "harmful_content"),
        ("pii",           "pii_leak"),
        ("personal data", "pii_leak"),
        ("code",          "code_execution"),
        ("exfil",         "data_exfiltration"),
        ("credential",    "credential_leak"),
        ("secret",        "credential_leak"),
    ):
        if kw in reason:
            return label

    return "malicious"


def _slug(s: str) -> str:
    return re.sub(r"[^a-z0-9]+", "_", s.lower()).strip("_")


# --------------------------------------------------------------------------- #
# decide_fast — the fast-path attempt on a cache miss.
# --------------------------------------------------------------------------- #
async def decide_fast(agent_host: str, system_prompt: str, user_text: str,
                      timer: Optional[Any] = None) -> Dict[str, Any]:
    """Fast ALLOW/ESCALATE on the miss path (never BLOCK — see decision.py).

    Returns {decision, reason, intent, units, unit_texts, unit_sources,
    unit_vectors, extraction_confidence, extraction_method}. The unit
    texts/vectors are returned so scan_handler.py can reuse them post-cascade
    (queuing to intent/corpus.py) without re-segmenting or re-embedding — the
    same reuse pattern the semantic cache's `prep` already follows. Never
    raises (fail-open → ESCALATE).
    """
    try:
        # Independent Redis reads — fetch concurrently rather than back-to-back.
        profile, structure_profile = await asyncio.gather(
            get_capability_profile(agent_host, system_prompt or ""),
            get_structure_profile(agent_host),
        )
        extra_verbs = frozenset((structure_profile or {}).get("instruction_verbs") or ())

        if timer is not None:
            with timer.span("segment"):
                seg = segmenter.segment(user_text, capability_profile=profile, structure_profile=structure_profile)
        else:
            seg = segmenter.segment(user_text, capability_profile=profile, structure_profile=structure_profile)

        unit_texts: List[str] = []
        unit_sources: List[str] = []
        unit_methods: List[str] = []
        for span, src, meth in zip(seg["units"], seg["unit_sources"], seg["unit_methods"]):
            sub_units = segmenter.split_instruction_units(span, extra_verbs)
            unit_texts.extend(sub_units)
            unit_sources.extend([src] * len(sub_units))
            unit_methods.extend([meth] * len(sub_units))

        unit_vectors: List[Optional[List[float]]] = []
        unit_results: List[Optional[Dict[str, Any]]] = []
        if unit_texts:
            if timer is not None:
                with timer.span("embed_units"):
                    unit_vectors = await client.embed_units(unit_texts)
            else:
                unit_vectors = await client.embed_units(unit_texts)

            valid_idx = [i for i, v in enumerate(unit_vectors) if v is not None]
            vecs_to_classify = [unit_vectors[i] for i in valid_idx]
            unit_results = [None] * len(unit_texts)
            if vecs_to_classify:
                if timer is not None:
                    with timer.span("classify"):
                        classified = await client.classify_intent(agent_host, vecs_to_classify)
                else:
                    classified = await client.classify_intent(agent_host, vecs_to_classify)
                for idx, res in zip(valid_idx, classified):
                    unit_results[idx] = res

        ev = {
            "units": unit_results,
            "extraction_confidence": seg["extraction_confidence"],
        }
        result = decision.decide(
            ev,
            extraction_confidence_floor=_float("INTENT_EXTRACTION_CONFIDENCE_FLOOR", 0.5),
            base_confidence=_float("INTENT_BASE_CONFIDENCE", 0.55),
            base_margin=_float("INTENT_BASE_MARGIN", 0.05),
            margin_scale=_float("INTENT_MARGIN_SCALE", 0.35),
            base_centroid=_float("INTENT_BASE_CENTROID_SIM", 0.5),
            centroid_scale=_float("INTENT_CENTROID_SCALE", 0.35),
        )
        result["units"] = unit_results
        result["unit_texts"] = unit_texts
        result["unit_sources"] = unit_sources
        result["unit_methods"] = unit_methods
        result["unit_vectors"] = unit_vectors
        result["extraction_confidence"] = seg["extraction_confidence"]
        result["extraction_method"] = seg["method"]
        return result
    except Exception as exc:  # absolute backstop — prefilter must never block traffic
        logger.warning(f"[intent] decide_fast failed: {exc}")
        return {
            "decision": decision.ESCALATE, "reason": f"prefilter_error: {exc}",
            "intent": {"units": [], "risk_category": "unknown"},
            "units": [], "unit_texts": [], "unit_sources": [], "unit_methods": [], "unit_vectors": [],
            "extraction_confidence": 0.0, "extraction_method": "error",
        }
