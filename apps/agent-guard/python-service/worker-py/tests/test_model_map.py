"""ModelMapScanner cascade logic — fake async providers, no network.

We monkeypatch provider construction and the LLMScanner so each modelConfigs
entry yields a scripted result, then assert the cascade routes/escalates and
shapes results correctly.
"""

import pytest

import model_map


# A scripted result per provider name. _classify treats is_valid False OR
# decision_confidence < safeDecisionThreshold as unsafe.
SAFE = {"is_valid": True, "risk_score": 0.02, "decision_confidence": 0.99, "details": {}}
UNSAFE = {"is_valid": False, "risk_score": 0.97, "decision_confidence": 0.97, "details": {}}


class FakeProvider:
    def __init__(self, name):
        self.name = name


class FakeScanner:
    """Stands in for LLMScanner; returns scripted[provider] and records calls."""

    script = {}
    calls = []

    def __init__(self, provider):
        self.provider = provider

    async def scan(self, scanner_name, scanner_type, text, config):
        FakeScanner.calls.append(self.provider.name)
        entry = FakeScanner.script[self.provider.name]
        if isinstance(entry, Exception):
            raise entry
        r = {k: v for k, v in entry.items()}
        r["details"] = dict(r.get("details", {}), llm_provider=self.provider.name)
        return r


@pytest.fixture(autouse=True)
def patch_providers(monkeypatch):
    FakeScanner.calls = []
    FakeScanner.script = {}
    monkeypatch.setattr(model_map, "build_provider_from_config",
                        lambda entry: FakeProvider(entry["provider"]))
    # run() does `from llm_scanner import LLMScanner`; patch it there.
    import llm_scanner
    monkeypatch.setattr(llm_scanner, "LLMScanner", FakeScanner)
    yield


def _entry(provider, role, **kw):
    e = {"provider": provider, "modelRole": role, "safeDecisionThreshold": 0.9}
    e.update(kw)
    return e


async def _run(model_configs, script, **cfg):
    FakeScanner.script = script
    config = {"modelConfigs": model_configs, **cfg}
    return await model_map.ModelMapScanner("Toxicity", "prompt", "text", config).run()


# ── default 2-entry config (tier1 + arbiter, no tier2): arbiter always decides ──


async def test_default_config_arbiter_allows():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": SAFE, "gemma_vertexai": SAFE})
    assert r["is_valid"] is True
    assert r["details"]["cascade_decision"] == "gemma_authority"
    assert "gemma_vertexai" in FakeScanner.calls  # arbiter consulted


async def test_default_config_arbiter_blocks():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": SAFE, "gemma_vertexai": UNSAFE})
    assert r["is_valid"] is False


async def test_tier1_unsafe_escalates_to_arbiter():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": UNSAFE, "gemma_vertexai": UNSAFE})
    assert r["is_valid"] is False
    assert "gemma_vertexai" in FakeScanner.calls


# ── 3-entry config exercises the tier2 short-circuit ─────────────────────────


async def test_all_safe_three_tier_skips_arbiter():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("openai", "FAST_FALLBACK_SAFE_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": SAFE, "openai": SAFE, "gemma_vertexai": SAFE})
    assert r["is_valid"] is True
    # tier1 SAFE + tier2 SAFE -> arbiter NOT consulted
    assert "gemma_vertexai" not in FakeScanner.calls


async def test_tier2_unsafe_escalates_to_arbiter():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("openai", "FAST_FALLBACK_SAFE_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": SAFE, "openai": UNSAFE, "gemma_vertexai": UNSAFE})
    assert r["is_valid"] is False
    assert "gemma_vertexai" in FakeScanner.calls


# ── failure handling + result shaping ────────────────────────────────────────


async def test_provider_failure_counts_unsafe():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": RuntimeError("boom"), "gemma_vertexai": UNSAFE})
    # tier1 raised -> treated unsafe -> arbiter decides (unsafe)
    assert r["is_valid"] is False


async def test_result_shape_has_per_model_summaries():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": SAFE, "gemma_vertexai": SAFE})
    d = r["details"]
    assert d["scanner_type"] == "prompt"
    assert d["qwen"]["completed"] is True
    assert d["gemma"]["completed"] is True
    assert "execution_time_ms" in r


async def test_no_arbiter_configured_fails_closed():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER")]
    r = await _run(cfg, {"qwen3guard": UNSAFE})
    assert r["is_valid"] is False
    assert r["risk_score"] == 1.0
