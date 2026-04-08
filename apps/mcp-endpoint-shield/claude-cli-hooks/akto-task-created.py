#!/usr/bin/env python3
"""
TaskCreated hook - validates task creation via guardrails.
Blocks via continue = false if guardrails denies.
"""
import json
import sys

from akto_ingestion_utility import MODE, send_ingestion_data, setup_logger

logger = setup_logger("task.log")


def main():
    logger.info(f"=== TaskCreated hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("TaskCreated input:\n%s", json.dumps(input_data, indent=2))

        task_id = input_data.get("task_id", "")

        result = send_ingestion_data(
            hook_name="TaskCreated",
            request_payload={
                "task_id": task_id,
                "task_subject": input_data.get("task_subject", ""),
                "task_description": input_data.get("task_description", ""),
                "teammate_name": input_data.get("teammate_name", ""),
                "team_name": input_data.get("team_name", "")
            },
            response_payload={},
            tags=None,
            guardrails=True,
            logger=logger,
        )

        allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
        if not allowed:
            reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
            logger.warning(f"BLOCKING TaskCreated {task_id}: {reason}")
            print(json.dumps({"continue": False, "stopReason": reason}))

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
