"""LLM provider implementations (async port for the Worker runtime).

Differences from the container version:
  - httpx.AsyncClient + async complete()
  - every request sets Accept-Encoding: identity (Pyodide double-gunzip fix)
  - Vertex auth uses gcp_auth.get_token() instead of google-auth credentials
  - Bedrock IAM auth uses aws_auth.sign_headers() instead of botocore
"""

import asyncio
import json
import logging
import math
import time
from abc import ABC, abstractmethod
from collections.abc import Callable
from contextlib import contextmanager
from contextvars import ContextVar
from typing import Any, Optional
from urllib.parse import quote, urlparse

import httpx

import aws_auth
import gcp_auth
import http_client
from settings import settings

logger = logging.getLogger(__name__)

DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
_DEFAULT_MAX_TOKENS = 256
_ASYNC_MAX_TOKENS = 4096
_relaxed_limits: ContextVar[bool] = ContextVar("relaxed_limits", default=False)


@contextmanager
def relaxed_limits(enabled: bool = True):
    token = _relaxed_limits.set(enabled)
    try:
        yield
    finally:
        _relaxed_limits.reset(token)


def _max_tokens() -> int:
    return _ASYNC_MAX_TOKENS if _relaxed_limits.get() else _DEFAULT_MAX_TOKENS


DEFAULT_ANTHROPIC_MODEL = "claude-haiku-4-5-20251001"

_IDENTITY = {"Accept-Encoding": "identity"}

# Process-wide cache of built providers, keyed by (provider, model, base_url, deployment).
_PROVIDER_CACHE: dict[tuple[str, str, str, str], "LLMProvider"] = {}


def _redact_secret(secret: str, keep: int = 4) -> str:
    """First/last `keep` chars + length, middle redacted — enough to eyeball
    "is this even the right key" / "did whitespace sneak into the env var"
    without a debugger, never enough to reconstruct the secret. Deliberately
    does NOT strip before measuring length, so leading/trailing whitespace
    (a common .env copy-paste mistake) shows up as a mismatched length."""
    if not secret:
        return "<empty>"
    stripped = secret.strip()
    if len(stripped) <= keep * 2:
        redacted = "*" * len(stripped)
    else:
        redacted = f"{stripped[:keep]}...{stripped[-keep:]}"
    note = f" len={len(secret)}"
    if secret != stripped:
        note += " (has leading/trailing whitespace!)"
    return f"{redacted}{note}"


def _cached_provider(
    cache_key: tuple[str, str, str, str], builder: Callable[[], Optional["LLMProvider"]]
) -> Optional["LLMProvider"]:
    cached = _PROVIDER_CACHE.get(cache_key)
    if cached is not None:
        return cached
    built = builder()
    if built is not None:
        _PROVIDER_CACHE[cache_key] = built
    return built


async def _post_json_logged(
    client: httpx.AsyncClient,
    url: str,
    headers: dict,
    json_body: dict | None,
    log_tag: str,
    extra: str = "",
    *,
    content: bytes | None = None,
) -> dict:
    """POST + parse JSON, logging enough on failure to diagnose without a debugger.

    Every branch that can raise logs first: network-level failures (DNS,
    connection refused, timeout), HTTP error status (with the response body —
    this is where providers put the actual reason: auth failure, wrong
    deployment, quota exceeded), and non-JSON/malformed bodies. Callers still
    see the same exceptions as before (nothing swallowed), just with a log
    line alongside so a client-only failure doesn't require a repro to debug.

    Pass pre-serialized `content` instead of `json_body` when the request is
    signed over its body bytes (SigV4) — those exact bytes must be what's sent.
    """
    ctx = f" ({extra})" if extra else ""
    body_kwargs: dict[str, Any] = {"content": content} if content is not None else {"json": json_body}
    try:
        resp = await client.post(url, headers=headers, **body_kwargs)
    except httpx.RequestError as exc:
        logger.error(f"{log_tag} request to {url} failed{ctx}: {exc!r}")
        raise
    try:
        resp.raise_for_status()
    except httpx.HTTPStatusError:
        body = getattr(resp, "text", "")
        resp_headers = dict(getattr(resp, "headers", {}) or {})
        logger.error(
            f"{log_tag} HTTP {resp.status_code} from {url}{ctx}: {body[:1000]!r} response_headers={resp_headers}"
        )
        raise
    try:
        return resp.json()
    except ValueError as exc:
        body = getattr(resp, "text", "")
        logger.error(f"{log_tag} non-JSON response from {url}{ctx}: {body[:500]!r}")
        raise ValueError(f"{log_tag} non-JSON response from {url}: {body[:200]!r}") from exc


def _log_vllm_metrics(provider: str, model: str, base_url: str, body: Any, client_ms: float) -> None:
    metrics = body.get("metrics") if isinstance(body, dict) else None
    if isinstance(metrics, dict):
        fields = " ".join(f"{key}={value}" for key, value in metrics.items())
        ttft = metrics.get("time_to_first_token_ms")
        generation = metrics.get("generation_time_ms")
        if isinstance(ttft, (int, float)) and isinstance(generation, (int, float)):
            llm_ms = ttft + generation
            fields += f" llm_ms={llm_ms:.1f} network_ms={client_ms - llm_ms:.1f}"
    elif metrics is None:
        fields = "metrics=missing"
    else:
        fields = "metrics=invalid"
    req_id = body.get("id") if isinstance(body, dict) else None
    logger.info(
        f"[vllm-metrics] provider={provider} model={model} host={urlparse(base_url).netloc} "
        f"req_id={req_id} client_ms={client_ms:.1f} {fields}"
    )


class LLMProvider(ABC):
    name: str = ""

    @property
    def prompt_name(self) -> str:
        """Name the prompt builders key their model-specific templates on
        (e.g. the Gemma-tuned BanTopics prompt for any "gemma*" name)."""
        return self.name

    @abstractmethod
    async def complete(self, prompt: str) -> str: ...


