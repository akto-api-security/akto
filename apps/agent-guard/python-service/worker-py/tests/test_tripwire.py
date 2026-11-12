"""Tripwire classifier as an observer: provider, scanner path, gating, and never changing the verdict (no network)."""

import asyncio
import json
import logging
import time

import httpx
import pytest

import constants
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


def test_null_fields_in_a_detect_reply():
    # A null is_attack falls back to the score, like a missing one, instead of reading as "not an attack".
    assert parse_tripwire_result({"attack_score": 0.98, "is_attack": None})["is_valid"] is False
    assert parse_tripwire_result({"attack_score": 0.02, "is_attack": None})["is_valid"] is True
    flagged = parse_tripwire_result({"attack_score": 0.9, "is_attack": True, "verdict": None})
    assert flagged["is_valid"] is False and flagged["details"]["reason"].startswith(
        "Tripwire flagged this as an attack"
    )


async def test_detect_posts_the_raw_text_on_its_own_connection_pool(monkeypatch):
    sent = {}

    def handler(request):
        sent.update(url=str(request.url), json=json.loads(request.content))
        return httpx.Response(200, json={"verdict": "benign", "is_attack": False, "attack_score": 0.01})

    shared = []
    monkeypatch.setattr(providers.http_client, "get_client", lambda: shared.append(1))
    tripwire = TripwireProvider("http://tripwire:8000/")
    tripwire._http = httpx.AsyncClient(transport=httpx.MockTransport(handler))
    reply = await tripwire.detect("hello")
    assert sent == {"url": "http://tripwire:8000/v1/detect", "json": {"text": "hello"}}
    assert reply["attack_score"] == 0.01
    assert shared == []  # never the pool Qwen and Gemma share
    assert TripwireProvider("http://t")._client()._transport._pool._max_connections == TripwireProvider._MAX_CONNECTIONS


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
    for scanner, side in [("Toxicity", "prompt"), ("PromptInjection", "output")]:
        with pytest.raises(ValueError):
            await LLMScanner(TripwireProvider("http://t")).scan(scanner, side, "x", {})


def test_one_rule_decides_what_tripwire_observes():
    assert constants.observes("PromptInjection", "prompt")
    assert not constants.observes("PromptInjection", "output")
    assert not constants.observes("Toxicity", "prompt")


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


@pytest.mark.parametrize("llm_risk", [0.02, 0.70])
async def test_a_failed_tripwire_changes_nothing(llm_risk):
    r = await _scan(_llm(llm_risk), httpx.ConnectError("refused"))
    assert r["is_valid"] is (llm_risk < 0.5) and r["risk_score"] == llm_risk and "error" not in r["details"]
    assert r["details"]["tripwire"] == {"completed": False}


def _slow(seconds, result):
    async def answer():
        await asyncio.sleep(seconds)
        return result

    return answer


async def _scan_and_store(trip, tripwire=None):
    """A scan Qwen allows at once; returns the verdict, how long it took, and the (live) list of store calls."""
    stored: list = []
    FakeScanner.script = {"qwen": QWEN_SAFE, "gemma": _llm(0.02), "tripwire": trip}
    scanner = model_map.ModelMapScanner(
        "PromptInjection",
        "prompt",
        "text",
        _config(tripwire),
        store_fn=lambda completed, name: stored.append([r["details"]["llm_provider"] for r in completed]),
    )
    started = time.perf_counter()
    r = await scanner.run()
    return r, time.perf_counter() - started, stored


async def test_a_slow_tripwire_never_delays_the_scan():
    r, took, stored = await _scan_and_store(_slow(0.3, _trip(0.99)), {"timeoutMs": 2000})
    assert took < 0.15 and r["execution_time_ms"] < 150
    assert r["is_valid"] is True and r["details"]["tripwire"] == {"completed": False}
    assert stored == []  # held until Tripwire answers, so the scan's results are stored together
    await asyncio.sleep(0.5)
    assert stored == [["qwen", "tripwire"]]


