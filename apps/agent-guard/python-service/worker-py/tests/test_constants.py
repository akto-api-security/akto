"""get_default_config — per-deployment modelMap override resolution."""

import json

from constants import BUILTIN_DEFAULT_CONFIG, force_gemma_only, get_default_config
from settings import settings


def test_empty_falls_back_to_builtin():
    assert get_default_config("") is BUILTIN_DEFAULT_CONFIG
    assert get_default_config("   ") is BUILTIN_DEFAULT_CONFIG


def test_valid_json_overrides_builtin():
    custom = {
        "modelConfigs": [
            {"provider": "anthropic", "modelRole": "FINAL_ARBITER", "safeDecisionThreshold": 0.8, "timeoutMs": 20000},
        ],
        "parallelExecution": True,
        "storeAllResults": True,
    }
    out = get_default_config(json.dumps(custom))
    assert out == custom
    assert out is not BUILTIN_DEFAULT_CONFIG
    assert out["modelConfigs"][0]["provider"] == "anthropic"


def test_malformed_json_falls_back():
    assert get_default_config("{not valid json") is BUILTIN_DEFAULT_CONFIG


def test_json_without_modelconfigs_falls_back():
    # well-formed but missing the required key → built-in
    assert get_default_config('{"parallelExecution": true}') is BUILTIN_DEFAULT_CONFIG


def test_builtin_shape_is_intact():
    # guards against accidental edits to the fallback
    providers = [m["provider"] for m in BUILTIN_DEFAULT_CONFIG["modelConfigs"]]
    assert providers == ["qwen3guard", "gemma_foundry"]


# ── force_gemma_only — Gemma backend resolution ──────────────────────────────


def test_force_gemma_only_always_uses_foundry(monkeypatch):
    # Hostile env: Vertex populated, Foundry not. Password must still pick Foundry —
    # honouring a leftover GEMMA_VERTEX_* block pinned it to a dead endpoint.
    monkeypatch.setattr(settings, "GEMMA_VERTEX_ENDPOINT_ID", "1234567890")
    monkeypatch.setattr(settings, "GEMMA_FOUNDRY_BASE_URL", "")
    cfg = force_gemma_only([])
    assert cfg == [{"provider": "gemma_foundry", "modelRole": "FINAL_ARBITER", "timeoutMs": 30000}]
