"""Reach-back gateway client + /reachback/test — Azure AD assertion, token caching,
401 refresh, failure stages. Network mocked: token endpoint via the shared
client, gateway via the mTLS client.
"""

import base64
import hashlib
import json

import httpx
import pytest
import rsa
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa as crsa
from fastapi import HTTPException

import app as app_module
import azure_ad_auth
import reachback
from jwt_sign import load_private_key
from settings import settings

_TENANT = "morganstanley.onmicrosoft.com"
_CLIENT_ID = "11111111-2222-3333-4444-555555555555"
_SCOPE = "api://reachback-gw/.default"
_THUMBPRINT = hashlib.sha1(b"public_key.cer").hexdigest().upper()
_BASE_URL = "https://reach-back-gateway.ms.com/openai/v1"
_REQUEST = app_module.ReachbackTestRequest(prompt="hi", model="gpt-6-luna")
_COMPLETION = {"model": "gpt-6-luna", "choices": [{"message": {"role": "assistant", "content": "ok"}}]}


def _unb64(s: str) -> bytes:
    return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))


def _make_key(fmt=serialization.PrivateFormat.PKCS8):
    key = crsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = key.private_bytes(serialization.Encoding.PEM, fmt, serialization.NoEncryption()).decode()
    return key, pem


class _FakeClient:
    """Records POSTs and replies from a queue; one class per role (token endpoint, gateway)."""

    def __init__(self, replies):
        self.replies = list(replies)
        self.posts = []

    async def post(self, url, **kwargs):
        self.posts.append({"url": url, **kwargs})
        reply = self.replies.pop(0)
        if isinstance(reply, Exception):
            raise reply
        return reply


def _token_reply(token="eyJ.token", status=200):
    body = {"access_token": token, "expires_in": 3599} if status == 200 else {"error": "invalid_client"}
    return httpx.Response(status, json=body, request=httpx.Request("POST", azure_ad_auth.token_url(_TENANT)))


def _gateway_reply(status=200, body=None):
    return httpx.Response(status, json=body if body is not None else _COMPLETION)


@pytest.fixture
def key(tmp_path, monkeypatch):
    crypto_key, pem = _make_key()
    key_file = tmp_path / "private_key.pem"
    key_file.write_text(pem)
    values = {
        "REACHBACK_BASE_URL": _BASE_URL,
        "REACHBACK_TENANT": _TENANT,
        "REACHBACK_CLIENT_ID": _CLIENT_ID,
        "REACHBACK_SCOPE": _SCOPE,
        "REACHBACK_THUMBPRINT": _THUMBPRINT,
        "REACHBACK_SPN_KEY_FILE": str(key_file),
        "REACHBACK_MTLS_CERT_FILE": "/etc/reachback/mtls/tls.crt",
        "REACHBACK_MTLS_KEY_FILE": "/etc/reachback/mtls/tls.key",
        "REACHBACK_CA_FILE": "/etc/reachback/ca/ca.pem",
    }
    for name, value in values.items():
        monkeypatch.setattr(settings, name, value)
    azure_ad_auth._TOKEN_CACHE.clear()
    return crypto_key


def _wire(monkeypatch, token_replies, gateway_replies):
    token_client = _FakeClient(token_replies)
    gateway_client = _FakeClient(gateway_replies)
    monkeypatch.setattr(azure_ad_auth.http_client, "get_client", lambda: token_client)
    monkeypatch.setattr(reachback, "_mtls_client", lambda: gateway_client)
    return token_client, gateway_client


async def test_token_request_carries_verifiable_certificate_assertion(key, monkeypatch):
    token_client, _ = _wire(monkeypatch, [_token_reply()], [_gateway_reply()])

    await reachback.chat("hi", "gpt-6-luna")

    post = token_client.posts[0]
    assert post["url"] == f"https://login.microsoftonline.com/{_TENANT}/oauth2/v2.0/token"
    form = post["data"]
    assert form["grant_type"] == "client_credentials"
    assert form["client_id"] == _CLIENT_ID
    assert form["scope"] == _SCOPE
    assert form["client_assertion_type"] == "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"

    header_b64, claims_b64, sig_b64 = form["client_assertion"].split(".")
    header = json.loads(_unb64(header_b64))
    claims = json.loads(_unb64(claims_b64))
    assert header == {
        "alg": "RS256",
        "typ": "JWT",
        "x5t": base64.urlsafe_b64encode(bytes.fromhex(_THUMBPRINT)).rstrip(b"=").decode(),
    }
    assert claims["iss"] == claims["sub"] == _CLIENT_ID
    assert claims["aud"] == post["url"]
    assert claims["exp"] - claims["iat"] == 600
    pub = rsa.PublicKey(key.public_key().public_numbers().n, key.public_key().public_numbers().e)
    assert rsa.verify(f"{header_b64}.{claims_b64}".encode(), _unb64(sig_b64), pub) == "SHA-256"


