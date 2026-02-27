#!/usr/bin/env python3

import json
import logging
import os
import ssl
import sys
import urllib.request
from typing import Any, Dict, Tuple, Union

from akto_machine_id import get_machine_id

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.claude/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-mcp-request.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
logger.addHandler(file_handler)

console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)
logger.addHandler(console_handler)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "claude_code_cli")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"

if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    CLAUDE_API_URL = f"https://{device_id}.ai-agent.claudecli" if device_id else "https://api.anthropic.com"
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, CLAUDE_API_URL: {CLAUDE_API_URL}")
else:
    CLAUDE_API_URL = os.getenv("CLAUDE_API_URL", "https://api.anthropic.com")
    logger.info(f"MODE: {MODE}, CLAUDE_API_URL: {CLAUDE_API_URL}")


def create_ssl_context():
    """
    Create SSL context with graceful fallback strategy.

    Attempts in order:
    1. Custom SSL_CERT_PATH if provided
    2. System default SSL context
    3. Python certifi bundle (if available)
    4. Unverified context (last resort)
    """
    if not SSL_VERIFY:
        logger.warning("SSL verification disabled via SSL_VERIFY=false - INSECURE!")
        return ssl._create_unverified_context()

    if SSL_CERT_PATH:
        try:
            context = ssl.create_default_context(cafile=SSL_CERT_PATH)
            logger.info(f"Using custom SSL certificate: {SSL_CERT_PATH}")
            return context
        except Exception as e:
            logger.warning(f"Failed to load custom SSL certificate from {SSL_CERT_PATH}: {e}")

    try:
        context = ssl.create_default_context()
        logger.debug("Using system default SSL context")
        return context
    except Exception as e:
        logger.warning(f"Failed to create default SSL context: {e}")

    try:
        import certifi

        context = ssl.create_default_context(cafile=certifi.where())
        logger.info("Using Python certifi SSL bundle")
        return context
    except ImportError:
        logger.debug("certifi package not available")
    except Exception as e:
        logger.warning(f"Failed to create SSL context with certifi: {e}")

    logger.error("WARNING: All SSL verification methods failed! Falling back to UNVERIFIED context - INSECURE!")
    logger.error("This connection is vulnerable to Man-in-the-Middle attacks!")
    logger.error("Fix: Install proper certificates or set SSL_CERT_PATH environment variable")
    return ssl._create_unverified_context()


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def generate_curl_command(url: str, payload: Dict[str, Any], headers: Dict[str, str]) -> str:
    payload_json = json.dumps(payload)
    headers_str = " ".join([f"-H '{k}: {v}'" for k, v in headers.items()])
    payload_escaped = payload_json.replace("'", "'\\''")
    return f"curl -X POST {headers_str} -d '{payload_escaped}' '{url}'"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    import time

    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

    headers = {"Content-Type": "application/json"}
    curl_cmd = generate_curl_command(url, payload, headers)
    logger.debug(f"CURL EQUIVALENT:\n{curl_cmd}")

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


def get_hook_specific_input(input_data: Dict[str, Any]) -> Dict[str, Any]:
    hook_input = input_data.get("hook_specific_input")
    if isinstance(hook_input, dict):
        return hook_input

    hook_input = input_data.get("hookSpecificInput")
    if isinstance(hook_input, dict):
        return hook_input

    return {}


def extract_tool_name_and_input(input_data: Dict[str, Any]) -> Tuple[str, Any]:
    hook_input = get_hook_specific_input(input_data)

    tool_name = (
        hook_input.get("tool_name")
        or hook_input.get("toolName")
        or input_data.get("tool_name")
        or input_data.get("toolName")
        or ""
    )
    tool_input = hook_input.get("tool_input")
    if tool_input is None:
        tool_input = hook_input.get("toolInput")
    if tool_input is None:
        tool_input = input_data.get("tool_input")
    if tool_input is None:
        tool_input = input_data.get("toolInput")
    if tool_input is None:
        tool_input = {}

    return str(tool_name), tool_input


