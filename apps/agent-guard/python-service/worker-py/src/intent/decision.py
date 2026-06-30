"""Combine per-chunk signals into one request decision + intent triple.

Pure function, no I/O — unit-testable in isolation. Inputs per chunk:
    {
      "weight":  float,                      # from payload.normalize
      "attack":  {p_malicious, count, ...},  # signals.attack_signal
      "risk":    {read, write, pii},         # signals.risk_signal
      "p_malicious_clf": float | None,       # per-agent classifier, None if absent
      "cache":   {is_valid, distance, scope_bucket} | None,  # nearest neighbour
    }

Aggregation (task-primary, ALLOW asymmetric):
    BLOCK  if ANY chunk looks malicious (regex/classifier/blocked-neighbour).
    ALLOW  only if EVERY chunk is benign AND corroborated by a fresh safe
           allow-example (a near-identical task was approved before) — ALLOW
           skips the LLM, so it needs the higher bar.
    else   ESCALATE (the safe default; also seeds the mission on the miss path).
"""

from typing import Any, Dict, List, Optional

ALLOW = "ALLOW"
BLOCK = "BLOCK"
ESCALATE = "ESCALATE"


def _malice(ev: Dict[str, Any]) -> float:
    """Max of the regex and classifier malicious-probabilities for a chunk."""
    pa = float(ev.get("attack", {}).get("p_malicious", 0.0) or 0.0)
    pc = ev.get("p_malicious_clf")
    return max(pa, float(pc)) if pc is not None else pa


def _is_block(ev: Dict[str, Any], block_threshold: float, scope_distance: float) -> bool:
    if _malice(ev) >= block_threshold:
        return True
    if int(ev.get("attack", {}).get("count", 0)) >= 2:  # ≥2 distinct attack categories
        return True
    c = ev.get("cache")
    if c is not None and not c.get("is_valid", True) and c.get("distance", 1.0) <= scope_distance:
        return True
    return False


def _is_allow(ev: Dict[str, Any], allow_threshold: float, scope_distance: float) -> bool:
    c = ev.get("cache")
    has_allow_example = (
        c is not None and c.get("is_valid", False)
        and c.get("distance", 1.0) <= scope_distance
    )
    if not has_allow_example:
        return False
    if _malice(ev) > (1.0 - allow_threshold):
        return False
    risk = ev.get("risk", {})
    if risk.get("write") and risk.get("pii"):  # write + PII is never auto-allowed
        return False
    pc = ev.get("p_malicious_clf")
    if pc is not None and (1.0 - float(pc)) < allow_threshold:  # classifier not confident-benign
        return False
    return True


def _aggregate_triple(chunk_evals: List[Dict[str, Any]], decision: str,
                      top_category: str) -> Dict[str, Any]:
    risk = {"read": False, "write": False, "pii": False}
    scope_dist: Optional[float] = None
    for ev in chunk_evals:
        r = ev.get("risk", {})
        for k in risk:
            risk[k] = risk[k] or bool(r.get(k))
        c = ev.get("cache")
        if c is not None and c.get("is_valid", False):
            d = c.get("distance", 1.0)
            scope_dist = d if scope_dist is None else min(scope_dist, d)
    task = {ALLOW: "benign", BLOCK: top_category or "malicious"}.get(decision, "uncertain")
    return {"task": task, "risk": risk, "scope_distance": scope_dist}


def decide(chunk_evals: List[Dict[str, Any]], *, allow_threshold: float = 0.85,
           block_threshold: float = 0.80, scope_distance: float = 0.15) -> Dict[str, Any]:
    """Return {decision, reason, intent:{task,risk,scope_distance}}.

    Empty input ⇒ ESCALATE (nothing to judge).
    """
    if not chunk_evals:
        return {"decision": ESCALATE, "reason": "no_chunks",
                "intent": {"task": "uncertain", "risk": {}, "scope_distance": None}}

    # BLOCK dominates: one malicious chunk blocks the whole request.
    for ev in chunk_evals:
        if _is_block(ev, block_threshold, scope_distance):
            cats = ev.get("attack", {}).get("categories") or []
            top = cats[0] if cats else "malicious"
            triple = _aggregate_triple(chunk_evals, BLOCK, top)
            return {"decision": BLOCK,
                    "reason": f"malicious task (p={_malice(ev):.2f}, {top})",
                    "intent": triple}

    # ALLOW needs every chunk benign + corroborated.
    if all(_is_allow(ev, allow_threshold, scope_distance) for ev in chunk_evals):
        triple = _aggregate_triple(chunk_evals, ALLOW, "")
        return {"decision": ALLOW, "reason": "benign task, matches prior allowed",
                "intent": triple}

    triple = _aggregate_triple(chunk_evals, ESCALATE, "")
    return {"decision": ESCALATE, "reason": "uncertain — defer to cascade",
            "intent": triple}
