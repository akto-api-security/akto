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

from machine_id import get_machine_id

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = "cursor_mcp"

# Configure API_URL based on mode
if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    API_URL = f"https://{device_id}.cursor.ai-agent" if device_id else "https://api.anthropic.com"
else:
    API_URL = os.getenv("API_URL", "https://api.anthropic.com")


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    headers = {"Content-Type": "application/json"}
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )

    with urllib.request.urlopen(request, timeout=AKTO_TIMEOUT) as response:
        raw = response.read().decode("utf-8")
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return raw


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

    if not tool_input.strip() or tool_input == "{}":
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
            print(json.dumps(output))
            ingest_blocked_request(tool_input, mcp_server_name)
            sys.exit(0)

    # Allow the request
    print(json.dumps({"permission": "allow"}))
    sys.exit(0)


if __name__ == "__main__":
    main()
