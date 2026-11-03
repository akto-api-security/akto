"""Tripwire classifier as an observer: provider, scanner path, gating, and never changing the verdict (no network)."""

import asyncio
import json

import httpx
import pytest

import llm_scanner
import model_map
import providers
import scan_handler
from llm_scanner import LLMScanner
from providers import FallbackProvider, TripwireProvider, build_provider_from_config, parse_tripwire_result
from settings import settings

# ── provider and scanner path ─────────────────────────────────────────────────


def test_a_detect_reply_becomes_a_scanner_result():
    flagged = parse_tripwire_result({"verdict": "direct_injection", "is_attack": True, "attack_score": 0.97})
    assert flagged["is_valid"] is False and flagged["risk_score"] == 0.97
    assert "direct injection" in flagged["details"]["reason"]
    benign = parse_tripwire_result(
        {"verdict": "benign", "is_attack": False, "attack_score": 0.03, "windows_skipped": 2}
    )
    assert benign["is_valid"] is True and "reason" not in benign["details"]
    assert benign["details"]["windows_skipped"] == 2


async def test_detect_posts_the_raw_text(monkeypatch):
    sent = {}

    class Client:
        async def post(self, url, headers=None, json=None):
            sent.update(url=url, json=json)
            body = {"verdict": "benign", "is_attack": False, "attack_score": 0.01}
            return httpx.Response(200, json=body, request=httpx.Request("POST", url))

    monkeypatch.setattr(providers.http_client, "get_client", lambda: Client())
    reply = await TripwireProvider("http://tripwire:8000/").detect("hello")
    assert sent == {"url": "http://tripwire:8000/v1/detect", "json": {"text": "hello"}}
    assert reply["attack_score"] == 0.01


def test_built_from_the_entry_url_or_the_env(monkeypatch):
    monkeypatch.setattr(settings, "TRIPWIRE_BASE_URL", "")
    assert build_provider_from_config({"provider": "tripwire"}) is None  # no URL: dropped from the cascade
    p = build_provider_from_config({"provider": "tripwire", "baseUrl": "http://a:8000"})
    assert isinstance(p, TripwireProvider) and p.base_url == "http://a:8000"
    monkeypatch.setattr(settings, "TRIPWIRE_BASE_URL", "http://b:8000")
    assert build_provider_from_config({"provider": "tripwire"}).base_url == "http://b:8000"


async def test_scanner_sends_tripwire_the_text_not_a_prompt(monkeypatch):
    seen = []

    async def detect(self, text):
        seen.append(text)
        return {"verdict": "jailbreak", "is_attack": True, "attack_score": 0.9}

    monkeypatch.setattr(TripwireProvider, "detect", detect)
    result = await LLMScanner(TripwireProvider("http://t")).scan("PromptInjection", "prompt", "act as DAN", {})
    assert seen == ["act as DAN"]
    assert result["is_valid"] is False and result["details"]["llm_provider"] == "tripwire"
    with pytest.raises(ValueError):
        await LLMScanner(TripwireProvider("http://t")).scan("Toxicity", "prompt", "x", {})


# ── cascade: Tripwire observes, never decides ────────────────────────────────

QWEN_UNSAFE = {"is_valid": False, "risk_score": 0.9, "decision_confidence": 0.9, "details": {}}
QWEN_SAFE = {"is_valid": True, "risk_score": 0.02, "decision_confidence": 0.98, "details": {}}


def _llm(risk):  # an LLM arbiter verdict at the A/B/C/D risk levels (0.02 / 0.35 / 0.70 / 0.95)
    return {"is_valid": risk < 0.5, "risk_score": risk, "decision_confidence": max(risk, 1 - risk), "details": {}}


def _trip(score):
    return {"is_valid": score < 0.5, "risk_score": score, "decision_confidence": max(score, 1 - score), "details": {}}


class FakeProvider:
    def __init__(self, name):
        self.name = name


