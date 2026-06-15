#!/usr/bin/env python3
# Codex CLI PostToolUse: ingests Bash, apply_patch, and MCP tool-call results.
# MCP traffic is wrapped in JSON-RPC 2.0 tools/call + result so Akto classifies it as MCP.

import json
import logging
import os
import re
import sys
import time
from typing import Any, Dict, Tuple
from urllib.parse import quote

from akto_machine_id import get_machine_id, get_username
from akto_ingestion_utility import (
    apply_warn_resubmit_flow,
    build_http_proxy_url,
    fingerprint,
    installer_headers,
    is_warn_behaviour,
    parse_guardrails_result,
    post_payload_json,
    resolve_session_info,
)

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.codex/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-post-tool.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
logger.addHandler(file_handler)

console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)
logger.addHandler(console_handler)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR_VALUE = os.getenv("AKTO_CONNECTOR_VALUE", "codexcli")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
# Built-in (e.g. Bash) tool traffic: mirror non-MCP path; ingestion off by default (same as claude-cli-hooks).
AKTO_INGEST_NON_MCP_TOOLS = os.getenv("AKTO_INGEST_NON_MCP_TOOLS", "false").lower() == "true"
# /mcp matches Akto's JsonRpcUtils.isMcpPath; non-MCP uses /{prefix}/{normalized-tool-name}.
MCP_INGEST_PATH = os.getenv("MCP_INGEST_PATH", "/mcp")
NON_MCP_TOOL_PATH_PREFIX = os.getenv("NON_MCP_TOOL_PATH_PREFIX", "/tool")
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_posttool_warn_pending.json")


def _detect_codex_api():
    base_url = os.getenv("OPENAI_BASE_URL")
    if base_url:
        return base_url.rstrip("/"), "/v1/responses"
    if os.getenv("OPENAI_API_KEY"):
        return "https://api.openai.com", "/v1/responses"
    return "https://chatgpt.com", "/backend-api/codex/responses"


_detected_host, CODEX_API_PATH = _detect_codex_api()
DEVICE_ID = os.getenv("DEVICE_ID") or get_machine_id()
if MODE == "atlas":
    CODEX_API_HOST = f"https://{DEVICE_ID}.ai-agent.{AKTO_CONNECTOR_VALUE}" if DEVICE_ID else _detected_host
    logger.info(
        f"MODE: {MODE}, Device ID: {DEVICE_ID}, CODEX_API_HOST: {CODEX_API_HOST}, "
        f"CODEX_API_PATH: {CODEX_API_PATH}"
    )
else:
    CODEX_API_HOST = _detected_host
    logger.info(f"MODE: {MODE}, CODEX_API_HOST: {CODEX_API_HOST}, CODEX_API_PATH: {CODEX_API_PATH}")


def parse_codex_tool(tool_name: str) -> Tuple[bool, str, str]:
    """Codex MCP tool naming is `mcp__<server>__<tool>` (verbatim from
    https://developers.openai.com/codex/hooks). Returns (is_mcp, server, mcp_tool)."""
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
    return json.dumps(
        {
            "jsonrpc": "2.0",
            "method": "tools/call",
            "params": {"name": mcp_tool_name, "arguments": _tool_arguments_for_jsonrpc(tool_input)},
            "id": request_id,
        }
    )


def build_tools_call_result_jsonrpc(tool_response: Any, request_id: int = 1) -> str:
    """JSON-RPC success body for the mirrored response (avoids MCP error-path handling on empty/invalid result)."""
    if isinstance(tool_response, dict):
        result_body: Any = tool_response
    else:
        result_body = {"output": tool_response}
    return json.dumps({"jsonrpc": "2.0", "id": request_id, "result": result_body})


def mcp_mirror_host(mcp_server_name: str) -> str:
    return f"{DEVICE_ID}.{AKTO_CONNECTOR_VALUE}.{mcp_server_name}"


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


