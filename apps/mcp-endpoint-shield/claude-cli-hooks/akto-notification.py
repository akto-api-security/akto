#!/usr/bin/env python3
"""
Notification hook - logs and ingests notification events.
Observability only, cannot block.
"""
import json
import sys

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, send_ingestion_data, setup_logger

logger = setup_logger("notification.log")


def main():
    logger.info(f"=== Notification hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("Notification input:\n%s", json.dumps(input_data, indent=2))

        send_ingestion_data(
            hook_name="Notification",
            request_payload=input_data,
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
