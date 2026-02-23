#!/usr/bin/env python3

import json
import logging
import os
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, Union
from akto_machine_id import get_machine_id

# Configuration
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/akto-main/akto/.github/akto/copilot/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
GITHUB_COPILOT_API_URL = os.getenv("GITHUB_COPILOT_API_URL", "https://api.github.com")
AKTO_CONNECTOR = "github_copilot_cli"
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
MODE = os.getenv("MODE", "argus").lower()

# SSL Configuration
SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"

if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    GITHUB_COPILOT_API_URL = f"https://{device_id}.ai-agent.copilot" if device_id else GITHUB_COPILOT_API_URL

# Setup logging
os.makedirs(LOG_DIR, exist_ok=True)
logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-post-tool.log"))
    file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    logger.addHandler(console_handler)

if MODE == "atlas":
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, GITHUB_COPILOT_API_URL: {GITHUB_COPILOT_API_URL}")
else:
    logger.info(f"MODE: {MODE}, GITHUB_COPILOT_API_URL: {GITHUB_COPILOT_API_URL}")


def create_ssl_context():
    """
    Create SSL context with graceful fallback strategy.

    Attempts in order:
    1. Custom SSL_CERT_PATH if provided
    2. System default SSL context
    3. Python certifi bundle (if available)
    4. Unverified context (last resort)

    Returns:
        ssl.SSLContext or None
    """
    return ssl._create_unverified_context()


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
        ssl_context = create_ssl_context()
        with urllib.request.urlopen(request, context=ssl_context, timeout=AKTO_TIMEOUT) as response:
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


def uuid_to_ipv6_simple(uuid_str):
    hex_str = uuid_str.replace("-", "").lower()
    return ":".join(hex_str[i:i+4] for i in range(0, 32, 4))


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
    status_code = "200"
    status = "200"
    if result_type == "failure":
        status_code = "500"
        status = "500"
    elif result_type == "denied":
        status_code = "403"
        status = "403"

    tags = {"gen-ai": "Gen AI", "tool-use": "Tool Execution"}
    if MODE == "atlas":
        tags["ai-agent"] = "copilotcli"
        tags["source"] = CONTEXT_SOURCE

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = GITHUB_COPILOT_API_URL.replace("https://", "").replace("http://", "")

    request_headers = json.dumps({
        "host": host,
        "x-copilot-hook": "PostToolUse",
        "content-type": "application/json"
    })

    response_headers = json.dumps({
        "x-copilot-hook": "PostToolUse",
        "content-type": "application/json"
    })

    request_payload = json.dumps({
        "body": json.dumps({"toolName": tool_name, "toolArgs": tool_args})
    })

    response_payload = json.dumps({
        "body": json.dumps({"resultType": result_type, "result": result_text})
    })

    return {
        "path": f"/copilot/tool/{tool_name}",
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": uuid_to_ipv6_simple(device_id),
        "destIp": "127.0.0.1",
        "time": str(timestamp),
        "statusCode": status_code,
        "type": "HTTP/1.1",
        "status": status,
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
        "contextSource": "ENDPOINT"
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
    logger.info(f"=== Post-Tool Use Hook - Mode: {MODE} ===")
    
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
