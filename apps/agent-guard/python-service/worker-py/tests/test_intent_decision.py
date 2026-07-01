"""intent.decision — risk-weighted aggregation of per-unit classifier results
into ALLOW/ESCALATE. This module never returns BLOCK (see intent/decision.py's
module docstring) — blocking stays exclusively cache.try_serve()'s job."""

from intent import decision


def _unit(intent="faq_lookup", confidence=0.9, margin=0.5, centroid=0.95, risk="fetch_generic"):
    return {"intent": intent, "confidence": confidence, "margin": margin,
            "centroid_similarity": centroid, "risk_category": risk}


def _ev(units=None, extraction_confidence=1.0):
    return {"units": units if units is not None else [_unit()],
            "extraction_confidence": extraction_confidence}


def test_decide_never_returns_block():
    assert not hasattr(decision, "BLOCK")
    for ev in (_ev(), _ev(units=[])):
        assert decision.decide(ev)["decision"] in (decision.ALLOW, decision.ESCALATE)


def test_empty_units_escalates():
    out = decision.decide(_ev(units=[]))
    assert out["decision"] == decision.ESCALATE


def test_low_extraction_confidence_escalates_even_with_a_great_match():
    out = decision.decide(_ev(extraction_confidence=0.2))
    assert out["decision"] == decision.ESCALATE
    assert out["reason"] == "extraction_uncertain_or_empty"


def test_confident_low_risk_match_allows():
    out = decision.decide(_ev(units=[_unit(risk="fetch_generic", confidence=0.8, margin=0.3, centroid=0.9)]))
    assert out["decision"] == decision.ALLOW
    assert out["intent"]["risk_category"] == "fetch_generic"


def test_ambiguous_flight_hotel_style_low_margin_escalates():
    # Same shape as a genuine flight-vs-hotel embedding collision: decent
    # confidence, but a razor-thin margin to the runner-up class.
    out = decision.decide(_ev(units=[_unit(intent="hotel_booking", confidence=0.55,
                                            margin=0.01, centroid=0.7, risk="create")]))
    assert out["decision"] == decision.ESCALATE


def test_reject_classes_always_escalate():
    for reject in ("__other__", "__background__"):
        out = decision.decide(_ev(units=[_unit(intent=reject, confidence=0.99, margin=0.99, centroid=0.99)]))
        assert out["decision"] == decision.ESCALATE
        assert reject in out["reason"]


def test_unmatched_unit_escalates():
    out = decision.decide(_ev(units=[None]))
    assert out["decision"] == decision.ESCALATE
    assert out["reason"] == "unit_unmatched"


def test_delete_risk_requires_near_ceiling_confidence():
    # delete has weight 1.0 — required_confidence = base + 1.0*(1-base) = 1.0.
    almost_perfect = _unit(intent="delete_order", confidence=0.97, margin=0.9, centroid=0.95, risk="delete")
    out = decision.decide(_ev(units=[almost_perfect]))
    assert out["decision"] == decision.ESCALATE  # short of the near-1.0 bar


def test_multi_unit_all_must_pass_and_highest_risk_wins():
    weak_generic = _unit(intent="faq_lookup", confidence=0.8, margin=0.3, centroid=0.9, risk="fetch_generic")
    strong_delete = _unit(intent="delete_order", confidence=1.0, margin=1.0, centroid=1.0, risk="delete")
    out = decision.decide(_ev(units=[weak_generic, strong_delete]))
    assert out["decision"] == decision.ALLOW
    assert out["intent"]["risk_category"] == "delete"


def test_one_weak_unit_among_many_forces_escalate():
    strong = _unit(confidence=0.9, margin=0.5, centroid=0.9, risk="fetch_generic")
    weak = _unit(confidence=0.1, margin=0.01, centroid=0.1, risk="fetch_generic")
    out = decision.decide(_ev(units=[strong, weak]))
    assert out["decision"] == decision.ESCALATE
