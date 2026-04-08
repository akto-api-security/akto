#!/usr/bin/env python3
"""
PreCompact hook - logs and ingests pre-compaction events.
Observability only, cannot block.
"""
import json
import sys

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, send_ingestion_data, setup_logger

logger = setup_logger("compact.log")


def main():
    logger.info(f"=== PreCompact hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("PreCompact input:\n%s", json.dumps(input_data, indent=2))

        custom_instructions = input_data.get("custom_instructions")
        request_payload = {"custom_instructions": custom_instructions} if custom_instructions else input_data

        send_ingestion_data(
            hook_name="PreCompact",
            request_payload=request_payload,
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
