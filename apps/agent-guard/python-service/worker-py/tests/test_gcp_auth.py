"""gcp_auth — JWT signing + token exchange, with the network mocked.

Proves _load_private_key (PKCS#8 -> PKCS#1) + rsa RS256 signing produce a
verifiable assertion, and that get_token caches by service account.
"""

import base64
import json

import pytest
import rsa
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa as crsa

import gcp_auth


def _make_sa_info(email="svc@proj.iam.gserviceaccount.com"):
    """Generate a throwaway RSA key as a real PKCS#8 PEM service-account dict."""
    key = crsa.generate_private_key(public_exponent=65537, key_size=2048)
    pem = key.private_bytes(
        encoding=serialization.Encoding.PEM,
        format=serialization.PrivateFormat.PKCS8,
        encryption_algorithm=serialization.NoEncryption(),
    ).decode()
    return {"client_email": email, "private_key": pem}, key


class _FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


class _FakeClient:
    """Async-context-manager stand-in for httpx.AsyncClient."""

    posts = []
    payload = {"access_token": "ya29.fake-token", "expires_in": 3600}

    def __init__(self, *a, **kw):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *a):
        return False

    async def post(self, url, headers=None, data=None):
        _FakeClient.posts.append({"url": url, "data": data})
        return _FakeResponse(_FakeClient.payload)


@pytest.fixture(autouse=True)
def patch_httpx_and_cache(monkeypatch):
    _FakeClient.posts = []
    gcp_auth._TOKEN_CACHE.clear()
    monkeypatch.setattr(gcp_auth.httpx, "AsyncClient", _FakeClient)
    yield


def test_load_private_key_roundtrips():
    sa_info, _ = _make_sa_info()
    pk = gcp_auth._load_private_key(sa_info["private_key"])
    assert isinstance(pk, rsa.PrivateKey)


async def test_get_token_returns_and_signs_verifiable_jwt():
    sa_info, key = _make_sa_info()
    token = await gcp_auth.get_token(sa_info)
    assert token == "ya29.fake-token"
    assert len(_FakeClient.posts) == 1

    # Pull the assertion that was sent and verify its RS256 signature.
    assertion = _FakeClient.posts[0]["data"]["assertion"]
    header_b64, payload_b64, sig_b64 = assertion.split(".")

    def _unb64(s):
        return base64.urlsafe_b64decode(s + "=" * (-len(s) % 4))

    header = json.loads(_unb64(header_b64))
    payload = json.loads(_unb64(payload_b64))
    assert header == {"alg": "RS256", "typ": "JWT"}
    assert payload["iss"] == sa_info["client_email"]
    assert payload["aud"] == "https://oauth2.googleapis.com/token"

    pub = rsa.PublicKey(key.public_key().public_numbers().n,
                        key.public_key().public_numbers().e)
    signing_input = f"{header_b64}.{payload_b64}".encode()
    # rsa.verify raises on mismatch; returns hash name on success.
    assert rsa.verify(signing_input, _unb64(sig_b64), pub) == "SHA-256"


async def test_get_token_caches_per_service_account():
    sa_info, _ = _make_sa_info()
    t1 = await gcp_auth.get_token(sa_info)
    t2 = await gcp_auth.get_token(sa_info)
    assert t1 == t2
    # second call served from cache -> no second network post
    assert len(_FakeClient.posts) == 1
