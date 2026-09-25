#!/usr/bin/env python3
"""
Unit tests for client identity tags in custom_hooks.py.

Claude Code sends {device_id, account_uuid, session_id} in the Anthropic
metadata.user_id on every request. At pre-call time LiteLLM has not populated
litellm_params.metadata yet, so the hook reads it from the request body. These
tests check that the guardrail verdict, blocked-request ingestion and tool-call
events carry the same identity as the post-call ingest.

Usage (needs litellm installed, e.g. inside the LiteLLM proxy container):
    python -m unittest discover -s tests/client_identity -v
"""
import json
import os
import sys
import unittest

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

from custom_hooks import GuardrailsHandler  # noqa: E402

DEVICE_ID = "e5682ef8c5847e7f62e4e2bc2124da809f6b8b98591f2ffb56f5c199bac68ebd"
USER_ID = json.dumps({"device_id": DEVICE_ID, "account_uuid": "", "session_id": "f1d17d46"})


def claude_code_request():
    """Request body as LiteLLM hands it to async_pre_call_hook for Claude Code's /v1/messages."""
    return {
        "model": "claude-opus-5-5",
        "messages": [{"role": "user", "content": [{"type": "text", "text": "What is 2+2?"}]}],
        "metadata": {"user_id": USER_ID},
    }


class ClientIdentityTagsTest(unittest.TestCase):
    def setUp(self):
        self.handler = GuardrailsHandler()

    def test_pre_call_reads_identity_from_request_body(self):
        tags = self.handler.client_identity_tags({"cache": None}, claude_code_request())
        self.assertEqual(tags["client_device_id"], DEVICE_ID)
        self.assertEqual(tags["client_session_id"], "f1d17d46")
        self.assertNotIn("client_account_uuid", tags)  # empty in Claude Code's blob

    def test_post_call_metadata_still_wins(self):
        kwargs = {"litellm_params": {"metadata": {"user_id": json.dumps({"device_id": "from-litellm"})}}}
        tags = self.handler.client_identity_tags(kwargs, claude_code_request())
        self.assertEqual(tags["client_device_id"], "from-litellm")

    def test_verdict_payload_carries_device_id(self):
        data = self.handler._validation_view(claude_code_request())
        payload = self.handler.build_payload(data, "anthropic_messages", None, kwargs={"cache": None})
        self.assertEqual(json.loads(payload["tag"])["client_device_id"], DEVICE_ID)

    def test_blocked_request_payload_carries_device_id(self):
        payload = self.handler.build_payload(
            claude_code_request(), "anthropic_messages", {"x-blocked-by": "Akto Proxy"}, status_code=403, kwargs={"cache": None})
        self.assertEqual(json.loads(payload["tag"])["client_device_id"], DEVICE_ID)

    def test_tool_call_payload_carries_device_id(self):
        kwargs = {"litellm_params": {"metadata": {"user_id": USER_ID}}, "litellm_call_id": "1ddebc2e"}
        payload = self.handler.build_tool_call_ingest_payload("Bash", {"command": "ls"}, model="claude-opus-5-5", kwargs=kwargs)
        tags = json.loads(payload["tag"])
        self.assertEqual(tags["client_device_id"], DEVICE_ID)
        self.assertEqual(tags["tool_name"], "Bash")

    def test_clients_without_user_id_get_no_client_tags(self):
        data = {"model": "gpt-4o", "messages": [{"role": "user", "content": "hi"}], "metadata": {}}
        self.assertEqual(self.handler.client_identity_tags({"cache": None}, data), {})

    def test_opaque_user_id_is_kept_whole(self):
        data = {"model": "gpt-4o", "messages": [], "metadata": {"user_id": "user-123"}}
        self.assertEqual(self.handler.client_identity_tags(None, data), {"client_user_id": "user-123"})


if __name__ == "__main__":
    unittest.main()
