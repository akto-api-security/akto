"""qwen3guard_fast / gemma_fast / gemma_fast_arbiter — each falls back to its
original provider on failure, network mocked.

No new schema, no new env var: these are plain modelConfigs entries selected
by "provider" like any other, reading "model"/"baseUrl" the same way
"openai_compatible" already does.
"""

import pytest

import providers
from providers import GemmaFastProvider, Qwen3GuardFastProvider, Qwen3GuardOutput, build_provider_from_config

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


def test_fast_provider_name_is_not_overwritten_by_openai_init():
    # OpenAIProvider.__init__ would otherwise clobber .name to "openai"/"openai_compatible" —
    # ban_topics.py's provider_name.startswith("gemma") prompt-template choice, and the
    # llm_provider field in scan results, both depend on the real name surviving construction.
    gemma = build_provider_from_config({"provider": "gemma_fast", "model": "m", "baseUrl": _HOST})
    arbiter = build_provider_from_config({"provider": "gemma_fast_arbiter", "model": "m", "baseUrl": _HOST})
    qwen = build_provider_from_config({"provider": "qwen3guard_fast", "model": "m", "baseUrl": _HOST})
    assert gemma.name == "gemma_fast"
    assert arbiter.name == "gemma_fast_arbiter"
    assert qwen.name == "qwen3guard_fast"


def test_gemma_fast_providers_get_the_gemma_tuned_ban_topics_prompt():
    # End-to-end proof of the name fix: ban_topics.py selects its benchmarked
    # Gemma-tuned template only when provider_name.startswith("gemma").
    from prompts import ban_topics

    config = {"topics": ["violence"]}
    vertex_prompt = ban_topics.build(config, "gemma_vertexai", "some text")
    other_prompt = ban_topics.build(config, "azure_foundry", "some text")
    assert ban_topics.build(config, "gemma_fast", "some text") == vertex_prompt
    assert ban_topics.build(config, "gemma_fast_arbiter", "some text") == vertex_prompt
    assert other_prompt != vertex_prompt


def test_fast_provider_falls_back_to_its_own_env_var_when_entry_omits_base_url(monkeypatch):
    monkeypatch.setattr(providers.settings, "GEMMA_VLLM_BASE_URL", _HOST)
    p = build_provider_from_config({"provider": "gemma_fast", "model": "gemma-fast"})
    assert isinstance(p, GemmaFastProvider)
    assert p.base_url == _HOST


def test_fast_provider_entry_base_url_overrides_its_env_var(monkeypatch):
    monkeypatch.setattr(providers.settings, "GEMMA_VLLM_BASE_URL", "https://env-default-host/v1")
    p = build_provider_from_config({"provider": "gemma_fast", "model": "gemma-fast", "baseUrl": _HOST})
    assert isinstance(p, GemmaFastProvider)
    assert p.base_url == _HOST


def test_each_fast_provider_reads_its_own_distinct_env_var(monkeypatch):
    # qwen3guard_fast and gemma_fast_arbiter must NOT fall back to GEMMA_VLLM_BASE_URL.
    monkeypatch.setattr(providers.settings, "GEMMA_VLLM_BASE_URL", "https://gemma-fast-host/v1")
    assert build_provider_from_config({"provider": "qwen3guard_fast", "model": "m"}) is None
    assert build_provider_from_config({"provider": "gemma_fast_arbiter", "model": "m"}) is None


@pytest.mark.parametrize(
    ("provider_name", "setting_name"),
    [
        ("qwen3guard_fast", "QWEN_VLLM_KEY"),
        ("gemma_fast", "GEMMA_VLLM_KEY"),
        ("gemma_fast_arbiter", "GEMMA_26B_VLLM_KEY"),
    ],
)
async def test_each_fast_provider_sends_its_own_api_key_as_bearer_auth(monkeypatch, provider_name, setting_name):
    monkeypatch.setattr(providers.settings, setting_name, f"{setting_name}-value")
    _FakeClient.responses = {_HOST: {"choices": [{"message": {"content": "ok"}}]}}
    p = build_provider_from_config({"provider": provider_name, "model": "m", "baseUrl": _HOST})
    await p.complete("hi")
    assert _FakeClient.posts[0]["headers"]["Authorization"] == f"Bearer {setting_name}-value"


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
# (the real model only ever answers Safety:/Categories:, so this can't use ABCD)


def test_qwen3guard_fast_is_a_qwen3guard_output():
    p = build_provider_from_config({"provider": "qwen3guard_fast", "model": "qwen3-fast", "baseUrl": _HOST})
    assert isinstance(p, Qwen3GuardFastProvider)
    assert isinstance(p, Qwen3GuardOutput)


async def test_qwen3guard_fast_uses_its_own_endpoint_when_it_succeeds():
    _FakeClient.responses = {
        _HOST: {"choices": [{"message": {"content": "Safety: safe"}, "logprobs": {"content": []}}]}
    }
    p = build_provider_from_config({"provider": "qwen3guard_fast", "model": "qwen3-fast", "baseUrl": _HOST})
    content, _lp = await p.complete_with_logprobs("fine text")
    assert content == "Safety: safe"


async def test_qwen3guard_fast_falls_back_to_qwen3guard_on_failure(monkeypatch):
    monkeypatch.setattr(providers.settings, "QWEN3GUARD_SA_KEY_JSON", "")  # left unset -> qwen3guard build fails too
    p = build_provider_from_config({"provider": "qwen3guard_fast", "model": "qwen3-fast", "baseUrl": _HOST})
    _FakeClient.responses = {_HOST: ConnectionError("fast host unreachable")}
    with pytest.raises(ConnectionError):
        await p.complete_with_logprobs("bad text")
