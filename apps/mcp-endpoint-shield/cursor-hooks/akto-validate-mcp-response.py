#!/usr/bin/env python3
"""
Cursor MCP After Hook - Response Ingestion via Akto HTTP Proxy API
Logs MCP tool responses for monitoring and analysis.
NOTE: Cursor afterMCPExecution hooks cannot block responses, only log/ingest.
"""
import json
import logging
import os
import ssl
import sys
import urllib.request
from typing import Any, Dict, Union

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
AKTO_CONNECTOR_VALUE = os.getenv("AKTO_CONNECTOR_VALUE", "cursor")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
# /mcp matches Akto's JsonRpcUtils.isMcpPath; non-MCP keeps /v1/messages (legacy).
MCP_INGEST_PATH = os.getenv("MCP_INGEST_PATH", "/mcp")

# SSL Configuration
SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"

DEVICE_ID = os.getenv("DEVICE_ID") or get_machine_id()

# Configure API_URL based on mode
if MODE == "atlas":
    API_URL = f"https://{DEVICE_ID}.ai-agent.{AKTO_CONNECTOR_VALUE}" if DEVICE_ID else "https://api.anthropic.com"
    logger.info(f"MODE: {MODE}, Device ID: {DEVICE_ID}, API_URL: {API_URL}")
else:
    API_URL = os.getenv("API_URL", "https://api.anthropic.com")
    logger.info(f"MODE: {MODE}, API_URL: {API_URL}")


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
    if not SSL_VERIFY:
        logger.warning("SSL verification disabled via SSL_VERIFY=false - INSECURE!")
        return ssl._create_unverified_context()

    # Try 1: Custom certificate path
    if SSL_CERT_PATH:
        try:
            context = ssl.create_default_context(cafile=SSL_CERT_PATH)
            logger.info(f"Using custom SSL certificate: {SSL_CERT_PATH}")
            return context
        except Exception as e:
            logger.warning(f"Failed to load custom SSL certificate from {SSL_CERT_PATH}: {e}")

    # Try 2: System default context
    try:
        context = ssl.create_default_context()
        logger.debug("Using system default SSL context")
        return context
    except Exception as e:
        logger.warning(f"Failed to create default SSL context: {e}")

    # Try 3: Python certifi bundle
    try:
        import certifi
        context = ssl.create_default_context(cafile=certifi.where())
        logger.info("Using Python certifi SSL bundle")
        return context
    except ImportError:
        logger.debug("certifi package not available")
    except Exception as e:
        logger.warning(f"Failed to create SSL context with certifi: {e}")

    # Try 4: Unverified context (last resort)
    logger.error("WARNING: All SSL verification methods failed! Falling back to UNVERIFIED context - INSECURE!")
    logger.error("This connection is vulnerable to Man-in-the-Middle attacks!")
    logger.error("Fix: Install proper certificates or set SSL_CERT_PATH environment variable")
    return ssl._create_unverified_context()


