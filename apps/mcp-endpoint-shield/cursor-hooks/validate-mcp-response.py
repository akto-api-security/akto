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
import urllib.request
from typing import Any, Dict, Union

from machine_id import get_machine_id

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.cursor/mcp-logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

# Create log directory if it doesn't exist
os.makedirs(LOG_DIR, exist_ok=True)

# Setup logging with both file and console handlers
logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

# File handler
file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-response.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
file_handler.setFormatter(file_formatter)
logger.addHandler(file_handler)

# Console handler
console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)  # Only show errors in console
logger.addHandler(console_handler)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = "cursor_mcp"

# Configure API_URL based on mode
if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    API_URL = f"https://{device_id}.cursor.ai-agent" if device_id else "https://api.anthropic.com"
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, API_URL: {API_URL}")
else:
    API_URL = os.getenv("API_URL", "https://api.anthropic.com")
    logger.info(f"MODE: {MODE}, API_URL: {API_URL}")


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    import time

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
        with urllib.request.urlopen(request, timeout=AKTO_TIMEOUT) as response:
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
    """Build the request body for data ingestion."""
    # Build tags based on mode
    tags = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        tags["ai-agent"] = "cursor"
        tags["source"] = "ENDPOINT"

    return {
        "url": API_URL,
        "path": "/v1/messages",
        "request": {
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "body": {
                "tool_input": tool_input
            },
            "queryParams": {},
            "metadata": {
                "tag": tags,
                "mcp_server_name": mcp_server_name
            }
        },
        "response": {
            "body": json.loads(result_json) if result_json else {},
            "headers": {
                "content-type": "application/json"
            },
            "statusCode": 200,
            "status": "OK"
        }
    }


def send_ingestion_data(tool_input: str, result_json: str, mcp_server_name: str):
    if not tool_input.strip() or not result_json.strip():
        return

    logger.info(f"Ingesting response data for MCP server: {mcp_server_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {tool_input[:500]}...")
        logger.debug(f"Result: {result_json[:500]}...")

    try:
        request_body = build_ingestion_payload(tool_input, result_json, mcp_server_name)
        post_payload_json(
            build_http_proxy_url(
                guardrails=not AKTO_SYNC_MODE,
                ingest_data=True,
            ),
            request_body,
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
