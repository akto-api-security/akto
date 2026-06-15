#!/usr/bin/env python3

import json
import logging
import os
import re
import sys
import time
from urllib.parse import quote
from typing import Any, Dict, Optional, Tuple

from akto_machine_id import get_machine_id, get_username
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

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.gemini/akto/chat-logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-pre-tool.log"))
    file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    logger.addHandler(console_handler)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_INGEST_NON_MCP_TOOLS = os.getenv("AKTO_INGEST_NON_MCP_TOOLS", "false").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "gemini_cli")
AKTO_CONNECTOR_VALUE = os.getenv("AKTO_CONNECTOR_VALUE", "geminicli")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_pretool_warn_pending.json")
MCP_INGEST_PATH = os.getenv("MCP_INGEST_PATH", "/mcp")
NON_MCP_TOOL_PATH_PREFIX = os.getenv("NON_MCP_TOOL_PATH_PREFIX", "/tool")

DEVICE_ID = os.getenv("DEVICE_ID") or get_machine_id()

GEMINI_API_URL = resolve_host_url("https://generativelanguage.googleapis.com", legacy_env="GEMINI_API_URL")
logger.info(f"MODE: {MODE}, GEMINI_API_URL: {GEMINI_API_URL}")


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


# Gemini MCP tools are named mcp_<server>_<tool>. Server name is the first segment after "mcp_";
# the remainder (which may itself contain underscores) is the tool name.
# When Gemini supplies mcp_context with server_name/tool_name, prefer those to avoid ambiguity
# for server names that contain underscores.
def parse_gemini_tool(tool_name: str, mcp_context: Optional[Dict[str, Any]] = None) -> Tuple[bool, str, str]:
    if isinstance(mcp_context, dict) and mcp_context:
        server = str(mcp_context.get("server_name") or mcp_context.get("serverName") or "").strip()
        mcp_tool = str(mcp_context.get("tool_name") or mcp_context.get("toolName") or "").strip()
        if server and mcp_tool:
            return True, server, mcp_tool

    if not tool_name.startswith("mcp_"):
        return False, "", ""
    rest = tool_name[4:]
    server, _, mcp_tool = rest.partition("_")
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


def mcp_mirror_host(mcp_server_name: str) -> str:
    return f"{DEVICE_ID}.{AKTO_CONNECTOR_VALUE}.{mcp_server_name}"


def build_hook_tags(*, is_mcp: bool) -> Dict[str, str]:
    tags: Dict[str, str] = {}
    if is_mcp:
        tags["mcp-server"] = "MCP Server"
        tags["mcp-client"] = AKTO_CONNECTOR_VALUE
    else:
        tags["gen-ai"] = "Gen AI"
        tags["ai-agent"] = AKTO_CONNECTOR_VALUE
    if MODE == "atlas":
        tags["source"] = CONTEXT_SOURCE
    return tags


def build_validation_request(
    tool_name: str,
    tool_input: Any,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    session_info: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    tags = build_hook_tags(is_mcp=is_mcp)

    if is_mcp:
        host = mcp_mirror_host(mcp_server_name)
    else:
        host = GEMINI_API_URL.replace("https://", "").replace("http://", "")

    req_hdr: Dict[str, str] = {
        "host": host,
        "x-gemini-hook": "BeforeTool",
        "content-type": "application/json",
    }
    if is_mcp and mcp_server_name:
        req_hdr["x-mcp-server"] = mcp_server_name
    if session_info:
        req_hdr.update(installer_headers(session_info))

    request_headers = json.dumps(req_hdr)
    response_headers = json.dumps({"x-gemini-hook": "BeforeTool"})
    if is_mcp:
        request_payload = build_tools_call_jsonrpc(mcp_tool_name, tool_input)
    else:
        request_payload = json.dumps({"body": tool_input, "toolName": tool_name})
    response_payload = json.dumps({})

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
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    session_info: Optional[Dict[str, Any]] = None,
) -> Tuple[bool, str, str]:
    if not tool_input:
        return True, "", ""

    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing request (fail-open)")
        return True, "", ""

    if is_mcp:
        logger.info(f"Validating MCP tools/call for {mcp_tool_name} (server={mcp_server_name}, geminiTool={tool_name})")
    else:
        logger.info(f"Validating built-in / non-MCP tool request: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input payload: {json.dumps(tool_input)[:500]}...")

    try:
        request_body = build_validation_request(
            tool_name,
            tool_input,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            session_info=session_info,
        )
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=True),
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
    reason: str,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    session_info: Optional[Dict[str, Any]] = None,
):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    if not is_mcp and not AKTO_INGEST_NON_MCP_TOOLS:
        logger.info(
            "Skipping non-MCP blocked-request ingestion (set AKTO_INGEST_NON_MCP_TOOLS=true to re-enable)"
        )
        return

    logger.info("Ingesting blocked request data")
    try:
        request_body = build_validation_request(
            tool_name,
            tool_input,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            session_info=session_info,
        )
        request_body["responseHeaders"] = json.dumps(
            {
                "x-gemini-hook": "BeforeTool",
                "x-blocked-by": "Akto Proxy",
                "content-type": "application/json",
            }
        )
        request_body["responsePayload"] = json.dumps(
            {"body": {"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"}}
        )
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


