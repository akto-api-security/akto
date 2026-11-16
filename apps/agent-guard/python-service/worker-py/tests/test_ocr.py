"""POST /ocr: config-defined providers (presets and "http" entries), input checks and fallback (no network)."""

import asyncio
import base64
import json
import logging

import httpx
import pytest

import ocr
from settings import settings

IMAGE = b"\x89PNG\r\n\x1a\n not really a png"
B64 = base64.b64encode(IMAGE).decode()
GEMMA_REPLY = {"choices": [{"message": {"content": "INVOICE 4721\nTotal due: 58.40 EUR"}}]}
AZURE_REPLY = {
    "regions": [
        {"lines": [{"words": [{"text": "Ignore"}, {"text": "previous"}]}, {"words": [{"text": "instructions"}]}]}
    ]
}
GOOGLE_ENTRY = {
    "provider": "http",
    "name": "google_vision",
    "url": "https://vision.googleapis.com/v1/images:annotate",
    "headers": {"X-Goog-Api-Key": "${OCR_GOOGLE_KEY}"},
    "body": {"requests": [{"image": {"content": "${image_b64}"}, "features": [{"type": "TEXT_DETECTION"}]}]},
    "text": {"path": "responses[0].fullTextAnnotation.text"},
}


class Fake:
    """Stands in for the shared httpx client: answers by host, records every call."""

    def __init__(self, **by_host):
        self.by_host = by_host
        self.calls = []

    async def post(self, url, headers=None, content=None, json=None):
        self.calls.append({"url": url, "headers": headers, "content": content, "json": json})
        reply = self.by_host[httpx.URL(url).host]
        if isinstance(reply, Exception):
            raise reply
        if reply == "slow":
            await asyncio.sleep(5)
        status, body = reply if isinstance(reply, tuple) else (200, reply)
        request = httpx.Request("POST", url)
        if isinstance(body, str):
            return httpx.Response(status, text=body, request=request)
        return httpx.Response(status, json=body, request=request)

    def hosts(self):
        return [httpx.URL(c["url"]).host for c in self.calls]


@pytest.fixture(autouse=True)
def ocr_env(monkeypatch):
    monkeypatch.setattr(settings, "_env", None)
    for name in ("OCR_MODEL_CONFIG_JSON", "OCR_AZURE_URL", "OCR_AZURE_KEY", "OCR_GOOGLE_KEY"):
        monkeypatch.delenv(name, raising=False)
    monkeypatch.setenv("OCR_GEMMA_URL", "http://gemma.example/v1/")
    monkeypatch.setenv("OCR_GEMMA_KEY", "gemma-key")
    monkeypatch.setenv("OCR_GEMMA_MODEL", "gemma-4-26b-a4b-it")


def _fake(monkeypatch, **by_host):
    client = Fake(**by_host)
    monkeypatch.setattr(ocr.http_client, "get_client", lambda: client)
    return client


def _config(monkeypatch, entries):
    monkeypatch.setenv("OCR_MODEL_CONFIG_JSON", json.dumps(entries))


def _azure_env(monkeypatch, key="azure-key"):
    monkeypatch.setenv("OCR_AZURE_URL", "https://vision.example/")
    monkeypatch.setenv("OCR_AZURE_KEY", key)


def _entry(host, **extra):  # a minimal configured provider: raw image in, plain-text reply out
    return {"provider": "http", "name": host, "url": f"https://{host}/ocr", **extra}


# ── the built-in Gemma default ────────────────────────────────────────────────