class OpenAIProvider(LLMProvider):
    """OpenAI-compatible (OpenAI, Ollama, vLLM, LM Studio, …)."""

    def __init__(self, api_key: str, model: str, base_url: str = ""):
        self.api_key = api_key
        self.model = model or DEFAULT_OPENAI_MODEL
        self.base_url = (base_url or "https://api.openai.com/v1").rstrip("/")
        self.name = "openai" if "openai.com" in self.base_url else "openai_compatible"
        logger.info(f"[OpenAI] model={self.model} base_url={self.base_url} api_key={_redact_secret(self.api_key)}")

    include_metrics = False

    async def complete(self, prompt: str) -> str:
        headers = dict(_IDENTITY, **{"Content-Type": "application/json"})
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        payload = {
            "model": self.model,
            "temperature": 0.1,
            "max_tokens": _max_tokens(),
            "messages": [{"role": "user", "content": prompt}],
        }
        if self.include_metrics:
            payload["include_metrics"] = True
        client = http_client.get_client()
        started = time.perf_counter()
        resp = await client.post(f"{self.base_url}/chat/completions", headers=headers, json=payload)
        client_ms = (time.perf_counter() - started) * 1000
        resp.raise_for_status()
        body = resp.json()
        if self.include_metrics:
            _log_vllm_metrics(self.name, self.model, self.base_url, body, client_ms)
        return body["choices"][0]["message"]["content"]


class AnthropicProvider(LLMProvider):
    """Anthropic Messages API — direct api.anthropic.com by default.

    URL and auth headers are factored into overridable helpers so hosted
    variants (e.g. Azure Foundry's native Anthropic route) can reuse the
    identical request body and response parsing."""

    name = "anthropic"
    _log_tag = "[Anthropic]"
    _DEFAULT_BASE_URL = "https://api.anthropic.com"

    def __init__(self, api_key: str, model: str, base_url: str = ""):
        self.api_key = api_key
        self.model = model or DEFAULT_ANTHROPIC_MODEL
        self.base_url = (base_url or self._DEFAULT_BASE_URL).rstrip("/")
        logger.info(
            f"{self._log_tag} model={self.model} base_url={self.base_url} api_key={_redact_secret(self.api_key)}"
        )

    def _messages_url(self) -> str:
        return f"{self.base_url}/v1/messages"

    def _headers(self) -> dict[str, str]:
        return dict(
            _IDENTITY,
            **{
                "Content-Type": "application/json",
                "x-api-key": self.api_key,
                "anthropic-version": "2023-06-01",
            },
        )

    async def complete(self, prompt: str) -> str:
        # Deliberately not logging `prompt` here — this is the customer's
        # scanned text, not our own config; for the Password scanner it IS the
        # secret the scan exists to catch.
        client = http_client.get_client()
        body = await _post_json_logged(
            client,
            self._messages_url(),
            self._headers(),
            {
                "model": self.model,
                "max_tokens": _max_tokens(),
                "messages": [{"role": "user", "content": prompt}],
            },
            self._log_tag,
        )
        return body["content"][0]["text"]


def _normalize_anthropic_foundry_base_url(base_url: str) -> str:
    """Reduce an Azure Foundry Anthropic-route URL to its host+/anthropic base.

    The portal/docs show this route in several shapes (bare ".../anthropic",
    ".../anthropic/v1", the full ".../anthropic/v1/messages" path, or a
    managed-compute ".../score"); the Messages API always lives at
    "{base}/v1/messages", so peel those suffixes back to the base.
    """
    url = (base_url or "").strip().rstrip("/")
    for suffix in ("/score", "/messages", "/v1"):
        if url.endswith(suffix):
            url = url[: -len(suffix)].rstrip("/")
    return url


class AnthropicFoundryProvider(AnthropicProvider):
    """Anthropic (Claude) served through Azure AI Foundry's native Anthropic
    route (host *.services.ai.azure.com/anthropic).

    Same Messages API schema as direct Anthropic — only host, base path, and
    auth differ: Azure authenticates with the endpoint key as `api-key` (and
    Bearer), and the deployment is selected by the body `model` field (also
    mirrored to the azureml-model-deployment header for managed compute)."""

    name = "anthropic_foundry"
    _log_tag = "[AnthropicFoundry]"

    def __init__(self, base_url: str, api_key: str, deployment: str = "", model: str = ""):
        self.deployment = (deployment or "").strip()
        super().__init__(
            api_key=api_key,
            model=model or self.deployment,
            base_url=_normalize_anthropic_foundry_base_url(base_url),
        )

    def _headers(self) -> dict[str, str]:
        headers = dict(
            _IDENTITY,
            **{
                "Content-Type": "application/json",
                "api-key": self.api_key,
                "Authorization": f"Bearer {self.api_key}",
                "anthropic-version": "2023-06-01",
            },
        )
        if self.deployment:
            headers["azureml-model-deployment"] = self.deployment
        return headers


class VertexAIProvider(LLMProvider):
    """Vertex AI predict endpoint over the chatCompletions request format."""

    name = "vertexai"
    _log_tag = "[VertexAI]"

    def __init__(self, sa_key_json_b64: str, project: str, location: str, endpoint_id: str, dedicated_dns: str = ""):
        self.sa_info = gcp_auth.sa_info_from_b64(sa_key_json_b64)
        self.project = project
        self.location = location
        self.endpoint_id = endpoint_id
        self.dedicated_dns = (dedicated_dns or "").strip()
        logger.info(
            f"{self._log_tag} project={project} location={location} endpoint={endpoint_id} "
            f"sa_key={_redact_secret(sa_key_json_b64)}"
        )

    def _predict_url(self) -> str:
        host = self.dedicated_dns or f"{self.location}-aiplatform.googleapis.com"
        return (
            f"https://{host}/v1/projects/{self.project}/locations/{self.location}/endpoints/{self.endpoint_id}:predict"
        )

    async def _post(self, instance: dict[str, Any]) -> dict:
        token = await gcp_auth.get_token(self.sa_info)
        client = http_client.get_client()
        url = self._predict_url()
        headers = dict(
            _IDENTITY,
            **{
                "Content-Type": "application/json",
                "Authorization": f"Bearer {token}",
            },
        )
        extra = f"project={self.project} location={self.location} endpoint={self.endpoint_id}"
        return await _post_json_logged(client, url, headers, {"instances": [instance]}, self._log_tag, extra)

    async def complete(self, prompt: str) -> str:
        body = await self._post(
            {
                "@requestFormat": "chatCompletions",
                "messages": [{"role": "user", "content": prompt}],
                "max_tokens": 512,
                "temperature": 0.1,
            }
        )
        return body["predictions"]["choices"][0]["message"]["content"]


