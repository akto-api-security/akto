#!/usr/bin/env python3

import os
import json
import sys

if not os.getenv("LOG_DIR"):
    os.environ["LOG_DIR"] = os.path.expanduser("~/.claude/akto/logs")

from akto_ingestion_utility import MODE, read_file_content, send_ingestion_data, setup_logger

logger = setup_logger("session.log")


def main():
    logger.info(f"=== InstructionsLoaded hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("InstructionsLoaded input:\n%s", json.dumps(input_data, indent=2))

        file_path = input_data.get("file_path", "")
        file_content = read_file_content(file_path, logger)

        send_ingestion_data(
            hook_name="InstructionsLoaded",
            request_payload={**input_data, "file_content": file_content},
            response_payload={},
            tags=None,
            guardrails=False,
            logger=logger,
        )

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
