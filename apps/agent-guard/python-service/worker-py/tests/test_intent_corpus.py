"""intent.corpus — per-unit queuing, the GOOD-verdict refresh counter, and
the per-agent structure-profile lexicon. Every bucket build_structure_profile()
and load() ever see is LLM ground-truthed by construction (the Java-side
labeling cron only ever writes a bucket to the final corpus once an LLM has
classified it), so there is no worker's-own-guess fallback tier to test.

Buckets are the grouped-aggregation shape AgentGuardCorpusDao.findBucketsByAgentHost
returns: {"_id": {agentHost, taskIntent, riskCategory}, "breakDowns": [...]}."""

import intent
from intent import corpus


def _bucket(task_intent="delete_record", risk_category="delete", breakdowns=None):
    return {
        "_id": {"agentHost": "agentA", "taskIntent": task_intent, "riskCategory": risk_category},
        "breakDowns": breakdowns if breakdowns is not None else [_gt()],
    }


def _gt(key="instruction", text="delete the record"):
    return {"groundTruthSourceKey": key, "groundTruthInstructionText": text}


def test_queue_stores_unit_text_only():
    corpus._buffer.clear()
    corpus.queue("agentA", [{"text": "delete the record"}])
    row = dict(corpus._buffer[-1])
    row.pop("createdAt")
    assert row == {"agentHost": "agentA", "unitText": "delete the record"}
    corpus._buffer.clear()


def test_queue_skips_units_with_no_text():
    corpus._buffer.clear()
    assert corpus.queue("agentA", [{"text": "   "}]) is False
    assert len(corpus._buffer) == 0


def test_record_good_crosses_threshold(monkeypatch):
    monkeypatch.setattr(corpus.settings, "INTENT_REFRESH_EVERY_N", "3")
    corpus._good_since_refresh.pop("agentX", None)
    assert corpus.record_good("agentX") is False
    assert corpus.record_good("agentX") is False
    assert corpus.record_good("agentX") is True  # 3rd call crosses the threshold
    assert corpus._good_since_refresh["agentX"] == 0  # counter reset


# --------------------------------------------------------------------------- #
# build_structure_profile — single-tier, ground-truth only
# --------------------------------------------------------------------------- #
def test_build_structure_profile_requires_minimum_observations():
    buckets = [_bucket(breakdowns=[_gt(text="reconcile the ledger")]) for _ in range(2)]  # below _MIN_OBSERVATIONS=3
    profile = corpus.build_structure_profile(buckets)
    assert profile["instruction_keys"] == []
    assert profile["instruction_verbs"] == []


def test_build_structure_profile_learns_frequent_key_and_verb():
    buckets = [_bucket(breakdowns=[_gt(key="userask", text="delete the record")]) for _ in range(5)]
    profile = corpus.build_structure_profile(buckets)
    assert "userask" in profile["instruction_keys"]
    assert "delete" in profile["instruction_verbs"]


def test_build_structure_profile_ignores_breakdowns_without_a_ground_truth_key():
    buckets = [_bucket(breakdowns=[{"groundTruthSourceKey": "", "groundTruthInstructionText": ""}])
              for _ in range(5)]
    profile = corpus.build_structure_profile(buckets)
    assert profile["instruction_keys"] == []


def test_build_structure_profile_caps_learned_keys():
    buckets = []
    for i in range(corpus._MAX_LEARNED_KEYS + 5):
        buckets.extend(_bucket(breakdowns=[_gt(key=f"key{i}", text="do something")])
                       for _ in range(corpus._MIN_OBSERVATIONS))
    profile = corpus.build_structure_profile(buckets)
    assert len(profile["instruction_keys"]) == corpus._MAX_LEARNED_KEYS


def test_build_structure_profile_aggregates_multiple_breakdowns_per_bucket():
    buckets = [_bucket(breakdowns=[_gt(key="userask", text="delete the record")] * 3)]
    profile = corpus.build_structure_profile(buckets)
    assert "userask" in profile["instruction_keys"]
    assert "delete" in profile["instruction_verbs"]


# --------------------------------------------------------------------------- #
# _labeled_examples / load — ground-truth text preferred, embedded at load time
# --------------------------------------------------------------------------- #
def test_labeled_examples_skips_unlabeled_buckets():
    assert corpus._labeled_examples([_bucket(task_intent="")]) == []


