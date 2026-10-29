"""Chat completions against models behind Morgan Stanley's reach-back gateway.

Two layers of auth on every call:
  1. Azure AD app token (azure_ad_auth) — sent as `Authorization: Bearer`.
  2. mTLS — the connection presents the client cert/key and trusts only the
     gateway's CA bundle (_mtls_client).

Container (FastAPI) runtime only: it reads the key/cert files mounted in the
pod and needs client-certificate TLS, which the Cloudflare Worker lacks —
hence `ssl` stays out of http_client, which the Worker also imports.
"""

import logging
import ssl
import time
from typing import Any

import httpx

import azure_ad_auth
from settings import settings

logger = logging.getLogger(__name__)

_REQUIRED = (
    "REACHBACK_BASE_URL",
    "REACHBACK_TENANT",
    "REACHBACK_CLIENT_ID",
    "REACHBACK_SCOPE",
    "REACHBACK_THUMBPRINT",
    "REACHBACK_SPN_KEY_FILE",
    "REACHBACK_MTLS_CERT_FILE",
    "REACHBACK_MTLS_KEY_FILE",
    "REACHBACK_CA_FILE",
)
# GPT-5-class models reject `max_tokens` and any non-default `temperature`.
_MAX_COMPLETION_TOKENS = 512
_TIMEOUT_S = 120.0

# (cert_file, key_file, ca_file) -> client presenting that client certificate
_mtls_clients: dict[tuple[str, str, str], httpx.AsyncClient] = {}


class ReachbackError(Exception):
    """A failed reach-back call, tagged with the stage that failed:
    "token" (Azure AD), "connect" (DNS/TCP/TLS handshake/timeout to the
    gateway) or "http" (the gateway answered with an error status)."""

    def __init__(self, stage: str, error: str, status: int | None = None):
        super().__init__(f"{stage}: {error}")
        self.stage = stage
        self.error = error
        self.status = status

    def as_dict(self) -> dict[str, Any]:
        return {"stage": self.stage, "status": self.status, "error": self.error}


def missing_settings() -> list[str]:
    return [name for name in _REQUIRED if not getattr(settings, name)]


def _chat_url() -> str:
    # Accept the base (".../openai/v1") or the full route as the client pasted it.
    return settings.REACHBACK_BASE_URL.strip().rstrip("/").removesuffix("/chat/completions") + "/chat/completions"


def _mtls_client() -> httpx.AsyncClient:
    """Client that presents the mTLS cert and trusts only the CA bundle — kept apart from
    http_client.get_client(), which must keep trusting public CAs (the token call goes to
    login.microsoftonline.com). Files are read once per process: a renewed cert needs a restart."""
    key = (settings.REACHBACK_MTLS_CERT_FILE, settings.REACHBACK_MTLS_KEY_FILE, settings.REACHBACK_CA_FILE)
    client = _mtls_clients.get(key)
    if client is None or client.is_closed:
        ctx = ssl.create_default_context(cafile=settings.REACHBACK_CA_FILE)
        ctx.load_cert_chain(settings.REACHBACK_MTLS_CERT_FILE, settings.REACHBACK_MTLS_KEY_FILE)
        client = httpx.AsyncClient(verify=ctx, timeout=_TIMEOUT_S)
        _mtls_clients[key] = client
    return client


def _token_key() -> tuple[str, str, str]:
    return settings.REACHBACK_TENANT, settings.REACHBACK_CLIENT_ID, settings.REACHBACK_SCOPE


async def _token() -> str:
    try:
        with open(settings.REACHBACK_SPN_KEY_FILE) as f:
            private_key_pem = f.read()
        return await azure_ad_auth.get_token(*_token_key(), settings.REACHBACK_THUMBPRINT, private_key_pem)
    except httpx.HTTPStatusError as exc:
        raise ReachbackError("token", exc.response.text[:1000], exc.response.status_code) from exc
    except (httpx.RequestError, OSError, ValueError, KeyError) as exc:
        raise ReachbackError("token", repr(exc)) from exc


async def _post(token: str, prompt: str, model: str) -> httpx.Response:
    try:
        return await _mtls_client().post(
            _chat_url(),
            headers={"Authorization": f"Bearer {token}"},
            json={
                "model": model,
                "messages": [{"role": "user", "content": prompt}],
                "max_completion_tokens": _MAX_COMPLETION_TOKENS,
            },
        )
    except (httpx.RequestError, ssl.SSLError, OSError) as exc:
        raise ReachbackError("connect", repr(exc)) from exc


async def chat(prompt: str, model: str) -> dict[str, Any]:
    """One chat completion; a 401 drops the cached token and retries once with a fresh one."""
    started = time.perf_counter()
    token = await _token()
    token_ms = (time.perf_counter() - started) * 1000

    call_started = time.perf_counter()
    resp = await _post(token, prompt, model)
    if resp.status_code == 401:
        logger.warning(f"[Reachback] 401 from gateway model={model}; refreshing token and retrying once")
        azure_ad_auth.invalidate(*_token_key())
        resp = await _post(await _token(), prompt, model)
    call_ms = (time.perf_counter() - call_started) * 1000

    if resp.is_error:
        logger.error(f"[Reachback] HTTP {resp.status_code} model={model}: {resp.text[:1000]!r}")
        raise ReachbackError("http", resp.text[:1000], resp.status_code)
    body = resp.json()
    logger.info(f"[Reachback] model={model} token_ms={token_ms:.0f} call_ms={call_ms:.0f}")
    return {
        "content": body["choices"][0]["message"]["content"],
        "model": body.get("model", model),
        "token_ms": round(token_ms),
        "call_ms": round(call_ms),
    }
