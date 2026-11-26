"""/scan and /guardrails/llm* log the caller's account_id sent by guardrails-service."""

import logging

from fastapi.testclient import TestClient

import app as app_module


def test_scan_logs_account_id(monkeypatch, caplog):
    async def fake_scan_payload(body, schedule_fn):
        return {"is_valid": True, "details": {}}

    monkeypatch.setattr(app_module, "scan_payload", fake_scan_payload)
    client = TestClient(app_module.app)  # no `with`: skip lifespan (metrics push, self-check)

    with caplog.at_level(logging.INFO, logger="app"):
        client.post("/scan", json={"scanner_name": "BanCode", "text": "hi", "account_id": "1758787662"})
        client.post("/scan", json={"scanner_name": "Secrets", "text": "hi"})
        client.post(
            "/scan",
            json={"scanner_name": "BanTopics", "text": "hi", "account_id": "1768362636", "enrichment": "reason"},
        )
        client.post("/scan/batch", json=[{"scanner_name": "BanTopics", "text": "hi", "account_id": "1794942795"}])

    lines = [r.getMessage() for r in caplog.records if r.getMessage().startswith("[scan] ")]
    assert lines == [
        "[scan] account=1758787662 scanner=BanCode enrichment=-",
        "[scan] account=- scanner=Secrets enrichment=-",
        "[scan] account=1768362636 scanner=BanTopics enrichment=reason",
        "[scan] account=1794942795 scanner=BanTopics enrichment=-",
    ]


def test_guardrails_llm_logs_route_and_account_id(monkeypatch, caplog):
    class FakeProvider:
        name = "gemma_fast_arbiter"

        async def complete(self, prompt):
            return "ok"

    monkeypatch.setattr(app_module, "get_default_config", lambda raw="": {"modelConfigs": []})
    monkeypatch.setattr(app_module, "get_enrichment_config", lambda: None)
    monkeypatch.setattr(app_module, "build_arbiter", lambda configs, model: FakeProvider())
    client = TestClient(app_module.app)

    with caplog.at_level(logging.INFO, logger="app"):
        client.post("/guardrails/llm", json={"prompt": "p", "account_id": "1758787662"})
        client.post("/guardrails/llm/async", json={"prompt": "p", "account_id": "1768362636"})
        client.post("/guardrails/llm/session", json={"prompt": "p"})

    lines = [r.getMessage() for r in caplog.records if r.getMessage().startswith("[GuardrailsLLM] route=")]
    assert [" ".join(line.split()[1:3]) for line in lines] == [
        "route=/guardrails/llm account=1758787662",
        "route=/guardrails/llm/async account=1768362636",
        "route=/guardrails/llm/session account=-",
    ]
