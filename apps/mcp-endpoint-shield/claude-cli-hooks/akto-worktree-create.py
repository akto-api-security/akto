#!/usr/bin/env python3
"""
WorktreeCreate hook - validates worktree creation via guardrails.
Any non-zero exit blocks worktree creation.
"""
import json
import sys

from akto_ingestion_utility import MODE, send_ingestion_data, setup_logger

logger = setup_logger("worktree.log")


def main():
    logger.info(f"=== WorktreeCreate hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("WorktreeCreate input:\n%s", json.dumps(input_data, indent=2))

        cwd = input_data.get("cwd", "")

        result = send_ingestion_data(
            hook_name="WorktreeCreate",
            request_payload={"cwd": cwd},
            response_payload={},
            tags=None,
            guardrails=True,
            logger=logger,
        )

        allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
        if not allowed:
            reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
            logger.warning(f"BLOCKING WorktreeCreate for {cwd}: {reason}")
            print(reason, file=sys.stderr)
            sys.exit(1)

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
