#!/usr/bin/env python3
"""
ElicitationResult hook - validates MCP elicitation results via guardrails.
Blocks via action = "decline" if guardrails denies.
"""
import json
import sys

from akto_ingestion_utility import MODE, send_ingestion_data, setup_logger

logger = setup_logger("elicitation.log")


def main():
    logger.info(f"=== ElicitationResult hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("ElicitationResult input:\n%s", json.dumps(input_data, indent=2))

        mcp_server = input_data.get("mcp_server", "")

        result = send_ingestion_data(
            hook_name="ElicitationResult",
            request_payload=input_data,
            response_payload={},
            tags=None,
            guardrails=True,
            logger=logger,
        )

        allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
        if not allowed:
            reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
            logger.warning(f"BLOCKING ElicitationResult for {mcp_server}: {reason}")
            print(json.dumps({
                "hookSpecificOutput": {
                    "hookEventName": "ElicitationResult",
                    "action": "decline",
                }
            }))

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
