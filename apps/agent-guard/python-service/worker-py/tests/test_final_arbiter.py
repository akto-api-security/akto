"""FINAL_ARBITER / FINAL_ARBITER_BACKUP are modelConfigs entries; nothing about the provider or model is hardcoded.

/guardrails/llm and the cascade resolve both via model_map.build_arbiter from the model config
(DEFAULT_MODEL_CONFIG_JSON, else the built-in default). A FINAL_ARBITER entry with no FINAL_ARBITER_BACKUP entry
has no backup. Network mocked.
"""

import asyncio
import json

import pytest

import providers
from constants import get_default_config
from model_map import build_arbiter
from providers import (
    FallbackProvider,
    GemmaFastArbiterProvider,
    GemmaFoundryProvider,
    OpenAIProvider,
    build_provider_from_config,
)

_HOST = "https://arbiter-host:8000/v1"
_OTHER_HOST = "https://other-host:8000/v1"
_OK = {"choices": [{"message": {"content": "from primary"}}]}
_ANTHROPIC_OK = {"content": [{"text": "from anthropic"}]}


class _FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


class _Hang:
    def __init__(self, seconds: float = 3600.0):
        self.seconds = seconds


class _Slow:
    def __init__(self, seconds: float, payload):
        self.seconds = seconds
        self.payload = payload


class _FakeClient:
    posts = []
    responses = {}

    async def post(self, url, headers=None, json=None):
        _FakeClient.posts.append({"url": url, "headers": headers, "json": json})
        for substr, resp in _FakeClient.responses.items():
            if substr in url:
                if isinstance(resp, _Hang):
                    await asyncio.sleep(resp.seconds)
                    raise AssertionError("_Hang should have been cancelled by the caller's own timeout first")
                if isinstance(resp, _Slow):
                    await asyncio.sleep(resp.seconds)
                    return _FakeResponse(resp.payload)
                if isinstance(resp, Exception):
                    raise resp
                return _FakeResponse(resp)
        raise AssertionError(f"unscripted url: {url}")


def build_final_arbiter(model_override=""):
    cfg = get_default_config(providers.settings.DEFAULT_MODEL_CONFIG_JSON)
    return build_arbiter(cfg["modelConfigs"], model_override)


def _anthropic_posts():
    return [post for post in _FakeClient.posts if "api.anthropic.com" in post["url"]]


def _cfg(*entries):
    return json.dumps({"modelConfigs": list(entries)})


def _arbiter(**kw):
    return {"provider": "gemma_fast_arbiter", "modelRole": "FINAL_ARBITER", "baseUrl": _HOST, **kw}


def _backup(**kw):
    return {"provider": "anthropic", "modelRole": "FINAL_ARBITER_BACKUP", **kw}


@pytest.fixture(autouse=True)
def arbiter_env(monkeypatch):
    _FakeClient.posts = []
    _FakeClient.responses = {}
    providers._PROVIDER_CACHE.clear()
    monkeypatch.setattr(providers.http_client, "get_client", lambda: _FakeClient())
    monkeypatch.setattr(providers.settings, "DEFAULT_MODEL_CONFIG_JSON", "")
    monkeypatch.setattr(providers.settings, "GEMMA_VLLM_ARBITER_BASE_URL", _HOST)
    monkeypatch.setattr(providers.settings, "GEMMA_FOUNDRY_BASE_URL", "https://foundry-host/openai/v1")
    monkeypatch.setattr(providers.settings, "GEMMA_FOUNDRY_API_KEY", "foundry-key")
    monkeypatch.setattr(providers.settings, "ANTHROPIC_API_KEY", "key-123")
    yield
    providers._PROVIDER_CACHE.clear()


def _set_cfg(monkeypatch, *entries):
    monkeypatch.setattr(providers.settings, "DEFAULT_MODEL_CONFIG_JSON", _cfg(*entries))


# ── primary: the FINAL_ARBITER entry of the model config, whatever provider/model it names ──


def test_env_unset_uses_the_built_in_default_config_arbiter_not_a_hardcoded_provider():
    p = build_final_arbiter()
    assert isinstance(p, GemmaFoundryProvider)
    assert not isinstance(p, GemmaFastArbiterProvider)
    assert not isinstance(p, FallbackProvider)


def test_final_arbiter_entry_of_the_env_json_is_the_primary(monkeypatch):
    _set_cfg(monkeypatch, _arbiter(model="cfg-model", baseUrl=_OTHER_HOST))
    p = build_final_arbiter()
    assert isinstance(p, GemmaFastArbiterProvider)
    assert p.model == "cfg-model"
    assert p.base_url == _OTHER_HOST


def test_primary_can_be_any_provider(monkeypatch):
    _set_cfg(
        monkeypatch,
        {"provider": "openai_compatible", "modelRole": "FINAL_ARBITER", "model": "m1", "baseUrl": _OTHER_HOST},
    )
    p = build_final_arbiter()
    assert isinstance(p, OpenAIProvider)
    assert not isinstance(p, GemmaFastArbiterProvider)
    assert p.model == "m1"
    assert p.base_url == _OTHER_HOST


def test_request_model_overrides_the_entry_model(monkeypatch):
    _set_cfg(monkeypatch, _arbiter(model="cfg-model"))
    assert build_final_arbiter("req-model").model == "req-model"
    assert build_final_arbiter().model == "cfg-model"


def test_model_config_without_a_final_arbiter_entry_gives_no_arbiter(monkeypatch):
    _set_cfg(monkeypatch, {"provider": "gemma_foundry", "modelRole": "FAST_THREAT_FILTER"})
    assert build_final_arbiter() is None


