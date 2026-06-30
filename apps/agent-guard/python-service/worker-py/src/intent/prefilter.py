"""Worker-side orchestration of the intent prefilter.

Ties together payload normalization, the regex signals, the per-agent classifier
(via the embedder), and the decision rule. Reuses the embedding the semantic
cache already computed (prep["vec"]) so the classifier adds no extra embed on the
hot path. Pure-Python + one optional HTTP classify call; fail-open throughout.

Single-unit MVP: the normalized chunks are joined into one canonical NL string
for the cache key + classifier vector (one embed). Regex runs over the full
string so a malicious sentence anywhere still triggers BLOCK. Per-chunk
embedding/classification is a later refinement (see plan).
"""

import logging
import re
from typing import Any, Dict, List, Optional, Tuple

from settings import settings

from . import client, decision, payload, signals

logger = logging.getLogger(__name__)

_DEFAULT_MAX_CHUNKS = 16
_DEFAULT_ALLOW_THRESHOLD = 0.80
_DEFAULT_BLOCK_THRESHOLD = 0.80
_DEFAULT_SCOPE_DISTANCE = 0.30


def enabled() -> bool:
    return str(getattr(settings, "INTENT_ENABLED", "")).strip().lower() in ("1", "true", "yes", "on")


def act() -> bool:
    """When False (default), ALLOW/BLOCK are shadow-logged only; request still ESCALATEs."""
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
    """Return (canonical NL string for cache+classifier, chunk_count)."""
    chunks = payload.normalize(text, _int("INTENT_MAX_CHUNKS", _DEFAULT_MAX_CHUNKS))
    joined = " ".join(c for c, _ in chunks)
    return joined, len(chunks)


def enrich_result(result: Dict[str, Any], norm_text: str) -> None:
    """Stamp the cascade result's details with the intent triple to be learned.

    task_intent is extracted from the LLM's own verdict fields (categories,
    matchedTopic, safety, reason) so examples carry a specific label like
    "prompt_injection" or "banned_pii" rather than a generic "malicious".
    risk_intent comes from regex over the NL text. Mutates result["details"]
    in place so cache.observe() and corpus.queue() both see the enriched triple.
    """
    details = result.setdefault("details", {})
    is_valid = bool(result.get("is_valid", True))
    risk = signals.risk_signal(norm_text)

    task_intent = "benign" if is_valid else _extract_task_category(details)
    details["task_intent"] = task_intent
    details["risk_intent"] = _risk_str(risk)
    details["scope_bucket"] = "allow" if is_valid else "blocked"

    # Preserve decision_confidence so corpus can weight high-confidence examples
    # more heavily during training (a confident LLM verdict is a better label).
    conf = result.get("decision_confidence")
    if conf is not None:
        details.setdefault("example_confidence", round(float(conf), 4))


def _extract_task_category(details: Dict[str, Any]) -> str:
    """Return the most specific malicious-intent label the LLM gave us.

    Priority: Qwen3Guard categories → BanTopics matchedTopic → Qwen3Guard
    safety "controversial" → keywords in the LLM's reason text → "malicious".
    """
    # Qwen3Guard emits "S1, S2" style category strings.
    cats = (details.get("categories") or "").strip()
    if cats and cats.lower() not in ("none", ""):
        first = cats.split(",")[0].strip()
        slug = _slug(first)
        return slug if slug else "malicious"

    # BanTopics puts the matched topic here.
    topic = (details.get("matchedTopic") or "").strip()
    if topic:
        slug = _slug(topic)
        return f"banned_{slug}" if slug else "malicious"

    # Qwen3Guard "controversial" is a distinct risk level, not just unsafe.
    if (details.get("safety") or "").strip().lower() == "controversial":
        return "controversial"

    # Fall back to the LLM's reason text (lightweight keyword scan).
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


def _risk_str(risk: Dict[str, bool]) -> str:
    return ",".join(k for k in ("read", "write", "pii") if risk.get(k)) or "none"


async def decide_fast(agent_host: str, norm_text: str,
                      prep: Optional[Dict[str, Any]],
                      timer: Optional[Any] = None) -> Dict[str, Any]:
    """Fast ALLOW/BLOCK/ESCALATE on the miss path, reusing the cache embedding.

    Returns {decision, reason, intent}. Never raises (fail-open → ESCALATE).
    `timer` (a scan_diag.StageTimer) records the classify + decide stages so the
    request log shows where intent time went.
    """
    try:
        attack = signals.attack_signal(norm_text)
        risk = signals.risk_signal(norm_text)
        vec = (prep or {}).get("vec")
        p_clf: Optional[float] = None
        if vec is not None:
            if timer is not None:
                with timer.span("classify"):
                    probs = await client.classify_vectors(agent_host, [vec])
            else:
                probs = await client.classify_vectors(agent_host, [vec])
            p_clf = probs[0] if probs else None
            if p_clf is None:
                # Cold start: embedder has no trained model for this agent yet.
                # Regex signals + Redis KNN (prep["cached"]) are the only active
                # signals until enough cascade verdicts accumulate to train.
                logger.debug(f"[intent] classifier cold-start agent={agent_host!r} — regex+cache only")
        cached = (prep or {}).get("cached")
        ev = {
            "weight": 1.0,
            "attack": attack,
            "risk": risk,
            "p_malicious_clf": p_clf,
            "cache": cached,
        }
        result = decision.decide(
            [ev],
            allow_threshold=_float("INTENT_ALLOW_THRESHOLD", _DEFAULT_ALLOW_THRESHOLD),
            block_threshold=_float("INTENT_BLOCK_THRESHOLD", _DEFAULT_BLOCK_THRESHOLD),
            scope_distance=_float("INTENT_SCOPE_DISTANCE", _DEFAULT_SCOPE_DISTANCE),
        )
        result["intent"]["risk"] = _risk_str(risk)  # flatten for transport/logging
        result["p_malicious_clf"] = p_clf
        return result
    except Exception as exc:  # absolute backstop — prefilter must never block traffic
        logger.warning(f"[intent] decide_fast failed: {exc}")
        return {"decision": decision.ESCALATE, "reason": f"prefilter_error: {exc}",
                "intent": {"task": "uncertain", "risk": "none", "scope_distance": None}}
