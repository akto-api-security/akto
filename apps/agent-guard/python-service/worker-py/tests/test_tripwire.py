"""Tripwire classifier next to the LLM arbiter: provider, scanner path, gating and arbiterCombine (no network)."""

import httpx
import pytest

import llm_scanner
import model_map
import providers
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


# ── cascade: gating and arbiterCombine ───────────────────────────────────────

QWEN_UNSAFE = {"is_valid": False, "risk_score": 0.9, "decision_confidence": 0.9, "details": {}}


def _llm(risk):  # an LLM arbiter verdict at the A/B/C/D risk levels (0.02 / 0.35 / 0.70 / 0.95)
    return {"is_valid": risk < 0.5, "risk_score": risk, "decision_confidence": max(risk, 1 - risk), "details": {}}


def _trip(score, **details):
    return {
        "is_valid": score < 0.5,
        "risk_score": score,
        "decision_confidence": max(score, 1 - score),
        "details": {"attack_score": score, **details},
    }


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
        return {**entry, "details": {**entry.get("details", {}), "llm_provider": self.provider.name}}


@pytest.fixture(autouse=True)
def fakes(monkeypatch):
    FakeScanner.script, FakeScanner.calls, FakeScanner.built = {}, [], []
    monkeypatch.setattr(llm_scanner, "LLMScanner", FakeScanner)
    monkeypatch.setattr(model_map, "build_provider_from_config", lambda entry: FakeProvider(entry["provider"]))


def _config(combine=None, **extra):
    config = {
        "modelConfigs": [
            {"provider": "qwen", "modelRole": "FAST_THREAT_FILTER", "safeDecisionThreshold": 0.9},
            {"provider": "gemma", "modelRole": "FINAL_ARBITER"},
            {"provider": "tripwire", "modelRole": "FINAL_ARBITER"},
        ],
        **extra,
    }
    if combine:
        config["arbiterCombine"] = combine
    return config


async def _scan(llm, trip, combine=None, scanner="PromptInjection", side="prompt", **extra):
    FakeScanner.script = {"qwen": QWEN_UNSAFE, "gemma": llm, "tripwire": trip}  # qwen escalates to the arbiters
    return await model_map.ModelMapScanner(scanner, side, "text", _config(combine, **extra)).run()


async def test_default_any_blocks_if_either_arbiter_flags():
    r = await _scan(_llm(0.02), _trip(0.97))
    assert r["is_valid"] is False


async def test_shadow_lets_the_llm_decide_and_records_tripwire():
    r = await _scan(_llm(0.02), _trip(0.97), combine="shadow")
    assert r["is_valid"] is True and r["details"]["arbiter_combine"] == "shadow"
    assert r["details"]["tripwire"]["risk_score"] == 0.97  # Tripwire's score is in the result


async def test_shadow_fails_open_like_today_when_the_llm_fails():
    r = await _scan(TimeoutError(), _trip(0.97), combine="shadow")
    assert r["is_valid"] is True and "error" in r["details"]


@pytest.mark.parametrize(
    "llm_risk,trip_score,blocked",
    [
        (0.02, 0.97, False),  # Gemma A: Tripwire needs >= 0.98 to override
        (0.02, 0.99, True),
        (0.35, 0.70, True),  # Gemma B, Tripwire leaning attack
        (0.70, 0.20, False),  # Gemma C, Tripwire fairly sure it's safe
        (0.95, 0.02, False),  # Gemma D: only a near-certain Tripwire clears it
        (0.95, 0.10, True),
    ],
)
async def test_mean_averages_the_two_scores(llm_risk, trip_score, blocked):
    r = await _scan(_llm(llm_risk), _trip(trip_score), combine="mean")
    assert r["is_valid"] is (not blocked)
    assert r["risk_score"] == pytest.approx((llm_risk + trip_score) / 2)
    assert r["details"]["arbiter_combine"] == "mean"


async def test_mean_explains_the_verdict_with_the_side_that_agrees():
    r = await _scan(
        _llm(0.35), _trip(0.70, reason="Tripwire flagged this as jailbreak (attack score 0.70)."), combine="mean"
    )
    assert r["is_valid"] is False and r["details"]["llm_provider"] == "tripwire"
    assert "jailbreak" in r["details"]["reason"]


async def test_mean_honours_arbiter_threshold():
    r = await _scan(_llm(0.35), _trip(0.70), combine="mean", arbiterThreshold=0.6)
    assert r["is_valid"] is True  # average 0.525 is below 0.6


async def test_mean_with_tripwire_down_leaves_the_llm_alone():
    r = await _scan(_llm(0.70), httpx.ConnectError("refused"), combine="mean")
    assert r["is_valid"] is False and r["details"]["arbiter_combine"] == "mean_llm_only"


async def test_mean_with_the_llm_down_leaves_tripwire_alone():
    blocked = await _scan(TimeoutError(), _trip(0.9), combine="mean")
    assert blocked["is_valid"] is False and blocked["details"]["arbiter_combine"] == "mean_tripwire_only"
    allowed = await _scan(TimeoutError(), _trip(0.1), combine="mean")
    assert allowed["is_valid"] is True and "error" not in allowed["details"]


async def test_a_partial_tripwire_read_never_clears_the_input():
    # with the LLM: the LLM, which read everything, decides
    r = await _scan(_llm(0.70), _trip(0.01, windows_skipped=3), combine="mean")
    assert r["is_valid"] is False and r["details"]["arbiter_combine"] == "mean_tripwire_partial"
    # alone: a partial "safe" fails open uncached instead of being served as a real verdict
    alone = await _scan(TimeoutError(), _trip(0.01, windows_skipped=3), combine="mean")
    assert alone["is_valid"] is True and "error" in alone["details"]


@pytest.mark.parametrize(
    "scanner,side,source",
    [("Toxicity", "prompt", ""), ("PromptInjection", "output", ""), ("PromptInjection", "prompt", "ENDPOINT")],
)
async def test_tripwire_only_judges_promptinjection_input_outside_atlas(scanner, side, source):
    r = await _scan(_llm(0.02), _trip(0.99), combine="mean", scanner=scanner, side=side, contextSource=source)
    assert "tripwire" not in FakeScanner.calls and "tripwire" not in r["details"]
    assert r["is_valid"] is True  # the LLM decided alone


@pytest.mark.parametrize("source", ["", "AGENTIC"])
async def test_tripwire_runs_for_argus_promptinjection_input(source):
    await _scan(_llm(0.02), _trip(0.01), combine="mean", contextSource=source)
    assert "tripwire" in FakeScanner.calls


async def test_tripwire_is_not_wrapped_with_the_llm_backup(monkeypatch):
    monkeypatch.setattr(
        model_map,
        "build_provider_from_config",
        lambda e: TripwireProvider("http://t") if e["provider"] == "tripwire" else FakeProvider(e["provider"]),
    )
    config = _config("mean")
    config["modelConfigs"].append({"provider": "anthropic", "modelRole": "FINAL_ARBITER_BACKUP"})
    FakeScanner.script = {"qwen": QWEN_UNSAFE, "gemma": _llm(0.02), "tripwire": _trip(0.01)}
    await model_map.ModelMapScanner("PromptInjection", "prompt", "text", config).run()
    by_name = {s.provider.name: s.provider for s in FakeScanner.built}
    assert isinstance(by_name["tripwire"], TripwireProvider)
    assert isinstance(by_name["gemma"], FallbackProvider)  # the LLM arbiter still gets its backup
