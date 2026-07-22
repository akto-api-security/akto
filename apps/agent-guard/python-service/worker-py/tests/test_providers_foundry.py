"""Azure Foundry providers — request shaping + response parsing, network mocked.

Foundry managed-compute endpoints are vLLM behind an OpenAI-compatible
/v1/chat/completions route: key as Bearer, deployment pinned via the
azureml-model-deployment header, logprobs in the standard OpenAI shape.
"""

import pytest

import providers
from llm_scanner import LLMScanner
from prompts import ban_topics
from providers import (
    AnthropicFoundryProvider,
    AzureFoundryProvider,
    GemmaFoundryProvider,
    Qwen3GuardFoundryProvider,
    Qwen3GuardOutput,
    Qwen3GuardProvider,
    _normalize_anthropic_foundry_base_url,
    _normalize_foundry_base_url,
    build_provider_from_config,
)

_HOST = "https://guard-ep.eastus2.inference.ml.azure.com"


class _FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


class _FakeClient:
    """Stand-in for the shared http_client.get_client() AsyncClient."""

    posts = []
    payload = {}

    async def post(self, url, headers=None, json=None):
        _FakeClient.posts.append({"url": url, "headers": headers, "json": json})
        return _FakeResponse(_FakeClient.payload)


@pytest.fixture(autouse=True)
def patch_http_and_cache(monkeypatch):
    _FakeClient.posts = []
    _FakeClient.payload = {"choices": [{"message": {"content": "ok"}}]}
    providers._PROVIDER_CACHE.clear()
    monkeypatch.setattr(providers.http_client, "get_client", lambda: _FakeClient())
    yield
    providers._PROVIDER_CACHE.clear()


# ── base URL normalization ────────────────────────────────────────────────────


def test_base_url_appends_v1():
    assert _normalize_foundry_base_url(_HOST) == f"{_HOST}/v1"


def test_base_url_strips_portal_score_route():
    # The portal displays ".../score"; the OpenAI route lives at ".../v1".
    assert _normalize_foundry_base_url(f"{_HOST}/score") == f"{_HOST}/v1"


def test_base_url_keeps_existing_v1_and_trailing_slash():
    assert _normalize_foundry_base_url(f"{_HOST}/v1/") == f"{_HOST}/v1"
    # serverless Foundry Models route already carries /openai/v1
    assert (
        _normalize_foundry_base_url("https://res.services.ai.azure.com/openai/v1")
        == "https://res.services.ai.azure.com/openai/v1"
    )


# ── request shaping ───────────────────────────────────────────────────────────


async def test_complete_posts_openai_payload_with_foundry_headers():
    p = AzureFoundryProvider(_HOST, "key-123", deployment="dep-1", model="Qwen/Qwen3Guard-Gen-8B")
    out = await p.complete("hello")
    assert out == "ok"

    post = _FakeClient.posts[0]
    assert post["url"] == f"{_HOST}/v1/chat/completions"
    assert post["headers"]["Authorization"] == "Bearer key-123"
    assert post["headers"]["api-key"] == "key-123"
    assert post["headers"]["azureml-model-deployment"] == "dep-1"
    assert post["json"] == {
        "model": "Qwen/Qwen3Guard-Gen-8B",
        "messages": [{"role": "user", "content": "hello"}],
        "max_tokens": 512,
        "temperature": 0.1,
    }


async def test_optional_deployment_and_model_are_omitted():
    p = AzureFoundryProvider(_HOST, "key-123")
    await p.complete("hello")
    post = _FakeClient.posts[0]
    assert "azureml-model-deployment" not in post["headers"]
    assert "model" not in post["json"]


async def test_qwen3guard_foundry_sends_and_parses_logprobs():
    _FakeClient.payload = {
        "choices": [
            {
                "message": {"content": "Safety: unsafe\nCategories: Violent"},
                "logprobs": {"content": [{"token": "Safety", "logprob": -0.01, "top_logprobs": []}]},
            }
        ]
    }
    p = Qwen3GuardFoundryProvider(_HOST, "key-123", deployment="dep-1")
    content, lp = await p.complete_with_logprobs("bad text", top_logprobs=5)
    assert content == "Safety: unsafe\nCategories: Violent"
    assert lp == [{"token": "Safety", "logprob": -0.01, "top_logprobs": []}]

    body = _FakeClient.posts[0]["json"]
    assert body["logprobs"] is True
    assert body["top_logprobs"] == 5
    assert body["max_tokens"] == 64
    assert body["temperature"] == 0.0


async def test_qwen3guard_foundry_complete_omits_logprobs():
    _FakeClient.payload = {"choices": [{"message": {"content": "Safety: safe"}}]}
    p = Qwen3GuardFoundryProvider(_HOST, "key-123")
    assert await p.complete("fine text") == "Safety: safe"
    body = _FakeClient.posts[0]["json"]
    assert "logprobs" not in body
    assert "top_logprobs" not in body


