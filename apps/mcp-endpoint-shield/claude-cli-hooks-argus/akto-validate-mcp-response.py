#!/usr/bin/env python3
import json
import logging
import os
import re
import sys
import time
from urllib.parse import quote
from typing import Any, Dict, Tuple

from akto_helpers import get_device_ip
from akto_ingestion_utility import (
    apply_warn_resubmit_flow,
    build_http_proxy_url,
    fingerprint,
    installer_headers,
    is_warn_behaviour,
    parse_guardrails_result,
    post_payload_json,
    resolve_host_url,
    resolve_session_info,
)

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.claude/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-mcp-response.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
logger.addHandler(file_handler)

console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)
logger.addHandler(console_handler)

AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_HOST = resolve_host_url("https://api.anthropic.com", legacy_env="AKTO_HOST")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
# Non-MCP PostToolUse ingestion is off by default; set AKTO_INGEST_NON_MCP_TOOLS=true to send it.
AKTO_INGEST_NON_MCP_TOOLS = os.getenv("AKTO_INGEST_NON_MCP_TOOLS", "false").lower() == "true"
AKTO_CONNECTOR_VALUE = os.getenv("AKTO_CONNECTOR_VALUE", "claudecli")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "AGENTIC")
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_posttool_warn_pending.json")
MCP_INGEST_PATH = os.getenv("MCP_INGEST_PATH", "/mcp")
NON_MCP_TOOL_PATH_PREFIX = os.getenv("NON_MCP_TOOL_PATH_PREFIX", "/tool")

DEVICE_IP = get_device_ip()
HOST_HEADER = AKTO_HOST.replace("https://", "").replace("http://", "")

logger.info(f"AKTO_HOST: {AKTO_HOST}, DEVICE_IP: {DEVICE_IP}")


# Mirror MCP tools/call + JSON-RPC result so runtime can classify MCP traffic (McpRequestResponseUtils.isMcpRequest).


def normalize_tool_name_for_url_path(tool_name: str) -> str:
    s = (tool_name or "unknown").strip()
    s = re.sub(r"[^a-zA-Z0-9._~-]+", "-", s)
    s = re.sub(r"-+", "-", s).strip("-")
    if not s:
        s = "unknown"
    return quote(s, safe=".-_~")


def non_mcp_ingest_path(tool_name: str) -> str:
    fixed = (os.getenv("NON_MCP_INGEST_PATH") or "").strip()
    if fixed:
        return fixed if fixed.startswith("/") else "/" + fixed
    prefix = (NON_MCP_TOOL_PATH_PREFIX or "/tool").strip()
    if not prefix.startswith("/"):
        prefix = "/" + prefix
    prefix = prefix.rstrip("/")
    if not prefix:
        prefix = "/tool"
    return f"{prefix}/{normalize_tool_name_for_url_path(tool_name)}"


def parse_claude_tool(tool_name: str) -> Tuple[bool, str, str]:
    if not tool_name.startswith("mcp__"):
        return False, "", ""
    parts = tool_name.split("__")
    if len(parts) < 3:
        return False, "", ""
    server = parts[1]
    mcp_tool = "__".join(parts[2:])
    if not server or not mcp_tool:
        return False, "", ""
    return True, server, mcp_tool


def _tool_arguments_for_jsonrpc(tool_input: Any) -> Dict[str, Any]:
    if isinstance(tool_input, dict):
        return tool_input
    if tool_input is None:
        return {}
    return {"input": tool_input}


def build_tools_call_jsonrpc(mcp_tool_name: str, tool_input: Any, request_id: int = 1) -> str:
    return json.dumps({
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {"name": mcp_tool_name, "arguments": _tool_arguments_for_jsonrpc(tool_input)},
        "id": request_id,
    })


def build_tools_call_result_jsonrpc(tool_response: Any, request_id: int = 1) -> str:
    """JSON-RPC success body for the mirrored response (avoids MCP error-path handling on empty/invalid result)."""
    if isinstance(tool_response, dict):
        result_body: Any = tool_response
    else:
        result_body = {"output": tool_response}
    return json.dumps({"jsonrpc": "2.0", "id": request_id, "result": result_body})


