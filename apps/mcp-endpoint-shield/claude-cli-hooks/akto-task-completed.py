#!/usr/bin/env python3
"""
TaskCompleted hook - validates task completion via guardrails.
Blocks via continue = false if guardrails denies.
"""
import json
import sys

from akto_ingestion_utility import MODE, send_ingestion_data, setup_logger

logger = setup_logger("task.log")


def main():
    logger.info(f"=== TaskCompleted hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("TaskCompleted input:\n%s", json.dumps(input_data, indent=2))

        task_id = input_data.get("task_id", "")
        task_subject = input_data.get("task_subject", "")
        task_description = input_data.get("task_description", "")
        teammate_name = input_data.get("teammate_name", "")

        result = send_ingestion_data(
            hook_name="TaskCompleted",
            request_payload={
                "task_id": task_id,
                "task_subject": task_subject,
                "task_description": task_description,
                "teammate_name": teammate_name,
            },
            response_payload={},
            tags=None,
            guardrails=True,
            logger=logger,
        )

        allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
        if not allowed:
            reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
            logger.warning(f"BLOCKING TaskCompleted {task_id}: {reason}")
            print(json.dumps({"continue": False, "stopReason": reason}))

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
