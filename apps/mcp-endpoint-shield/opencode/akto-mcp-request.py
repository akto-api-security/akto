#!/usr/bin/env python3
"""
Akto OpenCode MCP Tool Request Validation Handler
Validates MCP tool calls (JSON-RPC format) against Akto guardrails
Hook: tool.execute.before (for MCP tools)
"""

import json
import logging
import os
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, Tuple, Union

from akto_machine_id import get_machine_id, get_username

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.config/opencode/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-mcp-request.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
logger.addHandler(file_handler)

console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)
logger.addHandler(console_handler)

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL") or ""
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "opencode")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

# MCP-specific paths
MCP_INGEST_PATH = os.getenv("MCP_INGEST_PATH", "/mcp")

SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"


def create_ssl_context():
    return ssl._create_unverified_context()


def build_http_proxy_url(*, ingest_data: bool = False) -> str:
    """Build Akto HTTP proxy URL for MCP requests."""
    params = []
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    """Send payload to Akto API."""
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)}")

    headers = {"Content-Type": "application/json"}
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )

    start_time = time.time()
    try:
        ssl_context = create_ssl_context()
        with urllib.request.urlopen(request, context=ssl_context, timeout=AKTO_TIMEOUT) as response:
            duration_ms = int((time.time() - start_time) * 1000)
            status_code = response.getcode()
            raw = response.read().decode("utf-8")
            logger.info(f"API RESPONSE: Status {status_code}, Duration: {duration_ms}ms, Size: {len(raw)} bytes")

            if LOG_PAYLOADS:
                logger.debug(f"Response body: {raw}")

            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                return raw
    except Exception as e:
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(f"API CALL FAILED after {duration_ms}ms: {e}")
        raise


def parse_opencode_mcp_tool(tool_name: str) -> Tuple[bool, str, str]:
    """
    Parse OpenCode MCP tool_name into (is_mcp, server_name, mcp_tool_name).
    OpenCode format: <server>_<tool> (single underscore)
    Example: calculator_add -> (True, 'calculator', 'add')
    """
    if not tool_name or "_" not in tool_name:
        return False, "", ""

    # Split on first underscore only (tool name might contain underscores)
    parts = tool_name.split("_", 1)
    if len(parts) != 2:
        return False, "", ""

    server = parts[0].strip()
    mcp_tool = parts[1].strip()

    if not server or not mcp_tool:
        return False, "", ""

    return True, server, mcp_tool


def _tool_arguments_for_jsonrpc(tool_input: Any) -> Dict[str, Any]:
    """Convert tool input to JSON-RPC arguments format."""
    if isinstance(tool_input, dict):
        return tool_input
    if tool_input is None:
        return {}
    return {"input": tool_input}


def build_tools_call_jsonrpc(mcp_tool_name: str, tool_input: Any, request_id: int = 1) -> str:
    """
    Build JSON-RPC body for MCP tools/call.
    """
    return json.dumps(
        {
            "jsonrpc": "2.0",
            "method": "tools/call",
            "params": {"name": mcp_tool_name, "arguments": _tool_arguments_for_jsonrpc(tool_input)},
            "id": request_id,
        }
    )


def mcp_mirror_host(mcp_server_name: str) -> str:
    """Build host header for MCP server."""
    device_id = get_machine_id()
    return f"{device_id}.opencode.{mcp_server_name}"


