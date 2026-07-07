"""Worker-side orchestration of the intent prefilter.

Ties together instruction/data segmentation (intent/segmenter.py), the
per-agent learned structure profile, the per-agent multi-class instruction
classifier (via the embedder), and the decision rule. Pure-Python + a couple
of batched HTTP calls; fail-open throughout — any internal error degrades to
ESCALATE, never a crash or a false ALLOW/BLOCK.

Latency shape: segmentation is regex-only (sub-ms); the structure-profile
lookup is a single cached Redis GET; a request's instruction units are
embedded in one batched /embed/batch call and classified in one batched
/classify call — never one HTTP round-trip per unit, and only once the agent
has a structure profile (see decide_fast's `is_warm` check) — a cold agent
skips both calls entirely. Target: ≤10-50ms for this whole step at 500 req/s
(see the design plan for the budget breakdown).
"""

import json
import logging
import re
from typing import Any, Dict, List, Optional, Tuple

from settings import settings

from . import client, decision, payload, segmenter

logger = logging.getLogger(__name__)

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
async def decide_fast(agent_host: str, user_text: str,
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
        structure_profile = await get_structure_profile(agent_host)
        is_warm = structure_profile is not None
        extra_verbs = frozenset((structure_profile or {}).get("instruction_verbs") or ())

        if timer is not None:
            with timer.span("segment"):
                seg = segmenter.segment(user_text, structure_profile=structure_profile)
        else:
            seg = segmenter.segment(user_text, structure_profile=structure_profile)

        unit_texts: List[str] = []
        unit_sources: List[str] = []
        unit_methods: List[str] = []
        for span, src, meth in zip(seg["units"], seg["unit_sources"], seg["unit_methods"]):
            sub_units = segmenter.split_instruction_units(span, extra_verbs)
            unit_texts.extend(sub_units)
            unit_sources.extend([src] * len(sub_units))
            unit_methods.extend([meth] * len(sub_units))

        unit_vectors: List[Optional[List[float]]] = []
        unit_results: List[Optional[Dict[str, Any]]] = [None] * len(unit_texts)
        if unit_texts and is_warm:
            if timer is not None:
                with timer.span("embed_units"):
                    unit_vectors = await client.embed_units(unit_texts)
            else:
                unit_vectors = await client.embed_units(unit_texts)

            valid_idx = [i for i, v in enumerate(unit_vectors) if v is not None]
            vecs_to_classify = [unit_vectors[i] for i in valid_idx]
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
        result["classifier_warm"] = is_warm
        return result
    except Exception as exc:  # absolute backstop — prefilter must never block traffic
        logger.warning(f"[intent] decide_fast failed: {exc}")
        return {
            "decision": decision.ESCALATE, "reason": f"prefilter_error: {exc}",
            "intent": {"units": [], "risk_category": "unknown"},
            "units": [], "unit_texts": [], "unit_sources": [], "unit_methods": [], "unit_vectors": [],
            "extraction_confidence": 0.0, "extraction_method": "error", "classifier_warm": False,
        }
