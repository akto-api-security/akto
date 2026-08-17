#!/usr/bin/env python3
"""
Akto Amp assistant response validation and ingestion.

Amp event: agent.end. Evaluates the assistant's reply against RESPONSE guardrails
and records a violation as a 403, matching the Stop hook in claude-cli-hooks.

It cannot BLOCK: Amp's agent.end result is only `{action:'continue'}`, so the reply
cannot be retracted or redacted once generated — the text has already streamed to
the user. A violation is therefore detected, flagged in Guardrail Activity, and
surfaced to the user via a notification, but not suppressed.

Set AKTO_RESPONSE_GUARDRAILS=false to ingest without evaluating.
"""

import json
import sys

from akto_amp_common import (
    AKTO_RESPONSE_GUARDRAILS,
    AKTO_SYNC_MODE,
    MODE,
    api_host,
    build_hook_tags,
    build_mirror_payload,
    call_guardrails,
    emit_block,
    heartbeat,
    ingest,
    mark_blocked,
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

    payload = build_mirror_payload(
        path=RESPONSE_INGEST_PATH,
        hook_name=HOOK_NAME,
        request_payload=json.dumps({"body": prompt.strip()}),
        response_payload=json.dumps({"body": response.strip()}),
        tags=build_hook_tags(is_mcp=False),
        host=api_host(),
        session_info=session_info,
        status_code="200" if status == "done" else "500",
    )

    if AKTO_SYNC_MODE and AKTO_RESPONSE_GUARDRAILS:
        verdict = call_guardrails(
            payload, logger, guardrails=False, response_guardrails=True, ingest_data=False
        )
        if not verdict.allowed:
            reason = verdict.reason or "Policy violation"
            # Reported, not blocked: the reply has already reached the user.
            emit_block(f"Response flagged: {reason}", logger)
            ingest(mark_blocked(payload, reason, is_mcp=False), logger)
            sys.exit(0)

    ingest(payload, logger)
    sys.exit(0)


if __name__ == "__main__":
    main()
