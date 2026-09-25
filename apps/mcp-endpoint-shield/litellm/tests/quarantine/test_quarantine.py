#!/usr/bin/env python3
"""
Unit tests for quarantine of blocked prompts in custom_hooks.py.

After a request is blocked, Claude Code appends the user's next prompt to the
rejected message as a new text block. These tests check that the blocked text is
removed before the next verdict and before forwarding, so later prompts in the
session are judged on their own.

Usage (needs litellm installed, e.g. inside the LiteLLM proxy container):
    python -m unittest discover -s tests/quarantine -v
"""
import asyncio
import json
import os
import sys
import unittest
from unittest import mock

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..", "..")))

from fastapi import HTTPException  # noqa: E402

import custom_hooks  # noqa: E402
from custom_hooks import GuardrailsHandler  # noqa: E402

SESSION = "11111111-2222-3333-4444-555555555555"
HARNESS = {"type": "text", "text": "<system-reminder>\nAs you answer, use this context: user@example.com\n</system-reminder>"}
BLOCKED = "Reply with exactly: ok"
NEXT = "What is 2+2?"
THIRD = "What is the capital of France?"


def text(t):
    return {"type": "text", "text": t}


def request(*messages):
    return {
        "model": "claude-opus-5-5",
        "messages": list(messages),
        "metadata": {"user_id": json.dumps({"device_id": "d", "account_uuid": "", "session_id": SESSION})},
    }


def user(*blocks):
    return {"role": "user", "content": list(blocks)}


def user_texts(data):
    return [[b.get("text") for b in m["content"] if b.get("type") == "text"] if isinstance(m["content"], list) else m["content"]
            for m in data["messages"] if m["role"] == "user"]


class QuarantineTest(unittest.TestCase):
    def setUp(self):
        GuardrailsHandler._quarantine.clear()

    def quarantine(self, *blocks):
        GuardrailsHandler.remember_blocked(request(user(*blocks)))

    def test_blocked_text_is_removed_from_merged_newest_turn(self):
        self.quarantine(HARNESS, text(BLOCKED))
        data = request(user(HARNESS, text(BLOCKED), text(NEXT)))
        out = GuardrailsHandler.strip_quarantined_history(data)
        self.assertEqual(user_texts(out), [[HARNESS["text"], NEXT]])

    def test_harness_blocks_are_not_quarantined(self):
        self.quarantine(HARNESS, text(BLOCKED))
        self.assertEqual(list(GuardrailsHandler._quarantine[SESSION]), [BLOCKED])

    def test_newest_turn_with_only_blocked_text_is_kept_for_rejudging(self):
        self.quarantine(HARNESS, text(BLOCKED))
        data = request(user(HARNESS, text(BLOCKED)))
        self.assertIs(GuardrailsHandler.strip_quarantined_history(data), data)

    def test_earlier_turn_with_only_blocked_text_is_dropped_with_its_reply(self):
        self.quarantine(text(BLOCKED))
        data = request(user(text(BLOCKED)), {"role": "assistant", "content": "ok"}, user(text(NEXT)))
        out = GuardrailsHandler.strip_quarantined_history(data)
        self.assertEqual([m["role"] for m in out["messages"]], ["user"])
        self.assertEqual(user_texts(out), [[NEXT]])

    def test_earlier_merged_turn_is_trimmed_and_its_reply_kept(self):
        self.quarantine(text(BLOCKED))
        data = request(user(text(BLOCKED), text(NEXT)), {"role": "assistant", "content": "4"}, user(text(THIRD)))
        out = GuardrailsHandler.strip_quarantined_history(data)
        self.assertEqual([m["role"] for m in out["messages"]], ["user", "assistant", "user"])
        self.assertEqual(user_texts(out), [[NEXT], [THIRD]])

    def test_resent_text_with_additions_matches_by_containment(self):
        self.quarantine(text(BLOCKED))
        data = request(user(text(BLOCKED + " Continue from where you left off."), text(NEXT)))
        self.assertEqual(user_texts(GuardrailsHandler.strip_quarantined_history(data)), [[NEXT]])

    def test_short_text_matches_only_exactly(self):
        self.quarantine(text("Say hello"))
        out = GuardrailsHandler.strip_quarantined_history(request(user(text("Say hello"), text(NEXT))))
        self.assertEqual(user_texts(out), [[NEXT]])
        kept = request(user(text("Say hello to the new team members")))
        self.assertIs(GuardrailsHandler.strip_quarantined_history(kept), kept)

    def test_plain_string_turns_are_quarantined_whole(self):
        GuardrailsHandler.remember_blocked(request({"role": "user", "content": BLOCKED}))
        data = request({"role": "user", "content": BLOCKED}, {"role": "user", "content": NEXT})
        self.assertEqual(user_texts(GuardrailsHandler.strip_quarantined_history(data)), [NEXT])

    def test_other_sessions_are_untouched(self):
        self.quarantine(text(BLOCKED))
        data = request(user(text(BLOCKED), text(NEXT)))
        data["metadata"] = {"user_id": json.dumps({"session_id": "another-session"})}
        self.assertIs(GuardrailsHandler.strip_quarantined_history(data), data)

    def test_disabled_quarantine_leaves_request_unchanged(self):
        self.quarantine(text(BLOCKED))
        data = request(user(text(BLOCKED), text(NEXT)))
        with mock.patch.object(custom_hooks, "QUARANTINE_BLOCKED_HISTORY", False):
            self.assertIs(GuardrailsHandler.strip_quarantined_history(data), data)


