"""Azure AD (Entra ID) app-only tokens with a certificate credential.

The OAuth2 client-credentials grant with a signed client assertion, as MSAL's
ConfidentialClientApplication does it (see Morgan Stanley's
github.com/morganstanley/api client samples): RS256-sign a short-lived JWT
with the app's private key, `x5t` naming the registered certificate by its
SHA-1 thumbprint, and exchange it at the tenant's v2.0 token endpoint. Tokens
are cached per (tenant, client_id, scope) until shortly before expiry, so
signing and the token round-trip happen ~once per hour, not per request.
"""

import time
import uuid

import http_client
import metrics_push
from jwt_sign import b64url, load_private_key, sign_rs256

_ASSERTION_TYPE = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"
_ASSERTION_TTL_S = 600
_REFRESH_MARGIN_S = 300

# (tenant, client_id, scope) -> (access_token, absolute_expiry_epoch)
_TOKEN_CACHE: dict[tuple[str, str, str], tuple[str, float]] = {}


def token_url(tenant: str) -> str:
    return f"https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token"


def _x5t(thumbprint_hex: str) -> str:
    """Hex SHA-1 thumbprint (colons tolerated) -> base64url of the raw digest bytes."""
    return b64url(bytes.fromhex(thumbprint_hex.replace(":", "").strip()))


def invalidate(tenant: str, client_id: str, scope: str) -> None:
    """Drop the cached token, e.g. after the API rejected it with 401."""
    _TOKEN_CACHE.pop((tenant, client_id, scope), None)


async def get_token(tenant: str, client_id: str, scope: str, thumbprint_hex: str, private_key_pem: str) -> str:
    """Return a valid access token for `scope`, minting a new one when the cached one is near expiry."""
    key = (tenant, client_id, scope)
    now = time.time()
    cached = _TOKEN_CACHE.get(key)
    if cached and cached[1] - _REFRESH_MARGIN_S > now:
        metrics_push.COUNTS["cache_hits"].increment("azure_ad_token")
        return cached[0]
    metrics_push.COUNTS["cache_misses"].increment("azure_ad_token")

    url = token_url(tenant)
    iat = int(now)
    assertion = sign_rs256(
        {"alg": "RS256", "typ": "JWT", "x5t": _x5t(thumbprint_hex)},
        {
            "iss": client_id,
            "sub": client_id,
            "aud": url,
            "jti": str(uuid.uuid4()),
            "nbf": iat,
            "iat": iat,
            "exp": iat + _ASSERTION_TTL_S,
        },
        load_private_key(private_key_pem),
    )

    resp = await http_client.get_client().post(
        url,
        data={
            "grant_type": "client_credentials",
            "client_id": client_id,
            "scope": scope,
            "client_assertion_type": _ASSERTION_TYPE,
            "client_assertion": assertion,
        },
        timeout=30,
    )
    resp.raise_for_status()
    body = resp.json()
    token = body["access_token"]
    _TOKEN_CACHE[key] = (token, now + int(body.get("expires_in", 3600)))
    metrics_push.set_cache_size("azure_ad_token", len(_TOKEN_CACHE))
    return token