def main():
    stdin_enc_before = getattr(sys.stdin, "encoding", "unknown")
    stdout_enc_before = getattr(sys.stdout, "encoding", "unknown")
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")
    stdin_enc_after = getattr(sys.stdin, "encoding", "unknown")

    logger.info(f"=== BeforeTool Hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")
    logger.info(f"Platform: {sys.platform}, Python: {sys.version.split()[0]}")
    logger.info(f"stdin encoding: {stdin_enc_before} -> {stdin_enc_after}, stdout encoding: {stdout_enc_before}")
    logger.info(f"AKTO_DATA_INGESTION_URL set: {bool(AKTO_DATA_INGESTION_URL)}, DEVICE_ID: {DEVICE_ID or '(auto)'}, CONNECTOR: {AKTO_CONNECTOR}")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.stdout.write(json.dumps({}))
        sys.exit(0)

    hook_event = input_data.get("hook_event_name", "")
    if hook_event and hook_event != "BeforeTool":
        logger.info(f"Ignoring non-BeforeTool event: {hook_event}")
        sys.stdout.write(json.dumps({}))
        sys.exit(0)

    session_info = resolve_session_info(input_data, logger)

    tool_name = str(input_data.get("tool_name") or "")
    tool_input = input_data.get("tool_input") or {}
    mcp_context = input_data.get("mcp_context") or {}
    is_mcp, mcp_server_name, mcp_tool_name = parse_gemini_tool(tool_name, mcp_context)

    if is_mcp:
        logger.info(f"Processing MCP tool request: {tool_name} (server={mcp_server_name}, mcpTool={mcp_tool_name})")
    else:
        logger.info(f"Processing non-MCP tool request (gen-ai only): {tool_name}")

    if AKTO_SYNC_MODE:
        gr_allowed, gr_reason, behaviour = call_guardrails(
            tool_name,
            tool_input,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            session_info=session_info,
        )
        fp = fingerprint({"t": tool_name, "i": tool_input})
        allowed, _ = apply_warn_resubmit_flow(
            WARN_STATE_PATH, fp, gr_allowed, gr_reason, behaviour, logger
        )

        if not allowed:
            if is_warn_behaviour(behaviour):
                block_reason = (
                    "Warning!!, tool request blocked, please review it. Send again to bypass. "
                    f"Reason for blocking: {gr_reason}"
                )
            else:
                block_reason = f"Tool request blocked: {gr_reason}"

            output = {
                "decision": "deny",
                "reason": block_reason,
            }
            logger.warning(f"BLOCKING tool request - Tool: {tool_name}, Reason: {block_reason}")
            sys.stdout.write(json.dumps(output))
            ingest_blocked_request(
                tool_name,
                tool_input,
                gr_reason,
                is_mcp=is_mcp,
                mcp_server_name=mcp_server_name,
                mcp_tool_name=mcp_tool_name,
                session_info=session_info,
            )
            sys.exit(0)

    logger.info(f"Tool request allowed for {tool_name}")
    sys.stdout.write(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
