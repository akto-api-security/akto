"""GCP service-account auth for Vertex AI, validated for the Worker runtime.

The native `cryptography` lib (and therefore `google-auth`) cannot load in
Pyodide/workerd, so we mint the access token ourselves with the pure-Python
`rsa` + `pyasn1` libs: decode the PKCS#8 SA key -> inner PKCS#1 -> RS256-sign a
JWT-bearer assertion -> exchange it at Google's OAuth endpoint. Tokens are
cached per service account until shortly before expiry, so the (slow, wasm)
RSA signing runs ~once per hour rather than per request.

Every outbound request sets `Accept-Encoding: identity` — the fetch layer under
Pyodide already decompresses, and httpx double-gunzips otherwise.
"""

import base64
import json
import time
from typing import Dict, Tuple

import rsa
from pyasn1.codec.der import decoder as der_decoder

import http_client

_TOKEN_URL = "https://oauth2.googleapis.com/token"
_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
_GRANT = "urn:ietf:params:oauth:grant-type:jwt-bearer"
_IDENTITY = {"Accept-Encoding": "identity"}

# client_email -> (access_token, absolute_expiry_epoch)
_TOKEN_CACHE: Dict[str, Tuple[str, float]] = {}


def sa_info_from_b64(sa_key_b64: str) -> dict:
    """Decode a base64-encoded service-account-key JSON into a dict."""
    return json.loads(base64.b64decode(sa_key_b64))


def _b64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode()


def _load_private_key(pem: str) -> "rsa.PrivateKey":
    body = "".join(line for line in pem.splitlines() if line and not line.startswith("-----"))
    pkcs8_der = base64.b64decode(body)
    decoded, _ = der_decoder.decode(pkcs8_der)
    # PKCS#8 PrivateKeyInfo: [version, algorithm, privateKey OCTET STRING (PKCS#1)]
    pkcs1_der = bytes(decoded[2])
    return rsa.PrivateKey.load_pkcs1(pkcs1_der, format="DER")


async def get_token(sa_info: dict) -> str:
    """Return a valid cloud-platform access token for this service account."""
    email = sa_info["client_email"]
    now = time.time()
    cached = _TOKEN_CACHE.get(email)
    if cached and cached[1] - 300 > now:
        return cached[0]

    privkey = _load_private_key(sa_info["private_key"])
    iat = int(now)
    header = _b64url(json.dumps({"alg": "RS256", "typ": "JWT"}).encode())
    payload = _b64url(json.dumps({
        "iss": email,
        "scope": _SCOPE,
        "aud": _TOKEN_URL,
        "iat": iat,
        "exp": iat + 3600,
    }).encode())
    signing_input = (header + "." + payload).encode()
    signature = rsa.sign(signing_input, privkey, "SHA-256")
    assertion = header + "." + payload + "." + _b64url(signature)

    client = http_client.get_client()
    resp = await client.post(
        _TOKEN_URL,
        headers=_IDENTITY,
        data={"grant_type": _GRANT, "assertion": assertion},
        timeout=30,
    )
    resp.raise_for_status()
    body = resp.json()
    token = body["access_token"]
    ttl = int(body.get("expires_in", 3600))
    _TOKEN_CACHE[email] = (token, now + ttl)
    return token
