#!/usr/bin/env python3

import json
import logging
import os
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, Union

from akto_machine_id import get_machine_id, get_username

# Configure logging
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

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "claude_code_cli")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"

if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    CLAUDE_API_URL = f"https://{device_id}.ai-agent.claudecli" if device_id else "https://api.anthropic.com"
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, CLAUDE_API_URL: {CLAUDE_API_URL}")
else:
    CLAUDE_API_URL = os.getenv("CLAUDE_API_URL", "https://api.anthropic.com")
    logger.info(f"MODE: {MODE}, CLAUDE_API_URL: {CLAUDE_API_URL}")


def create_ssl_context():
    return ssl._create_unverified_context()


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

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
                logger.debug(f"Response body: {raw[:1000]}...")

            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                return raw
    except Exception as e:
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(f"API CALL FAILED after {duration_ms}ms: {e}")
        raise


def extract_mcp_server_name(tool_name: str) -> str:
    if not tool_name.startswith("mcp__"):
        return "claude-built-in"
    parts = tool_name.split("__")
    if len(parts) >= 3 and parts[1]:
        return parts[1]
    return "claude-built-in"


def build_ingestion_payload(
    tool_name: str, tool_input: str, tool_response: str, mcp_server_name: str
) -> Dict[str, Any]:
    tags = {
        "gen-ai": "Gen AI",
        "tool-use": "Tool Execution",
        "mcp_server_name": mcp_server_name,
    }
    if MODE == "atlas":
        tags["ai-agent"] = "claudecli"
        tags["source"] = CONTEXT_SOURCE

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = CLAUDE_API_URL.replace("https://", "").replace("http://", "")

    request_headers = json.dumps(
        {
            "host": host,
            "x-claude-hook": "PostToolUse",
            "content-type": "application/json",
        }
    )
    response_headers = json.dumps(
        {"x-claude-hook": "PostToolUse", "content-type": "application/json"}
    )
    request_payload = json.dumps({"body": {"toolName": tool_name, "toolArgs": tool_input}})
    response_payload = json.dumps({"body": {"result": tool_response}})

    return {
        "path": "/v1/messages",
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
        "akto_vxlan_id": device_id,
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


def send_ingestion_data(tool_name: str, tool_input: Any, tool_response: Any, mcp_server_name: str):
    if not AKTO_DATA_INGESTION_URL:
        logger.info("AKTO_DATA_INGESTION_URL not set, skipping ingestion")
        return

    if not tool_input:
        logger.info("Skipping ingestion due to empty tool input")
        return

    if not tool_response:
        logger.info("Skipping ingestion due to empty tool response")
        return

    logger.info(f"Ingesting MCP response for tool: {tool_name} (server: {mcp_server_name})")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {json.dumps(tool_input)[:500]}...")
        logger.debug(f"Tool response: {json.dumps(tool_response)[:500]}...")

    try:
        request_body = build_ingestion_payload(tool_name, tool_input, tool_response, mcp_server_name)
        post_payload_json(
            build_http_proxy_url(guardrails=not AKTO_SYNC_MODE, ingest_data=True),
            request_body,
        )
        logger.info("MCP response ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info(f"=== PostToolUse MCP hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    tool_name = str(input_data.get("tool_name") or "")
    mcp_server_name = extract_mcp_server_name(tool_name)
    tool_input = input_data.get("tool_input") or {}
    tool_response = input_data.get("tool_response") or {}

    logger.info(f"Processing MCP tool response: {tool_name} (server: {mcp_server_name})")
    send_ingestion_data(tool_name, tool_input, tool_response, mcp_server_name)

    sys.exit(0)


if __name__ == "__main__":
    main()
