#!/usr/bin/env python3
"""
ConfigChange hook - validates config changes via guardrails.
Blocks via decision = "block" if guardrails denies.
"""
import json
import sys

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, send_ingestion_data, setup_logger

logger = setup_logger("config.log")


def main():
    logger.info(f"=== ConfigChange hook started - Mode: {MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        logger.info("ConfigChange input:\n%s", json.dumps(input_data, indent=2))

        result = send_ingestion_data(
            hook_name="ConfigChange",
            request_payload=input_data,
            response_payload={},
            tags=None,
            guardrails=not AKTO_SYNC_MODE,
            logger=logger,
        )

        allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
        if not allowed:
            reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
            logger.warning(f"BLOCKING ConfigChange: {reason}")
            print(json.dumps({"decision": "block", "reason": reason}))
            send_ingestion_data(
                hook_name="ConfigChange",
                request_payload=input_data,
                response_payload={"reason": reason or "Policy violation", "blockedBy": "Akto Proxy"},
                tags=None,
                guardrails=False,
                status_code="403",
                logger=logger,
            )

    except Exception as e:
        logger.error(f"Main error: {e}")

    sys.exit(0)


if __name__ == "__main__":
    main()
