#!/usr/bin/env python3
"""
SubagentStop hook - logs subagent completion and ingests conversation data.
Can block subagent from stopping via {"decision": "block", "reason": "..."}.
Can stop the main session via {"continue": false, "stopReason": "..."}.
"""
import json
import sys

from akto_ingestion_utility import (
    AKTO_SYNC_MODE,
    MODE,
    get_last_user_prompt,
    send_ingestion_data,
    setup_logger,
)

logger = setup_logger("sub-agent.log")


def main():
    logger.info(f"=== SubagentStop hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("SubagentStop input:\n%s", json.dumps(input_data, indent=2))

        agent_type = input_data.get("agent_type", "")
        response_text = input_data.get("last_assistant_message", "").strip()

        transcript_path = input_data.get("transcript_path", "")
        user_prompt = get_last_user_prompt(transcript_path, logger)

        if not user_prompt or not response_text:
            logger.info("Incomplete interaction, skipping ingestion")
            sys.exit(0)

        logger.info(f"Agent type: {agent_type}, Prompt: {len(user_prompt)} chars, Response: {len(response_text)} chars, Transcript: {transcript_path}")
        send_ingestion_data(
            hook_name="SubagentStop",
            request_payload=user_prompt,
            response_payload=response_text,
            tags={"hook": "SubagentStop", "agent_type": agent_type},
            guardrails=not AKTO_SYNC_MODE,
            logger=logger,
        )

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
