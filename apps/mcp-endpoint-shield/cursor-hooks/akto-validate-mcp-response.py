#!/usr/bin/env python3
"""
Cursor MCP After Hook - Response Ingestion via Akto HTTP Proxy API
Logs MCP tool responses for monitoring and analysis.
NOTE: Cursor afterMCPExecution hooks cannot block responses, only log/ingest.
"""
import json
import sys
from typing import Any, Dict

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, send_ingestion_data, setup_logger

logger = setup_logger("akto-validate-mcp-response.log")


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

    send_ingestion_data(
        hook_name="afterMCPExecution",
        request_payload=tool_input,
        response_payload=result_json,
        guardrails=not AKTO_SYNC_MODE,
        logger=logger,
    )

    # After hooks must return empty JSON (cannot modify/block responses)
    logger.info("Response ingestion completed")
    print(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