def build_hook_tags(*, is_mcp: bool, tool_name: str) -> Dict[str, str]:
    tags: Dict[str, str] = {}
    if is_mcp:
        tags["mcp-server"] = "MCP Server"
        tags["mcp-client"] = AKTO_CONNECTOR_VALUE
    else:
        tags["gen-ai"] = "Gen AI"
        tags["ai-agent"] = AKTO_CONNECTOR_VALUE
        tags["tool-use"] = "Tool Execution"
        tags["tool_name"] = tool_name
    if MODE == "atlas":
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
    tags = build_hook_tags(is_mcp=is_mcp, tool_name=tool_name)

    if is_mcp:
        host = mcp_mirror_host(mcp_server_name)
    else:
        host = CODEX_API_HOST.replace("https://", "").replace("http://", "")

    req_headers: Dict[str, str] = {
        "host": host,
        "x-codex-hook": "PostToolUse",
        "content-type": "application/json",
    }
    if is_mcp and mcp_server_name:
        req_headers["x-mcp-server"] = mcp_server_name
    if session_info:
        req_headers.update(installer_headers(session_info))

    request_headers = json.dumps(req_headers)
    response_headers = json.dumps(
        {"x-codex-hook": "PostToolUse", "content-type": "application/json"}
    )
    if is_mcp:
        request_payload = build_tools_call_jsonrpc(mcp_tool_name, tool_input)
        response_payload = build_tools_call_result_jsonrpc(tool_response)
    else:
        request_payload = json.dumps({"body": {"toolName": tool_name, "toolArgs": tool_input}})
        response_payload = json.dumps({"body": {"result": tool_response}})

    path = MCP_INGEST_PATH if is_mcp else non_mcp_ingest_path(tool_name)
    return {
        "path": path,
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": get_username(),
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
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing tool result (fail-open)")
        return True, "", ""

    if is_mcp:
        logger.info(f"Validating MCP tools/call result for {mcp_tool_name} (server={mcp_server_name}, codexTool={tool_name})")
    else:
        logger.info(f"Validating PostToolUse result for: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {json.dumps(tool_input)[:500]}...")
        logger.debug(f"Tool response: {json.dumps(tool_response)[:500]}...")

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
            logger.info(f"Tool result ALLOWED by guardrails for {tool_name}")
        else:
            logger.warning(f"Tool result DENIED by guardrails for {tool_name}: {reason}")

        return allowed, reason, behaviour

    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, "", ""


def ingest_blocked_tool_result(
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
        logger.info(
            "Skipping non-MCP blocked PostToolUse ingestion (set AKTO_INGEST_NON_MCP_TOOLS=true to re-enable)"
        )
        return

    logger.info("Ingesting blocked PostToolUse data")
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
        request_body["responseHeaders"] = json.dumps(
            {
                "x-codex-hook": "PostToolUse",
                "x-blocked-by": "Akto Proxy",
                "content-type": "application/json",
            }
        )
        request_body["responsePayload"] = json.dumps(
            {
                "body": json.dumps(
                    {
                        "x-blocked-by": "Akto Proxy",
                        "reason": reason or "Policy violation",
                    }
                )
            }
        )
        request_body["statusCode"] = "403"
        request_body["status"] = "403"
        post_payload_json(
            build_http_proxy_url(
                guardrails=False, response_guardrails=False, ingest_data=True
            ),
            request_body,
            logger,
        )
        logger.info("Blocked PostToolUse ingestion successful")
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
        logger.info(
            "Skipping non-MCP tool response ingestion (set AKTO_INGEST_NON_MCP_TOOLS=true to re-enable)"
        )
        return

    if is_mcp:
        logger.info(f"Ingesting MCP tools/call result for {mcp_tool_name} (server={mcp_server_name}, codexTool={tool_name})")
    else:
        logger.info(f"Ingesting tool response for: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {json.dumps(tool_input)[:500]}...")
        logger.debug(f"Tool response: {json.dumps(tool_response)[:500]}...")

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
    logger.info(f"=== PostToolUse hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    session_info = resolve_session_info(input_data, logger)

    tool_name = str(input_data.get("tool_name") or "")
    tool_input = input_data.get("tool_input") or {}
    tool_response = input_data.get("tool_response") or {}
    tool_use_id = str(input_data.get("tool_use_id") or "")
    is_mcp, mcp_server_name, mcp_tool_name = parse_codex_tool(tool_name)

    if is_mcp:
        logger.info(f"Session: {input_data.get('session_id', '')}, Processing MCP tool response: {tool_name} (server={mcp_server_name}, mcpTool={mcp_tool_name})")
    else:
        logger.info(f"Session: {input_data.get('session_id', '')}, Processing non-MCP tool response: {tool_name}")

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
        fp = fingerprint({
            "t": tool_name,
            "u": tool_use_id or "",
            "i": tool_input,
            "r": tool_response,
        })
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
            logger.warning(
                f"BLOCKING tool result - Tool: {tool_name}, Reason: {gr_reason}"
            )
            print(json.dumps(output))
            ingest_blocked_tool_result(
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
