"""PER_POD_MODEL_CONFIGS_JSON: each pod (StatefulSet ordinal in POD_NAME) runs
PromptInjection and Toxicity on its own entry instead of DEFAULT_MODEL_CONFIG_JSON.
A request's own modelConfigs still wins; any problem falls back to the shared
default — never to another pod's entry. Fake providers, no network."""

import asyncio
import json

import pytest

import constants
import llm_scanner
import model_map
import scan_handler
from settings import settings


def _cfg(provider):
    return {"modelConfigs": [{"provider": provider, "modelRole": "FINAL_ARBITER", "safeDecisionThreshold": 0.9}]}


DEFAULT = _cfg("default")
PER_POD = [_cfg("pod-0"), _cfg("pod-1")]


class FakeProvider:
    def __init__(self, name):
        self.name = name

    async def complete(self, prompt):
        return json.dumps({"reason": self.name})


class FakeScanner:
    calls = []

    def __init__(self, provider, response_format=""):
        self.provider = provider

    async def scan(self, scanner_name, scanner_type, text, config):
        FakeScanner.calls.append(self.provider.name)
        return {"is_valid": True, "risk_score": 0.0, "decision_confidence": 0.99, "details": {}}


@pytest.fixture(autouse=True)
def fakes(monkeypatch):
    FakeScanner.calls = []
    constants._resolve_pod_model_config.cache_clear()
    monkeypatch.setattr(model_map, "build_provider_from_config", lambda entry: FakeProvider(entry["provider"]))
    monkeypatch.setattr(scan_handler, "build_arbiter", lambda cfgs, model="": FakeProvider(cfgs[0]["provider"]))
    monkeypatch.setattr(llm_scanner, "LLMScanner", FakeScanner)
    monkeypatch.setattr(settings, "DEFAULT_MODEL_CONFIG_JSON", json.dumps(DEFAULT))
    monkeypatch.setattr(settings, "ENRICHMENT_MODEL_CONFIG_JSON", "")
    monkeypatch.setattr(settings, "PER_POD_MODEL_CONFIGS_JSON", json.dumps(PER_POD))
    monkeypatch.setattr(settings, "POD_NAME", "akto-agent-guard-1")
    yield
    constants._resolve_pod_model_config.cache_clear()


def _scan(scanner, config=None):
    payload = {"scanner_name": scanner, "scanner_type": "prompt", "text": "hi", "config": config or {}}
    asyncio.run(scan_handler.scan_payload(payload, schedule_fn=lambda coro: coro.close()))
    return FakeScanner.calls[-1]


def _reason(scanner, config=None):
    payload = {"scanner_name": scanner, "text": "t", "enrichment": "reason", "config": config or {}}
    return asyncio.run(scan_handler.scan_payload(payload))["details"]["reason"]


def test_one_entry_drives_both_scanners():
    assert _scan("PromptInjection") == "pod-1"
    assert _scan("Toxicity") == "pod-1"
    assert _reason("PromptInjection") == "pod-1"


def test_pod_zero_gets_its_own_entry(monkeypatch):
    monkeypatch.setattr(settings, "POD_NAME", "akto-agent-guard-0")
    assert _scan("PromptInjection") == "pod-0"
    assert _scan("Toxicity") == "pod-0"


def test_request_model_configs_still_win():
    assert _scan("PromptInjection", _cfg("request")) == "request"
    assert _reason("Toxicity", _cfg("request")) == "request"


def test_other_scanners_keep_the_shared_default():
    assert _scan("BanTopics") == "default"
    assert _reason("BanTopics") == "default"


@pytest.mark.parametrize(
    "pod_name, raw",
    [
        ("akto-agent-guard-2", json.dumps(PER_POD)),  # more pods than entries
        ("akto-agent-guard-7d9f-xk2pq", json.dumps(PER_POD)),  # Deployment pod, no ordinal
        ("", json.dumps(PER_POD)),  # POD_NAME not wired
        ("akto-agent-guard-1", "not json"),
        ("akto-agent-guard-1", json.dumps(_cfg("x"))),  # not a list
        ("akto-agent-guard-1", json.dumps([_cfg("x"), {"modelConfigs": []}])),  # entry without models
        ("akto-agent-guard-1", ""),  # unset
    ],
)
def test_problems_fall_back_to_shared_default(monkeypatch, pod_name, raw):
    monkeypatch.setattr(settings, "POD_NAME", pod_name)
    monkeypatch.setattr(settings, "PER_POD_MODEL_CONFIGS_JSON", raw)
    assert _scan("PromptInjection") == "default"