def extract_mcp_server_name(tool_name: str) -> str:
    if not tool_name.startswith("mcp__"):
        return "claude-unknown"
    parts = tool_name.split("__")
    if len(parts) >= 3 and parts[1]:
        return parts[1]
    return "claude-unknown"


def normalize_payload(value: Any) -> str:
    if isinstance(value, str):
        stripped = value.strip()
        return stripped if stripped else "{}"
    try:
        return json.dumps(value if value is not None else {})
    except Exception:
        return "{}"


def uuid_to_ipv6_simple(uuid_str: str) -> str:
    hex_str = uuid_str.replace("-", "").lower()
    return ":".join(hex_str[i:i + 4] for i in range(0, len(hex_str), 4))


def build_validation_request(tool_name: str, tool_input: str, mcp_server_name: str) -> Dict[str, Any]:
    import time

    tags = {"gen-ai": "Gen AI", "mcp_server_name": mcp_server_name}
    if MODE == "atlas":
        tags["ai-agent"] = "claudecli"
        tags["source"] = CONTEXT_SOURCE

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = CLAUDE_API_URL.replace("https://", "").replace("http://", "")

    request_headers = json.dumps(
        {
            "host": host,
            "x-claude-hook": "PreToolUse",
            "content-type": "application/json",
        }
    )
    response_headers = json.dumps({"x-claude-hook": "PreToolUse"})
    request_payload = json.dumps({"body": tool_input, "toolName": tool_name})
    response_payload = json.dumps({})

    return {
        "path": "/v1/messages",
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": uuid_to_ipv6_simple(device_id),
        "destIp": "127.0.0.1",
        "time": str(int(time.time() * 1000)),
        "statusCode": "200",
        "type": None,
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


def call_guardrails(tool_name: str, tool_input: str, mcp_server_name: str) -> Tuple[bool, str]:
    if not tool_input.strip() or tool_input == "{}":
        return True, ""

    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing request (fail-open)")
        return True, ""

    logger.info(f"Validating MCP request for tool: {tool_name} (server: {mcp_server_name})")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input payload: {tool_input[:500]}...")

    try:
        request_body = build_validation_request(tool_name, tool_input, mcp_server_name)
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=False),
            request_body,
        )

        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")

        if allowed:
            logger.info(f"Request ALLOWED for {tool_name}")
        else:
            logger.warning(f"Request DENIED for {tool_name}: {reason}")

        return allowed, reason
    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, ""


def ingest_blocked_request(tool_name: str, tool_input: str, mcp_server_name: str, reason: str):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    try:
        blocked_response_payload = {
            "body": {"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"},
            "headers": {"content-type": "application/json"},
            "statusCode": 403,
            "status": "forbidden",
        }

        request_body = build_validation_request(tool_name, tool_input, mcp_server_name)
        request_body["response"] = blocked_response_payload
        post_payload_json(
            build_http_proxy_url(guardrails=False, ingest_data=True),
            request_body,
        )
        logger.info("Blocked MCP request ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info(f"=== PreToolUse MCP hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    tool_name, raw_tool_input = extract_tool_name_and_input(input_data)
    if not tool_name.startswith("mcp__"):
        logger.info(f"Skipping non-MCP tool: {tool_name or 'unknown'}")
        sys.exit(0)

    tool_input = normalize_payload(raw_tool_input)
    mcp_server_name = extract_mcp_server_name(tool_name)

    logger.info(f"Processing MCP tool request: {tool_name} (server: {mcp_server_name})")

    if AKTO_SYNC_MODE:
        allowed, reason = call_guardrails(tool_name, tool_input, mcp_server_name)
        if not allowed:
            block_reason = reason or "Policy violation"
            output = {
                "decision": "block",
                "reason": f"Blocked by Akto Guardrails: {block_reason}",
            }
            logger.warning(f"BLOCKING MCP request - Tool: {tool_name}, Reason: {block_reason}")
            print(json.dumps(output))
            ingest_blocked_request(tool_name, tool_input, mcp_server_name, block_reason)
            sys.exit(0)

    logger.info(f"MCP request allowed for {tool_name}")
    sys.exit(0)


if __name__ == "__main__":
    main()
