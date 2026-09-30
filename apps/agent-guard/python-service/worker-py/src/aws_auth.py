"""AWS SigV4 request signing for Bedrock, validated for the Worker runtime.

botocore (and therefore boto3) cannot load in Pyodide/workerd, so we sign
requests ourselves with the stdlib `hmac` + `hashlib`, following the SigV4
spec: canonical request -> string-to-sign -> HMAC-SHA256 with a key derived
from the secret, date, region and service.

Only a minimal header set is signed (host, content-type, x-amz-*). Anything
the fetch layer might rewrite in flight (Accept-Encoding, User-Agent) stays
unsigned, so the runtime touching it can't invalidate the signature.

It also resolves IAM-role credentials on EKS without any configured keys
(get_role_credentials): EKS Pod Identity and ECS task roles via the container
credentials endpoint, IRSA via STS AssumeRoleWithWebIdentity. Both are driven
by the standard AWS_* env vars EKS injects into the pod, read from os.environ
like every AWS SDK does (not settings — these aren't ours to name). Resolved
credentials are cached until shortly before expiry, like gcp_auth tokens.
"""

import hashlib
import hmac
import logging
import os
import time
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from datetime import UTC, datetime
from urllib.parse import parse_qsl, quote, urlsplit

import http_client
import metrics_push

logger = logging.getLogger(__name__)

_ALGORITHM = "AWS4-HMAC-SHA256"
_SIGNED_HEADER_NAMES = ("content-type",)
_IDENTITY = {"Accept-Encoding": "identity"}

# ECS task-role endpoint host; AWS_CONTAINER_CREDENTIALS_RELATIVE_URI is a path on it.
_ECS_CREDENTIALS_HOST = "http://169.254.170.2"
_STS_NS = {"sts": "https://sts.amazonaws.com/doc/2011-06-15/"}
_DEFAULT_SESSION_NAME = "akto-agent-guard"
_REFRESH_MARGIN_S = 300

# One role per pod, so a single cache slot: (credentials, absolute_expiry_epoch).
_ROLE_CACHE: dict[str, tuple["AwsCredentials", float]] = {}
_ROLE_CACHE_KEY = "role"


@dataclass(frozen=True)
class AwsCredentials:
    access_key_id: str
    secret_access_key: str
    session_token: str = ""


# ── IAM role credentials (EKS Pod Identity / IRSA / ECS) ─────────────────────


def role_credentials_source() -> str:
    """Which ambient role mechanism this process can use, or "" for none."""
    if os.environ.get("AWS_CONTAINER_CREDENTIALS_FULL_URI") or os.environ.get("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI"):
        return "container"
    if os.environ.get("AWS_WEB_IDENTITY_TOKEN_FILE") and os.environ.get("AWS_ROLE_ARN"):
        return "web_identity"
    return ""


def _read_file(path: str) -> str:
    with open(path) as f:
        return f.read().strip()


def _epoch(iso_timestamp: str) -> float:
    return datetime.fromisoformat(iso_timestamp.replace("Z", "+00:00")).timestamp()


async def _fetch_container_credentials() -> tuple[AwsCredentials, float]:
    """EKS Pod Identity (FULL_URI + token file) or ECS task role (RELATIVE_URI)."""
    url = os.environ.get("AWS_CONTAINER_CREDENTIALS_FULL_URI") or (
        _ECS_CREDENTIALS_HOST + os.environ.get("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI", "")
    )
    headers = dict(_IDENTITY)
    # Pod Identity rotates the token file, so it is re-read on every refresh.
    token_file = os.environ.get("AWS_CONTAINER_AUTHORIZATION_TOKEN_FILE")
    token = _read_file(token_file) if token_file else os.environ.get("AWS_CONTAINER_AUTHORIZATION_TOKEN", "")
    if token:
        headers["Authorization"] = token
    resp = await http_client.get_client().get(url, headers=headers, timeout=10)
    resp.raise_for_status()
    body = resp.json()
    creds = AwsCredentials(body["AccessKeyId"], body["SecretAccessKey"], body.get("Token", ""))
    return creds, _epoch(body["Expiration"])


async def _fetch_web_identity_credentials(region: str) -> tuple[AwsCredentials, float]:
    """IRSA: exchange the projected service-account token at STS. The call is
    unsigned — the web-identity token is the proof of identity."""
    sts_region = os.environ.get("AWS_REGION") or os.environ.get("AWS_DEFAULT_REGION") or region
    resp = await http_client.get_client().post(
        f"https://sts.{sts_region}.amazonaws.com/",
        headers=_IDENTITY,
        data={
            "Action": "AssumeRoleWithWebIdentity",
            "Version": "2011-06-15",
            "RoleArn": os.environ["AWS_ROLE_ARN"],
            "RoleSessionName": os.environ.get("AWS_ROLE_SESSION_NAME") or _DEFAULT_SESSION_NAME,
            "WebIdentityToken": _read_file(os.environ["AWS_WEB_IDENTITY_TOKEN_FILE"]),
        },
        timeout=10,
    )
    resp.raise_for_status()
    node = ET.fromstring(resp.text).find(".//sts:Credentials", _STS_NS)
    if node is None:
        raise ValueError(f"AssumeRoleWithWebIdentity response has no Credentials: {resp.text[:300]!r}")

    def field(name: str) -> str:
        return node.findtext(f"sts:{name}", default="", namespaces=_STS_NS)

    creds = AwsCredentials(field("AccessKeyId"), field("SecretAccessKey"), field("SessionToken"))
    return creds, _epoch(field("Expiration"))


