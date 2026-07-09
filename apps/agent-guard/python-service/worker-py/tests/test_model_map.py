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
# Safe verdict below the 0.9 safeDecisionThreshold: escalates past the fast tiers.
HEDGED_SAFE = {"is_valid": True, "risk_score": 0.3, "decision_confidence": 0.7, "details": {}}


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


async def test_confident_safe_tier1_skips_arbiter():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": SAFE, "gemma_vertexai": SAFE})
    assert r["is_valid"] is True
    assert "gemma_vertexai" not in FakeScanner.calls  # no escalation needed


async def test_hedged_tier1_escalates_and_arbiter_allows():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": HEDGED_SAFE, "gemma_vertexai": SAFE})
    assert r["is_valid"] is True
    assert r["details"]["cascade_decision"] == "gemma_authority"
    assert "gemma_vertexai" in FakeScanner.calls  # arbiter consulted


async def test_default_config_arbiter_blocks():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": HEDGED_SAFE, "gemma_vertexai": UNSAFE})
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
    r = await _run(cfg, {"qwen3guard": HEDGED_SAFE, "gemma_vertexai": SAFE})
    d = r["details"]
    assert d["scanner_type"] == "prompt"
    assert d["qwen"]["completed"] is True
    assert d["gemma"]["completed"] is True
    assert "execution_time_ms" in r


async def test_no_arbiter_configured_fails_open_with_error():
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER")]
    r = await _run(cfg, {"qwen3guard": UNSAFE})
    assert r["is_valid"] is True
    assert r["risk_score"] == 0.0
    assert r["details"]["error"] == "no arbiter configured"


async def test_all_arbiters_failed_fails_open_with_error():
    # Arbiter timeout/error is an infrastructure failure, not a verdict: allow,
    # and carry details.error so the caller can skip caching the degraded result.
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": UNSAFE, "gemma_vertexai": TimeoutError()})
    assert r["is_valid"] is True
    assert r["details"]["error"] == "all arbiters failed"


async def test_both_tiers_empty_escalates_to_arbiter_instead_of_crashing():
    # Both tiers empty (e.g. Gemma-only scanner strips Qwen): must escalate, not crash on max([]).
    cfg = [_entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"gemma_vertexai": SAFE})
    assert r["is_valid"] is True
    assert "gemma_vertexai" in FakeScanner.calls  # arbiter WAS consulted, not skipped


async def test_both_tiers_empty_arbiter_blocks():
    cfg = [_entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"gemma_vertexai": UNSAFE})
    assert r["is_valid"] is False


async def test_both_tiers_empty_two_arbiters_both_safe():
    # Password's real config after strip_qwen_tier: only the two FINAL_ARBITER entries remain.
    cfg = [_entry("gemma_vertexai", "FINAL_ARBITER"), _entry("anthropic", "FINAL_ARBITER")]
    r = await _run(cfg, {"gemma_vertexai": SAFE, "anthropic": SAFE})
    assert r["is_valid"] is True
    assert "gemma_vertexai" in FakeScanner.calls
    assert "anthropic" in FakeScanner.calls  # both arbiters actually consulted


async def test_both_tiers_empty_two_arbiters_one_unsafe_blocks():
    cfg = [_entry("gemma_vertexai", "FINAL_ARBITER"), _entry("anthropic", "FINAL_ARBITER")]
    r = await _run(cfg, {"gemma_vertexai": SAFE, "anthropic": UNSAFE})
    assert r["is_valid"] is False  # either arbiter flagging it blocks


async def test_both_tiers_empty_two_arbiters_one_fails_other_still_decides():
    cfg = [_entry("gemma_vertexai", "FINAL_ARBITER"), _entry("anthropic", "FINAL_ARBITER")]
    r = await _run(cfg, {"gemma_vertexai": SAFE, "anthropic": RuntimeError("timeout")})
    assert r["is_valid"] is True  # one arbiter errored, the other's SAFE verdict still used
    assert "error" not in r["details"]


async def test_arbiter_hedged_acquittal_allows():
    # Arbiter acquittal below safeDecisionThreshold must not be converted into a block.
    hedged_safe = {"is_valid": True, "risk_score": 0.15,
                   "decision_confidence": 0.85, "details": {}}
    cfg = [_entry("qwen3guard", "FAST_THREAT_FILTER"),
           _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"qwen3guard": UNSAFE, "gemma_vertexai": hedged_safe})
    assert r["is_valid"] is True


async def test_arbiter_reason_priority_first_listed_wins_despite_lower_score():
    # gemma is listed before qwen in modelConfigs, so gemma wins even with a lower risk_score.
    gemma_unsafe = {"is_valid": False, "risk_score": 0.8, "decision_confidence": 0.8,
                     "details": {"reason": "Explicit instructions for making a weapon."}}
    qwen_unsafe = {"is_valid": False, "risk_score": 1.0, "decision_confidence": 1.0, "details": {}}
    cfg = [_entry("gemma_vertexai", "FINAL_ARBITER"), _entry("qwen3guard", "FINAL_ARBITER")]
    r = await _run(cfg, {"gemma_vertexai": gemma_unsafe, "qwen3guard": qwen_unsafe})
    assert r["is_valid"] is False
    assert r["details"]["llm_provider"] == "gemma_vertexai"
    assert r["details"]["reason"] == "Explicit instructions for making a weapon."


async def test_arbiter_reason_empty_when_qwen_is_sole_objector():
    # Qwen alone flags it: no reason available, gateway shows its generic message instead.
    gemma_safe = {"is_valid": True, "risk_score": 0.05, "decision_confidence": 0.95, "details": {}}
    qwen_unsafe = {"is_valid": False, "risk_score": 1.0, "decision_confidence": 1.0, "details": {}}
    cfg = [_entry("gemma_vertexai", "FINAL_ARBITER"), _entry("qwen3guard", "FINAL_ARBITER")]
    r = await _run(cfg, {"gemma_vertexai": gemma_safe, "qwen3guard": qwen_unsafe})
    assert r["is_valid"] is False
    assert r["details"]["llm_provider"] == "qwen3guard"
    assert r["details"].get("reason", "") == ""


async def test_arbiter_reason_priority_follows_modelconfigs_order():
    # anthropic is listed first, so it wins over gemma regardless of risk_score.
    anthropic_unsafe = {"is_valid": False, "risk_score": 0.6, "decision_confidence": 0.6,
                         "details": {"reason": "anthropic reason"}}
    gemma_unsafe = {"is_valid": False, "risk_score": 0.99, "decision_confidence": 0.99,
                     "details": {"reason": "gemma reason"}}
    cfg = [_entry("anthropic", "FINAL_ARBITER"), _entry("gemma_vertexai", "FINAL_ARBITER")]
    r = await _run(cfg, {"anthropic": anthropic_unsafe, "gemma_vertexai": gemma_unsafe})
    assert r["details"]["llm_provider"] == "anthropic"
    assert r["details"]["reason"] == "anthropic reason"
