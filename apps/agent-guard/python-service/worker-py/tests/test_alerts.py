"""alerts — Slack block building + sink no-op gating (network mocked)."""

import alerts


def test_slack_blocks_blocked_verdict_and_per_model_rows():
    result = {
        "is_valid": False,
        "risk_score": 0.97,
        "details": {
            "llm_provider": "gemma_vertexai",
            "cascade_decision": "gemma_authority",
            "reason": "override attempt",
            "qwen": {"completed": True, "is_valid": False, "risk_score": 0.99, "decision_confidence": 0.82},
            "gemma": {"completed": False},
        },
    }
    blocks = alerts._build_blocks("PromptInjection", "prompt", "ignore instructions", result)
    flat = str(blocks)
    assert "🚫 BLOCKED" in flat
    assert "gemma_vertexai" in flat
    assert "override attempt" in flat
    # both per-model rows present (one consulted, one not)
    assert "is_valid=`False`" in flat
    assert "_not consulted_" in flat


def test_slack_blocks_truncate_long_input():
    long_text = "x" * 5000
    blocks = alerts._build_blocks("Toxicity", "prompt", long_text, {"is_valid": True, "risk_score": 0.0, "details": {}})
    flat = str(blocks)
    assert "…" in flat
    assert "x" * 5000 not in flat


def test_slack_blocks_flags_fail_open_instead_of_allowed():
    # is_valid=True here (fail-open convention), but details.error means this
    # was NEVER ACTUALLY SCANNED — a plain "✅ ALLOWED" would be indistinguishable
    # from a genuinely clean verdict and hide a fully-broken cascade from anyone
    # watching Slack.
    result = {
        "is_valid": True,
        "risk_score": 0.0,
        "details": {"error": "no arbiter configured", "cascade_decision": "no_authority"},
    }
    blocks = alerts._build_blocks("PromptInjection", "prompt", "some prompt", result)
    flat = str(blocks)
    assert "✅ ALLOWED" not in flat
    assert "DEGRADED" in flat
    assert "no arbiter configured" in flat


async def test_post_slack_noop_when_unset(monkeypatch):
    monkeypatch.setattr(alerts.settings, "SLACK_WEBHOOK_URL", "")
    called = {"n": 0}

    class _Boom:
        def __init__(self, *a, **k):
            called["n"] += 1

    monkeypatch.setattr(alerts.httpx, "AsyncClient", _Boom)
    await alerts.post_slack("Toxicity", "prompt", "hi", {"is_valid": True, "details": {}})
    assert called["n"] == 0  # never touched the network


async def test_store_results_noop_when_unset(monkeypatch):
    monkeypatch.setattr(alerts.settings, "DATABASE_ABSTRACTOR_SERVICE_URL", "")
    called = {"n": 0}

    class _Boom:
        def __init__(self, *a, **k):
            called["n"] += 1

    monkeypatch.setattr(alerts.httpx, "AsyncClient", _Boom)
    await alerts.store_results([{"x": 1}], "Toxicity")
    assert called["n"] == 0


async def test_post_slack_posts_when_configured(monkeypatch):
    monkeypatch.setattr(alerts.settings, "SLACK_WEBHOOK_URL", "https://hooks.example/abc")
    sent = {}

    class _Resp:
        status_code = 200

    class _Client:
        def __init__(self, *a, **k):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *a):
            return False

        async def post(self, url, headers=None, json=None):
            sent["url"] = url
            sent["json"] = json
            return _Resp()

    monkeypatch.setattr(alerts.httpx, "AsyncClient", _Client)
    await alerts.post_slack("Toxicity", "prompt", "hi", {"is_valid": True, "risk_score": 0.0, "details": {}})
    assert sent["url"] == "https://hooks.example/abc"
    assert "blocks" in sent["json"]