# ── scanner dispatch (Qwen3GuardOutput mixin replaces the concrete isinstance) ─


def test_guard_output_mixin_membership():
    assert not isinstance(AzureFoundryProvider(_HOST, "k"), Qwen3GuardOutput)
    assert not isinstance(GemmaFoundryProvider(_HOST, "k"), Qwen3GuardOutput)
    assert isinstance(Qwen3GuardFoundryProvider(_HOST, "k"), Qwen3GuardOutput)
    assert issubclass(Qwen3GuardProvider, Qwen3GuardOutput)


async def test_llm_scanner_routes_foundry_qwen_through_guard_parser():
    _FakeClient.payload = {"choices": [{"message": {"content": "Safety: unsafe\nCategories: Violent"}}]}
    scanner = LLMScanner(Qwen3GuardFoundryProvider(_HOST, "key-123"))
    r = await scanner.scan("Toxicity", "prompt", "bad text", {})
    assert r["is_valid"] is False
    assert r["risk_score"] == 1.0
    assert r["details"]["llm_provider"] == "qwen3guard_foundry"
    assert r["details"]["categories"] == "Violent"


def test_ban_topics_gemma_prompt_covers_all_gemma_backends():
    # BanTopics ships a Gemma-tuned template (benchmarked F1 0.981); every Gemma
    # backend must get it, not just the Vertex one.
    config = {"topics": ["violence"]}
    vertex_prompt = ban_topics.build(config, "gemma_vertexai", "some text")
    foundry_prompt = ban_topics.build(config, "gemma_foundry", "some text")
    other_prompt = ban_topics.build(config, "azure_foundry", "some text")
    assert foundry_prompt == vertex_prompt
    assert other_prompt != vertex_prompt


# ── builder / config resolution ───────────────────────────────────────────────


def test_build_from_config_uses_settings(monkeypatch):
    monkeypatch.setattr(providers.settings, "QWEN3GUARD_FOUNDRY_BASE_URL", f"{_HOST}/score")
    monkeypatch.setattr(providers.settings, "QWEN3GUARD_FOUNDRY_API_KEY", "key-123")
    monkeypatch.setattr(providers.settings, "QWEN3GUARD_FOUNDRY_DEPLOYMENT", "dep-1")
    p = build_provider_from_config({"provider": "qwen3guard_foundry"})
    assert isinstance(p, Qwen3GuardFoundryProvider)
    assert p.base_url == f"{_HOST}/v1"
    assert p.deployment == "dep-1"


def test_build_from_config_entry_baseurl_overrides_settings(monkeypatch):
    monkeypatch.setattr(providers.settings, "GEMMA_FOUNDRY_BASE_URL", "https://env-host/v1")
    monkeypatch.setattr(providers.settings, "GEMMA_FOUNDRY_API_KEY", "key-123")
    p = build_provider_from_config({"provider": "gemma_foundry", "baseUrl": f"{_HOST}/v1", "model": "gemma-3-27b"})
    assert isinstance(p, GemmaFoundryProvider)
    assert p.base_url == f"{_HOST}/v1"
    assert p.model == "gemma-3-27b"


def test_build_from_config_missing_required_vars_skips(monkeypatch):
    monkeypatch.setattr(providers.settings, "AZURE_FOUNDRY_BASE_URL", f"{_HOST}/v1")
    monkeypatch.setattr(providers.settings, "AZURE_FOUNDRY_API_KEY", "")
    assert build_provider_from_config({"provider": "azure_foundry"}) is None


# ── anthropic_foundry: native Anthropic Messages route on Foundry ─────────────
#
# Unlike the azure_*/gemma_*/qwen_* Foundry providers (OpenAI /chat/completions),
# Claude on Foundry speaks the Anthropic Messages API at {base}/v1/messages.

_ANTHROPIC_HOST = "https://res.services.ai.azure.com/anthropic"


def test_anthropic_foundry_base_url_reduces_to_anthropic_base():
    # Every shape the portal/docs show collapses to the ".../anthropic" base;
    # the Messages API is then reached at "{base}/v1/messages".
    assert _normalize_anthropic_foundry_base_url(_ANTHROPIC_HOST) == _ANTHROPIC_HOST
    assert _normalize_anthropic_foundry_base_url(f"{_ANTHROPIC_HOST}/") == _ANTHROPIC_HOST
    assert _normalize_anthropic_foundry_base_url(f"{_ANTHROPIC_HOST}/v1") == _ANTHROPIC_HOST
    assert _normalize_anthropic_foundry_base_url(f"{_ANTHROPIC_HOST}/v1/messages") == _ANTHROPIC_HOST


