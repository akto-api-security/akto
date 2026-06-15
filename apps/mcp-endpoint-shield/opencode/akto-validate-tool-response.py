#!/usr/bin/env python3
"""
Akto OpenCode Tool Response Logging Hook
Logs tool responses to Akto for audit trail
Hook: tool.execute.after
"""

import json
import logging
import os
import sys
import time
from typing import Any, Dict

# OpenCode launches hooks via a JS plugin that does not export AKTO_CONNECTOR,
# so default it before importing the shared module (which reads it at import time).
os.environ.setdefault("AKTO_CONNECTOR", "opencode")

from akto_machine_id import get_machine_id, get_username
from akto_ingestion_utility import (
    build_http_proxy_url,
    post_payload_json,
)

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.config/opencode/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-tool-response.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
logger.addHandler(file_handler)

console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)
logger.addHandler(console_handler)

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

# MODE configuration (for device tracking in atlas mode)
MODE = os.getenv("MODE", "atlas").lower()

if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    OPENCODE_API_URL = f"https://{device_id}.opencode.local" if device_id else "https://api.opencode.ai"
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, OPENCODE_API_URL: {OPENCODE_API_URL}")
else:
    OPENCODE_API_URL = os.getenv("OPENCODE_API_URL", "https://api.opencode.ai")
    logger.info(f"MODE: {MODE}, OPENCODE_API_URL: {OPENCODE_API_URL}")


def extract_tool_server_name(tool_name: str) -> str:
    """Extract tool server name from tool identifier"""
    if not tool_name:
        return "opencode"
    return "opencode"


def build_ingestion_payload(
    tool_name: str, tool_input: str, tool_response: str, tool_server_name: str
) -> Dict[str, Any]:
    tags = {
        "gen-ai": "Gen AI",
        "tool-use": "Tool Execution",
        "tool_server_name": tool_server_name,
    }
    if MODE == "atlas":
        tags["ai-agent"] = "opencode"
        tags["source"] = CONTEXT_SOURCE

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = OPENCODE_API_URL.replace("https://", "").replace("http://", "")

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


def send_ingestion_data(tool_name: str, tool_input: Any, tool_response: Any, tool_server_name: str):
    if not AKTO_DATA_INGESTION_URL:
        logger.info("AKTO_DATA_INGESTION_URL not set, skipping ingestion")
        return

    if not tool_input:
        logger.info("Skipping ingestion due to empty tool input")
        return

    if not tool_response:
        logger.info("Skipping ingestion due to empty tool response")
        return

    logger.info(f"Ingesting tool response for tool: {tool_name} (server: {tool_server_name})")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {json.dumps(tool_input)[:500]}...")
        logger.debug(f"Tool response: {json.dumps(tool_response)[:500]}...")

    try:
        request_body = build_ingestion_payload(tool_name, tool_input, tool_response, tool_server_name)
        post_payload_json(
            build_http_proxy_url(guardrails=not AKTO_SYNC_MODE, ingest_data=True),
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

    tool_name = str(input_data.get("tool_name") or "")
    tool_server_name = extract_tool_server_name(tool_name)
    tool_input = input_data.get("tool_input") or {}
    tool_response = input_data.get("tool_response") or {}

    logger.info(f"Processing tool response: {tool_name} (server: {tool_server_name})")
    send_ingestion_data(tool_name, tool_input, tool_response, tool_server_name)

    sys.exit(0)


if __name__ == "__main__":
    main()
