#!/usr/bin/env python3
"""
PermissionRequest hook - validates permission requests via guardrails.
Blocks by returning decision.behavior = "deny" if guardrails denies.
"""
import json
import sys

from akto_ingestion_utility import MODE, send_ingestion_data, setup_logger

logger = setup_logger("permission.log")


def main():
    logger.info(f"=== PermissionRequest hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("PermissionRequest input:\n%s", json.dumps(input_data, indent=2))

        tool_name = input_data.get("tool_name", "")
        tool_input = input_data.get("tool_input", {})

        result = send_ingestion_data(
            hook_name="PermissionRequest",
            request_payload={"tool_name": tool_name, "tool_input": tool_input},
            response_payload={},
            tags=None,
            guardrails=True,
            logger=logger,
        )

        allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
        if not allowed:
            reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
            logger.warning(f"BLOCKING PermissionRequest for {tool_name}: {reason}")
            print(json.dumps({
                "hookSpecificOutput": {
                    "hookEventName": "PermissionRequest",
                    "decision": {"behavior": "deny", "message": reason},
                }
            }))

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
