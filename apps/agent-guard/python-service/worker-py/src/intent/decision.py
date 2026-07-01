"""Combine per-unit instruction-classification signals into one request
decision.

Pure function, no I/O — unit-testable in isolation. Input:
    {
      "units":                [{intent, confidence, margin, centroid_similarity,
                                risk_category} | None, ...],  # one per extracted
                                                               # instruction unit
      "extraction_confidence": float,   # how confidently intent/segmenter.py
                                          # believes it separated instruction
                                          # from data for this request
    }

This module NEVER returns BLOCK. Blocking a request is, and remains,
exclusively cache.try_serve()'s job (near-exact repeat of a previously-
cascade-blocked verdict) — that runs upstream of intent/prefilter.py's
decide_fast() entirely, before this function is ever called. The only two
outcomes here are ALLOW (fast-path, skips the cascade) and ESCALATE (defer to
the LLM cascade, the safe default).

Policy (risk-weighted, asymmetric — ALLOW needs a higher bar than ESCALATE,
because an ALLOW has no safety net behind it):
    ESCALATE  if extraction itself is uncertain, or nothing was extracted;
    ESCALATE  if ANY unit is unmatched (None), a reject class
              ("__other__"/"__background__"), or fails its own risk-scaled
              confidence/margin/centroid-similarity bar;
    ALLOW     only when EVERY unit clears its bar — overall risk is the
              highest risk category across all units.
"""

from typing import Any, Dict, List, Optional

ALLOW = "ALLOW"
ESCALATE = "ESCALATE"

REJECT_CLASSES = ("__other__", "__background__")

# Higher risk → higher required confidence/margin/centroid-similarity to trust
# a match as "found". delete is highest priority risk; fetch_generic lowest.
RISK_WEIGHTS: Dict[str, float] = {
    "delete": 1.0,
    "edit": 0.8,
    "create": 0.8,
    "fetch_pii": 0.4,
    "fetch_generic": 0.1,
}
_UNKNOWN_RISK_WEIGHT = 1.0  # an unrecognized risk_category is treated as max-caution


def _risk_weight(risk_category: Optional[str]) -> float:
    return RISK_WEIGHTS.get(risk_category or "", _UNKNOWN_RISK_WEIGHT)


def _required_confidence(weight: float, base_confidence: float) -> float:
    return base_confidence + weight * (1.0 - base_confidence)


def _required_margin(weight: float, base_margin: float, margin_scale: float) -> float:
    return base_margin + weight * margin_scale


def _required_centroid_similarity(weight: float, base_centroid: float, centroid_scale: float) -> float:
    return base_centroid + weight * centroid_scale


def _highest_risk(a: str, b: str) -> str:
    return a if _risk_weight(a) >= _risk_weight(b) else b


def decide(
    ev: Dict[str, Any],
    *,
    extraction_confidence_floor: float = 0.5,
    base_confidence: float = 0.55,
    base_margin: float = 0.05,
    margin_scale: float = 0.35,
    base_centroid: float = 0.5,
    centroid_scale: float = 0.35,
) -> Dict[str, Any]:
    """Return {decision, reason, intent}. Never raises (fail-open → ESCALATE
    is the caller's responsibility on exception; this function itself only
    ever returns ALLOW or ESCALATE, never BLOCK)."""
    extraction_confidence = float(ev.get("extraction_confidence") or 0.0)
    units: List[Optional[Dict[str, Any]]] = ev.get("units") or []
    if extraction_confidence < extraction_confidence_floor or not units:
        return {"decision": ESCALATE, "reason": "extraction_uncertain_or_empty",
                "intent": {"units": [], "risk_category": "unknown"}}

    highest_risk = "fetch_generic"
    matched_intents: List[str] = []
    for u in units:
        if u is None:
            return {"decision": ESCALATE, "reason": "unit_unmatched",
                    "intent": {"units": [], "risk_category": "unknown"}}
        intent = u.get("intent")
        if intent in REJECT_CLASSES:
            return {"decision": ESCALATE, "reason": f"unit_reject_class:{intent}",
                    "intent": {"units": [], "risk_category": "unknown"}}
        risk_category = u.get("risk_category")
        weight = _risk_weight(risk_category)
        confidence = float(u.get("confidence") or 0.0)
        margin = float(u.get("margin") or 0.0)
        centroid_similarity = float(u.get("centroid_similarity") or 0.0)
        if not (
            confidence >= _required_confidence(weight, base_confidence)
            and margin >= _required_margin(weight, base_margin, margin_scale)
            and centroid_similarity >= _required_centroid_similarity(weight, base_centroid, centroid_scale)
        ):
            return {"decision": ESCALATE, "reason": f"unit_below_threshold:{intent}",
                    "intent": {"units": [], "risk_category": "unknown"}}
        highest_risk = _highest_risk(highest_risk, risk_category or "fetch_generic")
        matched_intents.append(intent)

    return {"decision": ALLOW, "reason": "all_units_matched_known_intent",
            "intent": {"units": matched_intents, "risk_category": highest_risk}}
