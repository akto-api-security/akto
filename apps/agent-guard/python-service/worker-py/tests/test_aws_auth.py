"""aws_auth — SigV4 signing + IAM-role credential resolution, network mocked.

botocore can't load in the Worker runtime, which is why aws_auth exists; here
it serves as the oracle: for the same request, credentials and pinned clock,
both must produce the identical Authorization header. The role half covers
EKS Pod Identity / ECS (container endpoint) and IRSA (STS web identity).
"""

import hashlib
import time
from datetime import UTC, datetime

import pytest
from botocore import auth as bc_auth
from botocore.awsrequest import AWSRequest
from botocore.credentials import Credentials

import aws_auth

_NOW = datetime(2026, 9, 30, 12, 34, 56, tzinfo=UTC)
_REGION = "us-east-1"
_HOST = f"https://bedrock-runtime.{_REGION}.amazonaws.com"
_BODY = b'{"messages":[{"role":"user","content":[{"text":"hi"}]}]}'
_CREDS = aws_auth.AwsCredentials("AKIDEXAMPLE", "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY")


def _botocore_authorization(url: str, headers: dict[str, str], creds: aws_auth.AwsCredentials, monkeypatch) -> str:
    monkeypatch.setattr(bc_auth, "get_current_datetime", lambda: _NOW.replace(tzinfo=None))
    request = AWSRequest(method="POST", url=url, data=_BODY, headers=dict(headers))
    bc_creds = Credentials(creds.access_key_id, creds.secret_access_key, creds.session_token or None)
    bc_auth.SigV4Auth(bc_creds, "bedrock", _REGION).add_auth(request)
    return request.headers["Authorization"]


@pytest.mark.parametrize(
    "model_path",
    [
        "amazon.nova-micro-v1%3A0",
        "anthropic.claude-3-haiku-20240307-v1%3A0",
        "us.anthropic.claude-haiku-4-5-20251001-v1%3A0",
        "arn%3Aaws%3Abedrock%3Aus-east-1%3A123456789012%3Ainference-profile%2Fus.meta.llama3-v1%3A0",
    ],
)
@pytest.mark.parametrize(
    "creds", [_CREDS, aws_auth.AwsCredentials(_CREDS.access_key_id, _CREDS.secret_access_key, "TOKEN")]
)
def test_matches_botocore(model_path, creds, monkeypatch):
    url = f"{_HOST}/model/{model_path}/converse"
    base = {"Content-Type": "application/json"}
    ours = aws_auth.sign_headers("POST", url, base, _BODY, creds, _REGION, now=_NOW)
    # botocore signs every header on the request, so hand it exactly the set we
    # sign (content-type + x-amz-content-sha256); it adds host/date/token itself.
    reference = _botocore_authorization(
        url, {**base, "X-Amz-Content-Sha256": ours["X-Amz-Content-Sha256"]}, creds, monkeypatch
    )
    assert ours["Authorization"] == reference


def test_canonical_uri_double_encodes_path():
    assert (
        aws_auth._canonical_uri("/model/anthropic.claude-v1%3A0/converse")
        == "/model/anthropic.claude-v1%253A0/converse"
    )
    assert aws_auth._canonical_uri("") == "/"


def test_emits_payload_hash_date_and_optional_token():
    plain = aws_auth.sign_headers("POST", f"{_HOST}/model/m/converse", {}, _BODY, _CREDS, _REGION, now=_NOW)
    assert plain["X-Amz-Content-Sha256"] == hashlib.sha256(_BODY).hexdigest()
    assert plain["X-Amz-Date"] == "20260930T123456Z"
    assert "X-Amz-Security-Token" not in plain
    assert "Credential=AKIDEXAMPLE/20260930/us-east-1/bedrock/aws4_request" in plain["Authorization"]
    assert "SignedHeaders=host;x-amz-content-sha256;x-amz-date," in plain["Authorization"]

    token_creds = aws_auth.AwsCredentials("AKID", "secret", "TOKEN")
    signed = aws_auth.sign_headers("POST", f"{_HOST}/model/m/converse", {}, _BODY, token_creds, _REGION, now=_NOW)
    assert signed["X-Amz-Security-Token"] == "TOKEN"
    assert "x-amz-security-token" in signed["Authorization"]