def build_hook_tags(*, is_mcp: bool) -> Dict[str, str]:
    if is_mcp:
        tags = {"mcp-server": "MCP Server", "mcp-client": AKTO_CONNECTOR_VALUE}
    else:
        tags = {"gen-ai": "Gen AI", "ai-agent": AKTO_CONNECTOR_VALUE}
    tags["source"] = CONTEXT_SOURCE
    return tags


def build_ingestion_payload(
    tool_name: str,
    tool_input: Any,
    tool_response: Any,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    session_info: dict = None,
) -> Dict[str, Any]:
    tags = build_hook_tags(is_mcp=is_mcp)

    req_hdr: Dict[str, str] = {
        "host": HOST_HEADER,
        "x-claude-hook": "PostToolUse",
        "content-type": "application/json",
    }
    if is_mcp and mcp_server_name:
        req_hdr["x-mcp-server"] = mcp_server_name
    if session_info:
        req_hdr.update(installer_headers(session_info))

    if is_mcp:
        request_payload = build_tools_call_jsonrpc(mcp_tool_name, tool_input)
        response_payload = build_tools_call_result_jsonrpc(tool_response)
    else:
        request_payload = json.dumps({"body": {"toolName": tool_name, "toolArgs": tool_input}})
        response_payload = json.dumps({"body": {"result": tool_response}})

    path = MCP_INGEST_PATH if is_mcp else non_mcp_ingest_path(tool_name)
    return {
        "path": path,
        "requestHeaders": json.dumps(req_hdr),
        "responseHeaders": json.dumps(
            {"x-claude-hook": "PostToolUse", "content-type": "application/json"}
        ),
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": DEVICE_IP,
        "destIp": "127.0.0.1",
        "time": str(int(time.time() * 1000)),
        "statusCode": "200",
        "type": "HTTP/1.1",
        "status": "200",
        "akto_account_id": "1000000",
        "akto_vxlan_id": 0,
        "is_pending": "false",
        "source": "MIRRORING",
        "direction": None,
        "process_id": None,
        "socket_id": None,
        "daemonset_id": None,
        "enabled_graph": None,
        "tag": json.dumps(tags),
        "metadata": json.dumps(tags),
        "contextSource": CONTEXT_SOURCE,
    }


def call_guardrails(
    tool_name: str,
    tool_input: Any,
    tool_response: Any,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    session_info: dict = None,
) -> Tuple[bool, str, str]:
    if not tool_input or not tool_response:
        return True, "", ""

    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing request (fail-open)")
        return True, "", ""

    if is_mcp:
        logger.info(
            f"Validating MCP tools/call result for {mcp_tool_name} (server={mcp_server_name}, claudeTool={tool_name})"
        )
    else:
        logger.info(f"Validating built-in / non-MCP tool result: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {json.dumps(tool_input)}")
        logger.debug(f"Tool response: {json.dumps(tool_response)}")

    try:
        request_body = build_ingestion_payload(
            tool_name,
            tool_input,
            tool_response,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            session_info=session_info,
        )
        result = post_payload_json(
            build_http_proxy_url(
                guardrails=False,
                response_guardrails=True,
                ingest_data=False,
            ),
            request_body,
            logger,
        )

        allowed, reason, behaviour = parse_guardrails_result(result)
        if allowed:
            logger.info(f"Request ALLOWED for {tool_name}")
        else:
            logger.warning(f"Request DENIED for {tool_name}: {reason}")

        return allowed, reason, behaviour

    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, "", ""