async def test_anthropic_foundry_posts_messages_payload_with_azure_auth():
    _FakeClient.payload = {"content": [{"text": "blocked"}]}
    p = AnthropicFoundryProvider(f"{_ANTHROPIC_HOST}/v1", "key-123", deployment="claude-haiku-4-5")
    out = await p.complete("hello")
    assert out == "blocked"

    post = _FakeClient.posts[0]
    assert post["url"] == f"{_ANTHROPIC_HOST}/v1/messages"
    # Azure auth: api-key + Bearer, plus the Anthropic version header.
    assert post["headers"]["api-key"] == "key-123"
    assert post["headers"]["Authorization"] == "Bearer key-123"
    assert post["headers"]["anthropic-version"] == "2023-06-01"
    assert "x-api-key" not in post["headers"]
    assert post["headers"]["azureml-model-deployment"] == "claude-haiku-4-5"
    # Anthropic Messages body — deployment name used as the model when unset.
    assert post["json"] == {
        "model": "claude-haiku-4-5",
        "max_tokens": 256,
        "messages": [{"role": "user", "content": "hello"}],
    }


async def test_anthropic_foundry_model_overrides_deployment():
    _FakeClient.payload = {"content": [{"text": "ok"}]}
    p = AnthropicFoundryProvider(_ANTHROPIC_HOST, "k", deployment="dep-1", model="claude-sonnet-4-5")
    await p.complete("hi")
    assert _FakeClient.posts[0]["json"]["model"] == "claude-sonnet-4-5"


async def test_anthropic_foundry_omits_deployment_header_when_unset():
    _FakeClient.payload = {"content": [{"text": "ok"}]}
    p = AnthropicFoundryProvider(_ANTHROPIC_HOST, "k", model="claude-haiku-4-5")
    await p.complete("hi")
    assert "azureml-model-deployment" not in _FakeClient.posts[0]["headers"]


def test_build_anthropic_foundry_from_config(monkeypatch):
    monkeypatch.setattr(providers.settings, "ANTHROPIC_FOUNDRY_BASE_URL", f"{_ANTHROPIC_HOST}/v1/messages")
    monkeypatch.setattr(providers.settings, "ANTHROPIC_FOUNDRY_API_KEY", "key-123")
    monkeypatch.setattr(providers.settings, "ANTHROPIC_FOUNDRY_DEPLOYMENT", "claude-haiku-4-5")
    monkeypatch.setattr(providers.settings, "ANTHROPIC_FOUNDRY_MODEL", "")
    p = build_provider_from_config({"provider": "anthropic_foundry"})
    assert isinstance(p, AnthropicFoundryProvider)
    assert p.base_url == _ANTHROPIC_HOST
    assert p.deployment == "claude-haiku-4-5"
    assert p.model == "claude-haiku-4-5"


def test_build_anthropic_foundry_entry_baseurl_overrides_settings(monkeypatch):
    monkeypatch.setattr(providers.settings, "ANTHROPIC_FOUNDRY_BASE_URL", "https://env-host/anthropic")
    monkeypatch.setattr(providers.settings, "ANTHROPIC_FOUNDRY_API_KEY", "key-123")
    p = build_provider_from_config(
        {"provider": "anthropic_foundry", "baseUrl": _ANTHROPIC_HOST, "model": "claude-haiku-4-5"}
    )
    assert isinstance(p, AnthropicFoundryProvider)
    assert p.base_url == _ANTHROPIC_HOST
    assert p.model == "claude-haiku-4-5"


def test_two_gemma_foundry_entries_can_target_different_deployments(monkeypatch):
    # Same shared endpoint/key (one GEMMA_FOUNDRY_BASE_URL/API_KEY), but two
    # modelConfigs entries for two different roles (e.g. FAST_FALLBACK_SAFE_FILTER
    # on a small Gemma deployment, FINAL_ARBITER on a bigger one) — per-entry
    # "deployment" must resolve to two DISTINCT provider instances, not collide
    # in the process-wide cache or silently share one deployment.
    monkeypatch.setattr(providers.settings, "GEMMA_FOUNDRY_BASE_URL", f"{_HOST}/v1")
    monkeypatch.setattr(providers.settings, "GEMMA_FOUNDRY_API_KEY", "shared-key")
    small = build_provider_from_config({"provider": "gemma_foundry", "deployment": "gemma-4-e2b-it-dep"})
    big = build_provider_from_config({"provider": "gemma_foundry", "deployment": "gemma-4-32b-it-dep"})
    assert small is not big
    assert small.deployment == "gemma-4-e2b-it-dep"
    assert big.deployment == "gemma-4-32b-it-dep"
    assert small.base_url == big.base_url == f"{_HOST}/v1"
    # same call again with the same deployment must hit the cache, not rebuild
    small_again = build_provider_from_config({"provider": "gemma_foundry", "deployment": "gemma-4-e2b-it-dep"})
    assert small_again is small
