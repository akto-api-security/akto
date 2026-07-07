"""intent.corpus — per-unit queuing, the GOOD-verdict refresh counter, and
the per-agent structure-profile lexicon. Every row build_structure_profile()
and load() ever see is LLM ground-truthed by construction (the Java-side
labeling cron only ever writes a row to the final corpus once an LLM has
classified it), so there is no worker's-own-guess fallback tier to test."""

import intent
from intent import corpus


def _row(unit_text="delete the record", task_intent="delete_record", breakdown=None):
    row = {"unitText": unit_text, "taskIntent": task_intent, "riskCategory": "delete"}
    if breakdown is not None:
        row["breakdown"] = breakdown
    return row


def _gt(key="instruction", text=None):
    return {"groundTruthSourceKey": key, "groundTruthInstructionText": text}


def test_queue_stores_unit_text_only():
    corpus._buffer.clear()
    corpus.queue("agentA", [{"text": "delete the record"}])
    row = dict(corpus._buffer[-1])
    row.pop("createdAt")
    assert row == {"agentHost": "agentA", "unitText": "delete the record",
                    "taskIntent": "", "riskCategory": ""}
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
    rows = [_row(breakdown=_gt(text="reconcile the ledger")) for _ in range(2)]  # below _MIN_OBSERVATIONS=3
    profile = corpus.build_structure_profile(rows)
    assert profile["instruction_keys"] == []
    assert profile["instruction_verbs"] == []


def test_build_structure_profile_learns_frequent_key_and_verb():
    rows = [_row(breakdown=_gt(key="userask", text="delete the record")) for _ in range(5)]
    profile = corpus.build_structure_profile(rows)
    assert "userask" in profile["instruction_keys"]
    assert "delete" in profile["instruction_verbs"]


def test_build_structure_profile_falls_back_to_unit_text_when_ground_truth_text_missing():
    rows = [_row(unit_text="reconcile the ledger", breakdown=_gt(key="userask", text=None))
           for _ in range(corpus._MIN_OBSERVATIONS)]
    profile = corpus.build_structure_profile(rows)
    assert "reconcile" in profile["instruction_verbs"]


def test_build_structure_profile_ignores_rows_without_a_ground_truth_key():
    rows = [_row(breakdown={"groundTruthSourceKey": "", "groundTruthInstructionText": ""})
           for _ in range(5)]
    profile = corpus.build_structure_profile(rows)
    assert profile["instruction_keys"] == []


def test_build_structure_profile_caps_learned_keys():
    rows = []
    for i in range(corpus._MAX_LEARNED_KEYS + 5):
        rows.extend(_row(breakdown=_gt(key=f"key{i}", text="do something"))
                   for _ in range(corpus._MIN_OBSERVATIONS))
    profile = corpus.build_structure_profile(rows)
    assert len(profile["instruction_keys"]) == corpus._MAX_LEARNED_KEYS


# --------------------------------------------------------------------------- #
# _labeled_examples / load — ground-truth text preferred, embedded at load time
# --------------------------------------------------------------------------- #
def test_labeled_examples_skips_unlabeled_rows():
    assert corpus._labeled_examples([_row(task_intent="")]) == []


def test_labeled_examples_prefers_ground_truth_text_over_unit_text():
    rows = [_row(unit_text="wrong guess", breakdown=_gt(text="the corrected instruction"))]
    examples = corpus._labeled_examples(rows)
    assert examples[0]["text"] == "the corrected instruction"


def test_labeled_examples_falls_back_to_unit_text_without_ground_truth():
    rows = [_row(unit_text="delete the record")]
    examples = corpus._labeled_examples(rows)
    assert examples[0]["text"] == "delete the record"


async def test_load_embeds_text_and_drops_failed_embeddings(monkeypatch):
    raw_rows = [
        _row(unit_text="delete the record", task_intent="delete_record"),
        _row(unit_text="summarize the doc", task_intent="summarize"),
    ]

    async def _fetch_raw(agent_host):
        return raw_rows
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
    raw_rows = [_row(unit_text="delete the record", task_intent="delete_record",
                     breakdown=_gt(key="userask", text="delete the record"))
               for _ in range(corpus._MIN_OBSERVATIONS)]

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
    async def _cache(agent_host, profile):
        cache_calls["n"] += 1
    monkeypatch.setattr(corpus, "_cache_structure_profile", _cache)

    corpus._refreshing.discard("agentEmpty")
    await corpus.warmup("agentEmpty")
    assert cache_calls["n"] == 0  # no rows at all -> stays cold, never builds a profile
