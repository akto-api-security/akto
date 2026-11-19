"""get_default_config / single_model_config — per-deployment modelMap resolution."""

import json

import pytest

from constants import BUILTIN_DEFAULT_CONFIG, get_default_config, single_model_config
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


# ── single_model_config — Password's one model ───────────────────────────────


@pytest.fixture
def no_deployment_config(monkeypatch):
    monkeypatch.setattr(settings, "DEFAULT_MODEL_CONFIG_JSON", "")


def _deployment(monkeypatch, *entries):
    monkeypatch.setattr(settings, "DEFAULT_MODEL_CONFIG_JSON", json.dumps({"modelConfigs": list(entries)}))


# Without DEFAULT_MODEL_CONFIG_JSON: the configured Gemma backend, as before.


def test_fallback_prefers_foundry_when_both_configured(monkeypatch, no_deployment_config):
    # A leftover GEMMA_VERTEX_* block must not divert Password off Azure.
    monkeypatch.setattr(settings, "GEMMA_VERTEX_ENDPOINT_ID", "1234567890")
    monkeypatch.setattr(settings, "GEMMA_FOUNDRY_BASE_URL", "https://ep.eastus2.inference.ml.azure.com/v1")
    assert single_model_config()[0]["provider"] == "gemma_foundry"


def test_fallback_uses_foundry_when_vertex_absent(monkeypatch, no_deployment_config):
    monkeypatch.setattr(settings, "GEMMA_VERTEX_ENDPOINT_ID", "")
    monkeypatch.setattr(settings, "GEMMA_FOUNDRY_BASE_URL", "https://ep.eastus2.inference.ml.azure.com/v1")
    cfg = single_model_config()
    assert cfg == [{"provider": "gemma_foundry", "modelRole": "FINAL_ARBITER", "timeoutMs": 30000}]


def test_fallback_uses_vertex_when_it_is_the_only_backend(monkeypatch, no_deployment_config):
    monkeypatch.setattr(settings, "GEMMA_VERTEX_ENDPOINT_ID", "1234567890")
    monkeypatch.setattr(settings, "GEMMA_FOUNDRY_BASE_URL", "")
    assert single_model_config()[0]["provider"] == "gemma_vertexai"


def test_fallback_defaults_to_foundry_when_nothing_configured(monkeypatch, no_deployment_config):
    monkeypatch.setattr(settings, "GEMMA_VERTEX_ENDPOINT_ID", "")
    monkeypatch.setattr(settings, "GEMMA_FOUNDRY_BASE_URL", "")
    assert single_model_config()[0]["provider"] == "gemma_foundry"


# With DEFAULT_MODEL_CONFIG_JSON: one entry picked from it.


def test_fallback_filter_is_preferred(monkeypatch):
    _deployment(
        monkeypatch,
        {"provider": "bedrock", "model": "us.amazon.nova-micro-v1:0", "modelRole": "FAST_THREAT_FILTER"},
        {
            "provider": "bedrock",
            "model": "us.amazon.nova-lite-v1:0",
            "modelRole": "FAST_FALLBACK_SAFE_FILTER",
            "timeoutMs": 5000,
        },
        {"provider": "bedrock", "model": "us.amazon.nova-2-lite-v1:0", "modelRole": "FINAL_ARBITER"},
    )
    assert single_model_config() == [
        {"provider": "bedrock", "model": "us.amazon.nova-lite-v1:0", "modelRole": "FINAL_ARBITER", "timeoutMs": 5000}
    ]


def test_arbiter_used_when_no_fallback_filter(monkeypatch):
    _deployment(
        monkeypatch,
        {"provider": "gemma_foundry", "model": "gemma-4-31b", "modelRole": "FAST_THREAT_FILTER"},
        {"provider": "bedrock", "model": "us.amazon.nova-2-lite-v1:0", "modelRole": "FINAL_ARBITER"},
    )
    assert single_model_config() == [
        {"provider": "bedrock", "model": "us.amazon.nova-2-lite-v1:0", "modelRole": "FINAL_ARBITER", "timeoutMs": 30000}
    ]


def test_qwen_fallback_filter_is_skipped_for_the_arbiter(monkeypatch):
    _deployment(
        monkeypatch,
        {"provider": "qwen3guard_foundry", "modelRole": "FAST_FALLBACK_SAFE_FILTER"},
        {"provider": "gemma_foundry", "model": "gemma-4-31b", "modelRole": "FINAL_ARBITER", "timeoutMs": 30000},
    )
    assert single_model_config()[0]["provider"] == "gemma_foundry"


def test_falls_back_to_gemma_without_fallback_filter_or_arbiter(monkeypatch):
    # Only fast-threat-filter entries (incl. a non-qwen one) → none eligible → Gemma backend.
    monkeypatch.setattr(settings, "GEMMA_VERTEX_ENDPOINT_ID", "")
    monkeypatch.setattr(settings, "GEMMA_FOUNDRY_BASE_URL", "")
    _deployment(
        monkeypatch,
        {"provider": "qwen3guard", "modelRole": "FAST_THREAT_FILTER"},
        {"provider": "bedrock", "model": "m1", "modelRole": "FAST_THREAT_FILTER"},
    )
    assert single_model_config() == [{"provider": "gemma_foundry", "modelRole": "FINAL_ARBITER", "timeoutMs": 30000}]
