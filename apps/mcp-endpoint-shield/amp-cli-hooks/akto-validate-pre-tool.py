#!/usr/bin/env python3
"""
Akto Amp PreToolUse validation.

Amp event: tool.call. Blocking — a block decision becomes `reject-and-continue`
(the tool never runs), and an updatedInput becomes `modify`.

Handles both MCP tools (`mcp__<server>__<tool>`, mirrored to /mcp as JSON-RPC)
and Amp built-ins such as shell_command (mirrored to /tool/<tool-name>).
"""

import json
import sys

from akto_amp_common import (
    AKTO_INGEST_ON_REQUEST,
    AKTO_SYNC_MODE,
    MCP_INGEST_PATH,
    MODE,
    api_host,
    apply_warn_resubmit_flow,
    block_reason_text,
    build_hook_tags,
    build_mirror_payload,
    build_tools_call_jsonrpc,
    call_guardrails,
    emit_allow,
    emit_block,
    extract_tool_input_from_modified_payload,
    fingerprint,
    heartbeat,
    ingest,
    mark_blocked,
    mcp_mirror_host,
    non_mcp_ingest_path,
    parse_amp_tool,
    read_input,
    resolve_session_info,
    setup_logger,
)

HOOK_NAME = "PreToolUse"

logger = setup_logger("validate-pre-tool.log")


def build_tool_payload(
    tool_name: str,
    tool_input,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    session_info: dict,
) -> dict:
    if is_mcp:
        return build_mirror_payload(
            path=MCP_INGEST_PATH,
            hook_name=HOOK_NAME,
            request_payload=build_tools_call_jsonrpc(mcp_tool_name, tool_input),
            response_payload=json.dumps({}),
            tags=build_hook_tags(is_mcp=True),
            host=mcp_mirror_host(mcp_server_name),
            extra_request_headers={"x-mcp-server": mcp_server_name},
            session_info=session_info,
        )

    return build_mirror_payload(
        path=non_mcp_ingest_path(tool_name),
        hook_name=HOOK_NAME,
        request_payload=json.dumps({"body": tool_input, "toolName": tool_name}),
        response_payload=json.dumps({}),
        tags=build_hook_tags(is_mcp=False),
        host=api_host(),
        session_info=session_info,
    )


def main() -> None:
    logger.info(f"=== tool.call hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    heartbeat(logger)
    input_data = read_input(logger)
    tool_name = str(input_data.get("tool_name") or "")
    tool_input = input_data.get("tool_input") or {}

    if not tool_name:
        logger.info("No tool name, allowing")
        sys.exit(0)

    session_info = resolve_session_info(input_data, logger)
    is_mcp, mcp_server_name, mcp_tool_name = parse_amp_tool(tool_name)

    if is_mcp:
        logger.info(
            f"Processing MCP tool request: {tool_name} "
            f"(server={mcp_server_name}, mcpTool={mcp_tool_name})"
        )
    else:
        logger.info(f"Processing built-in tool request: {tool_name}")

    payload_args = dict(
        is_mcp=is_mcp,
        mcp_server_name=mcp_server_name,
        mcp_tool_name=mcp_tool_name,
        session_info=session_info,
    )

    if not AKTO_SYNC_MODE:
        ingest(build_tool_payload(tool_name, tool_input, **payload_args), logger)
        sys.exit(0)

    if not tool_input:
        logger.info("Empty tool input, allowing")
        sys.exit(0)

    verdict = call_guardrails(
        build_tool_payload(tool_name, tool_input, **payload_args),
        logger,
        ingest_data=AKTO_INGEST_ON_REQUEST,
    )
    allowed = apply_warn_resubmit_flow(
        verdict, "pretool", fingerprint("tool", tool_name, tool_input), logger
    )

    if not allowed:
        emit_block(block_reason_text(verdict, "tool request"), logger)
        # A blocked call is always ingested, MCP or not. AKTO_INGEST_NON_MCP_TOOLS
        # suppresses routine built-in tool traffic, not security violations — a block
        # that left no record would be invisible in the dashboard. A blocked call never
        # reaches tool.result either, so this is its only record.
        ingest(
            mark_blocked(
                build_tool_payload(tool_name, tool_input, **payload_args),
                verdict.reason,
                is_mcp=is_mcp,
            ),
            logger,
        )
        sys.exit(0)

    if verdict.modified and verdict.modified_payload:
        new_input = extract_tool_input_from_modified_payload(
            verdict.modified_payload, is_mcp=is_mcp, fallback=tool_input, logger=logger
        )
        if new_input is not tool_input:
            logger.info(f"Applying guardrail-modified tool_input for {tool_name}")
            emit_allow(new_input)
            sys.exit(0)

    logger.info(f"Tool request allowed for {tool_name}")
    emit_allow()
    sys.exit(0)


if __name__ == "__main__":
    main()
