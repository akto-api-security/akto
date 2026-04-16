#!/usr/bin/env python3
"""
WorktreeRemove hook - logs and ingests worktree removal events.
Observability only, cannot block.
"""
import json
import sys

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, send_ingestion_data, setup_logger

logger = setup_logger("worktree.log")


def main():
    logger.info(f"=== WorktreeRemove hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("WorktreeRemove input:\n%s", json.dumps(input_data, indent=2))

        send_ingestion_data(
            hook_name="WorktreeRemove",
            request_payload={"worktree_path": input_data.get("worktree_path", "")},
            response_payload={},
            tags=None,
            guardrails=AKTO_SYNC_MODE,
            logger=logger,
        )

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
