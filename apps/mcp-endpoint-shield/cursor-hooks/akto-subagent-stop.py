#!/usr/bin/env python3
"""
SubagentStop hook for Cursor - logs subagent completion and ingests conversation data.
Can block subagent from stopping via {"decision": "block", "reason": "..."}.
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
    logger.info("=== SubagentStop hook started ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("SubagentStop input:\n%s", json.dumps(input_data, indent=2))

        transcript_path = input_data.get("transcript_path", "")
        response_text = get_latest_message_for_cursor(transcript_path, "assistant", logger)
        user_prompt = get_latest_message_for_cursor(transcript_path, "user", logger)

        logger.info(
            "Prompt: %d chars, Response: %d chars, Transcript: %s",
            len(user_prompt), len(response_text), transcript_path,
        )
        result = send_ingestion_data(
            hook_name="subagentStop",
            request_payload={**input_data, "user_prompt": user_prompt},
            response_payload={"latest_assistant_message": response_text},
            guardrails=AKTO_SYNC_MODE,
            logger=logger,
        )

        allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
        if not allowed:
            reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
            logger.warning(f"BLOCKING subagentStop: {reason}")
            print(json.dumps({"decision": "block", "reason": reason}))
            send_ingestion_data(
                hook_name="subagentStop",
                request_payload=user_prompt,
                response_payload={"reason": reason, "blockedBy": "Akto Proxy"},
                guardrails=False,
                status_code="403",
                logger=logger,
            )
            sys.exit(0)

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
