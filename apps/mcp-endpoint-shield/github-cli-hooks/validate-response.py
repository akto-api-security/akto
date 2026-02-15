#!/usr/bin/env python3
"""
GitHub Copilot CLI Output Guardrails Hook (Workaround)
Validates tool outputs after execution as a workaround for response guardrails.
Uses postToolUse hook to catch potentially sensitive outputs.
"""
import json
import logging
import os
import sys
import tempfile
import time
import urllib.request
from pathlib import Path
from typing import Any, Dict, Optional, Union

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Environment variables
AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
GITHUB_COPILOT_API_URL = os.getenv("GITHUB_COPILOT_API_URL", "https://api.github.com")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = "github_copilot_cli"

# Tools to skip validation (read-only/non-sensitive operations)
SKIP_TOOLS = {"view", "read", "list", "search", "grep"}

# Minimum time between ingestions (seconds) to reduce noise
INGESTION_DEBOUNCE = 2


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    """Build the Akto HTTP proxy URL with appropriate query parameters."""
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    """Send a JSON payload to the specified URL and return the response."""
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


def get_last_prompt(cwd: str) -> Optional[Dict[str, Any]]:
    """Retrieve the most recent user prompt from temporary storage."""
    try:
        temp_dir = Path(tempfile.gettempdir()) / "akto-copilot-hooks"
        if not temp_dir.exists():
            return None
        
        # Find the most recent prompt file
        prompt_files = sorted(temp_dir.glob("prompt_*.json"), key=lambda p: p.stat().st_mtime, reverse=True)
        
        for prompt_file in prompt_files:
            try:
                with open(prompt_file, 'r') as f:
                    data = json.load(f)
                    # Use prompts from last 60 seconds
                    if time.time() - (data.get("timestamp", 0) / 1000) < 60:
                        return data
            except Exception as e:
                logger.debug(f"Failed to read prompt file {prompt_file}: {e}")
                continue
        
        return None
    except Exception as e:
        logger.error(f"Failed to retrieve prompt: {e}")
        return None


def build_ingestion_payload(user_prompt: str, tool_name: str, tool_args: str, tool_result: str) -> Dict[str, Any]:
    """Build payload for data ingestion with user prompt context."""
    payload = {
        "url": GITHUB_COPILOT_API_URL,
        "path": "/copilot/chat",
        "request": {
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "body": {
                "query": user_prompt,
                "tool": tool_name,
                "arguments": tool_args,
            },
            "queryParams": {},
            "metadata": {
                "tag": {
                    "gen-ai": "Gen AI"
                }
            }
        },
        "response": {
            "body": {
                "result": tool_result
            },
            "headers": {
                "content-type": "application/json"
            },
            "statusCode": 200,
            "status": "OK"
        }
    }
    print("Built ingestion payload:", json.dumps(payload, indent=2))
    return payload


def validate_output_guardrails(tool_result: str) -> tuple[bool, str]:
    """
    Validate tool output against guardrails.
    Returns: (allowed, reason)
    """
    if not tool_result.strip():
        return True, ""

    try:
        # Create a validation request treating the output as response content
        validation_payload = {
            "url": GITHUB_COPILOT_API_URL,
            "path": "/copilot/chat",
            "request": {
                "method": "POST",
                "headers": {"content-type": "application/json"},
                "body": {"query": "tool execution"},
                "queryParams": {},
                "metadata": {"tag": {"gen-ai": "Gen AI"}}
            },
            "response": {
                "body": {"content": tool_result},
                "headers": {"content-type": "application/json"},
                "statusCode": 200,
                "status": "OK"
            }
        }
        
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=False),
            validation_payload,
        )

        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")

        return allowed, reason

    except Exception as e:
        logger.error(f"Output validation error: {e}")
        return True, ""


def should_ingest_tool(tool_name: str, cwd: str) -> bool:
    """Determine if this tool execution should be ingested."""
    # Skip read-only tools
    if tool_name.lower() in SKIP_TOOLS:
        logger.debug(f"Skipping read-only tool: {tool_name}")
        return False
    
    # Debounce: Check if we recently ingested
    try:
        temp_dir = Path(tempfile.gettempdir()) / "akto-copilot-hooks"
        last_ingest_file = temp_dir / "last_ingestion.txt"
        
        if last_ingest_file.exists():
            last_time = float(last_ingest_file.read_text().strip())
            if time.time() - last_time < INGESTION_DEBOUNCE:
                logger.debug(f"Debouncing ingestion (last: {time.time() - last_time:.2f}s ago)")
                return False
        
        # Update last ingestion time
        temp_dir.mkdir(exist_ok=True)
        last_ingest_file.write_text(str(time.time()))
    except Exception as e:
        logger.debug(f"Debounce check failed: {e}")
    
    return True


def ingest_tool_execution(user_prompt: str, tool_name: str, tool_args: str, tool_result: str):
    """Ingest tool execution data for analytics."""
    if not AKTO_DATA_INGESTION_URL:
        return

    try:
        request_body = build_ingestion_payload(user_prompt, tool_name, tool_args, tool_result)
        post_payload_json(
            build_http_proxy_url(
                guardrails=not AKTO_SYNC_MODE,
                ingest_data=True,
            ),
            request_body,
        )
        logger.info("Tool execution data ingestion successful")

    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    """Main hook execution function."""
    try:
        # Read JSON input from stdin (GitHub Copilot postToolUse hook format)
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    print("Received input data:", json.dumps(input_data, indent=2))
    # Extract tool execution details
    tool_name = input_data.get("toolName", "")
    tool_args = input_data.get("toolArgs", "")
    tool_result_obj = input_data.get("toolResult", {})
    cwd = input_data.get("cwd", "")
    
    # Extract result text
    result_type = tool_result_obj.get("resultType", "")
    tool_result_text = tool_result_obj.get("textResultForLlm", "")

    # Only process successful tool executions
    if result_type != "success" or not tool_result_text.strip():
        sys.exit(0)

    # Check if we should process this tool
    if not should_ingest_tool(tool_name, cwd):
        sys.exit(0)

    # Get the user prompt from storage
    prompt_data = get_last_prompt(cwd)
    user_prompt = prompt_data.get("prompt", "tool execution") if prompt_data else "tool execution"

    # Validate output against guardrails in sync mode
    if AKTO_SYNC_MODE and AKTO_DATA_INGESTION_URL:
        allowed, reason = validate_output_guardrails(tool_result_text)
        
        if not allowed:
            logger.warning(f"Tool output flagged by guardrails: {reason}")
            # Note: GitHub Copilot doesn't support blocking output after tool execution
            # This is a logging/monitoring workaround
            output = {
                "warning": f"Output contains sensitive content detected by Akto Guardrails: {reason}"
            }
            print(json.dumps(output))

    # Ingest data for analytics (with user prompt context)
    ingest_tool_execution(user_prompt, tool_name, tool_args, tool_result_text)
    
    sys.exit(0)


if __name__ == "__main__":
    main()
