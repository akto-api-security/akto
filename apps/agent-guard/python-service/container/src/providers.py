"""LLM provider implementations for the scanner pipeline.

Each provider exposes:
    - .name        : str identifier used for prompt routing & telemetry
    - .complete()  : str -> str  (sends the prompt, returns raw model text)
"""

import base64
import json
import logging
import math
import threading
from abc import ABC, abstractmethod
from typing import Any, Callable, Dict, List, Optional, Tuple

import google.auth.transport.requests
import httpx
from google.oauth2 import service_account

from settings import settings

logger = logging.getLogger(__name__)

DEFAULT_OPENAI_MODEL = "gpt-4o-mini"
DEFAULT_ANTHROPIC_MODEL = "claude-haiku-4-5-20251001"
DEFAULT_VERTEX_AI_MODEL = "publishers/qwen/models/qwen3guard"

_HTTP_TIMEOUT_SECONDS = 120.0
_VERTEX_SCOPES = ["https://www.googleapis.com/auth/cloud-platform"]

# Process-wide cache of built providers. Vertex credentials, httpx connection
# pools, and base64/JSON decoding of SA keys are all expensive — building once
# per (provider, model, base_url) and reusing across requests is the win.
_PROVIDER_CACHE: Dict[Tuple[str, str, str], "LLMProvider"] = {}
_PROVIDER_CACHE_LOCK = threading.Lock()


def _cached_provider(
    cache_key: Tuple[str, str, str], builder: Callable[[], Optional["LLMProvider"]]
) -> Optional["LLMProvider"]:
    """Return the cached provider for cache_key, or build + store on miss.
    Failed builds (None) are not cached so transient credential issues recover."""
    cached = _PROVIDER_CACHE.get(cache_key)
    if cached is not None:
        return cached
    with _PROVIDER_CACHE_LOCK:
        cached = _PROVIDER_CACHE.get(cache_key)
        if cached is not None:
            return cached
        built = builder()
        if built is not None:
            _PROVIDER_CACHE[cache_key] = built
        return built


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
        if not self.credentials.valid:
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


class Qwen3GuardProvider(VertexAIProvider):
    """Vertex AI provider for Qwen3Guard — a specialised guard classifier that
    emits `Safety: Safe|Unsafe|Controversial` plus `Categories: …` and exposes a
    real probability distribution via the first-token top_logprobs.

    Unlike chat LLMs, Qwen3Guard ignores prompt-format instructions, so callers
    must pass raw user text (no JSON wrapper) and use `complete_with_logprobs`
    to get back the logprob payload needed for confidence scoring.
    """

    name = "qwen3guard"
    _log_tag = "[LLMScanner/Qwen3Guard]"

    def complete(self, prompt: str) -> str:
        # Satisfy the LLMProvider abstract contract; logprob-aware callers
        # should use complete_with_logprobs() directly.
        content, _ = self.complete_with_logprobs(prompt, top_logprobs=0)
        return content

    def complete_with_logprobs(
        self, text: str, top_logprobs: int = 5, temperature: float = 0.0
    ) -> Tuple[str, Optional[list]]:
        if not self.credentials.valid:
            self.credentials.refresh(google.auth.transport.requests.Request())
        instance: Dict[str, Any] = {
            "@requestFormat": "chatCompletions",
            "messages": [{"role": "user", "content": text}],
            "max_tokens": 64,
            "temperature": temperature,
        }
        if top_logprobs > 0:
            instance["logprobs"] = True
            instance["top_logprobs"] = top_logprobs
        resp = self._client.post(
            self._predict_url(),
            headers={
                "Content-Type": "application/json",
                "Authorization": f"Bearer {self.credentials.token}",
            },
            json={"instances": [instance]},
        )
        resp.raise_for_status()
        choice = resp.json()["predictions"]["choices"][0]
        content = choice["message"]["content"]
        lp = (choice.get("logprobs") or {}).get("content")
        return content, lp


# ── Qwen3Guard parser ────────────────────────────────────────────────────────


def _confidence_from_logprobs(
    content_lp: Optional[list], chosen_label: str
) -> Tuple[Optional[float], Optional[Dict[str, float]], str]:
    """Distribution over {safe, unsafe, controversial} from the safety-label
    token's top_logprobs. Ported from qwen_prompt_injection_bench/qwen_guard.py.

    Returns (confidence_in_chosen_label, distribution, source). `source` is
    "logprobs" on success, otherwise a fallback reason string.
    """
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
    pool: List[Dict[str, Any]] = list(entry.get("top_logprobs") or [])
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


