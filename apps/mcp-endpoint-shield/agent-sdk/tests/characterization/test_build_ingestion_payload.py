"""Characterization: pin the CURRENT output of the shared build_ingestion_payload
(the observability payload builder in shared/akto_ingestion_utility.py).

Phase 3's business_logic/build_akto_payload.py must reproduce this. Read-only.
"""
import json

import akto_ingestion_utility as util


def _payload(monkeypatch):
    # Freeze the two nondeterministic inputs so the golden is stable.
    monkeypatch.setattr(util.time, "time", lambda: 1_700_000_000.0)
    monkeypatch.setattr(util, "get_username", lambda: "tester")
    return util.build_ingestion_payload(
        hook_name="UserPromptSubmit",
        request_payload="hello world",
        response_payload="",
        session_info={"session_id": "sess-123", "current_message_id": "msg-abc"},
        input_data={"session_id": "sess-123"},
    )


def test_build_ingestion_payload_golden(monkeypatch, golden):
    golden("build_ingestion_payload_claude", _payload(monkeypatch))


def test_build_ingestion_payload_invariants(monkeypatch):
    payload = _payload(monkeypatch)

    assert payload["path"] == "/v1/hooks/UserPromptSubmit"
    assert payload["contextSource"] == "ENDPOINT"

    # Body is wrapped as {"body": ...} on request and response sides.
    assert json.loads(payload["requestPayload"]) == {"body": "hello world"}
    assert json.loads(payload["responsePayload"]) == {"body": ""}

    hdrs = json.loads(payload["requestHeaders"])
    # Host must be <id>.ai-agent.<connector> so the backend remaps serviceId correctly
    # (the literal-"api" serviceId drift bug is guarded here).
    assert hdrs["host"] == "test-device.ai-agent.claudecli"
    # Installer identity headers must be present (the Go-provider omission bug).
    assert hdrs["x-akto-installer-akto_session_id"] == "sess-123"
    assert hdrs["x-akto-installer-akto_message_id"] == "msg-abc"

    tags = json.loads(payload["tag"])
    assert tags["source"] == "ENDPOINT"
    assert tags["ai-agent"] == "claudecli"
    assert tags["gen-ai"] == "Gen AI"
