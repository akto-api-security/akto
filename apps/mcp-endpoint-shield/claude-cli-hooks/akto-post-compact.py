#!/usr/bin/env python3
"""
PostCompact hook - logs and ingests post-compaction events.
Observability only, cannot block.
"""
import json
import sys

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, get_last_user_prompt, send_ingestion_data, setup_logger

logger = setup_logger("compact.log")


def main():
    logger.info(f"=== PostCompact hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("PostCompact input:\n%s", json.dumps(input_data, indent=2))

        compact_summary = input_data.get("compact_summary", "")

        send_ingestion_data(
            hook_name="PostCompact",
            request_payload=input_data,
            response_payload={"compact_summary": compact_summary},
            tags=None,
            guardrails=not AKTO_SYNC_MODE,
            logger=logger,
        )

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
