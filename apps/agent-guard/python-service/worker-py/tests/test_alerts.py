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
            "qwen": {"completed": True, "is_valid": False, "risk_score": 0.99,
                     "decision_confidence": 0.82},
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
    blocks = alerts._build_blocks("Toxicity", "prompt", long_text,
                                  {"is_valid": True, "risk_score": 0.0, "details": {}})
    flat = str(blocks)
    assert "…" in flat
    assert "x" * 5000 not in flat


async def test_post_slack_noop_when_unset(monkeypatch):
    monkeypatch.setattr(alerts.settings, "SLACK_WEBHOOK_URL", "")
    called = {"n": 0}

    class _Boom:
        def __init__(self, *a, **k): called["n"] += 1
    monkeypatch.setattr(alerts.httpx, "AsyncClient", _Boom)
    await alerts.post_slack("Toxicity", "prompt", "hi", {"is_valid": True, "details": {}})
    assert called["n"] == 0  # never touched the network


async def test_store_results_noop_when_unset(monkeypatch):
    monkeypatch.setattr(alerts.settings, "DATABASE_ABSTRACTOR_SERVICE_URL", "")
    called = {"n": 0}

    class _Boom:
        def __init__(self, *a, **k): called["n"] += 1
    monkeypatch.setattr(alerts.httpx, "AsyncClient", _Boom)
    await alerts.store_results([{"x": 1}], "Toxicity")
    assert called["n"] == 0


async def test_post_slack_posts_when_configured(monkeypatch):
    monkeypatch.setattr(alerts.settings, "SLACK_WEBHOOK_URL", "https://hooks.example/abc")
    sent = {}

    class _Resp:
        status_code = 200

    class _Client:
        def __init__(self, *a, **k): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, url, headers=None, json=None):
            sent["url"] = url
            sent["json"] = json
            return _Resp()
    monkeypatch.setattr(alerts.httpx, "AsyncClient", _Client)
    await alerts.post_slack("Toxicity", "prompt", "hi",
                            {"is_valid": True, "risk_score": 0.0, "details": {}})
    assert sent["url"] == "https://hooks.example/abc"
    assert "blocks" in sent["json"]


async def test_store_results_posts_payload_shape(monkeypatch):
    monkeypatch.setattr(alerts.settings, "DATABASE_ABSTRACTOR_SERVICE_URL", "http://db:5678/")
    sent = {}

    class _Resp:
        status_code = 200

    class _Client:
        def __init__(self, *a, **k): pass
        async def __aenter__(self): return self
        async def __aexit__(self, *a): return False
        async def post(self, url, headers=None, json=None):
            sent["url"] = url
            sent["json"] = json
            return _Resp()
    monkeypatch.setattr(alerts.httpx, "AsyncClient", _Client)
    await alerts.store_results([{"is_valid": True}], "PromptInjection")
    assert sent["url"] == "http://db:5678/api/storeGuardrailModelResults"
    assert sent["json"] == {"scannerName": "PromptInjection",
                            "modelResults": [{"is_valid": True}]}
