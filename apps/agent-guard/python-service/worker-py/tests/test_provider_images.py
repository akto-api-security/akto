"""complete_with_image: which providers take an image (for /ocr's "arbiter" entry) and what they send. No network."""

import json

import httpx
import pytest

import providers
from aws_auth import AwsCredentials
from providers import (
    AnthropicProvider,
    AzureFoundryProvider,
    BedrockMantleProvider,
    BedrockProvider,
    FallbackProvider,
    GemmaFastArbiterProvider,
    GemmaFoundryProvider,
    OpenAIProvider,
    Qwen3GuardFastProvider,
    Qwen3GuardFoundryProvider,
    reads_images,
)

B64 = "iVBORw0KGgo="
IMAGE_TURN = [
    {
        "role": "user",
        "content": [
            {"type": "text", "text": "read it"},
            {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{B64}"}},
        ],
    }
]


def _reply(text):
    return {"choices": [{"message": {"content": text}}]}


class _Fake:
    """Stands in for the shared httpx client: answers by host, records every call."""

    def __init__(self, **by_host):
        self.by_host = by_host
        self.calls = []

    async def post(self, url, headers=None, json=None, content=None):
        self.calls.append({"url": url, "headers": headers, "json": json, "content": content})
        status, body = self.by_host[httpx.URL(url).host]
        return httpx.Response(status, json=body, request=httpx.Request("POST", url))


@pytest.fixture
def fake(monkeypatch):
    def install(**by_host):
        client = _Fake(
            **{host: (reply if isinstance(reply, tuple) else (200, reply)) for host, reply in by_host.items()}
        )
        monkeypatch.setattr(providers.http_client, "get_client", lambda: client)
        return client

    return install


def _sent(call):
    return call["json"] if call["json"] is not None else json.loads(call["content"])


async def test_openai_compatible_sends_the_image_as_a_data_url(fake):
    client = fake(**{"vllm.example": _reply("INVOICE 4721")})
    provider = OpenAIProvider("key", "gemma-4-26b-a4b-it", base_url="http://vllm.example/v1")
    assert await provider.complete_with_image("read it", B64, "image/png", 4096) == "INVOICE 4721"
    [call] = client.calls
    assert call["url"] == "http://vllm.example/v1/chat/completions"
    assert call["headers"]["Authorization"] == "Bearer key"
    assert _sent(call) == {
        "model": "gemma-4-26b-a4b-it",
        "temperature": 0.0,
        "max_tokens": 4096,
        "messages": IMAGE_TURN,
    }


async def test_text_completion_is_unchanged(fake):
    client = fake(**{"vllm.example": _reply("ok")})
    await OpenAIProvider("", "m", base_url="http://vllm.example/v1").complete("hi")
    assert _sent(client.calls[0])["messages"] == [{"role": "user", "content": "hi"}]


async def test_foundry_sends_the_image_with_its_headers(fake):
    client = fake(**{"ep.example": _reply("text")})
    provider = GemmaFoundryProvider("https://ep.example", "foundry-key", deployment="gemma-26b")
    assert await provider.complete_with_image("read it", B64, "image/png", 4096) == "text"
    [call] = client.calls
    assert call["headers"]["azureml-model-deployment"] == "gemma-26b"
    assert _sent(call) == {"messages": IMAGE_TURN, "max_tokens": 4096, "temperature": 0.0}


async def test_bedrock_mantle_sends_the_image_on_the_signed_route(fake):
    client = fake(**{"bedrock-mantle.us-east-1.api.aws": _reply("text")})
    provider = BedrockMantleProvider("google.gemma-4-26b-a4b", "us-east-1", credentials=AwsCredentials("AKID", "s"))
    assert await provider.complete_with_image("read it", B64, "image/png", 4096) == "text"
    [call] = client.calls
    assert call["url"] == "https://bedrock-mantle.us-east-1.api.aws/openai/v1/chat/completions"
    assert call["headers"]["Authorization"].startswith("AWS4-HMAC-SHA256 ")  # SigV4 over the exact bytes sent
    assert _sent(call)["messages"] == IMAGE_TURN and _sent(call)["max_tokens"] == 4096


@pytest.mark.parametrize(
    "provider, expected",
    [
        (OpenAIProvider("", "m", base_url="http://x/v1"), True),
        (GemmaFastArbiterProvider("", "m", base_url="http://x/v1"), True),
        (AzureFoundryProvider("https://x", "k"), True),
        (BedrockMantleProvider("google.gemma-4-26b-a4b", "us-east-1", api_key="k"), True),
        # Classifiers and APIs without an image path here:
        (Qwen3GuardFastProvider("", "m", base_url="http://x/v1"), False),
        (Qwen3GuardFoundryProvider("https://x", "k"), False),
        (BedrockProvider("anthropic.claude", "us-east-1", api_key="k"), False),
        (AnthropicProvider("k", "claude"), False),
    ],
    ids=lambda v: type(v).__name__ if not isinstance(v, bool) else str(v),
)
def test_which_providers_read_images(provider, expected):
    assert reads_images(provider) is expected


async def test_a_provider_without_images_refuses_rather_than_dropping_the_image():
    with pytest.raises(NotImplementedError):
        await Qwen3GuardFastProvider("", "m", base_url="http://x/v1").complete_with_image("p", B64, "image/png", 10)


async def test_the_arbiter_backup_also_gets_the_image(fake, monkeypatch):
    monkeypatch.setattr(providers.settings, "OPENAI_API_KEY", "")
    providers._PROVIDER_CACHE.clear()
    client = fake(**{"primary.example": (500, {"error": "down"}), "backup.example": _reply("from backup")})
    primary = OpenAIProvider("", "m", base_url="http://primary.example/v1")
    backup_entry = {"provider": "openai_compatible", "model": "m", "baseUrl": "http://backup.example/v1"}
    fallback = FallbackProvider(primary, backup_entry, primary_timeout_s=5)
    assert reads_images(fallback)
    assert await fallback.complete_with_image("read it", B64, "image/png", 4096) == "from backup"
    assert [httpx.URL(c["url"]).host for c in client.calls] == ["primary.example", "backup.example"]
    assert _sent(client.calls[1])["messages"] == IMAGE_TURN