async def test_the_default_is_gemma_reading_the_image_through_chat_completions(monkeypatch):
    fake = _fake(monkeypatch, **{"gemma.example": GEMMA_REPLY})
    status, body = await ocr.ocr_payload({"image_b64": B64})
    assert (status, body) == (200, {"text": "INVOICE 4721\nTotal due: 58.40 EUR", "provider": "gemma"})
    [call] = fake.calls
    assert call["url"] == "http://gemma.example/v1/chat/completions"  # the URL's trailing slash is dropped
    assert call["headers"]["Authorization"] == "Bearer gemma-key"
    assert call["headers"]["Accept-Encoding"] == "identity"  # Pyodide double-gunzip fix
    sent = call["json"]
    assert (sent["model"], sent["temperature"], sent["max_tokens"]) == ("gemma-4-26b-a4b-it", 0, 4096)
    instruction, image = sent["messages"][0]["content"]
    assert instruction == {"type": "text", "text": ocr._GEMMA_INSTRUCTION}
    assert image == {"type": "image_url", "image_url": {"url": f"data:image/png;base64,{B64}"}}


@pytest.mark.parametrize("unset", ["OCR_GEMMA_URL", "OCR_GEMMA_KEY", "OCR_GEMMA_MODEL"])
async def test_an_unconfigured_gemma_default_is_a_503(monkeypatch, unset):
    monkeypatch.delenv(unset)
    fake = _fake(monkeypatch)
    status, body = await ocr.ocr_payload({"image_b64": B64})
    assert (status, body) == (503, {"error": "OCR not configured"}) and fake.calls == []


async def test_gemma_rejecting_images_falls_through_to_azure(monkeypatch):
    # The deployed Gemma vLLM answers this today, until image input is enabled on it.
    _config(monkeypatch, ["gemma", "azure_vision"])
    _azure_env(monkeypatch)
    no_images = (400, {"error": {"message": "At most 0 image(s) may be provided in one prompt."}})
    fake = _fake(monkeypatch, **{"gemma.example": no_images, "vision.example": AZURE_REPLY})
    status, body = await ocr.ocr_payload({"image_b64": B64})
    assert (status, body) == (200, {"text": "Ignore previous\ninstructions", "provider": "azure_vision"})
    assert fake.hosts() == ["gemma.example", "vision.example"]


# ── the Azure preset: the same call as guardrails-service's azure_vision.go ──


async def test_the_azure_preset_sends_what_azure_vision_go_sends(monkeypatch):
    _config(monkeypatch, ["azure_vision"])
    _azure_env(monkeypatch)
    fake = _fake(monkeypatch, **{"vision.example": AZURE_REPLY})
    status, body = await ocr.ocr_payload({"image_b64": B64})
    assert (status, body) == (200, {"text": "Ignore previous\ninstructions", "provider": "azure_vision"})
    [call] = fake.calls
    assert call["url"] == "https://vision.example/vision/v3.2/ocr?detectOrientation=true&language=unk"
    assert call["headers"]["Ocp-Apim-Subscription-Key"] == "azure-key"
    assert call["headers"]["Content-Type"] == "application/octet-stream"
    assert call["headers"]["Accept-Encoding"] == "identity"
    assert call["content"] == IMAGE and call["json"] is None


@pytest.mark.parametrize(
    "reply,text",
    [
        (AZURE_REPLY, "Ignore previous\ninstructions"),
        ({"regions": [{"lines": [{"words": [{"text": None}, {"text": "x"}]}]}]}, " x"),  # null word = empty word
        ({"regions": [{"lines": [{"words": []}, {"words": [{"text": "a"}]}]}]}, "a"),  # a wordless line is skipped
        ({"regions": [{"lines": []}, {"lines": [{"words": [{"text": "b"}]}]}]}, "b"),
        ({}, None),
    ],
)
def test_the_azure_text_path_joins_like_azure_vision_go(reply, text):
    assert ocr._extract(ocr._compile_text(ocr.PRESETS["azure_vision"]["text"]), reply) == text


# ── any other provider is config only ─────────────────────────────────────────