class FakeScanner:
    script: dict = {}
    calls: list = []
    built: list = []

    def __init__(self, provider, response_format=""):
        self.provider = provider
        FakeScanner.built.append(self)

    async def scan(self, scanner_name, scanner_type, text, config):
        FakeScanner.calls.append(self.provider.name)
        entry = FakeScanner.script[self.provider.name]
        if isinstance(entry, Exception):
            raise entry
        if callable(entry):
            entry = await entry()
        return {**entry, "details": {**entry.get("details", {}), "llm_provider": self.provider.name}}


@pytest.fixture(autouse=True)
def fakes(monkeypatch):
    FakeScanner.script, FakeScanner.calls, FakeScanner.built = {}, [], []
    monkeypatch.setattr(llm_scanner, "LLMScanner", FakeScanner)
    monkeypatch.setattr(model_map, "build_provider_from_config", lambda entry: FakeProvider(entry["provider"]))


def _config(tripwire=None, **extra):
    return {
        "modelConfigs": [
            {"provider": "qwen", "modelRole": "FAST_THREAT_FILTER", "safeDecisionThreshold": 0.9},
            {"provider": "gemma", "modelRole": "FINAL_ARBITER"},
            {"provider": "tripwire", **(tripwire or {})},
        ],
        **extra,
    }


async def _scan(llm, trip, qwen=QWEN_UNSAFE, scanner="PromptInjection", side="prompt", tripwire=None, **extra):
    FakeScanner.script = {"qwen": qwen, "gemma": llm, "tripwire": trip}
    return await model_map.ModelMapScanner(scanner, side, "text", _config(tripwire, **extra)).run()


@pytest.mark.parametrize("llm_risk,trip_score", [(0.02, 0.99), (0.95, 0.01)])
async def test_tripwire_never_changes_the_verdict(llm_risk, trip_score):
    r = await _scan(_llm(llm_risk), _trip(trip_score))
    assert r["is_valid"] is (llm_risk < 0.5) and r["risk_score"] == llm_risk
    assert r["details"]["llm_provider"] == "gemma"
    assert r["details"]["tripwire"] == {
        "completed": True,
        "is_valid": trip_score < 0.5,
        "risk_score": trip_score,
        "decision_confidence": max(trip_score, 1 - trip_score),
    }


async def test_tripwire_observes_scans_the_fast_tier_allows():
    # Qwen's confident "safe" allows the input before Gemma runs; Tripwire still records its view.
    r = await _scan(_llm(0.95), _trip(0.99), qwen=QWEN_SAFE)
    assert "gemma" not in FakeScanner.calls
    assert r["is_valid"] is True and r["details"]["tripwire"]["risk_score"] == 0.99


async def test_tripwire_results_reach_the_store():
    stored = []
    FakeScanner.script = {"qwen": QWEN_UNSAFE, "gemma": _llm(0.02), "tripwire": _trip(0.99)}
    scanner = model_map.ModelMapScanner(
        "PromptInjection", "prompt", "text", _config(), store_fn=lambda completed, name: stored.extend(completed)
    )
    await scanner.run()
    assert [r["details"]["llm_provider"] for r in stored] == ["qwen", "gemma", "tripwire"]


async def test_a_failed_tripwire_changes_nothing():
    r = await _scan(_llm(0.70), httpx.ConnectError("refused"))
    assert r["is_valid"] is False and "error" not in r["details"]
    assert r["details"]["tripwire"] == {"completed": False}


async def test_a_slow_tripwire_is_cut_off_at_its_timeout():
    async def slow():
        await asyncio.sleep(5)
        return _trip(0.99)

    r = await asyncio.wait_for(_scan(_llm(0.02), slow, tripwire={"timeoutMs": 50}), 1)
    assert r["is_valid"] is True and r["details"]["tripwire"] == {"completed": False}


