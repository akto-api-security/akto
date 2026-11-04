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

import http_client
import metrics_push
from jwt_sign import load_private_key, sign_rs256

_TOKEN_URL = "https://oauth2.googleapis.com/token"
_SCOPE = "https://www.googleapis.com/auth/cloud-platform"
_GRANT = "urn:ietf:params:oauth:grant-type:jwt-bearer"
_IDENTITY = {"Accept-Encoding": "identity"}

# client_email -> (access_token, absolute_expiry_epoch)
_TOKEN_CACHE: dict[str, tuple[str, float]] = {}


def sa_info_from_b64(sa_key_b64: str) -> dict:
    """Decode a base64-encoded service-account-key JSON into a dict."""
    return json.loads(base64.b64decode(sa_key_b64))


async def get_token(sa_info: dict) -> str:
    """Return a valid cloud-platform access token for this service account."""
    email = sa_info["client_email"]
    now = time.time()
    cached = _TOKEN_CACHE.get(email)
    if cached and cached[1] - 300 > now:
        metrics_push.COUNTS["cache_hits"].increment("gcp_token")
        return cached[0]
    metrics_push.COUNTS["cache_misses"].increment("gcp_token")

    iat = int(now)
    assertion = sign_rs256(
        {"alg": "RS256", "typ": "JWT"},
        {"iss": email, "scope": _SCOPE, "aud": _TOKEN_URL, "iat": iat, "exp": iat + 3600},
        load_private_key(sa_info["private_key"]),
    )

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
    metrics_push.set_cache_size("gcp_token", len(_TOKEN_CACHE))
    return token
