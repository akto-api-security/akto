#!/usr/bin/env python3
"""
Cursor MCP Before Hook - Request Validation via Akto HTTP Proxy API
Validates MCP tool requests before execution using Akto guardrails.
Triggered by beforeMCPExecution hook.
"""
import json
import sys
from typing import Any, Dict

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, send_ingestion_data, setup_logger

logger = setup_logger("akto-validate-mcp-request.log")


def extract_mcp_server_name(input_data: Dict[str, Any]) -> str:
    """Extract MCP server identifier from Cursor hook input."""
    if server := input_data.get("server"):
        return server
    if url := input_data.get("url"):
        return url.replace("https://", "").replace("http://", "").split("/")[0]
    if command := input_data.get("command"):
        return command
    if tool_name := input_data.get("tool_name", ""):
        if tool_name.startswith("mcp__"):
            parts = tool_name.split("__")
            if len(parts) > 1:
                return parts[1]
    return "cursor-unknown"


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

    result = send_ingestion_data(
        hook_name="beforeMCPExecution",
        request_payload=tool_input,
        response_payload={},
        guardrails=True,
        logger=logger,
    )

    allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
    if not allowed:
        reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
        logger.warning(f"BLOCKING MCP request for {mcp_server_name}: {reason}")
        print(json.dumps({
            "permission": "deny",
            "user_message": "Request blocked by Akto security policy",
            "agent_message": f"Blocked by Akto Guardrails: {reason}",
        }))
        send_ingestion_data(
            hook_name="beforeMCPExecution",
            request_payload=tool_input,
            response_payload={"reason": reason, "blockedBy": "Akto Proxy"},
            guardrails=False,
            status_code="403",
            logger=logger,
        )
        sys.exit(0)

    # Allow the request
    logger.info("Request allowed")
    print(json.dumps({"permission": "allow"}))
    sys.exit(0)


if __name__ == "__main__":
    main()