def test_unsigned_headers_do_not_affect_signature():
    # Accept-Encoding/User-Agent may be rewritten by the fetch layer — they must stay out of the signature.
    url = f"{_HOST}/model/m/converse"
    a = aws_auth.sign_headers("POST", url, {"Content-Type": "application/json"}, _BODY, _CREDS, _REGION, now=_NOW)
    b = aws_auth.sign_headers(
        "POST",
        url,
        {"Content-Type": "application/json", "Accept-Encoding": "identity", "User-Agent": "x"},
        _BODY,
        _CREDS,
        _REGION,
        now=_NOW,
    )
    assert a["Authorization"] == b["Authorization"]


# ── IAM role credentials (EKS Pod Identity / IRSA / ECS) ─────────────────────

_ROLE_ENV = (
    "AWS_CONTAINER_CREDENTIALS_FULL_URI",
    "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI",
    "AWS_CONTAINER_AUTHORIZATION_TOKEN_FILE",
    "AWS_CONTAINER_AUTHORIZATION_TOKEN",
    "AWS_WEB_IDENTITY_TOKEN_FILE",
    "AWS_ROLE_ARN",
    "AWS_ROLE_SESSION_NAME",
    "AWS_REGION",
    "AWS_DEFAULT_REGION",
)
_LATER = "2099-01-01T00:00:00Z"
_STS_XML = """<AssumeRoleWithWebIdentityResponse xmlns="https://sts.amazonaws.com/doc/2011-06-15/">
  <AssumeRoleWithWebIdentityResult>
    <Credentials>
      <AccessKeyId>ASIAIRSA</AccessKeyId>
      <SecretAccessKey>irsa-secret</SecretAccessKey>
      <SessionToken>irsa-token</SessionToken>
      <Expiration>{expiration}</Expiration>
    </Credentials>
  </AssumeRoleWithWebIdentityResult>
</AssumeRoleWithWebIdentityResponse>"""


class _FakeResponse:
    def __init__(self, payload=None, text=""):
        self._payload = payload
        self.text = text

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


class _FakeClient:
    calls: list[dict] = []
    get_payload: dict = {}
    post_text = ""

    async def get(self, url, headers=None, timeout=None):
        _FakeClient.calls.append({"method": "GET", "url": url, "headers": headers})
        return _FakeResponse(payload=_FakeClient.get_payload)

    async def post(self, url, headers=None, data=None, timeout=None):
        _FakeClient.calls.append({"method": "POST", "url": url, "headers": headers, "data": data})
        return _FakeResponse(text=_FakeClient.post_text)


@pytest.fixture
def role_env(monkeypatch):
    for var in _ROLE_ENV:
        monkeypatch.delenv(var, raising=False)
    _FakeClient.calls = []
    _FakeClient.get_payload = {
        "AccessKeyId": "ASIAPOD",
        "SecretAccessKey": "pod-secret",
        "Token": "pod-token",
        "Expiration": _LATER,
    }
    _FakeClient.post_text = _STS_XML.format(expiration=_LATER)
    aws_auth._ROLE_CACHE.clear()
    monkeypatch.setattr(aws_auth.http_client, "get_client", lambda: _FakeClient())
    yield monkeypatch
    aws_auth._ROLE_CACHE.clear()


def test_role_source_detection(role_env):
    assert aws_auth.role_credentials_source() == ""
    role_env.setenv("AWS_ROLE_ARN", "arn:aws:iam::123456789012:role/agent-guard")
    assert aws_auth.role_credentials_source() == ""  # IRSA needs the token file too
    role_env.setenv("AWS_WEB_IDENTITY_TOKEN_FILE", "/var/run/secrets/eks.amazonaws.com/serviceaccount/token")
    assert aws_auth.role_credentials_source() == "web_identity"
    role_env.setenv("AWS_CONTAINER_CREDENTIALS_FULL_URI", "http://169.254.170.23/v1/credentials")
    assert aws_auth.role_credentials_source() == "container"