def ingest_blocked_request(
    tool_name: str,
    tool_input: Any,
    tool_response: Any,
    reason: str,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    session_info: dict = None,
):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    if not is_mcp and not AKTO_INGEST_NON_MCP_TOOLS:
        logger.info("Skipping non-MCP blocked-request ingestion (set AKTO_INGEST_NON_MCP_TOOLS=true to re-enable)")
        return

    logger.info("Ingesting blocked request data")
    try:
        request_body = build_ingestion_payload(
            tool_name,
            tool_input,
            tool_response,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            session_info=session_info,
        )
        request_body["responseHeaders"] = json.dumps({
            "x-claude-hook": "PostToolUse",
            "x-blocked-by": "Akto Proxy",
            "content-type": "application/json",
        })
        request_body["responsePayload"] = json.dumps({
            "body": json.dumps({
                "x-blocked-by": "Akto Proxy",
                "reason": reason or "Policy violation",
            })
        })
        request_body["statusCode"] = "403"
        request_body["status"] = "403"
        post_payload_json(
            build_http_proxy_url(guardrails=False, ingest_data=True),
            request_body,
            logger,
        )
        logger.info("Blocked request ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def send_ingestion_data(
    tool_name: str,
    tool_input: Any,
    tool_response: Any,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    session_info: dict = None,
):
    if not AKTO_DATA_INGESTION_URL:
        logger.info("AKTO_DATA_INGESTION_URL not set, skipping ingestion")
        return

    if not tool_input:
        logger.info("Skipping ingestion due to empty tool input")
        return

    if not tool_response:
        logger.info("Skipping ingestion due to empty tool response")
        return

    if not is_mcp and not AKTO_INGEST_NON_MCP_TOOLS:
        logger.info("Skipping non-MCP tool response ingestion (set AKTO_INGEST_NON_MCP_TOOLS=true to re-enable)")
        return

    if is_mcp:
        logger.info(
            f"Ingesting MCP tools/call result for {mcp_tool_name} (server={mcp_server_name}, claudeTool={tool_name})"
        )
    else:
        logger.info(f"Ingesting non-MCP tool result (gen-ai only): {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {json.dumps(tool_input)}")
        logger.debug(f"Tool response: {json.dumps(tool_response)}")

    try:
        request_body = build_ingestion_payload(
            tool_name,
            tool_input,
            tool_response,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            session_info=session_info,
        )
        post_payload_json(
            build_http_proxy_url(
                guardrails=False,
                response_guardrails=not AKTO_SYNC_MODE,
                ingest_data=True,
            ),
            request_body,
            logger,
        )
        logger.info("Tool response ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info(f"=== Hook execution started - Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    session_info = resolve_session_info(input_data, logger)

    tool_name = str(input_data.get("tool_name") or "")
    is_mcp, mcp_server_name, mcp_tool_name = parse_claude_tool(tool_name)
    tool_input = input_data.get("tool_input") or {}
    tool_response = input_data.get("tool_response") or {}

    if is_mcp:
        logger.info(f"Processing MCP tool response: {tool_name} (server={mcp_server_name}, mcpTool={mcp_tool_name})")
    else:
        logger.info(f"Processing non-MCP tool response (gen-ai only): {tool_name}")

    if AKTO_SYNC_MODE:
        gr_allowed, gr_reason, behaviour = call_guardrails(
            tool_name,
            tool_input,
            tool_response,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            session_info=session_info,
        )
        fp = fingerprint({"i": tool_input})
        allowed, _ = apply_warn_resubmit_flow(
            WARN_STATE_PATH, fp, gr_allowed, gr_reason, behaviour, logger
        )

        if not allowed:
            if is_warn_behaviour(behaviour):
                block_reason = (
                    "Warning!!, tool result blocked, please review it. Send again to bypass. "
                    f"Reason for blocking: {gr_reason}"
                )
            else:
                block_reason = f"Tool result blocked: {gr_reason}"

            output = {
                "decision": "block",
                "reason": block_reason,
                "hookSpecificOutput": {
                    "hookEventName": "PostToolUse",
                    "additionalContext": gr_reason or "Policy violation",
                },
            }
            logger.warning(f"BLOCKING tool result - Tool: {tool_name}, Reason: {gr_reason}")
            print(json.dumps(output))
            ingest_blocked_request(
                tool_name,
                tool_input,
                tool_response,
                gr_reason,
                is_mcp=is_mcp,
                mcp_server_name=mcp_server_name,
                mcp_tool_name=mcp_tool_name,
                session_info=session_info,
            )
            sys.exit(0)

    send_ingestion_data(
        tool_name,
        tool_input,
        tool_response,
        is_mcp=is_mcp,
        mcp_server_name=mcp_server_name,
        mcp_tool_name=mcp_tool_name,
        session_info=session_info,
    )

    sys.exit(0)


if __name__ == "__main__":
    main()
