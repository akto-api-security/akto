"""Characterization: pin the CURRENT behavior of the already-shared session identity
logic in shared/akto_ingestion_utility.py (installer_headers + turn rotation).

These functions are already adopted across connectors (SPEC §3). We pin them so
phase 2 can move them under engine/session_identity.py without changing behavior.
Read-only: nothing in shared/ is modified.
"""
import logging

import akto_ingestion_utility as util

log = logging.getLogger("test-session-identity")


def test_installer_headers_golden(golden):
    # claude_code_cli field map (set via env in conftest): session_id -> akto_session_id,
    # current_message_id -> akto_message_id, extras forwarded raw.
    session_info = {
        "session_id": "sess-123",
        "current_message_id": "msg-abc",
        "cwd": "/tmp/work",
    }
    headers = util.installer_headers(session_info)
    golden("installer_headers_claude", headers)

    # Bug guard: the normalized session/message headers the Java consumer reads MUST
    # be present (their absence was the Go-provider drift bug).
    assert headers["x-akto-installer-akto_session_id"] == "sess-123"
    assert headers["x-akto-installer-akto_message_id"] == "msg-abc"


def test_open_message_turn_counter(monkeypatch):
    # github uses the turn_counter strategy: deterministic per-session sequence.
    monkeypatch.setattr(util, "AKTO_CONNECTOR", "github")
    out = util.open_message_turn({}, {}, "sess-9", {"turn_seq": 4}, log)
    assert out["turn_seq"] == 5
    assert out["current_message_id"] == "sess-9:5"


def test_open_message_turn_passthrough(monkeypatch):
    # cursor passes a message id through on every event (generation_id).
    monkeypatch.setattr(util, "AKTO_CONNECTOR", "cursor")
    out = util.open_message_turn({"generation_id": "gen-1"}, {}, "conv-1", {}, log)
    assert out["current_message_id"] == "gen-1"
