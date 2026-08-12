#!/usr/bin/env python3
"""
Akto Amp prompt validation.

Amp event: agent.start. Blocking — the plugin cancels the turn when this prints a
block decision, so the prompt never reaches the model.
"""

import json
import sys

from akto_amp_common import (
    AKTO_SYNC_MODE,
    MODE,
    api_host,
    apply_warn_resubmit_flow,
    block_reason_text,
    build_hook_tags,
    build_mirror_payload,
    call_guardrails,
    emit_allow,
    emit_block,
    fingerprint,
    heartbeat,
    ingest,
    mark_blocked,
    read_input,
    resolve_session_info,
    setup_logger,
)

HOOK_NAME = "UserPromptSubmit"
PROMPT_INGEST_PATH = "/v1/messages"

logger = setup_logger("validate-prompt.log")


def build_prompt_payload(prompt: str, session_info: dict) -> dict:
    return build_mirror_payload(
        path=PROMPT_INGEST_PATH,
        hook_name=HOOK_NAME,
        request_payload=json.dumps({"body": prompt.strip()}),
        response_payload=json.dumps({}),
        tags=build_hook_tags(is_mcp=False),
        host=api_host(),
        session_info=session_info,
    )


def main() -> None:
    logger.info(f"=== agent.start hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    heartbeat(logger)
    input_data = read_input(logger)
    prompt = str(input_data.get("prompt") or "")

    if not prompt.strip():
        logger.info("Empty prompt, allowing")
        sys.exit(0)

    session_info = resolve_session_info(input_data, logger, is_prompt_hook=True)
    logger.info(f"Processing prompt (length: {len(prompt)} chars)")

    if not AKTO_SYNC_MODE:
        # Observational only: record the prompt and get out of the way.
        ingest(build_prompt_payload(prompt, session_info), logger)
        sys.exit(0)

    verdict = call_guardrails(build_prompt_payload(prompt, session_info), logger)
    allowed = apply_warn_resubmit_flow(verdict, "prompt", fingerprint("prompt", prompt), logger)

    if not allowed:
        emit_block(block_reason_text(verdict, "prompt"), logger)
        ingest(
            mark_blocked(build_prompt_payload(prompt, session_info), verdict.reason, is_mcp=False),
            logger,
        )
        sys.exit(0)

    logger.info("Prompt allowed")
    emit_allow()
    sys.exit(0)


if __name__ == "__main__":
    main()
