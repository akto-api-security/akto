#!/usr/bin/env python3
"""
SubagentStop hook - logs subagent completion and ingests conversation data.
Can block subagent from stopping via {"decision": "block", "reason": "..."}.
Can stop the main session via {"continue": false, "stopReason": "..."}.
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
        result = send_ingestion_data(
            hook_name="SubagentStop",
            request_payload={**input_data, "user_prompt": user_prompt},
            response_payload={},
            tags=None,
            guardrails=False,
            logger=logger,
        )

        allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
        if not allowed:
            reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
            logger.warning(f"BLOCKING SubagentStop: {reason}")
            print(json.dumps({"decision": "block", "reason": reason}))
            send_ingestion_data(
                hook_name="SubagentStop",
                request_payload=user_prompt,
                response_payload={"reason": reason or "Policy violation", "blockedBy": "Akto Proxy"},
                tags=None,
                guardrails=False,
                status_code="403",
                logger=logger,
            )

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
