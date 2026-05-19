"""LLM provider implementations for the scanner pipeline.

Each provider exposes:
    - .name        : str identifier used for prompt routing & telemetry
    - .complete()  : str -> str  (sends the prompt, returns raw model text)
"""

import base64
import json
import logging
import os
from abc import ABC, abstractmethod
from typing import Any, Dict, Optional

import google.auth.transport.requests
import httpx
from google.oauth2 import service_account

logger = logging.getLogger(__name__)

DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
DEFAULT_ANTHROPIC_MODEL = "claude-haiku-4-5-20251001"
DEFAULT_VERTEX_AI_MODEL = "publishers/qwen/models/qwen3guard"

_HTTP_TIMEOUT_SECONDS = 120.0
_VERTEX_SCOPES = ["https://www.googleapis.com/auth/cloud-platform"]


class LLMProvider(ABC):
    """Abstract LLM provider. Subclasses must set `name` and implement `complete`."""

    name: str = ""

    def __init__(self) -> None:
        self._client = httpx.Client(timeout=_HTTP_TIMEOUT_SECONDS)

    @abstractmethod
    def complete(self, prompt: str) -> str:
        ...


class OpenAIProvider(LLMProvider):
    """OpenAI-compatible provider.

    Works with OpenAI, Ollama, LM Studio, vLLM, or anything that speaks the
    /v1/chat/completions API.

    base_url: override to point at a local server, e.g. "http://localhost:11434/v1"
              for Ollama. Defaults to the OpenAI production endpoint.
    api_key:  pass an empty string for servers that don't require auth (Ollama).
    """

    def __init__(self, api_key: str, model: str, base_url: str = ""):
        super().__init__()
        self.api_key = api_key
        self.model = model or DEFAULT_OPENAI_MODEL
        self.base_url = (base_url or "https://api.openai.com/v1").rstrip("/")
        self.name = "openai" if "openai.com" in self.base_url else "openai_compatible"
        logger.info(
            f"[LLMScanner/OpenAI] Initialized provider model={self.model} base_url={self.base_url}"
        )

    def complete(self, prompt: str) -> str:
        headers = {"Content-Type": "application/json"}
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        resp = self._client.post(
            f"{self.base_url}/chat/completions",
            headers=headers,
            json={
                "model": self.model,
                "temperature": 0.1,
                "max_tokens": 256,
                "messages": [{"role": "user", "content": prompt}],
            },
        )
        resp.raise_for_status()
        return resp.json()["choices"][0]["message"]["content"]


class AnthropicProvider(LLMProvider):
    name = "anthropic"

    def __init__(self, api_key: str, model: str):
        super().__init__()
        self.api_key = api_key
        self.model = model or DEFAULT_ANTHROPIC_MODEL
        logger.info(f"[LLMScanner/Anthropic] Initialized provider model={self.model}")

    def complete(self, prompt: str) -> str:
        resp = self._client.post(
            "https://api.anthropic.com/v1/messages",
            headers={
                "Content-Type": "application/json",
                "x-api-key": self.api_key,
                "anthropic-version": "2023-06-01",
            },
            json={
                "model": self.model,
                "max_tokens": 256,
                "messages": [{"role": "user", "content": prompt}],
            },
        )
        resp.raise_for_status()
        return resp.json()["content"][0]["text"]


class VertexAIProvider(LLMProvider):
    """Vertex AI predict endpoint over the chatCompletions request format.

    Subclassed by GemmaVertexProvider to swap the provider name (which drives
    prompt routing) and to add an optional dedicated-DNS override.
    """

    name = "vertexai"
    _log_tag = "[LLMScanner/VertexAI]"

    def __init__(
        self,
        sa_key_json_b64: str,
        project: str,
        location: str,
        endpoint_id: str,
        dedicated_dns: str = "",
    ):
        super().__init__()
        self.project = project
        self.location = location
        self.endpoint_id = endpoint_id
        self.dedicated_dns = dedicated_dns.strip()

        sa_key_dict = json.loads(base64.b64decode(sa_key_json_b64).decode("utf-8"))
        self.credentials = service_account.Credentials.from_service_account_info(
            sa_key_dict,
            scopes=_VERTEX_SCOPES,
        )

        logger.info(
            f"{self._log_tag} Initialized provider project={self.project} "
            f"location={self.location} endpoint={self.endpoint_id} "
            f"dedicated_dns={'set' if self.dedicated_dns else 'unset'}"
        )

    def _predict_url(self) -> str:
        host = self.dedicated_dns or f"{self.location}-aiplatform.googleapis.com"
        return (
            f"https://{host}/v1/projects/{self.project}"
            f"/locations/{self.location}/endpoints/{self.endpoint_id}:predict"
        )

    def complete(self, prompt: str) -> str:
        self.credentials.refresh(google.auth.transport.requests.Request())
        resp = self._client.post(
            self._predict_url(),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.credentials.token}",
            },
            json={
                "instances": [
                    {
                        "@requestFormat": "chatCompletions",
                        "messages": [{"role": "user", "content": prompt}],
                        "max_tokens": 512,
                        "temperature": 0.1,
                    }
                ]
            },
        )
        resp.raise_for_status()
        return resp.json()["predictions"]["choices"][0]["message"]["content"]