async def get_role_credentials(region: str) -> AwsCredentials:
    """Return valid credentials for the pod's IAM role, refreshing ~5 min early.

    `region` is the fallback STS region for IRSA when EKS didn't inject
    AWS_REGION. Raises if no role mechanism is configured or the fetch fails.
    """
    cached = _ROLE_CACHE.get(_ROLE_CACHE_KEY)
    if cached and cached[1] - _REFRESH_MARGIN_S > time.time():
        metrics_push.COUNTS["cache_hits"].increment("aws_role_creds")
        return cached[0]
    metrics_push.COUNTS["cache_misses"].increment("aws_role_creds")

    source = role_credentials_source()
    try:
        if source == "container":
            creds, expiry = await _fetch_container_credentials()
        elif source == "web_identity":
            creds, expiry = await _fetch_web_identity_credentials(region)
        else:
            raise RuntimeError("no IAM role credentials in the environment (EKS Pod Identity / IRSA not configured)")
    except Exception as exc:
        logger.error(f"[AwsAuth] fetching IAM role credentials via {source or '-'} failed: {exc!r}")
        raise
    _ROLE_CACHE[_ROLE_CACHE_KEY] = (creds, expiry)
    metrics_push.set_cache_size("aws_role_creds", len(_ROLE_CACHE))
    logger.info(f"[AwsAuth] IAM role credentials refreshed via {source}, expire in {expiry - time.time():.0f}s")
    return creds


# ── SigV4 signing ─────────────────────────────────────────────────────────────


def _sha256_hex(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def _hmac(key: bytes, msg: str) -> bytes:
    return hmac.new(key, msg.encode(), hashlib.sha256).digest()


def _signing_key(secret: str, datestamp: str, region: str, service: str) -> bytes:
    k_date = _hmac(("AWS4" + secret).encode(), datestamp)
    k_region = _hmac(k_date, region)
    k_service = _hmac(k_region, service)
    return _hmac(k_service, "aws4_request")


def _canonical_uri(path: str) -> str:
    """URI-encode the (already percent-encoded) request path a second time.

    For every service except S3, SigV4 double-encodes path segments: a Bedrock
    model id like "anthropic.claude-3-haiku-20240307-v1:0" travels as "%3A" in
    the URL and must appear as "%253A" in the canonical request, otherwise AWS
    answers SignatureDoesNotMatch. Inference-profile ARNs (":" and "/") too.
    """
    return quote(path or "/", safe="/~")


def _canonical_query(query: str) -> str:
    pairs = sorted(parse_qsl(query, keep_blank_values=True))
    return "&".join(f"{quote(k, safe='-_.~')}={quote(v, safe='-_.~')}" for k, v in pairs)


def sign_headers(
    method: str,
    url: str,
    headers: dict[str, str],
    body: bytes,
    creds: AwsCredentials,
    region: str,
    service: str = "bedrock",
    now: datetime | None = None,
) -> dict[str, str]:
    """Return the SigV4 headers to merge into `headers` for this exact request.

    `body` must be the exact bytes that will be sent — the signature covers
    their SHA-256. `now` is injectable for deterministic tests.
    """
    ts = (now or datetime.now(UTC)).astimezone(UTC)
    amz_date = ts.strftime("%Y%m%dT%H%M%SZ")
    datestamp = ts.strftime("%Y%m%d")
    payload_hash = _sha256_hex(body)
    parts = urlsplit(url)

    out = {"X-Amz-Date": amz_date, "X-Amz-Content-Sha256": payload_hash}
    if creds.session_token:
        out["X-Amz-Security-Token"] = creds.session_token

    lowered = {k.lower(): str(v).strip() for k, v in headers.items()}
    to_sign = {"host": parts.netloc}
    to_sign.update({k: lowered[k] for k in _SIGNED_HEADER_NAMES if k in lowered})
    to_sign.update({k.lower(): v for k, v in out.items()})
    signed_headers = ";".join(sorted(to_sign))
    canonical_headers = "".join(f"{k}:{to_sign[k]}\n" for k in sorted(to_sign))

    canonical_request = "\n".join(
        [
            method.upper(),
            _canonical_uri(parts.path),
            _canonical_query(parts.query),
            canonical_headers,
            signed_headers,
            payload_hash,
        ]
    )
    scope = f"{datestamp}/{region}/{service}/aws4_request"
    string_to_sign = "\n".join([_ALGORITHM, amz_date, scope, _sha256_hex(canonical_request.encode())])
    signature = hmac.new(
        _signing_key(creds.secret_access_key, datestamp, region, service), string_to_sign.encode(), hashlib.sha256
    ).hexdigest()

    out["Authorization"] = (
        f"{_ALGORITHM} Credential={creds.access_key_id}/{scope}, SignedHeaders={signed_headers}, Signature={signature}"
    )
    return out