async def test_gateway_request_shape(key, monkeypatch):
    _, gateway_client = _wire(monkeypatch, [_token_reply("tok-1")], [_gateway_reply()])

    result = await reachback.chat("hello", "gpt-6-luna")

    post = gateway_client.posts[0]
    assert post["url"] == f"{_BASE_URL}/chat/completions"
    assert post["headers"] == {"Authorization": "Bearer tok-1"}
    assert post["json"] == {
        "model": "gpt-6-luna",
        "messages": [{"role": "user", "content": "hello"}],
        "max_completion_tokens": 512,
    }
    assert result["content"] == "ok"
    assert result["model"] == "gpt-6-luna"


async def test_full_chat_completions_url_is_accepted(key, monkeypatch):
    monkeypatch.setattr(settings, "REACHBACK_BASE_URL", f"{_BASE_URL}/chat/completions/")
    _, gateway_client = _wire(monkeypatch, [_token_reply()], [_gateway_reply()])

    await reachback.chat("hi", "gpt-6-sol")

    assert gateway_client.posts[0]["url"] == f"{_BASE_URL}/chat/completions"


async def test_token_is_cached_across_calls(key, monkeypatch):
    token_client, gateway_client = _wire(monkeypatch, [_token_reply()], [_gateway_reply(), _gateway_reply()])

    await reachback.chat("one", "gpt-6-luna")
    await reachback.chat("two", "gpt-6-luna")

    assert len(token_client.posts) == 1
    assert len(gateway_client.posts) == 2


async def test_401_refreshes_token_and_retries_once(key, monkeypatch):
    token_client, gateway_client = _wire(
        monkeypatch, [_token_reply("stale"), _token_reply("fresh")], [_gateway_reply(401, {}), _gateway_reply()]
    )

    result = await reachback.chat("hi", "gpt-6-luna")

    assert result["content"] == "ok"
    assert len(token_client.posts) == 2
    assert [p["headers"]["Authorization"] for p in gateway_client.posts] == ["Bearer stale", "Bearer fresh"]


async def test_gateway_error_reports_http_stage(key, monkeypatch):
    _wire(monkeypatch, [_token_reply()], [_gateway_reply(403, {"error": "forbidden"})])

    with pytest.raises(reachback.ReachbackError) as err:
        await reachback.chat("hi", "gpt-6-luna")

    assert err.value.as_dict() == {"stage": "http", "status": 403, "error": '{"error":"forbidden"}'}


async def test_entra_rejection_reports_token_stage(key, monkeypatch):
    _, gateway_client = _wire(monkeypatch, [_token_reply(status=401)], [])

    with pytest.raises(reachback.ReachbackError) as err:
        await reachback.chat("hi", "gpt-6-luna")

    assert err.value.stage == "token"
    assert err.value.status == 401
    assert "invalid_client" in err.value.error
    assert gateway_client.posts == []


async def test_missing_key_file_reports_token_stage(key, monkeypatch):
    monkeypatch.setattr(settings, "REACHBACK_SPN_KEY_FILE", "/nonexistent/private_key.pem")
    _wire(monkeypatch, [], [])

    with pytest.raises(reachback.ReachbackError) as err:
        await reachback.chat("hi", "gpt-6-luna")

    assert err.value.stage == "token"
    assert "FileNotFoundError" in err.value.error


async def test_handshake_failure_reports_connect_stage(key, monkeypatch):
    _wire(monkeypatch, [_token_reply()], [httpx.ConnectError("[SSL: TLSV13_ALERT_CERTIFICATE_REQUIRED]")])

    with pytest.raises(reachback.ReachbackError) as err:
        await reachback.chat("hi", "gpt-6-luna")

    assert err.value.stage == "connect"
    assert "CERTIFICATE_REQUIRED" in err.value.error


def test_pkcs1_private_key_loads():
    _, pem = _make_key(serialization.PrivateFormat.TraditionalOpenSSL)
    assert pem.startswith("-----BEGIN RSA PRIVATE KEY-----")
    assert isinstance(load_private_key(pem), rsa.PrivateKey)


def test_thumbprint_with_colons_matches_plain_hex():
    colon_form = ":".join(_THUMBPRINT[i : i + 2] for i in range(0, len(_THUMBPRINT), 2))
    assert azure_ad_auth._x5t(colon_form) == azure_ad_auth._x5t(_THUMBPRINT)


async def test_endpoint_returns_503_naming_missing_settings(monkeypatch):
    monkeypatch.setattr(settings, "REACHBACK_SCOPE", "")
    monkeypatch.setattr(settings, "REACHBACK_CA_FILE", "")

    with pytest.raises(HTTPException) as err:
        await app_module.reachback_test(_REQUEST)

    assert err.value.status_code == 503
    assert "REACHBACK_SCOPE" in err.value.detail
    assert "REACHBACK_CA_FILE" in err.value.detail


async def test_endpoint_returns_completion(key, monkeypatch):
    _wire(monkeypatch, [_token_reply()], [_gateway_reply()])

    result = await app_module.reachback_test(_REQUEST)

    assert result["content"] == "ok"


async def test_endpoint_returns_502_with_failed_stage(key, monkeypatch):
    _wire(monkeypatch, [_token_reply()], [_gateway_reply(403, {"error": "forbidden"})])

    resp = await app_module.reachback_test(_REQUEST)

    assert resp.status_code == 502
    assert json.loads(resp.body) == {"stage": "http", "status": 403, "error": '{"error":"forbidden"}'}
