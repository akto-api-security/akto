#!/usr/bin/env python3

import json
import logging
import sys

from akto_heartbeat import send_heartbeat
from cortex_common import (
    build_http_proxy_url,
    build_proxy_payload,
    configure_logger,
    extract_user_prompt,
    get_akto_url,
    get_effective_log_dir,
    is_sync_mode,
    parse_guardrails_allowed,
    post_payload_json,
)

logger = configure_logger("validate-prompt.log")
send_heartbeat(get_effective_log_dir(), logger)


def ingest_prompt_event(prompt: str, reason: str, blocked: bool):
    url = get_akto_url()
    if not url:
        return
    status = "403" if blocked else "200"
    body = {"prompt": prompt, "reason": reason} if reason else {"prompt": prompt}
    payload = build_proxy_payload(
        hook_event="UserPromptSubmit",
        path="/cortex/v1/prompt",
        request_payload_obj={"body": prompt.strip()},
        response_payload_obj=body,
        status_code=status,
        status=status,
    )
    if blocked:
        payload["responseHeaders"] = json.dumps(
            {
                "x-cortex-hook": "UserPromptSubmit",
                "x-blocked-by": "Akto Proxy",
                "content-type": "application/json",
            }
        )
    try:
        post_payload_json(
            build_http_proxy_url(guardrails=False, ingest_data=True),
            payload,
            logger,
        )
    except Exception as e:
        logger.error("ingest prompt event failed: %s", e)


def main():
    logger.info("UserPromptSubmit hook started")
    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error("invalid json: %s", e)
        sys.exit(0)

    prompt = extract_user_prompt(input_data)
    if not prompt.strip():
        sys.exit(0)

    url = get_akto_url()
    if not url or not is_sync_mode():
        sys.exit(0)

    payload = build_proxy_payload(
        hook_event="UserPromptSubmit",
        path="/cortex/v1/prompt",
        request_payload_obj={"body": prompt.strip()},
        response_payload_obj={},
    )
    try:
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=False),
            payload,
            logger,
        )
    except Exception as e:
        logger.error("guardrails call failed: %s", e)
        sys.exit(0)

    allowed, reason, _behaviour = parse_guardrails_allowed(result)
    if not allowed:
        logger.warning("guardrails violation (monitoring; UserPromptSubmit cannot block): %s", reason)
        ingest_prompt_event(prompt, reason or "Policy violation", blocked=True)
        out = {
            "systemMessage": f"Akto: prompt matched guardrails ({reason or 'policy'}). Cortex may still proceed; event was recorded.",
        }
        print(json.dumps(out))
    else:
        ingest_prompt_event(prompt, "", blocked=False)

    sys.exit(0)


if __name__ == "__main__":
    main()
