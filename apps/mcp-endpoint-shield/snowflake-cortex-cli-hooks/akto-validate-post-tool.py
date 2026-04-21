#!/usr/bin/env python3

import json
import logging
import sys

from cortex_common import (
    build_http_proxy_url,
    build_proxy_payload,
    configure_logger,
    extract_mcp_server_name,
    get_akto_url,
    is_sync_mode,
    post_payload_json,
)

logger = configure_logger("validate-post-tool.log")


def main():
    logger.info("PostToolUse hook started")
    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error("invalid json: %s", e)
        sys.exit(0)

    tool_name = str(input_data.get("tool_name") or "")
    tool_input = input_data.get("tool_input") or {}
    tool_response = input_data.get("tool_response") or input_data.get("toolResult") or {}
    mcp_server = extract_mcp_server_name(tool_name)

    url = get_akto_url()
    if not url:
        sys.exit(0)

    payload = build_proxy_payload(
        hook_event="PostToolUse",
        path="/cortex/v1/tool/" + (tool_name.replace("/", "_") or "unknown"),
        request_payload_obj={"toolName": tool_name, "toolArgs": tool_input},
        response_payload_obj={"result": tool_response},
        extra_tags={"mcp_server_name": mcp_server, "tool-use": "Tool Execution"},
    )

    try:
        post_payload_json(
            build_http_proxy_url(guardrails=not is_sync_mode(), ingest_data=True),
            payload,
            logger,
        )
    except Exception as e:
        logger.error("ingest failed: %s", e)

    sys.exit(0)


if __name__ == "__main__":
    main()
