#!/usr/bin/env python3
"""
SubagentStart hook for Cursor - logs subagent creation and ingests the triggering user prompt.
Cannot block subagent creation; used for observability and context injection only.
"""
import json
import sys

from akto_ingestion_utility import (
    AKTO_SYNC_MODE,
    get_latest_message_for_cursor,
    send_ingestion_data,
    setup_logger,
)

logger = setup_logger("subagent.log")


def main():
    logger.info("=== SubagentStart hook started ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("SubagentStart input:\n%s", json.dumps(input_data, indent=2))

        transcript_path = input_data.get("transcript_path", "")
        user_prompt = get_latest_message_for_cursor(transcript_path, "user", logger)

        logger.info("Prompt: %d chars", len(user_prompt))
        send_ingestion_data(
            hook_name="subagentStart",
            request_payload={**input_data, "user_prompt": user_prompt},
            response_payload={},
            guardrails=AKTO_SYNC_MODE,
            logger=logger,
        )

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