async def test_pod_identity_sends_token_file_and_caches(role_env, tmp_path):
    token = tmp_path / "eks-pod-identity-token"
    token.write_text("pod-jwt\n")
    role_env.setenv("AWS_CONTAINER_CREDENTIALS_FULL_URI", "http://169.254.170.23/v1/credentials")
    role_env.setenv("AWS_CONTAINER_AUTHORIZATION_TOKEN_FILE", str(token))

    creds = await aws_auth.get_role_credentials("us-east-1")
    assert creds == aws_auth.AwsCredentials("ASIAPOD", "pod-secret", "pod-token")
    (call,) = _FakeClient.calls
    assert call["url"] == "http://169.254.170.23/v1/credentials"
    assert call["headers"]["Authorization"] == "pod-jwt"

    assert await aws_auth.get_role_credentials("us-east-1") == creds
    assert len(_FakeClient.calls) == 1  # served from cache


async def test_refreshes_shortly_before_expiry(role_env):
    role_env.setenv("AWS_CONTAINER_CREDENTIALS_RELATIVE_URI", "/v2/credentials/abc")
    _FakeClient.get_payload["Expiration"] = datetime.fromtimestamp(time.time() + 120, UTC).isoformat()
    await aws_auth.get_role_credentials("us-east-1")
    await aws_auth.get_role_credentials("us-east-1")
    assert len(_FakeClient.calls) == 2  # inside the 5-min margin → refetched
    assert _FakeClient.calls[0]["url"] == "http://169.254.170.2/v2/credentials/abc"  # ECS task-role form


async def test_irsa_exchanges_web_identity_token_at_sts(role_env, tmp_path):
    token = tmp_path / "token"
    token.write_text("irsa-jwt")
    role_env.setenv("AWS_ROLE_ARN", "arn:aws:iam::123456789012:role/agent-guard")
    role_env.setenv("AWS_WEB_IDENTITY_TOKEN_FILE", str(token))
    role_env.setenv("AWS_REGION", "eu-west-1")

    creds = await aws_auth.get_role_credentials("us-east-1")
    assert creds == aws_auth.AwsCredentials("ASIAIRSA", "irsa-secret", "irsa-token")
    (call,) = _FakeClient.calls
    assert call["url"] == "https://sts.eu-west-1.amazonaws.com/"  # AWS_REGION beats the fallback
    assert call["data"]["Action"] == "AssumeRoleWithWebIdentity"
    assert call["data"]["RoleArn"] == "arn:aws:iam::123456789012:role/agent-guard"
    assert call["data"]["WebIdentityToken"] == "irsa-jwt"
    assert call["data"]["RoleSessionName"] == "akto-agent-guard"


async def test_irsa_falls_back_to_given_region(role_env, tmp_path):
    token = tmp_path / "token"
    token.write_text("irsa-jwt")
    role_env.setenv("AWS_ROLE_ARN", "arn:aws:iam::123456789012:role/agent-guard")
    role_env.setenv("AWS_WEB_IDENTITY_TOKEN_FILE", str(token))
    await aws_auth.get_role_credentials("ap-south-1")
    assert _FakeClient.calls[0]["url"] == "https://sts.ap-south-1.amazonaws.com/"


async def test_irsa_response_without_credentials_raises(role_env, tmp_path):
    token = tmp_path / "token"
    token.write_text("irsa-jwt")
    role_env.setenv("AWS_ROLE_ARN", "arn:aws:iam::123456789012:role/agent-guard")
    role_env.setenv("AWS_WEB_IDENTITY_TOKEN_FILE", str(token))
    _FakeClient.post_text = "<ErrorResponse/>"
    with pytest.raises(ValueError, match="no Credentials"):
        await aws_auth.get_role_credentials("us-east-1")


async def test_no_role_configured_raises(role_env):
    with pytest.raises(RuntimeError, match="no IAM role credentials"):
        await aws_auth.get_role_credentials("us-east-1")