class GemmaVertexProvider(VertexAIProvider):
    name = "gemma_vertexai"
    _log_tag = "[GemmaVertex]"


def _qwen3guard_params(text: str, top_logprobs: int, temperature: float) -> dict[str, Any]:
    """OpenAI-style chatCompletions params shared by all Qwen3Guard backends."""
    params: dict[str, Any] = {
        "messages": [{"role": "user", "content": text}],
        "max_tokens": 64,
        "temperature": temperature,
    }
    if top_logprobs > 0:
        params["logprobs"] = True
        params["top_logprobs"] = top_logprobs
    return params


def _choice_content_and_logprobs(chat_completion: dict) -> tuple[str, list | None]:
    """Extract (content, logprobs.content) from an OpenAI-shaped chat completion.

    Raises a descriptive ValueError (with the actual body attached) instead of
    a bare KeyError/IndexError — a deployment that doesn't speak the expected
    OpenAI shape (wrong route, custom scoring format, empty choices on a
    content-filtered response) needs to be diagnosable straight from the log
    line, since this is untested against the real endpoints.
    """
    choices = chat_completion.get("choices") or []
    if not choices:
        raise ValueError(f"chat completion response has no 'choices': {chat_completion!r}"[:500])
    message = choices[0].get("message") or {}
    if "content" not in message:
        raise ValueError(f"chat completion choice has no message.content: {choices[0]!r}"[:500])
    return message["content"], (choices[0].get("logprobs") or {}).get("content")


class Qwen3GuardOutput:
    """Qwen3Guard guard classifier — emits Safety:/Categories: and exposes a
    probability distribution via first-token top_logprobs. Mixin shared by the
    Vertex and Azure Foundry backends; concrete classes supply
    complete_with_logprobs over their own transport. LLMScanner routes any
    provider carrying this mixin through parse_qwen3guard_result."""

    async def complete_with_logprobs(
        self, text: str, top_logprobs: int = 5, temperature: float = 0.0
    ) -> tuple[str, list | None]:
        raise NotImplementedError

    async def complete(self, prompt: str) -> str:
        content, _ = await self.complete_with_logprobs(prompt, top_logprobs=0)
        return content


class Qwen3GuardProvider(Qwen3GuardOutput, VertexAIProvider):
    name = "qwen3guard"
    _log_tag = "[Qwen3Guard]"

    async def complete_with_logprobs(
        self, text: str, top_logprobs: int = 5, temperature: float = 0.0
    ) -> tuple[str, list | None]:
        instance = {"@requestFormat": "chatCompletions", **_qwen3guard_params(text, top_logprobs, temperature)}
        body = await self._post(instance)
        return _choice_content_and_logprobs(body["predictions"])


def _normalize_foundry_base_url(base_url: str) -> str:
    """Accept the Foundry portal's endpoint URL in any of its shapes.

    The portal displays managed-compute endpoints as ".../score" (the default
    scoring route); the OpenAI-compatible API lives at ".../v1/chat/completions"
    on the same host, so strip "/score" and ensure a "/v1" suffix.
    """
    url = (base_url or "").strip().rstrip("/")
    if url.endswith("/score"):
        url = url[: -len("/score")]
    if not url.endswith("/v1"):
        url += "/v1"
    return url


class AzureFoundryProvider(LLMProvider):
    """Azure AI Foundry endpoint (managed compute / vLLM, OpenAI-compatible).

    Managed-compute endpoints authenticate with the endpoint key as a Bearer
    token and route to a specific deployment via the azureml-model-deployment
    header; the api-key header is also sent so the same provider works against
    serverless *.services.ai.azure.com routes."""

    name = "azure_foundry"
    _log_tag = "[AzureFoundry]"

    def __init__(self, base_url: str, api_key: str, deployment: str = "", model: str = ""):
        self.base_url = _normalize_foundry_base_url(base_url)
        self.api_key = api_key
        self.deployment = (deployment or "").strip()
        self.model = (model or "").strip()
        logger.info(
            f"{self._log_tag} base_url={self.base_url} deployment={self.deployment} model={self.model} "
            f"api_key={_redact_secret(self.api_key)}"
        )
        if self.deployment and not self.model:
            # Deployment selection is sent via the azureml-model-deployment header
            # (classic AML managed-online-endpoint convention). Nextgen Foundry's
            # unified endpoint (*.services.ai.azure.com) instead routes by the
            # request body's "model" field — if this Foundry resource hosts more
            # than one deployment behind one base_url, the header alone may not
            # land on the right one. Set *_FOUNDRY_MODEL to the deployment name
            # too if requests seem to hit the wrong model.
            logger.warning(
                f"{self._log_tag} deployment={self.deployment!r} set without model — if this endpoint hosts "
                f"multiple deployments, routing may not work via the azureml-model-deployment header alone; "
                f"set the matching *_FOUNDRY_MODEL to the deployment name as well."
            )

    async def _chat(self, params: dict[str, Any]) -> dict:
        headers = dict(
            _IDENTITY,
            **{
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.api_key}",
                "api-key": self.api_key,
            },
        )
        if self.deployment:
            headers["azureml-model-deployment"] = self.deployment
        body = {"model": self.model, **params} if self.model else params
        client = http_client.get_client()
        url = f"{self.base_url}/chat/completions"
        extra = f"deployment={self.deployment}" if self.deployment else ""
        return await _post_json_logged(client, url, headers, body, self._log_tag, extra)

    async def complete(self, prompt: str) -> str:
        body = await self._chat(
            {
                "messages": [{"role": "user", "content": prompt}],
                "max_tokens": 512,
                "temperature": 0.1,
            }
        )
        content, _ = _choice_content_and_logprobs(body)
        return content


