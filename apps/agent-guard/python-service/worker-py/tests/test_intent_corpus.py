"""intent.corpus — per-unit queuing, the GOOD-verdict refresh counter, and
the per-agent structure-profile lexicon, which prefers the offline LLM's
ground-truth breakdown over the worker's own regex guess whenever available."""

from intent import corpus


def _row(source_key="instruction", extraction_method="structured", unit_text="delete the record",
        is_valid=True, breakdown=None):
    row = {"sourceKey": source_key, "extractionMethod": extraction_method,
          "unitText": unit_text, "isValid": is_valid}
    if breakdown is not None:
        row["breakdown"] = breakdown
    return row


def test_queue_stores_source_key_and_extraction_method_per_unit(monkeypatch):
    corpus._buffer.clear()
    units = [{"text": "delete the record", "vector": [0.1] * 4,
             "extraction_method": "structured", "source_key": "instruction"}]
    corpus.queue("agentA", units, is_valid=True, scope_bucket="allow")
    row = corpus._buffer[-1]
    assert row["sourceKey"] == "instruction"
    assert row["extractionMethod"] == "structured"
    assert row["unitText"] == "delete the record"
    corpus._buffer.clear()


def test_queue_skips_units_with_no_vector():
    corpus._buffer.clear()
    units = [{"text": "no vector here", "vector": None, "extraction_method": "structured", "source_key": "x"}]
    assert corpus.queue("agentA", units, is_valid=True, scope_bucket="allow") is False
    assert len(corpus._buffer) == 0


def test_record_good_crosses_threshold(monkeypatch):
    monkeypatch.setattr(corpus.settings, "INTENT_REFRESH_EVERY_N", "3")
    corpus._good_since_refresh.pop("agentX", None)
    assert corpus.record_good("agentX") is False
    assert corpus.record_good("agentX") is False
    assert corpus.record_good("agentX") is True  # 3rd call crosses the threshold
    assert corpus._good_since_refresh["agentX"] == 0  # counter reset


def test_build_structure_profile_requires_minimum_observations():
    rows = [_row(source_key="userask") for _ in range(2)]  # below _MIN_OBSERVATIONS=3
    profile = corpus.build_structure_profile(rows)
    assert profile["instruction_keys"] == []


def test_build_structure_profile_learns_frequent_key_and_verb():
    rows = [_row(source_key="userask", unit_text="delete the record") for _ in range(5)]
    profile = corpus.build_structure_profile(rows)
    assert "userask" in profile["instruction_keys"]
    assert "delete" in profile["instruction_verbs"]


def test_build_structure_profile_ignores_non_structured_and_invalid_rows():
    rows = (
        [_row(source_key="ignored_key", extraction_method="heuristic") for _ in range(5)]
        + [_row(source_key="also_ignored", is_valid=False) for _ in range(5)]
    )
    profile = corpus.build_structure_profile(rows)
    assert profile["instruction_keys"] == []


def test_build_structure_profile_caps_learned_keys():
    rows = []
    for i in range(corpus._MAX_LEARNED_KEYS + 5):
        rows.extend(_row(source_key=f"key{i}") for _ in range(corpus._MIN_OBSERVATIONS))
    profile = corpus.build_structure_profile(rows)
    assert len(profile["instruction_keys"]) == corpus._MAX_LEARNED_KEYS


# --------------------------------------------------------------------------- #
# Ground truth (offline LLM's `breakdown`) takes priority over the worker's
# own regex guess, and counts regardless of the worker's extractionMethod.
# --------------------------------------------------------------------------- #
def test_ground_truth_breakdown_counted_even_when_worker_tier_was_heuristic():
    # The worker's own regex guess never even reached tier-1 (heuristic tier),
    # but the offline LLM has since confirmed the true source key — should
    # still count, since ground truth outranks the worker's own guess.
    rows = [_row(source_key="", extraction_method="heuristic", unit_text="reconcile the ledger",
                breakdown={"groundTruthSourceKey": "userAsk",
                          "groundTruthInstructionText": "reconcile the ledger"})
           for _ in range(corpus._MIN_OBSERVATIONS)]
    profile = corpus.build_structure_profile(rows)
    assert "userask" in profile["instruction_keys"]
    assert "reconcile" in profile["instruction_verbs"]


def test_ground_truth_breakdown_corrects_a_wrong_worker_guess():
    # Worker guessed "payload" as the source key (regex false positive); the
    # offline LLM's ground truth says it was actually "context". Only the
    # ground-truth key should accumulate — the worker's wrong guess must not.
    rows = [_row(source_key="payload", extraction_method="structured",
                breakdown={"groundTruthSourceKey": "context", "groundTruthInstructionText": "delete the record"})
           for _ in range(corpus._MIN_OBSERVATIONS)]
    profile = corpus.build_structure_profile(rows)
    assert "context" in profile["instruction_keys"]
    assert "payload" not in profile["instruction_keys"]


def test_rows_without_ground_truth_still_fall_back_to_worker_guess():
    rows = [_row(source_key="userask", extraction_method="structured") for _ in range(corpus._MIN_OBSERVATIONS)]
    profile = corpus.build_structure_profile(rows)
    assert "userask" in profile["instruction_keys"]


def test_ground_truth_invalid_row_is_excluded_like_any_other():
    rows = [_row(is_valid=False, breakdown={"groundTruthSourceKey": "shouldnotcount"})
           for _ in range(corpus._MIN_OBSERVATIONS)]
    profile = corpus.build_structure_profile(rows)
    assert profile["instruction_keys"] == []


async def test_warmup_caches_structure_profile_even_without_offline_labels(monkeypatch):
    # Rows exist (so structure learning can run) but none are offline-labeled
    # yet (empty taskIntent) — classifier retraining should skip, but the
    # structure profile should still be built and cached.
    raw_rows = [_row(source_key="userask") for _ in range(5)]

    async def _fetch_raw(agent_host):
        return raw_rows
    monkeypatch.setattr(corpus, "_fetch_raw", _fetch_raw)

    cached = {}
    async def _cache(agent_host, profile):
        cached["agent_host"] = agent_host
        cached["profile"] = profile
    monkeypatch.setattr(corpus, "_cache_structure_profile", _cache)

    trained = {"n": 0}
    class _FakeTrainer:
        @staticmethod
        def load_examples(*a, **k):
            trained["n"] += 1
        @staticmethod
        async def train_now(*a, **k):
            trained["n"] += 1

    import sys
    monkeypatch.setitem(sys.modules, "intent.trainer", _FakeTrainer)

    corpus._refreshing.discard("agentZ")
    await corpus.warmup("agentZ")

    assert cached["agent_host"] == "agentZ"
    assert "userask" in cached["profile"]["instruction_keys"]
    assert trained["n"] == 0  # no offline-labeled rows -> classifier retrain skipped