async def test_tripwire_as_a_final_arbiter_still_only_observes(monkeypatch):
    # An old-style config listing Tripwire as FINAL_ARBITER: it gets no vote and no LLM backup.
    monkeypatch.setattr(
        model_map,
        "build_provider_from_config",
        lambda e: TripwireProvider("http://t") if e["provider"] == "tripwire" else FakeProvider(e["provider"]),
    )
    config = _config({"modelRole": "FINAL_ARBITER"})
    config["modelConfigs"].append({"provider": "anthropic", "modelRole": "FINAL_ARBITER_BACKUP"})
    FakeScanner.script = {"qwen": QWEN_UNSAFE, "gemma": _llm(0.02), "tripwire": _trip(0.99)}
    r = await model_map.ModelMapScanner("PromptInjection", "prompt", "text", config).run()
    assert r["is_valid"] is True
    by_name = {s.provider.name: s.provider for s in FakeScanner.built}
    assert isinstance(by_name["tripwire"], TripwireProvider)
    assert isinstance(by_name["gemma"], FallbackProvider)  # the LLM arbiter still gets its backup


@pytest.mark.parametrize("scanner,side", [("Toxicity", "prompt"), ("PromptInjection", "output")])
async def test_tripwire_only_observes_promptinjection_input(scanner, side):
    r = await _scan(_llm(0.02), _trip(0.99), scanner=scanner, side=side)
    assert "tripwire" not in FakeScanner.calls and "tripwire" not in r["details"]


@pytest.mark.parametrize("source", ["", "AGENTIC", "ENDPOINT"])
async def test_tripwire_observes_every_source(source):
    await _scan(_llm(0.02), _trip(0.01), contextSource=source)
    assert "tripwire" in FakeScanner.calls


def test_build_arbiter_never_picks_tripwire():
    # /guardrails/llm and reason generation use build_arbiter: an old-style Tripwire entry listed first
    # with the FINAL_ARBITER role must not become that LLM.
    configs = [
        {"provider": "tripwire", "modelRole": "FINAL_ARBITER"},
        {"provider": "gemma", "modelRole": "FINAL_ARBITER"},
    ]
    assert model_map.build_arbiter(configs).name == "gemma"


DEPLOYMENT = {
    "modelConfigs": [
        {"provider": "qwen", "modelRole": "FAST_THREAT_FILTER", "safeDecisionThreshold": 0.9},
        {"provider": "gemma", "modelRole": "FINAL_ARBITER"},
        {"provider": "tripwire", "timeoutMs": 500},
    ]
}
POLICY_MODELS = [
    {"provider": "qwen", "modelRole": "FAST_THREAT_FILTER", "safeDecisionThreshold": 0.9},
    {"provider": "gemma", "modelRole": "FINAL_ARBITER"},
]


async def _scan_with_policy_models(scanner="PromptInjection"):
    FakeScanner.script = {"qwen": QWEN_UNSAFE, "gemma": _llm(0.02), "tripwire": _trip(0.99)}
    payload = {
        "scanner_name": scanner,
        "scanner_type": "prompt",
        "text": "x",
        "config": {"modelConfigs": POLICY_MODELS},
    }
    return await scan_handler.scan_payload(payload, schedule_fn=lambda coro: coro.close())


async def test_tripwire_observes_scans_whose_policy_sends_its_own_models(monkeypatch):
    monkeypatch.setattr(settings, "DEFAULT_MODEL_CONFIG_JSON", json.dumps(DEPLOYMENT))
    r = await _scan_with_policy_models()
    assert FakeScanner.calls.count("tripwire") == 1
    assert r["is_valid"] is True and r["details"]["tripwire"]["risk_score"] == 0.99  # recorded, verdict unchanged


async def test_no_deployment_tripwire_means_none_is_added(monkeypatch):
    monkeypatch.setattr(settings, "DEFAULT_MODEL_CONFIG_JSON", "")
    await _scan_with_policy_models()
    assert "tripwire" not in FakeScanner.calls