def build_http_proxy_url(
    *,
    guardrails: bool = False,
    response_guardrails: bool = False,
    ingest_data: bool = False,
) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    if response_guardrails:
        params.append("response_guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def generate_curl_command(url: str, payload: Dict[str, Any], headers: Dict[str, str]) -> str:
    """Generate an equivalent curl command for debugging."""
    payload_json = json.dumps(payload)
    headers_str = " ".join([f"-H '{k}: {v}'" for k, v in headers.items()])

    # Escape single quotes in payload for shell
    payload_escaped = payload_json.replace("'", "'\\''")

    return f"curl -X POST {headers_str} -d '{payload_escaped}' '{url}'"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    import time

    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

    headers = {"Content-Type": "application/json"}

    # Generate and log curl command
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


# Cache: maps {url|command -> server alias} from ~/.cursor/mcp.json + project .cursor/mcp.json.
_MCP_CONFIG_CACHE: Dict[str, str] = {}


def _load_mcp_config_aliases() -> Dict[str, str]:
    global _MCP_CONFIG_CACHE
    if _MCP_CONFIG_CACHE:
        return _MCP_CONFIG_CACHE

    aliases: Dict[str, str] = {}
    candidates = [
        os.path.expanduser("~/.cursor/mcp.json"),
        os.path.join(os.getcwd(), ".cursor", "mcp.json"),
    ]
    for path in candidates:
        if not os.path.exists(path):
            continue
        try:
            with open(path, encoding="utf-8") as f:
                data = json.load(f)
            servers = data.get("mcpServers", {}) or {}
            for alias, server in servers.items():
                if not isinstance(server, dict):
                    continue
                if url := server.get("url"):
                    aliases[url] = alias
                if cmd := server.get("command"):
                    aliases[cmd] = alias
        except Exception as e:
            logger.warning(f"Could not parse {path}: {e}")
    _MCP_CONFIG_CACHE = aliases
    return aliases


def extract_mcp_server_name(input_data: Dict[str, Any]) -> str:
    """Extract MCP server identifier from Cursor hook input."""
    # Priority: configured alias from ~/.cursor/mcp.json > server field > url host > command > tool_name prefix > default
    aliases = _load_mcp_config_aliases()
    url = input_data.get("url")
    command = input_data.get("command")
    if url and url in aliases:
        return aliases[url]
    if command and command in aliases:
        return aliases[command]
    if server := input_data.get("server"):
        return server
    if url:
        # Extract domain from URL
        return url.replace("https://", "").replace("http://", "").split("/")[0]
    if command:
        return command
    if tool_name := input_data.get("tool_name", ""):
        if tool_name.startswith("mcp__"):
            parts = tool_name.split("__")
            if len(parts) > 1:
                return parts[1]
    return "cursor-unknown"


# ---------- JSON-RPC envelope helpers (lifted from claude-cli-hooks) ----------

def _tool_arguments_for_jsonrpc(tool_input: Any) -> Dict[str, Any]:
    if isinstance(tool_input, dict):
        return tool_input
    if tool_input is None:
        return {}
    return {"input": tool_input}


def build_tools_call_jsonrpc(mcp_tool_name: str, tool_input: Any, request_id: int = 1) -> str:
    return json.dumps(
        {
            "jsonrpc": "2.0",
            "method": "tools/call",
            "params": {"name": mcp_tool_name, "arguments": _tool_arguments_for_jsonrpc(tool_input)},
            "id": request_id,
        }
    )


def build_tools_call_result_jsonrpc(tool_response: Any, request_id: int = 1) -> str:
    """JSON-RPC success body for the mirrored response."""
    if isinstance(tool_response, dict):
        result_body: Any = tool_response
    else:
        result_body = {"output": tool_response}
    return json.dumps({"jsonrpc": "2.0", "id": request_id, "result": result_body})


def mcp_mirror_host(mcp_server_name: str) -> str:
    return f"{DEVICE_ID}.{AKTO_CONNECTOR_VALUE}.{mcp_server_name}"


def build_ingestion_payload(tool_name: str, tool_input_obj: Any, tool_response_obj: Any, mcp_server_name: str) -> Dict[str, Any]:
    """Build the request body for data ingestion.
    Wraps the MCP tool call + result in JSON-RPC 2.0 envelopes on path /mcp so Akto
    classifies this as MCP traffic."""
    import time

    # Build tags based on mode
    tags = {"mcp-server": "MCP Server", "mcp-client": AKTO_CONNECTOR_VALUE}
    if MODE == "atlas":
        tags["source"] = CONTEXT_SOURCE

    # Add MCP server name to tags
    tags["mcp_server_name"] = mcp_server_name

    # MCP traffic: host mirrors (deviceId.connector.serverAlias)
    host = mcp_mirror_host(mcp_server_name)

    # Build request headers as JSON string
    request_headers = json.dumps({
        "host": host,
        "x-cursor-hook": "afterMCPExecution",
        "x-mcp-server": mcp_server_name,
        "content-type": "application/json"
    })

    # Build response headers as JSON string
    response_headers = json.dumps({
        "x-cursor-hook": "afterMCPExecution",
        "content-type": "application/json"
    })

    # JSON-RPC tools/call envelope so Akto recognises this as MCP traffic
    request_payload = build_tools_call_jsonrpc(tool_name, tool_input_obj)

    # JSON-RPC result envelope mirroring the tool response
    response_payload = build_tools_call_result_jsonrpc(tool_response_obj)

    return {
        "path": MCP_INGEST_PATH,
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": get_username(),
        "destIp": "127.0.0.1",
        "time": str(int(time.time() * 1000)),
        "statusCode": "200",
        "type": None,
        "status": "200",
        "akto_account_id": "1000000",
        "akto_vxlan_id": DEVICE_ID,
        "is_pending": "false",
        "source": "MIRRORING",
        "direction": None,
        "process_id": None,
        "socket_id": None,
        "daemonset_id": None,
        "enabled_graph": None,
        "tag": json.dumps(tags),
        "metadata": json.dumps(tags),
        "contextSource": CONTEXT_SOURCE
    }


def send_ingestion_data(tool_name: str, tool_input_obj: Any, tool_response_obj: Any, mcp_server_name: str):
    if not tool_input_obj or not tool_response_obj:
        return

    logger.info(f"Ingesting MCP tools/call result for {tool_name} (server={mcp_server_name})")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {json.dumps(tool_input_obj)[:500]}...")
        logger.debug(f"Result: {json.dumps(tool_response_obj)[:500]}...")

    try:
        request_body = build_ingestion_payload(tool_name, tool_input_obj, tool_response_obj, mcp_server_name)
        # response_guardrails=true asks Akto to evaluate response-side guardrails on the mirrored result.
        # afterMCPExecution is observe-only per Cursor docs, so we cannot block — but Akto can still alert
        # server-side based on the response_guardrails verdict.
        post_payload_json(
            build_http_proxy_url(
                guardrails=False,
                response_guardrails=True,
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

    # Cursor passes tool_input and result_json as JSON-encoded *strings* (cursor.com/docs/hooks.md);
    # parse both so the JSON-RPC envelopes carry real arguments / result objects.
    raw_tool_input = input_data.get("tool_input", {})
    if isinstance(raw_tool_input, str):
        try:
            tool_input_obj = json.loads(raw_tool_input) if raw_tool_input.strip() else {}
        except json.JSONDecodeError:
            tool_input_obj = {"raw": raw_tool_input}
    else:
        tool_input_obj = raw_tool_input or {}

    raw_result = input_data.get("result_json", "{}")
    if isinstance(raw_result, str):
        try:
            tool_response_obj = json.loads(raw_result) if raw_result.strip() else {}
        except json.JSONDecodeError:
            tool_response_obj = {"raw": raw_result}
    else:
        tool_response_obj = raw_result or {}

    tool_name = str(input_data.get("tool_name") or "")
    mcp_server_name = extract_mcp_server_name(input_data)

    logger.info(f"Processing afterMCPExecution: {tool_name} (server={mcp_server_name})")

    if not tool_input_obj or not tool_response_obj:
        logger.info("Empty input or result, skipping ingestion")
        print(json.dumps({}))
        sys.exit(0)

    # Send data for ingestion (with response_guardrails=true so Akto evaluates the response)
    send_ingestion_data(tool_name, tool_input_obj, tool_response_obj, mcp_server_name)

    # After hooks must return empty JSON (cannot modify/block responses)
    logger.info("Response ingestion completed")
    print(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
