"""intent.prefilter — structure profile caching (fail-open) and
decide_fast()'s warm/cold gating around the embed+classify calls."""

from intent import prefilter, structure_cache


async def test_get_structure_profile_fail_open_before_any_warmup():
    structure_cache._profiles.pop("agentA", None)
    assert await prefilter.get_structure_profile("agentA") is None


async def test_get_structure_profile_round_trips_a_cached_profile():
    structure_cache.set("agentA", {"instruction_keys": ["userask"], "instruction_verbs": ["delete"]})
    assert await prefilter.get_structure_profile("agentA") == {
        "instruction_keys": ["userask"], "instruction_verbs": ["delete"],
    }
    structure_cache._profiles.pop("agentA", None)


async def test_get_structure_profile_empty_agent_host_returns_none():
    assert await prefilter.get_structure_profile("") is None


async def test_decide_fast_cold_agent_skips_classifier_warm_flag(monkeypatch):
    monkeypatch.setattr(prefilter.settings, "INTENT_ENABLED", "true")
    calls = []

    async def _structure(agent_host):
        calls.append("structure")
        return None  # no corpus/profile yet for this agent -> cold

    monkeypatch.setattr(prefilter, "get_structure_profile", _structure)

    out = await prefilter.decide_fast("agentA", '{"instruction": "list my orders"}')
    assert calls == ["structure"]
    assert out["classifier_warm"] is False
    # Cold agent: extraction still runs, but with no model to match against
    # every unit is unmatched -> ESCALATE (never a false ALLOW).
    assert out["decision"] == prefilter.decision.ESCALATE


async def test_decide_fast_never_raises_on_internal_error(monkeypatch):
    async def _boom(agent_host):
        raise RuntimeError("structure cache exploded")
    monkeypatch.setattr(prefilter, "get_structure_profile", _boom)

    out = await prefilter.decide_fast("agentA", "list my orders")
    assert out["decision"] == prefilter.decision.ESCALATE
    assert out["extraction_method"] == "error"
    assert out["classifier_warm"] is False
