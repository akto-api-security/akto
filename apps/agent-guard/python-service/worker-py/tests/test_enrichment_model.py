"""ENRICHMENT_MODEL_CONFIG_JSON moves post-verdict enrichment (/guardrails/llm/async and the
/scan enrichment=reason path) off the core model. Unset or invalid, every route must behave
exactly as before; /guardrails/llm and /guardrails/llm/session never follow it."""

import asyncio
import json

import pytest

import app
import scan_handler
from settings import settings

CORE = {"modelConfigs": [{"provider": "core", "modelRole": "FINAL_ARBITER"}]}
ENRICH = {"modelConfigs": [{"provider": "enrich", "modelRole": "FINAL_ARBITER"}]}
POLICY = [{"provider": "policy", "modelRole": "FINAL_ARBITER"}]


class FakeProvider:
    def __init__(self, name):
        self.name = name

    async def complete(self, prompt):
        return json.dumps({"reason": self.name})


def _fake_build_arbiter(model_configs, model_override=""):
    return FakeProvider(model_configs[0]["provider"])


@pytest.fixture
def fake_models(monkeypatch):
    monkeypatch.setattr(settings, "DEFAULT_MODEL_CONFIG_JSON", json.dumps(CORE))
    monkeypatch.setattr(settings, "ENRICHMENT_MODEL_CONFIG_JSON", "")
    monkeypatch.setattr(app, "build_arbiter", _fake_build_arbiter)
    monkeypatch.setattr(scan_handler, "build_arbiter", _fake_build_arbiter)


def _route(fn):
    return json.loads(asyncio.run(fn(app.GuardrailsLLMRequest(prompt="p")))["content"])["reason"]


def _reason(config=None):
    payload = {"scanner_name": "PromptInjection", "text": "t", "enrichment": "reason"}
    if config is not None:
        payload["config"] = config
    return asyncio.run(scan_handler.scan_payload(payload))["details"]["reason"]


@pytest.mark.parametrize("enrichment_json", ["", "not json", json.dumps({"modelConfigs": []})])
def test_unset_or_invalid_keeps_current_behaviour(fake_models, monkeypatch, enrichment_json):
    monkeypatch.setattr(settings, "ENRICHMENT_MODEL_CONFIG_JSON", enrichment_json)
    assert _route(app.guardrails_llm) == "core"
    assert _route(app.guardrails_llm_async) == "core"
    assert _route(app.guardrails_llm_session) == "core"
    assert _reason() == "core"
    assert _reason({"modelConfigs": POLICY}) == "policy"


def test_set_moves_only_enrichment(fake_models, monkeypatch):
    monkeypatch.setattr(settings, "ENRICHMENT_MODEL_CONFIG_JSON", json.dumps(ENRICH))
    assert _route(app.guardrails_llm) == "core"
    assert _route(app.guardrails_llm_session) == "core"
    assert _route(app.guardrails_llm_async) == "enrich"
    assert _reason() == "enrich"
    assert _reason({"modelConfigs": POLICY}) == "enrich"


def test_anthropic_reply_skips_leading_thinking_block():
    import providers

    body = {"content": [{"type": "thinking", "thinking": ""}, {"type": "text", "text": "answer"}]}
    assert providers._anthropic_text(body) == "answer"
    with pytest.raises(ValueError):
        providers._anthropic_text({"stop_reason": "refusal", "content": []})