class GemmaVertexProvider(VertexAIProvider):
    name = "gemma_vertexai"
    _log_tag = "[LLMScanner/GemmaVertex]"


def _require_env(values: dict, label: str) -> Optional[dict]:
    """Return the dict if every value is truthy, else log + return None."""
    missing = [k for k, v in values.items() if not v]
    if missing:
        logger.warning(f"{label}: missing required env vars {missing}; skipping")
        return None
    return values


# ── Construction helpers ─────────────────────────────────────────────────────


def _build_openai_compatible(model: str, base_url: str) -> Optional[LLMProvider]:
    api_key = os.getenv("OPENAI_API_KEY", "")
    # Ollama and other local OpenAI-compatible servers don't require a key,
    # but we still need *some* way to reach them.
    if not api_key and not base_url:
        logger.warning("[Providers] OPENAI_API_KEY not set and no baseUrl; skipping openai")
        return None
    return OpenAIProvider(api_key, model or DEFAULT_OPENAI_MODEL, base_url=base_url)


def _build_anthropic(model: str) -> Optional[LLMProvider]:
    api_key = os.getenv("ANTHROPIC_API_KEY", "")
    if not api_key:
        logger.warning("[Providers] ANTHROPIC_API_KEY not set; skipping anthropic")
        return None
    return AnthropicProvider(api_key, model or DEFAULT_ANTHROPIC_MODEL)


def _build_vertexai() -> Optional[LLMProvider]:
    env = _require_env(
        {
            "VERTEX_AI_SA_KEY_JSON": os.getenv("VERTEX_AI_SA_KEY_JSON", ""),
            "VERTEX_AI_PROJECT": os.getenv("VERTEX_AI_PROJECT", ""),
            "VERTEX_AI_LOCATION": os.getenv("VERTEX_AI_LOCATION", ""),
            "VERTEX_AI_ENDPOINT_ID": os.getenv("VERTEX_AI_ENDPOINT_ID", ""),
        },
        label="[Providers] vertexai",
    )
    if env is None:
        return None
    return VertexAIProvider(
        env["VERTEX_AI_SA_KEY_JSON"],
        env["VERTEX_AI_PROJECT"],
        env["VERTEX_AI_LOCATION"],
        env["VERTEX_AI_ENDPOINT_ID"],
    )


def _build_gemma_vertexai() -> Optional[LLMProvider]:
    env = _require_env(
        {
            "GEMMA_VERTEX_SA_KEY_JSON": os.getenv("GEMMA_VERTEX_SA_KEY_JSON", ""),
            "GEMMA_VERTEX_PROJECT": os.getenv("GEMMA_VERTEX_PROJECT", ""),
            "GEMMA_VERTEX_LOCATION": os.getenv("GEMMA_VERTEX_LOCATION", ""),
            "GEMMA_VERTEX_ENDPOINT_ID": os.getenv("GEMMA_VERTEX_ENDPOINT_ID", ""),
        },
        label="[Providers] gemma_vertexai",
    )
    if env is None:
        return None
    return GemmaVertexProvider(
        env["GEMMA_VERTEX_SA_KEY_JSON"],
        env["GEMMA_VERTEX_PROJECT"],
        env["GEMMA_VERTEX_LOCATION"],
        env["GEMMA_VERTEX_ENDPOINT_ID"],
        dedicated_dns=os.getenv("GEMMA_VERTEX_DEDICATED_DNS", ""),
    )


def build_provider_from_env(provider_name: str, model: str = "") -> Optional[LLMProvider]:
    """Build a provider using *only* env vars. Used by the SCANNER_LLM_PROVIDER path."""
    provider_name = provider_name.strip().lower()
    if provider_name == "openai":
        return _build_openai_compatible(model or os.getenv("OPENAI_MODEL", ""), base_url="")
    if provider_name == "anthropic":
        return _build_anthropic(model or os.getenv("ANTHROPIC_MODEL", ""))
    if provider_name == "vertexai":
        return _build_vertexai()
    if provider_name == "gemma_vertexai":
        return _build_gemma_vertexai()
    logger.warning(f'[Providers] Unknown provider "{provider_name}"; skipping')
    return None


def build_provider_from_config(entry: Dict[str, Any]) -> Optional[LLMProvider]:
    """Build a provider from a modelMap entry. Reads name/model/baseUrl from the
    entry; credentials still come from env vars."""
    provider_name = (entry.get("provider") or "").strip().lower()
    model = (entry.get("model") or "").strip()

    if provider_name in ("openai", "ollama", "openai_compatible"):
        # Entry-level baseUrl wins, then OPENAI_COMPATIBLE_BASE_URL, then default.
        base_url = (entry.get("baseUrl") or "").strip() or os.getenv("OPENAI_COMPATIBLE_BASE_URL", "")
        return _build_openai_compatible(model, base_url)
    if provider_name == "anthropic":
        return _build_anthropic(model)
    if provider_name == "vertexai":
        return _build_vertexai()
    if provider_name == "gemma_vertexai":
        return _build_gemma_vertexai()

    logger.warning(f"[ModelMap] Unknown provider '{provider_name}'; skipping entry")
    return None
