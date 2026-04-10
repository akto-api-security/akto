#!/usr/bin/env python3
"""
SubagentStart hook for Cursor - validates the triggering user prompt before subagent creation.
Can block subagent creation via {"decision": "block", "reason": "..."}.
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

        if not user_prompt:
            logger.info("No user prompt found, skipping ingestion")
            sys.exit(0)

        logger.info("Prompt: %d chars", len(user_prompt))
        result = send_ingestion_data(
            hook_name="subagentStart",
            request_payload={**input_data, "user_prompt": user_prompt},
            response_payload={},
            guardrails=AKTO_SYNC_MODE,
            logger=logger,
        )

        allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
        if not allowed:
            reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
            logger.warning(f"BLOCKING subagentStart: {reason}")
            print(json.dumps({
                "permission": "deny",
                "user_message": f"Request blocked by Akto security policy. Reason: {reason}",
            }))
            send_ingestion_data(
                hook_name="subagentStart",
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
