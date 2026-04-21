#!/usr/bin/env python3

import json
import logging
import sys
from typing import Any

from akto_heartbeat import send_heartbeat
from cortex_common import (
    build_http_proxy_url,
    build_proxy_payload,
    configure_logger,
    extract_mcp_server_name,
    get_akto_url,
    get_effective_log_dir,
    is_sync_mode,
    parse_guardrails_allowed,
    post_payload_json,
)

logger = configure_logger("validate-pre-tool.log")
send_heartbeat(get_effective_log_dir(), logger)


def ingest_blocked(tool_name: str, tool_input: Any, mcp_server: str, reason: str):
    url = get_akto_url()
    if not url or not is_sync_mode():
        return
    payload = build_proxy_payload(
        hook_event="PreToolUse",
        path="/cortex/v1/tool/" + tool_name.replace("/", "_"),
        request_payload_obj={"body": tool_input, "toolName": tool_name},
        response_payload_obj={"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"},
        status_code="403",
        status="403",
        extra_tags={"mcp_server_name": mcp_server},
    )
    payload["responseHeaders"] = json.dumps(
        {
            "x-cortex-hook": "PreToolUse",
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
        logger.error("blocked ingest failed: %s", e)


def main():
    logger.info("PreToolUse hook started")
    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error("invalid json: %s", e)
        sys.exit(0)

    tool_name = str(input_data.get("tool_name") or "")
    tool_input = input_data.get("tool_input") or {}
    mcp_server = extract_mcp_server_name(tool_name)

    if not tool_input:
        sys.exit(0)

    url = get_akto_url()
    if not url:
        sys.exit(0)

    if not is_sync_mode():
        sys.exit(0)

    payload = build_proxy_payload(
        hook_event="PreToolUse",
        path="/cortex/v1/tool/" + (tool_name.replace("/", "_") or "unknown"),
        request_payload_obj={"body": tool_input, "toolName": tool_name},
        response_payload_obj={},
        extra_tags={"mcp_server_name": mcp_server},
    )
    try:
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=False),
            payload,
            logger,
        )
    except Exception as e:
        logger.error("guardrails failed: %s", e)
        sys.exit(0)

    allowed, reason, _ = parse_guardrails_allowed(result)
    if not allowed:
        block_reason = reason or "Policy violation"
        print(json.dumps({"decision": "block", "reason": f"Blocked by Akto Guardrails: {block_reason}"}))
        ingest_blocked(tool_name, tool_input, mcp_server, block_reason)
        sys.exit(2)

    sys.exit(0)


if __name__ == "__main__":
    main()
