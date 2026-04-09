#!/usr/bin/env python3
"""
PostToolUseFailure hook - logs and ingests tool failure events.
Observability only, cannot block.
"""
import json
import sys

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, send_ingestion_data, setup_logger

logger = setup_logger("tools.log")


def main():
    logger.info(f"=== PostToolUseFailure hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("PostToolUseFailure input:\n%s", json.dumps(input_data, indent=2))

        tool_name = input_data.get("tool_name", "")
        send_ingestion_data(
            hook_name="PostToolUseFailure",
            request_payload={
                "tool_name": tool_name,
                "tool_input": input_data.get("tool_input", {}),
                "error": input_data.get("error", ""),
            },
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
