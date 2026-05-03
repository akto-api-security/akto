#!/usr/bin/env python3
"""
Cursor MCP After Hook - Response Ingestion via Akto HTTP Proxy API
Logs MCP tool responses for monitoring and analysis.
NOTE: Cursor afterMCPExecution hooks cannot block responses, only log/ingest.
"""
import json
import logging
import os
import sys
from typing import Any, Dict

from akto_cursor_mirror import post_json_to_akto_proxy, build_mirror_http_proxy_url, build_mirror_ingestion_payload
from akto_machine_id import get_machine_id, get_username

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.cursor/akto/mcp-logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

# Create log directory if it doesn't exist
os.makedirs(LOG_DIR, exist_ok=True)

# Setup logging with both file and console handlers
logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

# File handler
file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-response.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
file_handler.setFormatter(file_formatter)
logger.addHandler(file_handler)

# Console handler
console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)  # Only show errors in console
logger.addHandler(console_handler)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = "cursor"
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

# Configure API_URL based on mode
if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    API_URL = f"https://{device_id}.ai-agent.cursor" if device_id else "https://api.anthropic.com"
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, API_URL: {API_URL}")
else:
    API_URL = os.getenv("API_URL", "https://api.anthropic.com")
    logger.info(f"MODE: {MODE}, API_URL: {API_URL}")


def extract_mcp_server_name(input_data: Dict[str, Any]) -> str:
    """Extract MCP server identifier from Cursor hook input."""
    # Priority: server > url (extract domain) > command > tool_name prefix > default
    if server := input_data.get("server"):
        return server
    if url := input_data.get("url"):
        # Extract domain from URL
        return url.replace("https://", "").replace("http://", "").split("/")[0]
    if command := input_data.get("command"):
        return command
    if tool_name := input_data.get("tool_name", ""):
        if tool_name.startswith("mcp__"):
            parts = tool_name.split("__")
            if len(parts) > 1:
                return parts[1]
    return "cursor-unknown"


def build_ingestion_payload(tool_input: str, result_json: str, mcp_server_name: str) -> Dict[str, Any]:
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    username = get_username()
    return build_mirror_ingestion_payload(
        x_cursor_hook="afterMCPExecution",
        tool_input_str=tool_input,
        response_body_str=result_json,
        server_tag=mcp_server_name,
        mode=MODE,
        api_url=API_URL,
        context_source=CONTEXT_SOURCE,
        device_id=device_id,
        username=username,
    )


def send_ingestion_data(tool_input: str, result_json: str, mcp_server_name: str):
    if not tool_input.strip() or not result_json.strip():
        return

    logger.info(f"Ingesting response data for MCP server: {mcp_server_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {tool_input[:500]}...")
        logger.debug(f"Result: {result_json[:500]}...")

    try:
        request_body = build_ingestion_payload(tool_input, result_json, mcp_server_name)
        proxy_url = build_mirror_http_proxy_url(
            AKTO_DATA_INGESTION_URL,
            AKTO_CONNECTOR,
            guardrails=not AKTO_SYNC_MODE,
            ingest_data=True,
        )
        post_json_to_akto_proxy(
            proxy_url,
            request_body,
            logger,
            timeout=AKTO_TIMEOUT,
            log_payloads=LOG_PAYLOADS,
        )
        logger.info(f"Data ingestion successful for {mcp_server_name}")

    except Exception as e:
        logger.error(f"Ingestion error for {mcp_server_name}: {e}")


def main():
    logger.info(f"=== Hook execution started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        # After hooks must return empty JSON
        print(json.dumps({}))
        sys.exit(0)

    # Extract tool_input and result_json
    tool_input = json.dumps(input_data.get("tool_input", {}))
    result_json = input_data.get("result_json", "{}")
    mcp_server_name = extract_mcp_server_name(input_data)

    logger.info(f"Processing response from MCP server: {mcp_server_name}")

    if not tool_input or tool_input == "{}" or not result_json or result_json == "{}":
        logger.info("Empty input or result, skipping ingestion")
        print(json.dumps({}))
        sys.exit(0)

    # Send data for ingestion
    send_ingestion_data(tool_input, result_json, mcp_server_name)

    # After hooks must return empty JSON (cannot modify/block responses)
    logger.info("Response ingestion completed")
    print(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