class GemmaFoundryProvider(AzureFoundryProvider):
    name = "gemma_foundry"
    _log_tag = "[GemmaFoundry]"


class Qwen3GuardFoundryProvider(Qwen3GuardOutput, AzureFoundryProvider):
    name = "qwen3guard_foundry"
    _log_tag = "[Qwen3GuardFoundry]"

    async def complete_with_logprobs(
        self, text: str, top_logprobs: int = 5, temperature: float = 0.0
    ) -> tuple[str, list | None]:
        body = await self._chat(_qwen3guard_params(text, top_logprobs, temperature))
        return _choice_content_and_logprobs(body)


# Converse stopReasons meaning Bedrock itself withheld the answer — surfaced as
# errors so the cascade counts them as a provider failure, not a verdict.
_BEDROCK_BLOCKED_STOP_REASONS = {"guardrail_intervened", "content_filtered"}


def _converse_text(body: dict) -> str:
    """Extract the first text block from a Bedrock Converse response.

    Skips non-text blocks (e.g. reasoningContent from reasoning models, which
    precede the answer) and raises a descriptive ValueError with the body
    attached — same rationale as _choice_content_and_logprobs.
    """
    stop_reason = body.get("stopReason")
    if stop_reason in _BEDROCK_BLOCKED_STOP_REASONS:
        raise ValueError(f"Bedrock withheld the response (stopReason={stop_reason}): {body!r}"[:500])
    message = (body.get("output") or {}).get("message") or {}
    for block in message.get("content") or []:
        if "text" in block:
            return block["text"]
    raise ValueError(f"Converse response has no text content block: {body!r}"[:500])


class _BedrockAuthProvider(LLMProvider):
    """Shared AWS Bedrock auth + transport for the bedrock-runtime (Converse)
    and bedrock-mantle (OpenAI chat completions) providers.

    Auth, in precedence order: a Bedrock API key (sent as Bearer); static IAM
    credentials; else the pod's IAM role (EKS Pod Identity / IRSA), fetched
    and refreshed by aws_auth. Both IAM modes are SigV4-signed per request,
    under the endpoint's own signing service name."""

    _log_tag = "[Bedrock]"
    _signing_service = "bedrock"

    def __init__(
        self,
        model: str,
        region: str,
        api_key: str = "",
        credentials: aws_auth.AwsCredentials | None = None,
        base_url: str = "",
    ):
        if api_key:
            auth = f"api_key={_redact_secret(api_key)}"
        elif credentials is not None:
            auth = f"iam access_key_id={_redact_secret(credentials.access_key_id)}"
        elif source := aws_auth.role_credentials_source():
            auth = f"iam role via {source}"
        else:
            raise ValueError(
                f"{type(self).__name__} needs an api_key, IAM credentials or an IAM role in the environment"
            )
        self.model = model
        self.region = region
        self.api_key = api_key
        self.credentials = credentials
        self.base_url = (base_url or self._default_base_url(region)).rstrip("/")
        logger.info(f"{self._log_tag} model={self.model} region={self.region} base_url={self.base_url} {auth}")

    @staticmethod
    def _default_base_url(region: str) -> str:
        raise NotImplementedError

    @property
    def prompt_name(self) -> str:
        # One "bedrock" provider name serves every model, so Gemma-tuned
        # prompts are keyed on the model instead.
        return "gemma_bedrock" if "gemma" in self.model.lower() else self.name

    async def _headers(self, url: str, payload: bytes) -> dict[str, str]:
        headers = dict(_IDENTITY, **{"Content-Type": "application/json", "Accept": "application/json"})
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
            return headers
        creds = self.credentials or await aws_auth.get_role_credentials(self.region)
        headers.update(
            aws_auth.sign_headers("POST", url, headers, payload, creds, self.region, service=self._signing_service)
        )
        return headers

    async def _post(self, url: str, body: dict[str, Any]) -> dict:
        # Serialized once: SigV4 signs these exact bytes, so they must be what's sent.
        payload = json.dumps(body, separators=(",", ":")).encode()
        return await _post_json_logged(
            http_client.get_client(),
            url,
            await self._headers(url, payload),
            None,
            self._log_tag,
            f"model={self.model} region={self.region}",
            content=payload,
        )


class BedrockProvider(_BedrockAuthProvider):
    """Any AWS Bedrock model via the model-agnostic Converse API (bedrock-runtime).

    The model id may be a foundation model id, a cross-region inference
    profile (us.anthropic.…) or an ARN — it is percent-encoded into the path
    either way."""

    name = "bedrock"
    _log_tag = "[Bedrock]"

    @staticmethod
    def _default_base_url(region: str) -> str:
        return f"https://bedrock-runtime.{region}.amazonaws.com"

    def _converse_url(self) -> str:
        return f"{self.base_url}/model/{quote(self.model, safe='')}/converse"

    async def complete(self, prompt: str) -> str:
        body = await self._post(
            self._converse_url(),
            {
                "messages": [{"role": "user", "content": [{"text": prompt}]}],
                "inferenceConfig": {"maxTokens": 512, "temperature": 0.1},
            },
        )
        return _converse_text(body)


