"""qwen3guard_fast / gemma_fast / gemma_fast_arbiter — each falls back to its
original provider on failure, network mocked.

No new schema, no new env var: these are plain modelConfigs entries selected
by "provider" like any other, reading "model"/"baseUrl" the same way
"openai_compatible" already does.
"""

import pytest

import providers
from providers import GemmaFastProvider, Qwen3GuardOutput, build_provider_from_config

_HOST = "https://fast-host:8000/v1"


class _FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


class _FakeClient:
    """Stand-in for the shared http_client.get_client() AsyncClient.

    Routes by url substring so a test can script the fast leg and the
    original-provider leg differently (or make one raise) in the same call.
    """

    posts = []
    responses = {}  # url substring -> payload dict, or an Exception to raise

    async def post(self, url, headers=None, json=None):
        _FakeClient.posts.append({"url": url, "headers": headers, "json": json})
        for substr, resp in _FakeClient.responses.items():
            if substr in url:
                if isinstance(resp, Exception):
                    raise resp
                return _FakeResponse(resp)
        raise AssertionError(f"unscripted url: {url}")


@pytest.fixture(autouse=True)
def patch_http_and_cache(monkeypatch):
    _FakeClient.posts = []
    _FakeClient.responses = {}
    providers._PROVIDER_CACHE.clear()
    monkeypatch.setattr(providers.http_client, "get_client", lambda: _FakeClient())
    yield
    providers._PROVIDER_CACHE.clear()


# ── build_provider_from_config selects these like any other provider name ──


def test_fast_provider_reads_model_and_base_url_from_the_entry():
    p = build_provider_from_config({"provider": "gemma_fast", "model": "gemma-fast", "baseUrl": _HOST})
    assert isinstance(p, GemmaFastProvider)
    assert p.model == "gemma-fast"
    assert p.base_url == _HOST


def test_fast_provider_without_base_url_is_unconfigured():
    p = build_provider_from_config({"provider": "gemma_fast", "model": "gemma-fast"})
    assert p is None


# ── gemma_fast falls back to gemma_foundry ──────────────────────────────────


async def test_gemma_fast_uses_its_own_endpoint_when_it_succeeds():
    _FakeClient.responses = {_HOST: {"choices": [{"message": {"content": "from fast"}}]}}
    p = build_provider_from_config({"provider": "gemma_fast", "model": "gemma-fast", "baseUrl": _HOST})
    assert await p.complete("hi") == "from fast"


async def test_gemma_fast_falls_back_to_gemma_foundry_on_failure(monkeypatch):
    monkeypatch.setattr(providers.settings, "GEMMA_FOUNDRY_BASE_URL", "https://foundry-host/v1")
    monkeypatch.setattr(providers.settings, "GEMMA_FOUNDRY_API_KEY", "key-123")
    _FakeClient.responses = {
        _HOST: ConnectionError("fast host unreachable"),
        "foundry-host": {"choices": [{"message": {"content": "from foundry"}}]},
    }
    p = build_provider_from_config({"provider": "gemma_fast", "model": "gemma-fast", "baseUrl": _HOST})
    out = await p.complete("hi")
    assert out == "from foundry"
    assert any(_HOST in c["url"] for c in _FakeClient.posts)
    assert any("foundry-host" in c["url"] for c in _FakeClient.posts)


# ── gemma_fast_arbiter falls back to anthropic (the real FINAL_ARBITER) ─────


async def test_gemma_fast_arbiter_falls_back_to_anthropic_on_failure(monkeypatch):
    monkeypatch.setattr(providers.settings, "ANTHROPIC_API_KEY", "key-123")
    _FakeClient.responses = {
        _HOST: TimeoutError("fast host timed out"),
        "api.anthropic.com": {"content": [{"text": "from anthropic"}]},
    }
    p = build_provider_from_config({"provider": "gemma_fast_arbiter", "model": "gemma-fast-arbiter", "baseUrl": _HOST})
    out = await p.complete("hi")
    assert out == "from anthropic"


# ── qwen3guard_fast falls back to qwen3guard, preserving the logprobs interface ─


def test_qwen3guard_fast_is_a_qwen3guard_output():
    p = build_provider_from_config({"provider": "qwen3guard_fast", "model": "qwen3-fast", "baseUrl": _HOST})
    assert isinstance(p, Qwen3GuardOutput)


async def test_qwen3guard_fast_falls_back_to_qwen3guard_on_failure(monkeypatch):
    monkeypatch.setattr(providers.settings, "QWEN3GUARD_SA_KEY_JSON", "")  # left unset -> qwen3guard build fails
    p = build_provider_from_config({"provider": "qwen3guard_fast", "model": "qwen3-fast", "baseUrl": _HOST})
    _FakeClient.responses = {_HOST: ConnectionError("fast host unreachable")}
    with pytest.raises(ConnectionError):
        await p.complete_with_logprobs("bad text")


async def test_qwen3guard_fast_uses_its_own_endpoint_when_it_succeeds():
    _FakeClient.responses = {
        _HOST: {"choices": [{"message": {"content": "Safety: safe"}, "logprobs": {"content": []}}]}
    }
    p = build_provider_from_config({"provider": "qwen3guard_fast", "model": "qwen3-fast", "baseUrl": _HOST})
    content, _lp = await p.complete_with_logprobs("fine text")
    assert content == "Safety: safe"
