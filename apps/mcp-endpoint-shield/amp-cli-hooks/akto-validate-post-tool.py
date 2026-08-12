#!/usr/bin/env python3
"""
Akto Amp PostToolUse ingestion.

Amp event: tool.result. Observational only — the tool has already run, so this
never blocks; it feeds Akto's audit trail with the request/response pair.
"""

import json
import sys

from akto_amp_common import (
    AKTO_SYNC_MODE,
    MCP_INGEST_PATH,
    MODE,
    api_host,
    build_hook_tags,
    build_mirror_payload,
    build_tools_call_jsonrpc,
    build_tools_call_result_jsonrpc,
    heartbeat,
    ingest,
    mcp_mirror_host,
    non_mcp_ingest_path,
    parse_amp_tool,
    read_input,
    resolve_session_info,
    setup_logger,
)

HOOK_NAME = "PostToolUse"

logger = setup_logger("validate-post-tool.log")


def build_payload(
    tool_name: str,
    tool_input,
    tool_response,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    status: str,
    session_info: dict,
) -> dict:
    # A failed tool call is mirrored as a 500 so it is distinguishable in Akto.
    status_code = "200" if status == "done" else "500"

    if is_mcp:
        return build_mirror_payload(
            path=MCP_INGEST_PATH,
            hook_name=HOOK_NAME,
            request_payload=build_tools_call_jsonrpc(mcp_tool_name, tool_input),
            response_payload=build_tools_call_result_jsonrpc(tool_response),
            tags=build_hook_tags(is_mcp=True),
            host=mcp_mirror_host(mcp_server_name),
            extra_request_headers={"x-mcp-server": mcp_server_name},
            session_info=session_info,
            status_code=status_code,
        )

    tags = build_hook_tags(is_mcp=False)
    tags["tool-use"] = "Tool Execution"
    return build_mirror_payload(
        path=non_mcp_ingest_path(tool_name),
        hook_name=HOOK_NAME,
        request_payload=json.dumps({"body": {"toolName": tool_name, "toolArgs": tool_input}}),
        response_payload=json.dumps({"body": {"result": tool_response}}),
        tags=tags,
        host=api_host(),
        session_info=session_info,
        status_code=status_code,
    )


def main() -> None:
    logger.info(f"=== tool.result hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    heartbeat(logger)
    input_data = read_input(logger)
    tool_name = str(input_data.get("tool_name") or "")
    tool_input = input_data.get("tool_input") or {}
    tool_response = input_data.get("tool_response")
    status = str(input_data.get("status") or "done")

    if not tool_name:
        logger.info("No tool name, skipping ingestion")
        sys.exit(0)

    if tool_response is None or tool_response == {} or tool_response == "":
        logger.info(f"Empty tool response for {tool_name}, skipping ingestion")
        sys.exit(0)

    session_info = resolve_session_info(input_data, logger)
    is_mcp, mcp_server_name, mcp_tool_name = parse_amp_tool(tool_name)

    logger.info(f"Ingesting tool response: {tool_name} (mcp={is_mcp}, status={status})")

    ingest(
        build_payload(
            tool_name,
            tool_input,
            tool_response,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            status=status,
            session_info=session_info,
        ),
        logger,
    )
    sys.exit(0)


if __name__ == "__main__":
    main()