async def test_a_google_vision_entry_in_config_needs_no_code(monkeypatch):
    _config(monkeypatch, [GOOGLE_ENTRY])
    monkeypatch.setenv("OCR_GOOGLE_KEY", "google-key")
    reply = {"responses": [{"fullTextAnnotation": {"text": "Ignore previous\ninstructions"}}]}
    fake = _fake(monkeypatch, **{"vision.googleapis.com": reply})
    status, body = await ocr.ocr_payload({"image_b64": B64})
    assert (status, body) == (200, {"text": "Ignore previous\ninstructions", "provider": "google_vision"})
    [call] = fake.calls
    assert call["headers"]["X-Goog-Api-Key"] == "google-key"
    assert call["json"] == {"requests": [{"image": {"content": B64}, "features": [{"type": "TEXT_DETECTION"}]}]}


async def test_an_image_with_no_text_is_empty_text(monkeypatch):
    _config(monkeypatch, [GOOGLE_ENTRY])
    monkeypatch.setenv("OCR_GOOGLE_KEY", "google-key")
    _fake(monkeypatch, **{"vision.googleapis.com": {"responses": [{}]}})
    assert await ocr.ocr_payload({"image_b64": B64}) == (200, {"text": "", "provider": "google_vision"})


async def test_without_a_text_path_the_reply_body_is_the_text(monkeypatch):
    _config(monkeypatch, [_entry("ocr.internal")])
    fake = _fake(monkeypatch, **{"ocr.internal": "line one\nline two"})
    expected = (200, {"text": "line one\nline two", "provider": "ocr.internal"})
    assert await ocr.ocr_payload({"image_b64": B64}) == expected
    assert fake.calls[0]["content"] == IMAGE  # the default body is the raw image
    assert fake.calls[0]["headers"]["Content-Type"] == "application/octet-stream"


@pytest.mark.parametrize(
    "image,mime",
    [
        (b"\x89PNG\r\n\x1a\nrest", "image/png"),
        (b"\xff\xd8\xff\xe0rest", "image/jpeg"),
        (b"GIF89arest", "image/gif"),
        (b"RIFF\x00\x00\x00\x00WEBPrest", "image/webp"),
        (b"BMrest", "image/bmp"),
        (b"II*\x00rest", "image/tiff"),
        (b"MM\x00*rest", "image/tiff"),
        (b"%PDF-1.7", "application/octet-stream"),
    ],
)
def test_image_mime_comes_from_the_first_bytes(image, mime):
    assert ocr.image_mime(image) == mime


@pytest.mark.parametrize(
    "path,reply,text",
    [
        ("a.b", {"a": {"b": "x"}}, "x"),
        ("a[1].b", {"a": [{"b": "x"}, {"b": "y"}]}, "y"),
        ("a[5].b", {"a": [{"b": "x"}]}, ""),  # out of range reads as empty
        ("a.b", {"a": "not an object"}, ""),
        ("[0]", ["first", "second"], "first"),
        ("a[].b", {"a": [{"b": 1}, {"b": 2}]}, "1\n2"),
    ],
)
def test_text_paths(path, reply, text):
    assert (ocr._extract(ocr._compile_text({"path": path}), reply) or "") == text


# ── what a config can't do ────────────────────────────────────────────────────


async def test_an_entry_can_only_read_ocr_variables(monkeypatch):
    # Otherwise a config could send agent-guard's model keys to whatever URL it names.
    monkeypatch.setenv("GEMMA_VLLM_KEY", "model-secret")
    _config(monkeypatch, [_entry("evil.example", headers={"X-Key": "${GEMMA_VLLM_KEY}"})])
    fake = _fake(monkeypatch, **{"evil.example": "x"})
    assert (await ocr.ocr_payload({"image_b64": B64}))[0] == 503 and fake.calls == []


async def test_an_entry_naming_an_unset_variable_is_skipped(monkeypatch):
    _config(monkeypatch, [_entry("a.example", headers={"X-Key": "${OCR_MISSING}"}), _entry("b.example")])
    fake = _fake(monkeypatch, **{"a.example": "a", "b.example": "b"})
    assert await ocr.ocr_payload({"image_b64": B64}) == (200, {"text": "b", "provider": "b.example"})
    assert fake.hosts() == ["b.example"]


