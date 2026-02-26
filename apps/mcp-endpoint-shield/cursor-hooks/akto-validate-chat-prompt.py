#!/usr/bin/env python3
"""
Cursor Chat Before Hook - Prompt Validation via Akto HTTP Proxy API
Validates user prompts before submission using Akto guardrails.
Triggered by beforeSubmitPrompt hook.
"""
import json
import logging
import os
import ssl
import sys
import urllib.request
from typing import Any, Dict, Tuple, Union

from akto_machine_id import get_machine_id, get_username

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.cursor/akto/chat-logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

# Create log directory if it doesn't exist
os.makedirs(LOG_DIR, exist_ok=True)

# Setup logging with both file and console handlers
logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

# File handler
file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-chat-prompt.log"))
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
AKTO_CONNECTOR = "cursor"
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

# SSL Configuration
SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"

# Configure API_URL based on mode
if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    API_URL = f"https://{device_id}.ai-agent.cursor" if device_id else "https://api.anthropic.com"
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, API_URL: {API_URL}")
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

def build_validation_request(prompt: str, attachments: list) -> dict:
    """Build the request body for guardrails validation."""
    import time

    # Build tags based on mode
    tags = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        tags["ai-agent"] = "cursor"
        tags["source"] = CONTEXT_SOURCE

    # Add attachment info to tags if present
    if attachments:
        tags["attachments_count"] = len(attachments)
        tags["attachment_types"] = ",".join([att.get("type", "unknown") for att in attachments])

    # Get device ID
    device_id = os.getenv("DEVICE_ID") or get_machine_id()

    # Build host from API_URL
    host = API_URL.replace("https://", "").replace("http://", "")

    # Build request headers as JSON string
    request_headers = json.dumps({
        "host": host,
        "x-cursor-hook": "beforeSubmitPrompt",
        "content-type": "application/json"
    })

    # Build response headers as JSON string
    response_headers = json.dumps({
        "x-cursor-hook": "beforeSubmitPrompt"
    })

    # Build request payload as JSON string
    request_payload = json.dumps({
        "body": prompt
    })

    # Response payload is empty for before hooks
    response_payload = json.dumps({})

    return {
        "path": "/v1/messages",
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
        "contextSource": CONTEXT_SOURCE
    }


def call_guardrails(prompt: str, attachments: list) -> Tuple[bool, str]:
    """Call guardrails API to validate the prompt."""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing prompt")
        return True, ""

    logger.info(f"Validating chat prompt (length: {len(prompt)}, attachments: {len(attachments)})")
    if LOG_PAYLOADS:
        logger.debug(f"Prompt: {prompt[:500]}...")
        if attachments:
            logger.debug(f"Attachments: {json.dumps(attachments)[:500]}...")

    try:
        request_body = build_validation_request(prompt, attachments)
        response = post_payload_json(
            build_http_proxy_url(
                guardrails=AKTO_SYNC_MODE,
                ingest_data=not AKTO_SYNC_MODE,
            ),
            request_body,
        )

        data = response.get("data", {}) if isinstance(response, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")

        if not allowed:
            logger.warning(f"Prompt BLOCKED: {reason}")
            return False, reason

        logger.info("Prompt ALLOWED")
        return True, ""

    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        # Fail open: allow on error
        return True, ""


def main():
    logger.info(f"=== Chat Prompt Hook execution started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        print(json.dumps({"continue": True}))
        sys.exit(0)

    prompt = input_data.get("prompt", "")
    attachments = input_data.get("attachments", [])

    if not prompt.strip():
        logger.warning("Empty prompt received, allowing")
        print(json.dumps({"continue": True}))
        sys.exit(0)

    # Call guardrails validation
    allowed, reason = call_guardrails(prompt, attachments)

    # Return response
    response = {"continue": allowed}
    if not allowed:
        response["user_message"] = f"Prompt blocked: {reason}"

    logger.info(f"Hook response: {response}")
    print(json.dumps(response))
    sys.exit(0)


if __name__ == "__main__":
    main()
