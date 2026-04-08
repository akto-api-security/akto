#!/usr/bin/env python3
"""
TeammateIdle hook - logs and ingests teammate idle events.
Observability only, cannot block.
"""
import json
import sys

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, send_ingestion_data, setup_logger

logger = setup_logger("task.log")


def main():
    logger.info(f"=== TeammateIdle hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("TeammateIdle input:\n%s", json.dumps(input_data, indent=2))

        send_ingestion_data(
            hook_name="TeammateIdle",
            request_payload=input_data,
            response_payload={},
            tags=None,
            guardrails=not AKTO_SYNC_MODE,
            logger=logger,
        )

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