async def test_a_tripwire_slower_than_its_timeout_is_cut_off():
    r, took, stored = await _scan_and_store(_slow(5, _trip(0.99)), {"timeoutMs": 50})
    assert took < 0.15 and r["details"]["tripwire"] == {"completed": False}
    await asyncio.sleep(0.2)
    assert stored == [["qwen"]]


async def test_the_default_observer_timeout_is_500ms():
    _, _, stored = await _scan_and_store(_slow(5, _trip(0.99)))
    await asyncio.sleep(0.35)
    assert stored == []
    await asyncio.sleep(0.5)
    assert stored == [["qwen"]]


async def test_tripwire_runs_alongside_the_cascade_not_after_it():
    # Tripwire takes 0.2s and Qwen 0.4s: alongside, the scan takes ~0.4s and Tripwire's verdict is in it.
    started = time.perf_counter()
    r = await _scan(_llm(0.02), _slow(0.2, _trip(0.99)), qwen=_slow(0.4, QWEN_SAFE), tripwire={"timeoutMs": 2000})
    assert time.perf_counter() - started < 0.65
    assert r["details"]["tripwire"]["completed"] is True


@pytest.mark.parametrize("ending", ["error", "cancelled"])
async def test_a_scan_that_fails_or_is_cancelled_cancels_its_observers(monkeypatch, ending):
    cancelled = []

    async def tripwire():
        try:
            await asyncio.sleep(1)
        except asyncio.CancelledError:
            cancelled.append(True)
            raise
        return _trip(0.9)

    if ending == "error":
        monkeypatch.setattr(model_map.ModelMapScanner, "_shape_result", lambda self, winner: 1 / 0)
        with pytest.raises(ZeroDivisionError):
            await _scan(_llm(0.02), tripwire, qwen=QWEN_SAFE, tripwire={"timeoutMs": 2000})
    else:  # e.g. the client disconnected
        with pytest.raises(TimeoutError):
            scan = _scan(_llm(0.02), tripwire, qwen=_slow(1, QWEN_SAFE), tripwire={"timeoutMs": 2000})
            await asyncio.wait_for(scan, 0.05)
    await asyncio.sleep(0.01)
    assert cancelled == [True]


async def test_a_failed_open_verdict_is_not_logged_as_a_disagreement(caplog):
    with caplog.at_level(logging.INFO, logger="model_map"):
        r = await _scan(RuntimeError("arbiter down"), _trip(0.99))  # every arbiter failed: fail open
    assert r["is_valid"] is True and r["details"]["error"] == "all arbiters failed"
    assert "disagreement" not in caplog.text
    with caplog.at_level(logging.INFO, logger="model_map"):
        await _scan(_llm(0.02), _trip(0.99))  # a real SAFE verdict Tripwire disagrees with
    assert "observer disagreement: cascade=SAFE tripwire=0.99" in caplog.text


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
    monkeypatch.setattr(settings, "TRIPWIRE_BASE_URL", "http://tripwire:8000")
    monkeypatch.setattr(settings, "DEFAULT_MODEL_CONFIG_JSON", json.dumps(DEPLOYMENT))
    r = await _scan_with_policy_models()
    assert FakeScanner.calls.count("tripwire") == 1
    assert r["is_valid"] is True and r["details"]["tripwire"]["risk_score"] == 0.99  # recorded, verdict unchanged


async def test_the_deployment_tripwire_is_not_added_to_output_scans(monkeypatch):
    monkeypatch.setattr(settings, "TRIPWIRE_BASE_URL", "http://tripwire:8000")
    monkeypatch.setattr(settings, "DEFAULT_MODEL_CONFIG_JSON", json.dumps(DEPLOYMENT))
    sent = []

    async def cascade(scanner, side, text, config, store_fn=None, schedule_fn=None):
        sent.append([m["provider"] for m in config["modelConfigs"]])
        return {"is_valid": True, "risk_score": 0.0, "details": {}, "execution_time_ms": 1}

    monkeypatch.setattr(scan_handler, "scan_with_model_map", cascade)
    for side in ("prompt", "output"):
        payload = {
            "scanner_name": "PromptInjection",
            "scanner_type": side,
            "text": "x",
            "config": {"modelConfigs": POLICY_MODELS},
        }
        await scan_handler.scan_payload(payload, schedule_fn=lambda coro: coro.close())
    assert sent == [["qwen", "gemma", "tripwire"], ["qwen", "gemma"]]


