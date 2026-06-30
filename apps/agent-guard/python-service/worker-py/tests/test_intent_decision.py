"""intent.decision — aggregation of signals + classifier + cache into a verdict."""

from intent import decision


def _ev(*, p_attack=0.0, count=0, categories=None, p_clf=None,
        read=False, write=False, pii=False, cache=None, weight=1.0):
    return {
        "weight": weight,
        "attack": {"p_malicious": p_attack, "count": count, "categories": categories or []},
        "risk": {"read": read, "write": write, "pii": pii},
        "p_malicious_clf": p_clf,
        "cache": cache,
    }


def _safe_hit(distance=0.05):
    return {"is_valid": True, "distance": distance, "scope_bucket": "allow"}


def _block_hit(distance=0.0):
    return {"is_valid": False, "distance": distance, "scope_bucket": "blocked"}


def test_empty_escalates():
    assert decision.decide([])["decision"] == decision.ESCALATE


def test_block_on_high_classifier_malice():
    out = decision.decide([_ev(p_clf=0.95)], block_threshold=0.80)
    assert out["decision"] == decision.BLOCK
    assert out["intent"]["task"] != "benign"


def test_block_on_two_attack_categories():
    out = decision.decide([_ev(p_attack=0.9, count=2, categories=["sql_injection", "data_exfiltration"])])
    assert out["decision"] == decision.BLOCK
    assert out["intent"]["task"] == "sql_injection"


def test_block_on_blocked_neighbour_exact():
    out = decision.decide([_ev(cache=_block_hit(distance=0.0))], scope_distance=0.15)
    assert out["decision"] == decision.BLOCK


def test_allow_requires_safe_allow_example():
    # benign + low malice but NO cache allow-example → ESCALATE (seeds mission)
    assert decision.decide([_ev(p_clf=0.02)])["decision"] == decision.ESCALATE
    # with a fresh safe allow-example within scope → ALLOW
    out = decision.decide([_ev(p_clf=0.02, cache=_safe_hit(0.05))], allow_threshold=0.85)
    assert out["decision"] == decision.ALLOW
    assert out["intent"]["task"] == "benign"


def test_write_plus_pii_never_auto_allowed():
    out = decision.decide([_ev(p_clf=0.02, write=True, pii=True, cache=_safe_hit(0.05))])
    assert out["decision"] == decision.ESCALATE


def test_block_dominates_over_allow_across_chunks():
    evs = [_ev(p_clf=0.02, cache=_safe_hit(0.05)), _ev(p_clf=0.97)]
    assert decision.decide(evs)["decision"] == decision.BLOCK
