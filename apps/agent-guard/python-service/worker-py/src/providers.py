"""LLM provider implementations (async port for the Worker runtime).

Differences from the container version:
  - httpx.AsyncClient + async complete()
  - every request sets Accept-Encoding: identity (Pyodide double-gunzip fix)
  - Vertex auth uses gcp_auth.get_token() instead of google-auth credentials
"""

import asyncio
import logging
import math
import time
from abc import ABC, abstractmethod
from collections.abc import Callable
from typing import Any, Optional

import httpx

import gcp_auth
import http_client
from settings import settings

logger = logging.getLogger(__name__)

DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
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
    client: httpx.AsyncClient, url: str, headers: dict, json_body: dict, log_tag: str, extra: str = ""
) -> dict:
    """POST + parse JSON, logging enough on failure to diagnose without a debugger.

    Every branch that can raise logs first: network-level failures (DNS,
    connection refused, timeout), HTTP error status (with the response body —
    this is where providers put the actual reason: auth failure, wrong
    deployment, quota exceeded), and non-JSON/malformed bodies. Callers still
    see the same exceptions as before (nothing swallowed), just with a log
    line alongside so a client-only failure doesn't require a repro to debug.
    """
    ctx = f" ({extra})" if extra else ""
    try:
        resp = await client.post(url, headers=headers, json=json_body)
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


class LLMProvider(ABC):
    name: str = ""

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

    async def complete(self, prompt: str) -> str:
        headers = dict(_IDENTITY, **{"Content-Type": "application/json"})
        if self.api_key:
            headers["Authorization"] = f"Bearer {self.api_key}"
        client = http_client.get_client()
        resp = await client.post(
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
        self.api_key = api_key
        self.model = model or DEFAULT_ANTHROPIC_MODEL
        logger.info(f"[Anthropic] model={self.model} api_key={_redact_secret(self.api_key)}")

    async def complete(self, prompt: str) -> str:
        # Deliberately not logging `prompt` here (pre-existing full-prompt log
        # removed) — this is the customer's scanned text, not our own config;
        # for the Password scanner it IS the secret the scan exists to catch.
        client = http_client.get_client()
        resp = await client.post(
            "https://api.anthropic.com/v1/messages",
            headers=dict(
                _IDENTITY,
                **{
                    "Content-Type": "application/json",
                    "x-api-key": self.api_key,
                    "anthropic-version": "2023-06-01",
                },
            ),
            json={
                "model": self.model,
                "max_tokens": 256,
                "messages": [{"role": "user", "content": prompt}],
            },
        )
        resp.raise_for_status()
        return resp.json()["content"][0]["text"]


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


# Foundry provider name → (class, settings-var prefix). BASE_URL/API_KEY are
# required (entry baseUrl overrides the env); DEPLOYMENT/MODEL are optional.
_FOUNDRY_PROVIDERS: dict[str, tuple[type[AzureFoundryProvider], str]] = {
    "azure_foundry": (AzureFoundryProvider, "AZURE_FOUNDRY"),
    "gemma_foundry": (GemmaFoundryProvider, "GEMMA_FOUNDRY"),
    "qwen3guard_foundry": (Qwen3GuardFoundryProvider, "QWEN3GUARD_FOUNDRY"),
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
}


def _dispatch(provider_name: str, model: str, base_url: str, deployment: str = "") -> LLMProvider | None:
    builder = _BUILDERS.get(provider_name)
    if builder is None:
        logger.warning(f"[Providers] Unknown provider '{provider_name}'; skipping")
        return None
    return _cached_provider((provider_name, model, base_url, deployment), lambda: builder(model, base_url, deployment))


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
    if name in _FOUNDRY_PROVIDERS:
        # deployment (azureml-model-deployment header) is a routing label, not a
        # secret — safe to allow per-entry, unlike apiKey. This lets two
        # modelConfigs entries use the same provider (e.g. two "gemma_foundry"
        # roles) against two different deployments on the same Foundry
        # resource/endpoint, without needing separate env-var prefixes.
        return _dispatch(name, model, (entry.get("baseUrl") or "").strip(), (entry.get("deployment") or "").strip())
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
