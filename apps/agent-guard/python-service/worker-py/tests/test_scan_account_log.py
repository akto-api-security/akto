"""/scan logs the caller's account_id sent by guardrails-service."""

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
        client.post("/scan/batch", json=[{"scanner_name": "BanTopics", "text": "hi", "account_id": "1794942795"}])

    lines = [r.getMessage() for r in caplog.records if r.getMessage().startswith("[scan] ")]
    assert lines == [
        "[scan] account=1758787662 scanner=BanCode",
        "[scan] account=- scanner=Secrets",
        "[scan] account=1794942795 scanner=BanTopics",
    ]
