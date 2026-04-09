#!/usr/bin/env python3
"""
FileChanged hook - logs and ingests file change events.
Observability only, cannot block.
"""
import json
import sys

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, send_ingestion_data, setup_logger

logger = setup_logger("session.log")


def main():
    logger.info(f"=== FileChanged hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("FileChanged input:\n%s", json.dumps(input_data, indent=2))

        send_ingestion_data(
            hook_name="FileChanged",
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