class ValidateAndBlockSessionTest(unittest.TestCase):
    """Replays the Claude Code sequence: block, then two follow-up prompts merged into the blocked message."""

    def setUp(self):
        GuardrailsHandler._quarantine.clear()
        self.handler = GuardrailsHandler()
        self.judged = []

        async def verdict(data, *args, **kwargs):
            texts = [t for turn in user_texts(data) for t in turn if not t.startswith("<system-reminder>")]
            self.judged.append(texts)
            return (BLOCKED not in texts, "PromptInjection", None)

        patches = [
            mock.patch.object(GuardrailsHandler, "call_guardrails_validation", side_effect=verdict),
            mock.patch.object(GuardrailsHandler, "ingest_blocked_request", new=mock.AsyncMock()),
            mock.patch.object(custom_hooks, "SYNC_MODE", True),
        ]
        for p in patches:
            p.start()
            self.addCleanup(p.stop)

    def run_turn(self, data):
        return asyncio.run(self.handler.validate_and_block(data, "anthropic_messages"))

    def test_prompts_after_a_block_are_judged_and_forwarded_without_the_blocked_text(self):
        with self.assertRaises(HTTPException):
            self.run_turn(request(user(HARNESS, text(BLOCKED))))

        forwarded = self.run_turn(request(user(HARNESS, text(BLOCKED), text(NEXT))))
        self.assertEqual(user_texts(forwarded), [[HARNESS["text"], NEXT]])

        forwarded = self.run_turn(request(
            user(HARNESS, text(BLOCKED), text(NEXT)), {"role": "assistant", "content": "4"}, user(text(THIRD))))
        self.assertEqual(user_texts(forwarded), [[HARNESS["text"], NEXT], [THIRD]])

        self.assertEqual(self.judged, [[BLOCKED], [NEXT], [NEXT, THIRD]])

    def test_resending_only_the_blocked_prompt_is_blocked_again(self):
        with self.assertRaises(HTTPException):
            self.run_turn(request(user(HARNESS, text(BLOCKED))))
        with self.assertRaises(HTTPException):
            self.run_turn(request(user(HARNESS, text(BLOCKED))))


if __name__ == "__main__":
    unittest.main()