def test_backup_entry_alone_is_not_an_arbiter(monkeypatch):
    _set_cfg(monkeypatch, _backup())
    assert build_final_arbiter() is None


def test_primary_whose_provider_is_unconfigured_gives_none(monkeypatch):
    _set_cfg(monkeypatch, _arbiter(baseUrl=""))
    monkeypatch.setattr(providers.settings, "GEMMA_VLLM_ARBITER_BASE_URL", "")
    assert build_final_arbiter() is None


@pytest.mark.parametrize("raw", ["not json", "[1, 2]", "   ", '{"modelConfigs": []}'])
def test_unusable_env_json_falls_back_to_the_built_in_default_config(monkeypatch, raw):
    monkeypatch.setattr(providers.settings, "DEFAULT_MODEL_CONFIG_JSON", raw)
    assert isinstance(build_final_arbiter(), GemmaFoundryProvider)


# ── backup: FINAL_ARBITER_BACKUP entry; absent means NO backup ──


async def test_final_arbiter_without_backup_entry_has_no_fallback_even_with_anthropic_key_set(monkeypatch):
    _set_cfg(monkeypatch, _arbiter(model="cfg-model"))
    _FakeClient.responses = {_HOST: TimeoutError("down"), "api.anthropic.com": _ANTHROPIC_OK}
    with pytest.raises(TimeoutError):
        await build_final_arbiter().complete("hi")
    assert _anthropic_posts() == []


async def test_backup_entry_is_used_when_the_primary_fails(monkeypatch):
    _set_cfg(monkeypatch, _arbiter(model="cfg-model"), _backup(model="claude-haiku-4-5"))
    _FakeClient.responses = {_HOST: TimeoutError("down"), "api.anthropic.com": _ANTHROPIC_OK}
    p = build_final_arbiter()
    assert isinstance(p, FallbackProvider)
    assert isinstance(p.primary, GemmaFastArbiterProvider)
    assert p.name == "gemma_fast_arbiter"
    assert await p.complete("hi") == "from anthropic"
    assert _anthropic_posts()[0]["json"]["model"] == "claude-haiku-4-5"


async def test_backup_not_called_when_the_primary_succeeds(monkeypatch):
    _set_cfg(monkeypatch, _arbiter(), _backup())
    _FakeClient.responses = {_HOST: _OK, "api.anthropic.com": _ANTHROPIC_OK}
    assert await build_final_arbiter().complete("hi") == "from primary"
    assert _anthropic_posts() == []


async def test_slow_primary_is_waited_for_and_the_backup_is_not_used(monkeypatch):
    monkeypatch.setattr(providers, "_FAST_LEG_TIMEOUT_S", 0.05)
    _set_cfg(monkeypatch, _arbiter(), _backup())
    _FakeClient.responses = {_HOST: _Slow(0.2, _OK), "api.anthropic.com": _ANTHROPIC_OK}
    assert await build_final_arbiter().complete("hi") == "from primary"
    assert _anthropic_posts() == []


async def test_backup_can_be_any_provider_with_its_own_base_url(monkeypatch):
    _set_cfg(
        monkeypatch,
        {"provider": "openai_compatible", "modelRole": "FINAL_ARBITER", "model": "m1", "baseUrl": _HOST},
        {"provider": "openai_compatible", "modelRole": "FINAL_ARBITER_BACKUP", "model": "m2", "baseUrl": _OTHER_HOST},
    )
    _FakeClient.responses = {
        _HOST: ConnectionError("down"),
        _OTHER_HOST: {"choices": [{"message": {"content": "from backup host"}}]},
    }
    p = build_final_arbiter()
    assert await p.complete("hi") == "from backup host"
    assert _FakeClient.posts[-1]["json"]["model"] == "m2"


async def test_unusable_backup_reraises_the_original_primary_error(monkeypatch):
    monkeypatch.setattr(providers.settings, "ANTHROPIC_API_KEY", "")
    _set_cfg(monkeypatch, _arbiter(), _backup())
    _FakeClient.responses = {_HOST: TimeoutError("primary down")}
    with pytest.raises(TimeoutError, match="primary down"):
        await build_final_arbiter().complete("hi")


# ── the cascade's own FINAL_ARBITER entries (request / policy / DEFAULT_MODEL_CONFIG_JSON) ──


async def test_gemma_fast_arbiter_entry_alone_never_has_a_backup(monkeypatch):
    p = build_provider_from_config(_arbiter(model="cfg-model"))
    assert isinstance(p, GemmaFastArbiterProvider)
    assert not isinstance(p, FallbackProvider)
    _FakeClient.responses = {_HOST: TimeoutError("down"), "api.anthropic.com": _ANTHROPIC_OK}
    with pytest.raises(TimeoutError):
        await p.complete("hi")
    assert _anthropic_posts() == []


async def test_fast_leg_bound_is_not_imposed_on_a_non_fast_primary(monkeypatch):
    monkeypatch.setattr(providers, "_FAST_LEG_TIMEOUT_S", 0.05)
    _set_cfg(
        monkeypatch,
        {"provider": "openai_compatible", "modelRole": "FINAL_ARBITER", "model": "m1", "baseUrl": _HOST},
        _backup(),
    )
    _FakeClient.responses = {_HOST: _Slow(0.2, _OK), "api.anthropic.com": _ANTHROPIC_OK}
    assert await build_final_arbiter().complete("hi") == "from primary"
    assert _anthropic_posts() == []