class BedrockMantleProvider(_BedrockAuthProvider):
    """Bedrock models served only on the bedrock-mantle endpoint (Gemma 4),
    through its OpenAI-compatible chat completions route (Gemma 4 lives under
    /openai/v1, not /v1). Picked automatically by the "bedrock" provider for
    _MANTLE_ONLY_MODEL_PREFIXES, so it reports the same "bedrock" name. Same
    auth as BedrockProvider, but SigV4 is signed for the "bedrock-mantle"
    service and IAM needs bedrock-mantle:CreateInference."""

    name = "bedrock"
    _log_tag = "[BedrockMantle]"
    _signing_service = "bedrock-mantle"

    @staticmethod
    def _default_base_url(region: str) -> str:
        return f"https://bedrock-mantle.{region}.api.aws/openai/v1"

    async def complete(self, prompt: str) -> str:
        body = await self._post(
            f"{self.base_url}/chat/completions",
            {
                "model": self.model,
                "messages": [{"role": "user", "content": prompt}],
                "max_tokens": 512,
                "temperature": 0.1,
            },
        )
        content, _ = _choice_content_and_logprobs(body)
        return content


# ── Qwen3Guard parser (ported verbatim — sync) ────────────────────────────────


def _confidence_from_logprobs(
    content_lp: list | None, chosen_label: str
) -> tuple[float | None, dict[str, float] | None, str]:
    if not content_lp:
        return None, None, "unavailable"
    text = ""
    label_idx = None
    for i, tok in enumerate(content_lp):
        before = text.lower()
        text += tok.get("token", "")
        if "safety:" not in text.lower():
            continue
        if "safety:" in before:
            if tok.get("token", "").strip():
                label_idx = i
                break
        elif text.lower().split("safety:", 1)[1].strip():
            label_idx = i
            break
    if label_idx is None:
        return None, None, "no-safety-token"
    entry = content_lp[label_idx]
    pool: list[dict[str, Any]] = list(entry.get("top_logprobs") or [])
    if entry.get("token") is not None and entry.get("logprob") is not None:
        pool.append({"token": entry["token"], "logprob": entry["logprob"]})
    if not pool:
        return None, None, "no-top-logprobs"
    labels = ("safe", "unsafe", "controversial")
    agg = {label: 0.0 for label in labels}
    mapped = False
    for cand in pool:
        ct = cand.get("token", "").strip().lower()
        if "safety:" in ct:
            ct = ct.split("safety:")[-1].strip()
        ct = ct.lstrip(":").strip().strip("\"'")
        if not ct:
            continue
        for label in labels:
            if label.startswith(ct) or ct.startswith(label):
                try:
                    agg[label] += math.exp(cand["logprob"])
                    mapped = True
                except (KeyError, TypeError, OverflowError):
                    pass
                break
    total = sum(agg.values())
    if not mapped or total <= 0:
        return None, None, "unmapped"
    dist = {label: round(v / total, 4) for label, v in agg.items()}
    return dist.get((chosen_label or "").lower()), dist, "logprobs"


def parse_qwen3guard_result(scanner_name: str, raw: str, logprobs_content: list | None = None) -> dict[str, Any]:
    if not raw:
        raise ValueError("empty Qwen3Guard response")
    safety = ""
    categories = ""
    for line in raw.strip().splitlines():
        if ":" not in line:
            continue
        key, _, val = line.partition(":")
        key, val = key.strip().lower(), val.strip()
        if key == "safety":
            safety = val
        elif key in ("categories", "category"):
            categories = val
    if not safety:
        raise ValueError(f"no Safety line in Qwen3Guard response: {raw[:200]!r}")
    s = safety.lower()
    if s == "unsafe":
        is_valid, discrete_risk = False, 1.0
    elif s == "controversial":
        is_valid, discrete_risk = False, 0.5
    elif s == "safe":
        is_valid, discrete_risk = True, 0.0
    else:
        raise ValueError(f"unknown Safety value: {safety!r}")

    conf, dist, source = _confidence_from_logprobs(logprobs_content, s)
    if source != "logprobs":
        # LLMScanner always requests top_logprobs>0 for a real scan, so landing
        # here means the deployment didn't return a usable logprobs distribution
        # (untested against the real Qwen3Guard-Gen-8B Foundry deployment) —
        # risk_score/decision_confidence fall back to a flat per-label constant
        # instead of the calibrated distribution. Worth knowing if this fires
        # on every request rather than only on genuine anomalies.
        logger.warning(f"[Qwen3Guard] confidence fallback (source={source!r}) for scanner={scanner_name}")
    if dist is not None:
        risk_score = round(dist["unsafe"] + dist["controversial"], 4)
        decision_confidence = float(conf) if conf is not None else (1.0 - risk_score if is_valid else risk_score)
    else:
        risk_score = discrete_risk
        decision_confidence = 1.0

    details: dict[str, Any] = {"safety": safety, "confidence_source": source}
    if categories and categories.lower() != "none":
        details["categories"] = categories
        if scanner_name == "BanTopics":
            details["matchedTopic"] = categories
    if dist is not None:
        details["prob_distribution"] = dist

    return {
        "is_valid": is_valid,
        "risk_score": risk_score,
        "decision_confidence": decision_confidence,
        "details": details,
    }


# ── Construction helpers ─────────────────────────────────────────────────────


def _require(values: dict, label: str) -> dict | None:
    missing = [k for k, v in values.items() if not v]
    if missing:
        logger.warning(f"{label}: missing required vars {missing}; skipping")
        return None
    return values


def _build_openai_compatible(model: str, base_url: str) -> LLMProvider | None:
    api_key = settings.OPENAI_API_KEY
    if not api_key and not base_url:
        logger.warning("[Providers] OPENAI_API_KEY not set and no baseUrl; skipping openai")
        return None
    return OpenAIProvider(api_key, model or DEFAULT_OPENAI_MODEL, base_url=base_url)


def _build_anthropic(model: str) -> LLMProvider | None:
    api_key = settings.ANTHROPIC_API_KEY
    if not api_key:
        logger.warning("[Providers] ANTHROPIC_API_KEY not set; skipping anthropic")
        return None
    return AnthropicProvider(api_key, model or DEFAULT_ANTHROPIC_MODEL)


