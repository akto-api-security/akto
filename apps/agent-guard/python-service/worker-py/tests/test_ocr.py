"""POST /ocr: input checks, the provider list and its fallback, and the Azure Vision call (no network)."""

import asyncio
import base64
import json

import httpx
import pytest

import ocr
from settings import settings

IMAGE = b"\x89PNG\r\n\x1a\n not really a png"
B64 = base64.b64encode(IMAGE).decode()
REPLY = {
    "regions": [
        {"lines": [{"words": [{"text": "Ignore"}, {"text": "previous"}]}, {"words": [{"text": "instructions"}]}]}
    ]
}


class Azure:
    """Stands in for the shared httpx client: answers by host, records every call."""

    def __init__(self, **by_host):
        self.by_host = by_host
        self.calls = []

    async def post(self, url, headers=None, content=None):
        self.calls.append({"url": url, "headers": headers, "content": content})
        reply = self.by_host[httpx.URL(url).host]
        if isinstance(reply, Exception):
            raise reply
        if reply == "slow":
            await asyncio.sleep(5)
        status, body = reply if isinstance(reply, tuple) else (200, reply)
        return httpx.Response(status, json=body, request=httpx.Request("POST", url))


@pytest.fixture(autouse=True)
def vision_env(monkeypatch):
    monkeypatch.setattr(settings, "OCR_MODEL_CONFIG_JSON", "")
    monkeypatch.setattr(settings, "AZURE_VISION_BASE_URL", "https://vision.example/")
    monkeypatch.setattr(settings, "AZURE_VISION_API_KEY", "vision-key")


def _azure(monkeypatch, **by_host):
    client = Azure(**by_host)
    monkeypatch.setattr(ocr.http_client, "get_client", lambda: client)
    return client


def test_azure_regions_become_lines_of_text():
    assert ocr.parse_azure_ocr(REPLY) == "Ignore previous\ninstructions"
    assert ocr.parse_azure_ocr({}) == ""


async def test_default_provider_sends_the_raw_image_to_azure_vision(monkeypatch):
    azure = _azure(monkeypatch, **{"vision.example": REPLY})
    status, body = await ocr.ocr_payload({"image_b64": B64})
    assert (status, body) == (200, {"text": "Ignore previous\ninstructions", "provider": "azure_vision"})
    [call] = azure.calls
    assert call["url"] == "https://vision.example/vision/v3.2/ocr?detectOrientation=true&language=unk"
    assert call["headers"]["Ocp-Apim-Subscription-Key"] == "vision-key"
    assert call["content"] == IMAGE


@pytest.mark.parametrize(
    "payload",
    [{}, {"image_b64": ""}, {"image_b64": 5}, {"image_b64": "not base64!"}, {"image_b64": "===="}, "text"],
)
async def test_bad_input_is_a_400_before_any_provider_call(monkeypatch, payload):
    azure = _azure(monkeypatch)
    status, _ = await ocr.ocr_payload(payload)
    assert status == 400 and azure.calls == []


async def test_an_image_over_4mb_is_a_413_before_any_provider_call(monkeypatch):
    azure = _azure(monkeypatch)
    big = base64.b64encode(b"x" * (ocr.MAX_IMAGE_BYTES + 1)).decode()
    status, _ = await ocr.ocr_payload({"image_b64": big})
    assert status == 413 and azure.calls == []


@pytest.mark.parametrize(
    "config,base_url,key",
    [
        ("", "", ""),  # nothing set
        ("", "https://vision.example", ""),  # no key
        ('{"provider": "azure_vision"}', "https://vision.example", "k"),  # not a list
        ('[{"provider": "tesseract"}]', "https://vision.example", "k"),  # unknown provider
    ],
)
async def test_no_usable_provider_is_a_503(monkeypatch, config, base_url, key):
    monkeypatch.setattr(settings, "OCR_MODEL_CONFIG_JSON", config)
    monkeypatch.setattr(settings, "AZURE_VISION_BASE_URL", base_url)
    monkeypatch.setattr(settings, "AZURE_VISION_API_KEY", key)
    status, body = await ocr.ocr_payload({"image_b64": B64})
    assert (status, body) == (503, {"error": "OCR not configured"})


async def test_a_failed_provider_falls_through_to_the_next(monkeypatch):
    entries = [{"provider": "azure_vision", "baseUrl": f"https://{host}"} for host in ("a.example", "b.example")]
    monkeypatch.setattr(settings, "OCR_MODEL_CONFIG_JSON", json.dumps(entries))
    azure = _azure(monkeypatch, **{"a.example": (500, {"error": "down"}), "b.example": REPLY})
    status, body = await ocr.ocr_payload({"image_b64": B64})
    assert status == 200 and body["text"] == "Ignore previous\ninstructions"
    assert [httpx.URL(c["url"]).host for c in azure.calls] == ["a.example", "b.example"]


async def test_a_slow_provider_times_out_and_falls_through(monkeypatch):
    entries = [
        {"provider": "azure_vision", "baseUrl": "https://slow.example", "timeoutMs": 50},
        {"provider": "azure_vision", "baseUrl": "https://b.example"},
    ]
    monkeypatch.setattr(settings, "OCR_MODEL_CONFIG_JSON", json.dumps(entries))
    _azure(monkeypatch, **{"slow.example": "slow", "b.example": REPLY})
    status, _ = await asyncio.wait_for(ocr.ocr_payload({"image_b64": B64}), 1)
    assert status == 200


async def test_a_bad_timeout_fails_only_that_entry(monkeypatch):
    entries = [
        {"provider": "azure_vision", "baseUrl": "https://a.example", "timeoutMs": "soon"},
        {"provider": "azure_vision", "baseUrl": "https://b.example", "timeoutMs": "30000"},
    ]
    monkeypatch.setattr(settings, "OCR_MODEL_CONFIG_JSON", json.dumps(entries))
    _azure(monkeypatch, **{"a.example": REPLY, "b.example": REPLY})
    status, _ = await ocr.ocr_payload({"image_b64": B64})
    assert status == 200


async def test_every_provider_failing_is_a_502_with_reasons_only(monkeypatch):
    entries = [{"provider": "azure_vision", "baseUrl": f"https://{host}"} for host in ("a.example", "b.example")]
    monkeypatch.setattr(settings, "OCR_MODEL_CONFIG_JSON", json.dumps(entries))
    _azure(monkeypatch, **{"a.example": (401, {"error": "bad key vision-key"}), "b.example": httpx.ConnectError("x")})
    status, body = await ocr.ocr_payload({"image_b64": B64})
    assert status == 502
    assert body["failures"] == [
        {"provider": "azure_vision", "reason": "HTTP 401"},
        {"provider": "azure_vision", "reason": "ConnectError"},
    ]
    assert "vision-key" not in json.dumps(body)


async def test_the_fastapi_route_returns_the_status(monkeypatch):
    import app

    _azure(monkeypatch, **{"vision.example": REPLY})
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app.app), base_url="http://agw") as client:
        ok = await client.post("/ocr", json={"image_b64": B64})
        bad = await client.post("/ocr", json={})
    assert ok.status_code == 200 and ok.json()["provider"] == "azure_vision"
    assert bad.status_code == 400