def build_validation_request(
    tool_name: str,
    tool_input: Any,
    mcp_server_name: str,
    mcp_tool_name: str,
) -> Dict[str, Any]:
    """Build HTTP mirroring payload for MCP tool request."""
    host = mcp_mirror_host(mcp_server_name)

    req_hdr: Dict[str, str] = {
        "host": host,
        "x-opencode-hook": "PreToolUse",
        "content-type": "application/json",
    }
    if mcp_server_name:
        req_hdr["x-mcp-server"] = mcp_server_name

    request_headers = json.dumps(req_hdr)
    response_headers = json.dumps({"x-opencode-hook": "PreToolUse"})
    request_payload = build_tools_call_jsonrpc(mcp_tool_name, tool_input)
    response_payload = json.dumps({})

    tags = {
        "mcp-server": "MCP Server",
        "mcp-client": "opencode",
        "mcp-server-name": mcp_server_name,
        "source": CONTEXT_SOURCE,
        "gen-ai": "Gen AI",
    }

    return {
        "path": MCP_INGEST_PATH,
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
        "akto_vxlan_id": get_machine_id(),
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
    mcp_server_name: str,
    mcp_tool_name: str,
) -> Tuple[bool, str]:
    """Call Akto guardrails API for MCP tool validation."""
    if not tool_input:
        logger.info("Skipping validation due to empty tool input")
        return True, ""

    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing request (fail-open)")
        return True, ""

    logger.info(f"Validating MCP tool: {tool_name} (server={mcp_server_name}, mcpTool={mcp_tool_name})")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {json.dumps(tool_input)}")

    try:
        request_body = build_validation_request(
            tool_name,
            tool_input,
            mcp_server_name,
            mcp_tool_name,
        )
        result = post_payload_json(
            build_http_proxy_url(ingest_data=False),
            request_body,
        )

        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")

        if allowed:
            logger.info(f"MCP request ALLOWED for {tool_name}")
        else:
            logger.warning(f"MCP request DENIED for {tool_name}: {reason}")

        return allowed, reason
    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, ""


def ingest_blocked_request(
    tool_name: str,
    tool_input: Any,
    reason: str,
    mcp_server_name: str,
    mcp_tool_name: str,
):
    """Ingest blocked MCP request to Akto."""
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    logger.info("Ingesting blocked MCP request")
    try:
        request_body = build_validation_request(
            tool_name,
            tool_input,
            mcp_server_name,
            mcp_tool_name,
        )
        request_body["responseHeaders"] = json.dumps(
            {
                "x-opencode-hook": "PreToolUse",
                "x-blocked-by": "Akto Guardrails",
                "content-type": "application/json",
            }
        )
        request_body["responsePayload"] = json.dumps(
            {
                "jsonrpc": "2.0",
                "error": {
                    "code": -32000,
                    "message": f"Blocked: {reason or 'Policy violation'}",
                },
            }
        )
        request_body["statusCode"] = "403"
        request_body["status"] = "403"
        post_payload_json(
            build_http_proxy_url(ingest_data=True),
            request_body,
        )
        logger.info("Blocked MCP request ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info(f"=== MCP PreToolUse hook started - Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    tool_name = str(input_data.get("tool_name") or "")
    tool_input = input_data.get("tool_input") or {}

    is_mcp, mcp_server_name, mcp_tool_name = parse_opencode_mcp_tool(tool_name)

    if not is_mcp:
        logger.warning(f"Tool {tool_name} is not MCP format (server_tool). Skipping MCP handler.")
        sys.exit(0)

    logger.info(f"Processing MCP tool: {tool_name} (server={mcp_server_name}, tool={mcp_tool_name})")

    if AKTO_SYNC_MODE:
        allowed, reason = call_guardrails(
            tool_name,
            tool_input,
            mcp_server_name,
            mcp_tool_name,
        )

        if not allowed:
            block_reason = f"MCP tool blocked: {reason}"
            output = {
                "decision": "block",
                "reason": block_reason,
            }
            logger.warning(f"BLOCKING MCP request - Tool: {tool_name}, Reason: {reason}")
            print(json.dumps(output))
            ingest_blocked_request(
                tool_name,
                tool_input,
                reason,
                mcp_server_name,
                mcp_tool_name,
            )
            sys.exit(0)

    logger.info(f"MCP tool request allowed for {tool_name}")
    sys.exit(0)


if __name__ == "__main__":
    main()
