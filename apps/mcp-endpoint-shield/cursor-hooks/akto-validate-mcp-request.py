#!/usr/bin/env python3
"""
Cursor MCP Before Hook - Request Validation via Akto HTTP Proxy API
Validates MCP tool requests before execution using Akto guardrails.
"""
import json
import logging
import os
import sys
import urllib.request
from typing import Any, Dict, Tuple, Union

from akto_machine_id import get_machine_id

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
file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-request.log"))
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
AKTO_CONNECTOR = "claude_code_cli" # todo: update connector name to cursor

# Configure API_URL based on mode
if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    API_URL = f"https://{device_id}.ai-agent.cursor" if device_id else "https://api.anthropic.com"
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


def build_validation_request(tool_input: str, mcp_server_name: str) -> dict:
    """Build the request body for guardrails validation."""
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
                "tool_input": tool_input,
            },
            "queryParams": {},
            "metadata": {
                "tag": tags,
                "mcp_server_name": mcp_server_name
            }
        },
        "response": None
    }


def call_guardrails(tool_input: str, mcp_server_name: str) -> Tuple[bool, str]:
    if not tool_input.strip():
        return True, ""

    logger.info(f"Validating request for MCP server: {mcp_server_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input payload: {tool_input[:500]}...")  # Log first 500 chars

    try:
        request_body = build_validation_request(tool_input, mcp_server_name)
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=False),
            request_body,
        )

        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")

        if allowed:
            logger.info(f"Request ALLOWED for {mcp_server_name}")
        else:
            logger.warning(f"Request DENIED for {mcp_server_name}: {reason}")

        return allowed, reason

    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, ""


def ingest_blocked_request(tool_input: str, mcp_server_name: str):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    try:
        blocked_response_payload = {
            "body": {"x-blocked-by": "Akto Proxy"},
            "headers": {"content-type": "application/json"},
            "statusCode": 403,
            "status": "forbidden"
        }

        request_body = build_validation_request(tool_input, mcp_server_name)
        request_body["response"] = blocked_response_payload
        post_payload_json(
            build_http_proxy_url(guardrails=False, ingest_data=True),
            request_body,
        )
        logger.info("Data ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info(f"=== Hook execution started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        # Allow by default on parse errors
        print(json.dumps({"permission": "allow"}))
        sys.exit(0)

    # Extract tool_input (the actual MCP tool parameters)
    tool_input = json.dumps(input_data.get("tool_input", {}))
    mcp_server_name = extract_mcp_server_name(input_data)

    logger.info(f"Processing request for MCP server: {mcp_server_name}")

    if not tool_input.strip() or tool_input == "{}":
        logger.info("Empty tool input, allowing request")
        print(json.dumps({"permission": "allow"}))
        sys.exit(0)

    if AKTO_SYNC_MODE:
        allowed, reason = call_guardrails(tool_input, mcp_server_name)
        if not allowed:
            output = {
                "permission": "deny",
                "user_message": "Request blocked by Akto security policy",
                "agent_message": f"Blocked by Akto Guardrails: {reason}"
            }
            logger.warning(f"BLOCKING request - Reason: {reason}")
            print(json.dumps(output))
            ingest_blocked_request(tool_input, mcp_server_name)
            sys.exit(0)

    # Allow the request
    logger.info("Request allowed")
    print(json.dumps({"permission": "allow"}))
    sys.exit(0)


if __name__ == "__main__":
    main()