def test_labeled_examples_skips_breakdowns_with_no_ground_truth_text():
    buckets = [_bucket(breakdowns=[_gt(text=None), _gt(text="the corrected instruction")])]
    examples = corpus._labeled_examples(buckets)
    assert len(examples) == 1
    assert examples[0]["text"] == "the corrected instruction"


def test_labeled_examples_emits_one_example_per_breakdown():
    buckets = [_bucket(task_intent="delete_record", risk_category="delete",
                       breakdowns=[_gt(text="delete the record"), _gt(text="remove the record")])]
    examples = corpus._labeled_examples(buckets)
    assert [e["text"] for e in examples] == ["delete the record", "remove the record"]
    assert all(e["task_intent"] == "delete_record" and e["risk_category"] == "delete" for e in examples)


def test_labeled_examples_defaults_missing_risk_category_to_unknown():
    bucket = _bucket(task_intent="delete_record", risk_category=None)
    examples = corpus._labeled_examples([bucket])
    assert examples[0]["risk_category"] == "unknown"


async def test_load_embeds_text_and_drops_failed_embeddings(monkeypatch):
    buckets = [
        _bucket(task_intent="delete_record", risk_category="delete",
               breakdowns=[_gt(text="delete the record")]),
        _bucket(task_intent="summarize", risk_category="fetch_generic",
               breakdowns=[_gt(text="summarize the doc")]),
    ]

    async def _fetch_raw(agent_host):
        return buckets
    monkeypatch.setattr(corpus, "_fetch_raw", _fetch_raw)

    async def _embed_units(texts):
        return [[0.1, 0.2], None]  # second example fails to embed
    class _FakeClient:
        embed_units = staticmethod(_embed_units)

    # Patch the `intent` package attribute, not just sys.modules — corpus.py's
    # `from intent import client` resolves via getattr(intent, "client") when
    # that attribute is already set (e.g. by another test module having done
    # a real `from intent import client` earlier), which a sys.modules-only
    # patch would silently miss.
    monkeypatch.setattr(intent, "client", _FakeClient, raising=False)

    examples = await corpus.load("agentA")
    assert len(examples) == 1
    assert examples[0] == {"vector": [0.1, 0.2], "task_intent": "delete_record", "risk_category": "delete"}


async def test_load_returns_empty_when_nothing_labeled(monkeypatch):
    async def _fetch_raw(agent_host):
        return []
    monkeypatch.setattr(corpus, "_fetch_raw", _fetch_raw)
    assert await corpus.load("agentA") == []


# --------------------------------------------------------------------------- #
# warmup — builds the structure profile and retrains from whatever's labeled
# --------------------------------------------------------------------------- #
async def test_warmup_builds_structure_profile_and_trains(monkeypatch):
    buckets = [_bucket(task_intent="delete_record", risk_category="delete",
                       breakdowns=[_gt(key="userask", text="delete the record")] * corpus._MIN_OBSERVATIONS)]

    async def _fetch_raw(agent_host):
        return buckets
    monkeypatch.setattr(corpus, "_fetch_raw", _fetch_raw)

    cached = {}
    def _cache(agent_host, profile):
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

    async def _embed_units(texts):
        return [[0.1] * 4 for _ in texts]
    class _FakeClient:
        embed_units = staticmethod(_embed_units)

    monkeypatch.setattr(intent, "trainer", _FakeTrainer, raising=False)
    monkeypatch.setattr(intent, "client", _FakeClient, raising=False)

    corpus._refreshing.discard("agentZ")
    await corpus.warmup("agentZ")

    assert cached["agent_host"] == "agentZ"
    assert "userask" in cached["profile"]["instruction_keys"]
    assert trained["n"] == 2  # load_examples + train_now both called


async def test_warmup_stays_cold_when_no_rows_exist(monkeypatch):
    async def _fetch_raw(agent_host):
        return []
    monkeypatch.setattr(corpus, "_fetch_raw", _fetch_raw)

    cache_calls = {"n": 0}
    def _cache(agent_host, profile):
        cache_calls["n"] += 1
    monkeypatch.setattr(corpus, "_cache_structure_profile", _cache)

    corpus._refreshing.discard("agentEmpty")
    await corpus.warmup("agentEmpty")
    assert cache_calls["n"] == 0  # no rows at all -> stays cold, never builds a profile
