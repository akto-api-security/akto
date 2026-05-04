#!/usr/bin/env python3
"""
SubagentStart hook - logs input and ingests the triggering user prompt.
Cannot block subagent creation; used for observability and context injection only.
"""
import json
import sys

from akto_ingestion_utility import (
    MODE,
    get_last_user_prompt,
    send_ingestion_data,
    setup_logger,
)

logger = setup_logger("sub-agent.log")


def main():
    logger.info(f"=== SubagentStart hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("SubagentStart input:\n%s", json.dumps(input_data, indent=2))

        agent_type = input_data.get("agent_type", "")

        transcript_path = input_data.get("transcript_path", "")
        user_prompt = get_last_user_prompt(transcript_path, logger)

        if not user_prompt:
            logger.info("No user prompt found, skipping ingestion")
            sys.exit(0)

        logger.info(f"Agent type: {agent_type}, Prompt: {len(user_prompt)} chars")
        send_ingestion_data(
            hook_name="SubagentStart",
            request_payload={**input_data, "user_prompt": user_prompt},
            response_payload={},
            tags=None,
            guardrails=False,
            logger=logger,
        )

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
