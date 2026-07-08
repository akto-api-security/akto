"""intent_classifier — per-agent multi-class LogisticRegression on embeddings,
with per-class centroids for the corroborating centroid_similarity signal."""

import numpy as np
import pytest

import intent_classifier as ic


@pytest.fixture(autouse=True)
def _reset_models():
    ic._models.clear()
    yield
    ic._models.clear()


def _cluster(rng, center, n=6, dim=16, scale=0.05):
    return (center + rng.normal(scale=scale, size=(n, dim))).tolist()


def _basis(dim, i):
    v = np.zeros(dim)
    v[i] = 1.0
    return v


def test_predict_before_train_returns_none_per_vector():
    out = ic.predict("cold_agent", [[0.1] * 16, [0.2] * 16])
    assert out == [None, None]


def test_train_requires_at_least_two_classes():
    rng = np.random.default_rng(0)
    vectors = _cluster(rng, _basis(16, 0), n=6)
    res = ic.train("agentA", vectors, ["only_one_class"] * 6, {})
    assert res["trained"] is False
    assert res["reason"] == "insufficient_per_class_samples"
    assert ic.predict("agentA", vectors) == [None] * len(vectors)


def test_train_requires_min_per_class_samples():
    rng = np.random.default_rng(0)
    vectors = _cluster(rng, _basis(16, 0), n=1) + _cluster(rng, _basis(16, 1), n=6)
    labels = ["rare_class"] * 1 + ["common_class"] * 6
    res = ic.train("agentA", vectors, labels, {})
    assert res["trained"] is False


def test_train_drops_a_singleton_class_and_trains_on_the_rest():
    """A single sparse intent (e.g. only ever seen once in the corpus)
    shouldn't sink training for every other intent the agent has enough
    examples for — it's just dropped and its requests fall through to
    ESCALATE via the caller's unmatched-unit path."""
    rng = np.random.default_rng(0)
    c_flight, c_hotel, c_rare = _basis(16, 0), _basis(16, 1), _basis(16, 2)
    vectors = _cluster(rng, c_flight) + _cluster(rng, c_hotel) + _cluster(rng, c_rare, n=1)
    labels = ["flight_booking"] * 6 + ["hotel_booking"] * 6 + ["rare_singleton"] * 1

    res = ic.train("agentA", vectors, labels, {})
    assert res["trained"] is True
    assert sorted(res["classes"]) == ["flight_booking", "hotel_booking"]
    assert res["dropped_classes"] == ["rare_singleton"]

    query = (c_flight + rng.normal(scale=0.02, size=16)).tolist()
    result = ic.predict("agentA", [query])[0]
    assert result["intent"] == "flight_booking"


def test_train_and_predict_confident_match():
    rng = np.random.default_rng(0)
    c_flight, c_hotel = _basis(16, 0), _basis(16, 1)
    vectors = _cluster(rng, c_flight) + _cluster(rng, c_hotel)
    labels = ["flight_booking"] * 6 + ["hotel_booking"] * 6
    risk = {"flight_booking": "create", "hotel_booking": "create"}

    res = ic.train("agentA", vectors, labels, risk)
    assert res["trained"] is True
    assert sorted(res["classes"]) == ["flight_booking", "hotel_booking"]

    query = (c_flight + rng.normal(scale=0.02, size=16)).tolist()
    out = ic.predict("agentA", [query])
    assert len(out) == 1
    result = out[0]
    assert result["intent"] == "flight_booking"
    assert result["confidence"] > 0.5
    assert result["margin"] > 0.0
    assert result["centroid_similarity"] > 0.9  # query sits right on the flight cluster
    assert result["risk_category"] == "create"


def test_ambiguous_midpoint_has_low_margin_and_centroid_similarity():
    """The flight-vs-hotel collision this whole redesign is meant to catch:
    a query equidistant between two classes should score with a thin margin
    and a lower centroid_similarity, not a confident (and wrong) guess."""
    rng = np.random.default_rng(0)
    c_flight, c_hotel = _basis(16, 0), _basis(16, 1)
    vectors = _cluster(rng, c_flight) + _cluster(rng, c_hotel)
    labels = ["flight_booking"] * 6 + ["hotel_booking"] * 6
    ic.train("agentA", vectors, labels, {})

    midpoint = ((c_flight + c_hotel) / 2).tolist()
    result = ic.predict("agentA", [midpoint])[0]
    confident_result = ic.predict("agentA", [(c_flight + rng.normal(scale=0.02, size=16)).tolist()])[0]

    assert result["margin"] < confident_result["margin"]
    assert result["centroid_similarity"] < confident_result["centroid_similarity"]


def test_reject_classes_trainable_like_any_other_class():
    rng = np.random.default_rng(0)
    vectors = (
        _cluster(rng, _basis(16, 0))
        + _cluster(rng, _basis(16, 1))
        + _cluster(rng, _basis(16, 2))
    )
    labels = ["flight_booking"] * 6 + ["__other__"] * 6 + ["__background__"] * 6
    res = ic.train("agentA", vectors, labels, {})
    assert res["trained"] is True
    assert "__other__" in res["classes"]
    assert "__background__" in res["classes"]

    query = (_basis(16, 2) + rng.normal(scale=0.02, size=16)).tolist()
    result = ic.predict("agentA", [query])[0]
    assert result["intent"] == "__background__"


def test_atomic_swap_does_not_mix_models_across_agents():
    rng = np.random.default_rng(0)
    vectors_a = _cluster(rng, _basis(16, 0)) + _cluster(rng, _basis(16, 1))
    vectors_b = _cluster(rng, _basis(16, 5)) + _cluster(rng, _basis(16, 6))
    ic.train("agentA", vectors_a, ["x"] * 6 + ["y"] * 6, {})
    ic.train("agentB", vectors_b, ["p"] * 6 + ["q"] * 6, {})

    assert ic.stats()["agents_with_models"] == 2
    out_a = ic.predict("agentA", [(_basis(16, 0)).tolist()])[0]
    out_b = ic.predict("agentB", [(_basis(16, 5)).tolist()])[0]
    assert out_a["intent"] == "x"
    assert out_b["intent"] == "p"


def test_train_mismatched_lengths_fails_without_touching_existing_model():
    rng = np.random.default_rng(0)
    vectors = _cluster(rng, _basis(16, 0)) + _cluster(rng, _basis(16, 1))
    ic.train("agentA", vectors, ["x"] * 6 + ["y"] * 6, {})
    before = ic.predict("agentA", [(_basis(16, 0)).tolist()])[0]

    bad = ic.train("agentA", vectors, ["x"] * 5, {})  # length mismatch
    assert bad["trained"] is False

    after = ic.predict("agentA", [(_basis(16, 0)).tolist()])[0]
    assert before["intent"] == after["intent"]  # previous model untouched