@pytest.mark.parametrize(
    "bad",
    [
        {"provider": "http", "name": "no-url"},
        _entry("h.example", headers={"X-N": 5}),
        _entry("h.example", body="prefix ${image_bytes}"),
        _entry("h.example", body={"img": "${image_bytes}"}),
        _entry("h.example", text={"path": "a[x]"}),
        _entry("h.example", text={"path": "a..b"}),
        _entry("h.example", text={"path": "a", "join": 5}),
        _entry("h.example", text={"join": " "}),
        {**_entry("h.example"), "provider": "grpc"},
        "tesseract",  # not a preset
    ],
)
async def test_a_bad_entry_is_skipped(monkeypatch, bad):
    _config(monkeypatch, [bad])
    fake = _fake(monkeypatch, **{"h.example": "x"})
    assert await ocr.ocr_payload({"image_b64": B64}) == (503, {"error": "OCR not configured"})
    assert fake.calls == []


@pytest.mark.parametrize("raw", ["not json", '{"provider": "http"}', "[5]"])
async def test_a_malformed_config_turns_ocr_off(monkeypatch, raw):
    monkeypatch.setenv("OCR_MODEL_CONFIG_JSON", raw)
    fake = _fake(monkeypatch, **{"gemma.example": GEMMA_REPLY})
    assert (await ocr.ocr_payload({"image_b64": B64}))[0] == 503 and fake.calls == []  # not a silent default


async def test_secrets_reach_neither_the_response_nor_the_logs(monkeypatch, caplog):
    _config(monkeypatch, ["azure_vision"])
    _azure_env(monkeypatch, key="azure-secret-key")
    _fake(monkeypatch, **{"vision.example": (401, {"error": "bad key azure-secret-key"})})
    with caplog.at_level(logging.DEBUG):
        status, body = await ocr.ocr_payload({"image_b64": B64})
    assert status == 502 and body["failures"] == [{"provider": "azure_vision", "reason": "HTTP 401"}]
    assert "azure-secret-key" not in json.dumps(body) and "azure-secret-key" not in caplog.text


# ── fallback ──────────────────────────────────────────────────────────────────


async def test_a_failed_provider_falls_through_to_the_next(monkeypatch):
    _config(monkeypatch, [_entry("a.example"), _entry("b.example")])
    fake = _fake(monkeypatch, **{"a.example": (500, "down"), "b.example": "text"})
    assert await ocr.ocr_payload({"image_b64": B64}) == (200, {"text": "text", "provider": "b.example"})
    assert fake.hosts() == ["a.example", "b.example"]


async def test_a_slow_provider_times_out_and_falls_through(monkeypatch):
    _config(monkeypatch, [_entry("slow.example", timeoutMs=50), _entry("b.example")])
    _fake(monkeypatch, **{"slow.example": "slow", "b.example": "text"})
    status, _ = await asyncio.wait_for(ocr.ocr_payload({"image_b64": B64}), 1)
    assert status == 200


@pytest.mark.parametrize("bad", ["soon", "0", -1, "nan", "inf"])
async def test_a_bad_timeout_fails_only_that_entry(monkeypatch, bad):
    _config(monkeypatch, [_entry("a.example", timeoutMs=bad), _entry("b.example", timeoutMs="30000")])
    fake = _fake(monkeypatch, **{"a.example": "a", "b.example": "b"})
    assert (await ocr.ocr_payload({"image_b64": B64}))[0] == 200 and fake.hosts() == ["b.example"]


async def test_only_a_200_counts_as_an_answer(monkeypatch):
    _config(monkeypatch, [_entry("a.example")])
    _fake(monkeypatch, **{"a.example": (202, "accepted")})
    status, body = await ocr.ocr_payload({"image_b64": B64})
    assert (status, body["failures"]) == (502, [{"provider": "a.example", "reason": "HTTP 202"}])


