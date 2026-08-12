#!/usr/bin/env python3
"""
Akto Amp assistant response ingestion.

Amp event: agent.end. Observational only — the turn is already finished, so this
records the prompt/response pair for Akto's audit trail and never blocks.
"""

import json
import sys

from akto_amp_common import (
    AKTO_SYNC_MODE,
    MODE,
    api_host,
    build_hook_tags,
    build_mirror_payload,
    heartbeat,
    ingest,
    read_input,
    resolve_session_info,
    setup_logger,
)

HOOK_NAME = "Stop"
RESPONSE_INGEST_PATH = "/v1/messages"

logger = setup_logger("validate-response.log")


def main() -> None:
    logger.info(f"=== agent.end hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    heartbeat(logger)
    input_data = read_input(logger)
    prompt = str(input_data.get("prompt") or "")
    response = str(input_data.get("response") or "")
    status = str(input_data.get("status") or "done")

    if not response.strip():
        logger.info(f"Empty assistant response (status={status}), skipping ingestion")
        sys.exit(0)

    session_info = resolve_session_info(input_data, logger)
    logger.info(f"Ingesting turn (prompt: {len(prompt)} chars, response: {len(response)} chars)")

    ingest(
        build_mirror_payload(
            path=RESPONSE_INGEST_PATH,
            hook_name=HOOK_NAME,
            request_payload=json.dumps({"body": prompt.strip()}),
            response_payload=json.dumps({"body": response.strip()}),
            tags=build_hook_tags(is_mcp=False),
            host=api_host(),
            session_info=session_info,
            status_code="200" if status == "done" else "500",
        ),
        logger,
    )
    sys.exit(0)


if __name__ == "__main__":
    main()