async def test_a_tripwire_entry_with_no_url_is_dropped_and_warned_about_once(monkeypatch, caplog):
    monkeypatch.setattr(settings, "TRIPWIRE_BASE_URL", "")
    monkeypatch.setattr(settings, "DEFAULT_MODEL_CONFIG_JSON", json.dumps(DEPLOYMENT))
    constants._warn_no_tripwire_url.cache_clear()
    with caplog.at_level(logging.WARNING):
        for _ in range(3):
            await _scan_with_policy_models()
    assert "tripwire" not in FakeScanner.calls
    assert caplog.text.count("TRIPWIRE_BASE_URL is unset") == 1


def test_the_deployment_tripwire_is_parsed_once(monkeypatch):
    monkeypatch.setattr(settings, "TRIPWIRE_BASE_URL", "http://tripwire:8000")
    parsed = []
    parse = constants._parse_deployment_config
    monkeypatch.setattr(constants, "_parse_deployment_config", lambda raw, *a: parsed.append(raw) or parse(raw, *a))
    constants._deployment_tripwire.cache_clear()
    raw = json.dumps(DEPLOYMENT)
    for _ in range(3):
        configs = constants.with_deployment_tripwire(POLICY_MODELS, raw)
        assert configs[-1] == {"provider": "tripwire", "timeoutMs": 500}
        configs[-1]["timeoutMs"] = 1  # each scan gets its own copy of the cached entry
    assert parsed == [raw]


def test_a_gemma_only_scan_never_runs_on_tripwire_alone():
    # BanCode/Password drop the Qwen tier; Tripwire can't judge them, so it doesn't count as what's left.
    qwen = {"provider": "qwen", "modelRole": "FAST_THREAT_FILTER"}
    gemma = {"provider": "gemma", "modelRole": "FINAL_ARBITER"}
    tripwire = {"provider": "tripwire"}
    assert constants.strip_qwen_tier([qwen, gemma, tripwire]) == [gemma]
    assert constants.strip_qwen_tier([qwen, tripwire]) == [qwen, tripwire]  # nothing else: keep Qwen judging


async def test_no_deployment_tripwire_means_none_is_added(monkeypatch):
    monkeypatch.setattr(settings, "DEFAULT_MODEL_CONFIG_JSON", "")
    await _scan_with_policy_models()
    assert "tripwire" not in FakeScanner.calls


async def test_password_never_runs_on_an_old_style_tripwire_entry(monkeypatch):
    # Password's single model is picked by role; a Tripwire entry listed first must not be it.
    deployment = {"modelConfigs": [{"provider": "tripwire", "modelRole": "FINAL_ARBITER"}, *POLICY_MODELS]}
    monkeypatch.setattr(settings, "DEFAULT_MODEL_CONFIG_JSON", json.dumps(deployment))
    FakeScanner.script = {"gemma": _llm(0.95)}
    payload = {"scanner_name": "Password", "scanner_type": "prompt", "text": "pw=hunter2", "config": {}}
    r = await scan_handler.scan_payload(payload, schedule_fn=lambda coro: coro.close())
    assert FakeScanner.calls == ["gemma"] and r["is_valid"] is False


async def test_a_tripwire_entry_with_the_backup_role_is_still_reported():
    r = await _scan(_llm(0.02), _trip(0.99), tripwire={"modelRole": "FINAL_ARBITER_BACKUP"})
    assert r["details"]["tripwire"]["risk_score"] == 0.99
