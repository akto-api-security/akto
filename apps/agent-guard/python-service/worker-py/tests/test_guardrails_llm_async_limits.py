"""relaxed_limits: the async /guardrails/llm route lifts the reply ceiling (256 -> 4096 tokens).

Outside relaxed_limits every provider behaves exactly as before, so the blocking route is unaffected. Network mocked.
"""

import asyncio

import pytest

import providers
from providers import AnthropicProvider, FallbackProvider, GemmaFastArbiterProvider, OpenAIProvider, relaxed_limits

_HOST = "https://arbiter-host:8000/v1"
_OK = {"choices": [{"message": {"content": "from primary"}}]}
_ANTHROPIC_OK = {"content": [{"text": "from anthropic"}]}


class _FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


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
                if isinstance(resp, _Slow):
                    await asyncio.sleep(resp.seconds)
                    return _FakeResponse(resp.payload)
                if isinstance(resp, Exception):
                    raise resp
                return _FakeResponse(resp)
        raise AssertionError(f"unscripted url: {url}")


def _sent_max_tokens(url_part):
    return [post["json"]["max_tokens"] for post in _FakeClient.posts if url_part in post["url"]]


@pytest.fixture(autouse=True)
def fake_http(monkeypatch):
    _FakeClient.posts = []
    _FakeClient.responses = {}
    providers._PROVIDER_CACHE.clear()
    monkeypatch.setattr(providers.http_client, "get_client", lambda: _FakeClient())
    monkeypatch.setattr(providers.settings, "ANTHROPIC_API_KEY", "key-123")
    yield
    providers._PROVIDER_CACHE.clear()


async def test_defaults_are_unchanged_outside_relaxed_mode():
    _FakeClient.responses = {_HOST: _OK, "api.anthropic.com": _ANTHROPIC_OK}
    await OpenAIProvider("k", "m", base_url=_HOST).complete("hi")
    await AnthropicProvider("k", "claude-haiku-4-5").complete("hi")
    assert _sent_max_tokens(_HOST) == [256]
    assert _sent_max_tokens("api.anthropic.com") == [256]


async def test_relaxed_mode_lifts_the_reply_ceiling():
    _FakeClient.responses = {_HOST: _OK, "api.anthropic.com": _ANTHROPIC_OK}
    with relaxed_limits():
        await OpenAIProvider("k", "m", base_url=_HOST).complete("hi")
        await AnthropicProvider("k", "claude-haiku-4-5").complete("hi")
    assert _sent_max_tokens(_HOST) == [4096]
    assert _sent_max_tokens("api.anthropic.com") == [4096]


async def test_relaxed_mode_is_scoped_to_its_with_block():
    _FakeClient.responses = {_HOST: _OK}
    p = OpenAIProvider("k", "m", base_url=_HOST)
    with relaxed_limits():
        await p.complete("hi")
    await p.complete("hi")
    with relaxed_limits(False):
        await p.complete("hi")
    assert _sent_max_tokens(_HOST) == [4096, 256, 256]


async def test_relaxed_mode_does_not_leak_between_concurrent_requests():
    _FakeClient.responses = {_HOST: _Slow(0.05, _OK)}
    p = OpenAIProvider("k", "m", base_url=_HOST)

    async def relaxed_call():
        with relaxed_limits():
            await p.complete("hi")

    await asyncio.gather(relaxed_call(), p.complete("hi"), relaxed_call(), p.complete("hi"))
    assert sorted(_sent_max_tokens(_HOST)) == [256, 256, 4096, 4096]


async def test_gemma_fast_arbiter_has_no_time_bound_in_either_mode(monkeypatch):
    monkeypatch.setattr(providers, "_FAST_LEG_TIMEOUT_S", 0.05)
    _FakeClient.responses = {_HOST: _Slow(0.2, _OK)}
    p = GemmaFastArbiterProvider("k", "m", base_url=_HOST)

    assert await p.complete("hi") == "from primary"
    with relaxed_limits():
        assert await p.complete("hi") == "from primary"


async def test_relaxed_mode_reaches_both_the_primary_and_the_backup_of_a_fallback():
    _FakeClient.responses = {_HOST: ConnectionError("down"), "api.anthropic.com": _ANTHROPIC_OK}
    backup_entry = {"provider": "anthropic", "model": "claude-haiku-4-5"}
    fb = FallbackProvider(OpenAIProvider("k", "m", base_url=_HOST), backup_entry)
    with relaxed_limits():
        assert await fb.complete("hi") == "from anthropic"
    assert _sent_max_tokens(_HOST) == [4096]
    assert _sent_max_tokens("api.anthropic.com") == [4096]


def test_async_route_is_registered_next_to_the_blocking_route():
    agent_app = pytest.importorskip("app")
    paths = {getattr(route, "path", None) for route in agent_app.app.routes}
    assert "/guardrails/llm" in paths
    assert "/guardrails/llm/async" in paths