async def test_store_results_posts_payload_shape(monkeypatch):
    monkeypatch.setattr(alerts.settings, "DATABASE_ABSTRACTOR_SERVICE_URL", "http://db:5678/")
    sent = {}

    class _Resp:
        status_code = 200

    class _Client:
        def __init__(self, *a, **k):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *a):
            return False

        async def post(self, url, headers=None, json=None):
            sent["url"] = url
            sent["json"] = json
            return _Resp()

    monkeypatch.setattr(alerts.httpx, "AsyncClient", _Client)
    await alerts.store_results([{"is_valid": True}], "PromptInjection")
    assert sent["url"] == "http://db:5678/api/storeGuardrailModelResults"
    assert sent["json"] == {"scannerName": "PromptInjection", "modelResults": [{"is_valid": True}]}


# ── Tripwire records → Azure Blob Storage ─────────────────────────────────────

_SAS_URL = "https://acct.blob.core.windows.net/tripwire?sv=2024-11-04&sp=cw&sig=SECRET%2Bsig"


class _BlobClient:
    """Stands in for httpx.AsyncClient; answers every PUT with `status` (or raises `error`)."""

    sent: list = []
    status = 201
    error: Exception | None = None

    def __init__(self, *a, **k):
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *a):
        return False

    async def put(self, url, headers=None, content=None):
        _BlobClient.sent.append({"url": url, "headers": headers, "content": content})
        if _BlobClient.error:
            raise _BlobClient.error

        class _Resp:
            status_code = _BlobClient.status

        return _Resp()


def _blob_client(monkeypatch, status=201, error=None):
    _BlobClient.sent, _BlobClient.status, _BlobClient.error = [], status, error
    monkeypatch.setattr(alerts.httpx, "AsyncClient", _BlobClient)
    counted = []
    monkeypatch.setattr(alerts.metrics_push.COUNTS["alert_errors"], "increment", lambda key, by=1: counted.append(key))
    return counted


async def test_tripwire_result_is_not_uploaded_without_a_blob_url(monkeypatch):
    monkeypatch.setattr(alerts.settings, "TRIPWIRE_RESULTS_BLOB_URL", "")
    _blob_client(monkeypatch)
    await alerts.store_tripwire_result({"x": 1})
    assert _BlobClient.sent == []


async def test_tripwire_result_is_put_as_one_json_blob_named_by_time(monkeypatch):
    import json
    import re

    monkeypatch.setattr(alerts.settings, "TRIPWIRE_RESULTS_BLOB_URL", _SAS_URL)
    _blob_client(monkeypatch)
    await alerts.store_tripwire_result({"tripwire": {"attack_score": 0.9}})
    [put] = _BlobClient.sent
    base, _, sas = put["url"].partition("?")
    assert sas == "sv=2024-11-04&sp=cw&sig=SECRET%2Bsig"  # the SAS token goes through exactly as given
    assert re.fullmatch(
        r"https://acct\.blob\.core\.windows\.net/tripwire/\d{4}/\d{2}/\d{2}/\d{2}/\d{8}T\d{12}Z-[0-9a-f]{12}\.json",
        base,
    )
    assert put["headers"] == {"x-ms-blob-type": "BlockBlob", "Content-Type": "application/json"}
    assert json.loads(put["content"]) == {"tripwire": {"attack_score": 0.9}}


async def test_a_failed_tripwire_upload_is_counted_and_never_logs_the_sas(monkeypatch, caplog):
    import logging

    import httpx

    caplog.set_level(logging.WARNING)
    monkeypatch.setattr(alerts.settings, "TRIPWIRE_RESULTS_BLOB_URL", _SAS_URL)
    counted = _blob_client(monkeypatch, status=403)
    await alerts.store_tripwire_result({"x": 1})
    _BlobClient.error = httpx.ConnectError(f"cannot reach {_SAS_URL}")
    await alerts.store_tripwire_result({"x": 1})  # never raises
    assert counted == ["tripwire_blob:status_403", "tripwire_blob:ConnectError"]
    assert "upload of" in caplog.text and "sig=" not in caplog.text and "SECRET" not in caplog.text
