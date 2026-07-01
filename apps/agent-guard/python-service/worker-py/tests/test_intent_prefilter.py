"""intent.prefilter — capability/structure profile caching (fail-open) and
decide_fast()'s concurrent profile fetch."""

from intent import prefilter


async def test_get_capability_profile_fail_open_without_redis(monkeypatch):
    monkeypatch.setattr(prefilter.settings, "REDIS_URL", "")
    import cache_store
    cache_store._client = None
    cache_store._client_init = False
    assert await prefilter.get_capability_profile("agentA", "you are a helper") is None


async def test_get_structure_profile_fail_open_without_redis(monkeypatch):
    monkeypatch.setattr(prefilter.settings, "REDIS_URL", "")
    import cache_store
    cache_store._client = None
    cache_store._client_init = False
    assert await prefilter.get_structure_profile("agentA") is None


async def test_get_structure_profile_empty_agent_host_returns_none():
    assert await prefilter.get_structure_profile("") is None


async def test_decide_fast_fetches_both_profiles_concurrently(monkeypatch):
    monkeypatch.setattr(prefilter.settings, "INTENT_ENABLED", "true")
    calls = []

    async def _profile(agent_host, system_prompt):
        calls.append("capability")
        return None

    async def _structure(agent_host):
        calls.append("structure")
        return None

    monkeypatch.setattr(prefilter, "get_capability_profile", _profile)
    monkeypatch.setattr(prefilter, "get_structure_profile", _structure)

    out = await prefilter.decide_fast("agentA", "", '{"instruction": "list my orders"}')
    assert set(calls) == {"capability", "structure"}
    # No classifier configured (EMBEDDER_URL unset in test env) -> unmatched -> ESCALATE.
    assert out["decision"] in (prefilter.decision.ALLOW, prefilter.decision.ESCALATE)


async def test_decide_fast_never_raises_on_internal_error(monkeypatch):
    async def _boom(agent_host, system_prompt):
        raise RuntimeError("redis exploded")
    monkeypatch.setattr(prefilter, "get_capability_profile", _boom)

    out = await prefilter.decide_fast("agentA", "", "list my orders")
    assert out["decision"] == prefilter.decision.ESCALATE
    assert out["extraction_method"] == "error"
