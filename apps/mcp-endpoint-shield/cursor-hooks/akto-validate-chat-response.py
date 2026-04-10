#!/usr/bin/env python3
"""
Cursor Chat After Hook - Response Ingestion via Akto HTTP Proxy API
Logs agent responses for monitoring and analysis.
Triggered by afterAgentResponse hook.
NOTE: This hook is observational only - cannot block or modify responses.
"""
import json
import sys

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, send_ingestion_data, setup_logger

logger = setup_logger("akto-validate-chat-response.log")


def main():
    logger.info(f"=== Chat Response Hook execution started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        print(json.dumps({}))
        sys.exit(0)

    response_text = input_data.get("text", "")

    if not response_text.strip():
        logger.warning("Empty response received")
        print(json.dumps({}))
        sys.exit(0)

    send_ingestion_data(
        hook_name="afterAgentResponse",
        request_payload={},
        response_payload=response_text,
        guardrails=not AKTO_SYNC_MODE,
        logger=logger,
    )

    print(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
