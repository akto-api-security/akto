#!/usr/bin/env python3

import json
import logging
import os
import sys
import time
import urllib.request
from typing import Any, Dict, Union

# Configuration
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "./.github/akto/copilot/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
GITHUB_COPILOT_API_URL = os.getenv("GITHUB_COPILOT_API_URL", "https://api.github.com")
AKTO_CONNECTOR = "github_copilot_cli"

# Setup logging
os.makedirs(LOG_DIR, exist_ok=True)
logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "post-tool-hook.log"))
    file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(file_handler)
    
    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    logger.addHandler(console_handler)


def build_http_proxy_url(ingest_data: bool = True) -> str:
    """Build Akto HTTP proxy URL with query parameters."""
    params = [f"akto_connector={AKTO_CONNECTOR}"]
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_to_akto(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    """Send JSON payload to Akto API."""
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Payload: {json.dumps(payload, default=str)[:1000]}...")
    
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
            raw = response.read().decode("utf-8")
            logger.info(f"Response: {response.getcode()} in {duration_ms}ms")
            
            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                return raw
    except Exception as e:
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(f"API call failed after {duration_ms}ms: {e}")
        raise


def build_akto_request(
    tool_name: str,
    tool_args: str,
    tool_result: Dict[str, Any],
    cwd: str,
    timestamp: int
) -> Dict[str, Any]:
    """Build request payload for Akto data ingestion."""
    result_type = tool_result.get("resultType", "unknown")
    result_text = tool_result.get("textResultForLlm", "")
    
    # Map result type to HTTP status
    status_code = 200
    status = "ok"
    if result_type == "failure":
        status_code = 500
        status = "error"
    elif result_type == "denied":
        status_code = 403
        status = "forbidden"
    
    return {
        "url": GITHUB_COPILOT_API_URL,
        "path": f"/copilot/tool/{tool_name}",
        "request": {
            "method": "POST",
            "headers": {"content-type": "application/json"},
            "body": {"toolName": tool_name, "toolArgs": tool_args},
            "queryParams": {},
            "metadata": {
                "tag": {"gen-ai": "Gen AI", "tool-use": "Tool Execution"},
                "cwd": cwd,
                "timestamp": timestamp,
                "result_type": result_type
            }
        },
        "response": {
            "body": {
                "resultType": result_type,
                "result": result_text
            },
            "headers": {"content-type": "application/json"},
            "statusCode": status_code,
            "status": status
        }
    }


def ingest_tool_result(
    tool_name: str,
    tool_args: str,
    tool_result: Dict[str, Any],
    cwd: str,
    timestamp: int
):
    """Ingest tool execution result to Akto for analytics."""
    if not AKTO_DATA_INGESTION_URL:
        logger.info("Skipping ingestion - no Akto URL configured")
        return
    
    result_type = tool_result.get("resultType", "unknown")
    logger.info(f"Ingesting tool result: {tool_name} -> {result_type}")
    
    try:
        request_body = build_akto_request(tool_name, tool_args, tool_result, cwd, timestamp)
        post_to_akto(build_http_proxy_url(ingest_data=True), request_body)
        logger.info("Tool result ingested successfully")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info("=== Post-Tool Use Hook ===")
    
    try:
        input_data = json.load(sys.stdin)
        if LOG_PAYLOADS:
            logger.debug(f"Input: {json.dumps(input_data)}")
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)
    
    tool_name = input_data.get("toolName", "unknown")
    tool_args = input_data.get("toolArgs", "")
    tool_result = input_data.get("toolResult", {})
    cwd = input_data.get("cwd", "")
    timestamp = input_data.get("timestamp", int(time.time() * 1000))
    
    result_type = tool_result.get("resultType", "unknown")
    result_text = tool_result.get("textResultForLlm", "")
    
    logger.info(f"Tool: {tool_name}, Result: {result_type}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool args: {tool_args}")
        logger.debug(f"Result: {result_text}")
    else:
        logger.info(f"Result preview: {result_text[:100]}...")
    
    # Ingest the tool execution result (async mode - fire and forget)
    ingest_tool_result(tool_name, tool_args, tool_result, cwd, timestamp)
    
    logger.info("Hook completed")
    sys.exit(0)


if __name__ == "__main__":
    main()