def _build_vertexai() -> LLMProvider | None:
    env = _require(
        {
            "VERTEX_AI_SA_KEY_JSON": settings.VERTEX_AI_SA_KEY_JSON,
            "VERTEX_AI_PROJECT": settings.VERTEX_AI_PROJECT,
            "VERTEX_AI_LOCATION": settings.VERTEX_AI_LOCATION,
            "VERTEX_AI_ENDPOINT_ID": settings.VERTEX_AI_ENDPOINT_ID,
        },
        label="[Providers] vertexai",
    )
    if env is None:
        return None
    return VertexAIProvider(
        env["VERTEX_AI_SA_KEY_JSON"], env["VERTEX_AI_PROJECT"], env["VERTEX_AI_LOCATION"], env["VERTEX_AI_ENDPOINT_ID"]
    )


def _build_gemma_vertexai() -> LLMProvider | None:
    env = _require(
        {
            "GEMMA_VERTEX_SA_KEY_JSON": settings.GEMMA_VERTEX_SA_KEY_JSON,
            "GEMMA_VERTEX_PROJECT": settings.GEMMA_VERTEX_PROJECT,
            "GEMMA_VERTEX_LOCATION": settings.GEMMA_VERTEX_LOCATION,
            "GEMMA_VERTEX_ENDPOINT_ID": settings.GEMMA_VERTEX_ENDPOINT_ID,
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
        dedicated_dns=settings.GEMMA_VERTEX_DEDICATED_DNS,
    )


def _build_qwen3guard() -> LLMProvider | None:
    env = _require(
        {
            "QWEN3GUARD_SA_KEY_JSON": settings.QWEN3GUARD_SA_KEY_JSON,
            "QWEN3GUARD_PROJECT": settings.QWEN3GUARD_PROJECT,
            "QWEN3GUARD_LOCATION": settings.QWEN3GUARD_LOCATION,
            "QWEN3GUARD_ENDPOINT_ID": settings.QWEN3GUARD_ENDPOINT_ID,
        },
        label="[Providers] qwen3guard",
    )
    if env is None:
        return None
    return Qwen3GuardProvider(
        env["QWEN3GUARD_SA_KEY_JSON"],
        env["QWEN3GUARD_PROJECT"],
        env["QWEN3GUARD_LOCATION"],
        env["QWEN3GUARD_ENDPOINT_ID"],
        dedicated_dns=settings.QWEN3GUARD_DEDICATED_DNS,
    )


# ── Faster per-role alternates — old provider is the fallback on failure ──────
# Own model/baseUrl per modelConfigs entry, same as "openai_compatible"; on any
# error, each calls the existing builder for its role's original provider.

# Fast leg's own budget — bounds how long an unreachable/hung host can delay
# the fallback (asyncio.CancelledError from the caller's own timeout isn't an
# Exception, so an unreachable host must fail from inside our own except).
_FAST_LEG_TIMEOUT_S = 1.5


class Qwen3GuardFastProvider(Qwen3GuardOutput, OpenAIProvider):
    """Faster Qwen3Guard-hosting endpoint; falls back to qwen3guard (Vertex AI) on failure.

    Same contract as the real qwen3guard (raw text in, Safety:/Categories: out) —
    the model always answers that way regardless of prompt, on Vertex or here, so
    this can't take the ABCD path gemma_fast/gemma_fast_arbiter use.
    """

    name = "qwen3guard_fast"

    def __init__(self, api_key: str, model: str, base_url: str = ""):
        # OpenAIProvider.__init__ overwrites self.name — reassert it.
        super().__init__(api_key, model, base_url=base_url)
        self.name = "qwen3guard_fast"

    async def complete_with_logprobs(
        self, text: str, top_logprobs: int = 5, temperature: float = 0.0
    ) -> tuple[str, list | None]:
        try:
            headers = dict(_IDENTITY, **{"Content-Type": "application/json"})
            if self.api_key:
                headers["Authorization"] = f"Bearer {self.api_key}"
            client = http_client.get_client()
            body = await asyncio.wait_for(
                _post_json_logged(
                    client,
                    f"{self.base_url}/chat/completions",
                    headers,
                    {"model": self.model, **_qwen3guard_params(text, top_logprobs, temperature)},
                    "[Qwen3GuardFast]",
                ),
                timeout=_FAST_LEG_TIMEOUT_S,
            )
            return _choice_content_and_logprobs(body)
        except Exception as exc:
            logger.warning(f"[Qwen3GuardFast] fast endpoint failed ({exc!r}), falling back to qwen3guard")
            fallback = _build_qwen3guard()
            if not isinstance(fallback, Qwen3GuardOutput):
                raise
            return await fallback.complete_with_logprobs(text, top_logprobs, temperature)


class GemmaFastProvider(OpenAIProvider):
    """Faster Gemma endpoint; falls back to gemma_foundry on failure."""

    name = "gemma_fast"

    def __init__(self, api_key: str, model: str, base_url: str = ""):
        super().__init__(api_key, model, base_url=base_url)
        self.name = "gemma_fast"

    async def complete(self, prompt: str) -> str:
        try:
            return await asyncio.wait_for(super().complete(prompt), timeout=_FAST_LEG_TIMEOUT_S)
        except Exception as exc:
            logger.warning(f"[GemmaFast] fast endpoint failed ({exc!r}), falling back to gemma_foundry")
            fallback = _build_foundry("gemma_foundry", "", "", "")
            if fallback is None:
                raise
            return await fallback.complete(prompt)


class GemmaFastArbiterProvider(OpenAIProvider):
    """Faster (Gemma) arbiter endpoint. No built-in fallback and no time bound of its own: a slow answer is
    waited for, and a backup is a FINAL_ARBITER_BACKUP modelConfigs entry."""

    name = "gemma_fast_arbiter"
    include_metrics = True

    def __init__(self, api_key: str, model: str, base_url: str = ""):
        super().__init__(api_key, model, base_url=base_url)
        self.name = "gemma_fast_arbiter"


_FAST_PROVIDERS = ("qwen3guard_fast", "gemma_fast", "gemma_fast_arbiter")

# Fast provider name → settings attr holding its default endpoint. An entry's
# own "baseUrl" always overrides this; when the entry omits it, this is how
# the code knows which host to hit — add one more pair here for a new "_fast"
# provider, no other wiring needed.
_FAST_PROVIDER_BASE_URL_SETTING: dict[str, str] = {
    "qwen3guard_fast": "QWEN3GUARD_VLLM_BASE_URL",
    "gemma_fast": "GEMMA_VLLM_BASE_URL",
    "gemma_fast_arbiter": "GEMMA_VLLM_ARBITER_BASE_URL",
}


# Foundry provider name → (class, settings-var prefix). BASE_URL/API_KEY are
# required (entry baseUrl overrides the env); DEPLOYMENT/MODEL are optional.
# All classes take the same (base_url, api_key, deployment, model) constructor:
# the azure_* family speaks OpenAI /chat/completions, anthropic_foundry speaks
# the native Anthropic /v1/messages route.
# Every class shares the (base_url, api_key, deployment="", model="") constructor
# — Callable[..., LLMProvider] captures that across the two response families.
_FOUNDRY_PROVIDERS: dict[str, tuple[Callable[..., LLMProvider], str]] = {
    "azure_foundry": (AzureFoundryProvider, "AZURE_FOUNDRY"),
    "gemma_foundry": (GemmaFoundryProvider, "GEMMA_FOUNDRY"),
    "qwen3guard_foundry": (Qwen3GuardFoundryProvider, "QWEN3GUARD_FOUNDRY"),
    "anthropic_foundry": (AnthropicFoundryProvider, "ANTHROPIC_FOUNDRY"),
}


def _build_foundry(provider_name: str, model: str, base_url: str, deployment: str = "") -> LLMProvider | None:
    cls, prefix = _FOUNDRY_PROVIDERS[provider_name]
    env = _require(
        {
            f"{prefix}_BASE_URL": base_url or getattr(settings, f"{prefix}_BASE_URL"),
            f"{prefix}_API_KEY": getattr(settings, f"{prefix}_API_KEY"),
        },
        label=f"[Providers] {provider_name}",
    )
    if env is None:
        return None
    return cls(
        base_url=env[f"{prefix}_BASE_URL"],
        api_key=env[f"{prefix}_API_KEY"],
        deployment=deployment or getattr(settings, f"{prefix}_DEPLOYMENT"),
        model=model or getattr(settings, f"{prefix}_MODEL"),
    )


# Model ids AWS serves only on bedrock-mantle (not bedrock-runtime / Converse);
# the "bedrock" provider routes these to BedrockMantleProvider.
_MANTLE_ONLY_MODEL_PREFIXES = ("google.gemma-4",)


def _bedrock_class_for(model: str) -> type[_BedrockAuthProvider]:
    return BedrockMantleProvider if model.lower().startswith(_MANTLE_ONLY_MODEL_PREFIXES) else BedrockProvider


def _build_bedrock(model: str, base_url: str) -> LLMProvider | None:
    """Region + model are required; auth prefers the Bedrock API key, then
    static IAM access keys, then the pod's IAM role (EKS Pod Identity / IRSA —
    no keys configured at all). model/baseUrl may come per-entry, the
    credentials and region only from env. The model picks the endpoint:
    Gemma 4 goes to bedrock-mantle, everything else to Converse."""
    env = _require(
        {"BEDROCK_REGION": settings.BEDROCK_REGION, "BEDROCK_MODEL": model or settings.BEDROCK_MODEL},
        label="[Providers] bedrock",
    )
    if env is None:
        return None
    cls = _bedrock_class_for(env["BEDROCK_MODEL"])
    common = {"model": env["BEDROCK_MODEL"], "region": env["BEDROCK_REGION"], "base_url": base_url}
    if settings.BEDROCK_API_KEY:
        return cls(api_key=settings.BEDROCK_API_KEY, **common)
    if settings.BEDROCK_ACCESS_KEY_ID and settings.BEDROCK_SECRET_ACCESS_KEY:
        creds = aws_auth.AwsCredentials(
            settings.BEDROCK_ACCESS_KEY_ID, settings.BEDROCK_SECRET_ACCESS_KEY, settings.BEDROCK_SESSION_TOKEN
        )
        return cls(credentials=creds, **common)
    if aws_auth.role_credentials_source():
        return cls(**common)
    logger.warning(
        "[Providers] bedrock: no credentials (set BEDROCK_API_KEY, or BEDROCK_ACCESS_KEY_ID + "
        "BEDROCK_SECRET_ACCESS_KEY, or run with an IAM role via EKS Pod Identity / IRSA); skipping"
    )
    return None


_BUILDERS: dict[str, Callable[[str, str, str], LLMProvider | None]] = {
    "openai": lambda model, _b, _d: _build_openai_compatible(model, base_url=""),
    "openai_compatible": lambda model, base_url, _d: _build_openai_compatible(model, base_url),
    "anthropic": lambda model, _b, _d: _build_anthropic(model),
    "vertexai": lambda _m, _b, _d: _build_vertexai(),
    "gemma_vertexai": lambda _m, _b, _d: _build_gemma_vertexai(),
    "qwen3guard": lambda _m, _b, _d: _build_qwen3guard(),
    "azure_foundry": lambda model, base_url, deployment: _build_foundry("azure_foundry", model, base_url, deployment),
    "gemma_foundry": lambda model, base_url, deployment: _build_foundry("gemma_foundry", model, base_url, deployment),
    "qwen3guard_foundry": lambda model, base_url, deployment: _build_foundry(
        "qwen3guard_foundry", model, base_url, deployment
    ),
    "anthropic_foundry": lambda model, base_url, deployment: _build_foundry(
        "anthropic_foundry", model, base_url, deployment
    ),
    "qwen3guard_fast": lambda model, base_url, _d: (
        Qwen3GuardFastProvider(settings.QWEN_VLLM_KEY, model or DEFAULT_OPENAI_MODEL, base_url=base_url)
        if base_url
        else None
    ),
    "gemma_fast": lambda model, base_url, _d: (
        GemmaFastProvider(settings.GEMMA_VLLM_KEY, model or DEFAULT_OPENAI_MODEL, base_url=base_url)
        if base_url
        else None
    ),
    "gemma_fast_arbiter": lambda model, base_url, _d: (
        GemmaFastArbiterProvider(settings.GEMMA_26B_VLLM_KEY, model or DEFAULT_OPENAI_MODEL, base_url=base_url)
        if base_url
        else None
    ),
    "bedrock": lambda model, base_url, _d: _build_bedrock(model, base_url),
}


def _dispatch(provider_name: str, model: str, base_url: str, deployment: str = "") -> LLMProvider | None:
    builder = _BUILDERS.get(provider_name)
    if builder is None:
        logger.warning(f"[Providers] Unknown provider '{provider_name}'; skipping")
        return None
    return _cached_provider((provider_name, model, base_url, deployment), lambda: builder(model, base_url, deployment))


class FallbackProvider(LLMProvider):
    def __init__(self, primary: LLMProvider, backup_entry: dict[str, Any]):
        self.primary = primary
        self.backup_entry = backup_entry
        self.name = primary.name

    async def complete(self, prompt: str) -> str:
        try:
            return await self.primary.complete(prompt)
        except Exception as exc:
            backup = build_provider_from_config(self.backup_entry)
            if backup is None:
                raise
            logger.warning(
                f"[FinalArbiter] primary {self.primary.name} failed ({exc!r}), falling back to {backup.name}"
            )
            return await backup.complete(prompt)


def build_provider_from_env(provider_name: str, model: str = "") -> LLMProvider | None:
    name = provider_name.strip().lower()
    if name == "openai":
        model = model or settings.OPENAI_MODEL
    elif name == "anthropic":
        model = model or settings.ANTHROPIC_MODEL
    return _dispatch(name, model, "")


def build_provider_from_config(entry: dict[str, Any]) -> LLMProvider | None:
    name = (entry.get("provider") or "").strip().lower()
    model = (entry.get("model") or "").strip()
    if name in ("openai", "ollama", "openai_compatible"):
        base_url = (entry.get("baseUrl") or "").strip() or settings.OPENAI_COMPATIBLE_BASE_URL
        return _dispatch("openai_compatible", model, base_url)
    if name in _FAST_PROVIDERS:
        base_url = (entry.get("baseUrl") or "").strip() or getattr(settings, _FAST_PROVIDER_BASE_URL_SETTING[name], "")
        return _dispatch(name, model, base_url)
    if name in _FOUNDRY_PROVIDERS:
        # deployment (azureml-model-deployment header) is a routing label, not a
        # secret — safe to allow per-entry, unlike apiKey. This lets two
        # modelConfigs entries use the same provider (e.g. two "gemma_foundry"
        # roles) against two different deployments on the same Foundry
        # resource/endpoint, without needing separate env-var prefixes.
        return _dispatch(name, model, (entry.get("baseUrl") or "").strip(), (entry.get("deployment") or "").strip())
    if name == "bedrock":
        # model/baseUrl (e.g. a VPC interface endpoint) are routing, not secrets;
        # credentials and region stay env-only.
        return _dispatch(name, model, (entry.get("baseUrl") or "").strip())
    return _dispatch(name, model, "")


# ── Startup self-check ───────────────────────────────────────────────────────
# Fires one real, minimal request at each provider ACTUALLY configured in the
# cascade (DEFAULT_MODEL_CONFIG_JSON, falling back to the same built-in
# default a real request would use) right after process boot and logs
# PASS/FAIL immediately — a signal within seconds of deploy, not whenever the
# first real scan happens to hit that exact role.
#
# Pings each modelConfigs entry with ITS OWN per-entry model/baseUrl/deployment
# overrides — not a generic one-ping-per-provider-name check. That generic
# version was tried first and produced false failures against a real
# multi-deployment Foundry resource: two modelConfigs entries both used
# "gemma_foundry" (one Gemma 4 E2B-it, one Gemma 4 31B-it) distinguished only
# by a per-entry "model" field, with no usable env-var-level default model —
# a bare {"provider": "gemma_foundry"} ping has no model to send and the real
# endpoint rejects it with "Missed model deployment", even though the actual
# cascade entries (each carrying their own model) work fine.
_STARTUP_CHECK_TIMEOUT_S = 15.0
_STARTUP_CHECK_PROMPT = "Respond with the single word: ok"


async def _startup_check_entry(entry: dict[str, Any]) -> None:
    name = (entry.get("provider") or "").strip().lower()
    label = f"{name}[{entry.get('modelRole', '-')}]"
    provider = build_provider_from_config(entry)
    if provider is None:
        # Not configured (missing env vars) — build_provider_from_config already
        # logged which var via the "[Providers] ... skipping" warning; nothing
        # extra to say here, and staying silent avoids false-alarming on
        # providers this deployment was never meant to use.
        return
    logger.info(f"[StartupCheck] {label}: pinging real endpoint...")
    t0 = time.time()
    try:
        await asyncio.wait_for(provider.complete(_STARTUP_CHECK_PROMPT), timeout=_STARTUP_CHECK_TIMEOUT_S)
    except Exception as exc:
        elapsed_ms = (time.time() - t0) * 1000
        logger.error(f"[StartupCheck] {label}: FAILED after {elapsed_ms:.0f}ms: {exc!r}")
    else:
        elapsed_ms = (time.time() - t0) * 1000
        logger.info(f"[StartupCheck] {label}: OK ({elapsed_ms:.0f}ms)")


async def startup_self_check() -> None:
    """Run a check for every entry in the actually-configured cascade
    concurrently; never raises — failures are logged, not propagated, so a
    bad model never blocks boot."""
    from constants import get_default_config

    entries = get_default_config(settings.DEFAULT_MODEL_CONFIG_JSON).get("modelConfigs", [])
    logger.info(f"[StartupCheck] checking {len(entries)} configured cascade entries")
    await asyncio.gather(*(_startup_check_entry(e) for e in entries), return_exceptions=True)
    logger.info("[StartupCheck] done")