def parse_qwen3guard_result(
    scanner_name: str, raw: str, logprobs_content: Optional[list] = None
) -> Dict[str, Any]:
    """Parse Qwen3Guard output (Safety:/Categories: format) into the same
    uniform shape as parse_llm_result. risk_score = p(unsafe ∪ controversial)."""
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
    if dist is not None:
        risk_score = round(dist["unsafe"] + dist["controversial"], 4)
        decision_confidence = float(conf) if conf is not None else (1.0 - risk_score if is_valid else risk_score)
    else:
        risk_score = discrete_risk
        decision_confidence = 1.0  # discrete label has no calibrated confidence; trust it fully

    details: Dict[str, Any] = {
        "safety": safety,
        "confidence_source": source,
    }
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


def _require_env(values: dict, label: str) -> Optional[dict]:
    """Return the dict if every value is truthy, else log + return None."""
    missing = [k for k, v in values.items() if not v]
    if missing:
        logger.warning(f"{label}: missing required env vars {missing}; skipping")
        return None
    return values


# ── Construction helpers ─────────────────────────────────────────────────────


def _build_openai_compatible(model: str, base_url: str) -> Optional[LLMProvider]:
    api_key = settings.OPENAI_API_KEY
    # Ollama and other local OpenAI-compatible servers don't require a key,
    # but we still need *some* way to reach them.
    if not api_key and not base_url:
        logger.warning("[Providers] OPENAI_API_KEY not set and no baseUrl; skipping openai")
        return None
    return OpenAIProvider(api_key, model or settings.OPENAI_MODEL or DEFAULT_OPENAI_MODEL, base_url=base_url)


def _build_anthropic(model: str) -> Optional[LLMProvider]:
    api_key = settings.ANTHROPIC_API_KEY
    if not api_key:
        logger.warning("[Providers] ANTHROPIC_API_KEY not set; skipping anthropic")
        return None
    return AnthropicProvider(api_key, model or DEFAULT_ANTHROPIC_MODEL)


def _build_vertexai() -> Optional[LLMProvider]:
    env = _require_env(
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
        env["VERTEX_AI_SA_KEY_JSON"],
        env["VERTEX_AI_PROJECT"],
        env["VERTEX_AI_LOCATION"],
        env["VERTEX_AI_ENDPOINT_ID"],
    )


def _build_gemma_vertexai() -> Optional[LLMProvider]:
    env = _require_env(
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


def _build_qwen3guard() -> Optional[LLMProvider]:
    env = _require_env(
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


# Registry of provider builders, keyed by canonical provider name. Inputs are
# (model, base_url); credentials and other static config come from settings
# inside each builder. Adding a new provider means adding one row here.
_BUILDERS: Dict[str, Callable[[str, str], Optional[LLMProvider]]] = {
    "openai":            lambda model, _: _build_openai_compatible(model, base_url=""),
    "openai_compatible": _build_openai_compatible,
    "anthropic":         lambda model, _: _build_anthropic(model),
    "vertexai":          lambda _m, _b:   _build_vertexai(),
    "gemma_vertexai":    lambda _m, _b:   _build_gemma_vertexai(),
    "qwen3guard":        lambda _m, _b:   _build_qwen3guard(),
}


def _dispatch(provider_name: str, model: str, base_url: str) -> Optional[LLMProvider]:
    """Route a canonical provider name to its builder, returning the cached instance."""
    builder = _BUILDERS.get(provider_name)
    if builder is None:
        logger.warning(f"[Providers] Unknown provider '{provider_name}'; skipping")
        return None
    return _cached_provider(
        (provider_name, model, base_url),
        lambda: builder(model, base_url),
    )


def build_provider_from_env(provider_name: str, model: str = "") -> Optional[LLMProvider]:
    """Build a provider using *only* env vars. Used by the SCANNER_LLM_PROVIDER path."""
    name = provider_name.strip().lower()
    if name == "openai":
        model = model or settings.OPENAI_MODEL
    elif name == "anthropic":
        model = model or settings.ANTHROPIC_MODEL
    return _dispatch(name, model, "")


def build_provider_from_config(entry: Dict[str, Any]) -> Optional[LLMProvider]:
    """Build a provider from a modelMap entry. Reads name/model/baseUrl from the
    entry; credentials still come from settings."""
    name = (entry.get("provider") or "").strip().lower()
    model = (entry.get("model") or "").strip()
    if name in ("openai", "ollama", "openai_compatible"):
        # Entry-level baseUrl wins, then OPENAI_COMPATIBLE_BASE_URL.
        base_url = (entry.get("baseUrl") or "").strip() or settings.OPENAI_COMPATIBLE_BASE_URL
        return _dispatch("openai_compatible", model, base_url)
    return _dispatch(name, model, "")
