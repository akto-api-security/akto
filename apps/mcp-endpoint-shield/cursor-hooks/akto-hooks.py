#!/usr/bin/env python3
import io
import json
import os
import re
import sys

if not os.getenv("LOG_DIR"):
    os.environ["LOG_DIR"] = os.path.expanduser("~/.cursor/akto/chat-logs")

from akto_ingestion_utility import (
    _extract_text_from_entry,
    pop_cursor_reply,
    resolve_session_info,
    run_observability_hook,
    send_ingestion_data,
    setup_logger,
)


def get_last_turn(transcript_path: str):
    """Return (prompt, reply) of the last turn in a Cursor JSONL transcript."""
    prompt, replies = "", []
    with open(os.path.expanduser(transcript_path), "r") as f:
        for line in f:
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            if entry.get("role") == "user":
                prompt, replies = _extract_text_from_entry(entry), []
            elif entry.get("role") == "assistant":
                replies.append(_extract_text_from_entry(entry))
    # Cursor wraps the typed prompt as <timestamp>...</timestamp><user_query>...</user_query>
    match = re.search(r"<user_query>\s*(.*?)\s*</user_query>", prompt, re.S)
    return (match.group(1) if match else prompt), "\n".join(r for r in replies if r)


def ingest_last_turn(input_data: dict) -> None:
    """Send the turn's prompt -> reply span; Cursor only exposes transcript_path on stop."""
    logger = setup_logger("hook-executions.log")
    try:
        # Reply comes from afterAgentResponse's text (like Claude's last_assistant_message);
        # popped first so it is cleared for the next turn even if we bail out below.
        reply = pop_cursor_reply(input_data.get("conversation_id"), logger)
        transcript_path = input_data.get("transcript_path")
        if not transcript_path:
            logger.info("No transcript path provided")
            return
        prompt, transcript_reply = get_last_turn(transcript_path)
        reply = reply or transcript_reply
        if not prompt or not reply:
            logger.info("No complete interaction found in transcript")
            return
        logger.info(f"Extracted interaction - Prompt: {len(prompt)} chars, Response: {len(reply)} chars")
        send_ingestion_data(
            hook_name="afterAgentResponse",
            request_payload=prompt,
            response_payload=reply,
            session_info=resolve_session_info(input_data, logger),
            input_data=input_data,
            logger=logger,
        )
    except Exception as e:
        logger.error(f"Transcript ingestion error: {e}")


if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Usage: akto-hooks.py <hookName>", file=sys.stderr)
        sys.exit(1)

    hook = sys.argv[1]

    if hook == "stop":
        raw = sys.stdin.read()
        try:
            ingest_last_turn(json.loads(raw))
        except json.JSONDecodeError:
            pass
        # replay stdin so the stop event itself is still ingested as before
        sys.stdin = io.StringIO(raw)

    run_observability_hook(hook)
    print("{}")
    sys.exit(0)