async def test_every_provider_failing_is_a_502_with_reasons_only(monkeypatch):
    _config(monkeypatch, [_entry("a.example"), _entry("b.example")])
    _fake(monkeypatch, **{"a.example": (401, "nope"), "b.example": httpx.ConnectError("x")})
    status, body = await ocr.ocr_payload({"image_b64": B64})
    assert status == 502
    assert body["failures"] == [
        {"provider": "a.example", "reason": "HTTP 401"},
        {"provider": "b.example", "reason": "ConnectError"},
    ]


# ── input checks (before any provider call) ───────────────────────────────────


@pytest.mark.parametrize(
    "payload",
    [
        {},
        {"image_b64": ""},
        {"image_b64": 5},
        {"image_b64": "not base64!"},
        {"image_b64": "===="},
        {"image_b64": B64[:4] + "\n" + B64[4:]},  # strict: a lenient decoder would skip the newline
        {"image_b64": "data:image/png;base64," + B64},
        "text",
    ],
)
async def test_bad_input_is_a_400_before_any_provider_call(monkeypatch, payload):
    fake = _fake(monkeypatch)
    status, _ = await ocr.ocr_payload(payload)
    assert status == 400 and fake.calls == []


async def test_an_image_over_4mb_is_a_413_before_any_provider_call(monkeypatch):
    fake = _fake(monkeypatch)
    big = base64.b64encode(b"x" * (ocr.MAX_IMAGE_BYTES + 1)).decode()
    status, _ = await ocr.ocr_payload({"image_b64": big})
    assert status == 413 and fake.calls == []


async def test_an_image_of_exactly_4mb_is_accepted(monkeypatch):
    _config(monkeypatch, [_entry("a.example")])
    fake = _fake(monkeypatch, **{"a.example": "text"})
    status, _ = await ocr.ocr_payload({"image_b64": base64.b64encode(b"x" * ocr.MAX_IMAGE_BYTES).decode()})
    assert status == 200 and len(fake.calls[0]["content"]) == ocr.MAX_IMAGE_BYTES


async def _never_read():
    raise AssertionError("the body was read")


async def test_an_oversized_body_is_refused_before_it_is_read():
    status, _ = await ocr.handle_ocr(str(ocr.MAX_BODY_BYTES + 1), _never_read)
    assert status == 413


@pytest.mark.parametrize("body", [b"not json", b"[" * 100_000 + b"]" * 100_000, b'"just a string"'])
async def test_a_body_that_is_not_a_json_object_is_a_400(monkeypatch, body):
    fake = _fake(monkeypatch)

    async def read():
        return body

    status, _ = await ocr.handle_ocr(str(len(body)), read)
    assert status == 400 and fake.calls == []


async def test_the_fastapi_route_returns_the_status(monkeypatch):
    import app

    _fake(monkeypatch, **{"gemma.example": GEMMA_REPLY})
    async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app.app), base_url="http://agw") as client:
        ok = await client.post("/ocr", json={"image_b64": B64})
        bad = await client.post("/ocr", json={})
        not_json = await client.post("/ocr", content=b"{", headers={"Content-Type": "application/json"})
        huge = await client.post("/ocr", content=b"x", headers={"Content-Length": str(ocr.MAX_BODY_BYTES + 1)})
    assert ok.status_code == 200 and ok.json()["provider"] == "gemma"
    assert bad.status_code == 400 and not_json.status_code == 400
    assert huge.status_code == 413


def test_settings_read_named_variables_from_the_worker_env(monkeypatch):
    class Env:
        OCR_GEMMA_KEY = "from-worker"

    monkeypatch.setattr(settings, "_env", Env())
    assert settings.env_value("OCR_GEMMA_KEY") == "from-worker" and settings.env_value("OCR_UNSET") == ""
