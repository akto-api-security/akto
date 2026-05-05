#!/usr/bin/env python3
import json
import logging
import os
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, Tuple, Union

from akto_machine_id import get_machine_id, get_username

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.gemini/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-after-tool.log"))
    file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    logger.addHandler(console_handler)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "gemini_cli")
AKTO_CONNECTOR_VALUE = os.getenv("AKTO_CONNECTOR_VALUE", "geminicli")
AKTO_TOKEN = os.getenv("AKTO_TOKEN", "")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"

if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    GEMINI_API_URL = f"https://{device_id}.ai-agent.{AKTO_CONNECTOR_VALUE}" if device_id else "https://generativelanguage.googleapis.com"
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, GEMINI_API_URL: {GEMINI_API_URL}")
else:
    GEMINI_API_URL = os.getenv("GEMINI_API_URL", "https://generativelanguage.googleapis.com")
    logger.info(f"MODE: {MODE}, GEMINI_API_URL: {GEMINI_API_URL}")


def create_ssl_context():
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


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

    headers = {"Content-Type": "application/json"}
    if AKTO_TOKEN:
        headers["authorization"] = AKTO_TOKEN
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


def build_ingestion_payload(
    tool_name: str,
    tool_input: Any,
    tool_response: Any,
    session_info: dict = None,
) -> Dict[str, Any]:
    tags = {"gen-ai": "Gen AI", "tool-use": "PostTool", "tool_name": tool_name}
    if MODE == "atlas":
        tags["ai-agent"] = AKTO_CONNECTOR_VALUE
        tags["source"] = CONTEXT_SOURCE

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = GEMINI_API_URL.replace("https://", "").replace("http://", "")

    req_headers = {
        "host": host,
        "x-gemini-hook": "AfterTool",
        "x-gemini-tool": tool_name,
        "content-type": "application/json",
    }
    if session_info:
        for key, value in session_info.items():
            if value is not None:
                req_headers[f"x-akto-installer-{key}"] = str(value)

    request_headers = json.dumps(req_headers)
    response_headers = json.dumps({
        "x-gemini-hook": "AfterTool",
        "content-type": "application/json",
    })
    request_payload = json.dumps({"body": {"toolName": tool_name, "toolArgs": tool_input}})
    response_payload = json.dumps({"body": {"result": tool_response}})

    return {
        "path": "/gemini/tool",
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": get_username(),
        "destIp": "127.0.0.1",
        "time": str(int(time.time() * 1000)),
        "statusCode": "200",
        "type": "HTTP/1.1",
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


def call_guardrails_and_ingest(
    tool_name: str,
    tool_input: Any,
    tool_response: Any,
    session_info: dict = None,
) -> Tuple[bool, str]:
    if not tool_response:
        return True, ""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing tool response (fail-open)")
        return True, ""

    logger.info(f"Validating tool response for: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {json.dumps(tool_input)[:500]}...")
        logger.debug(f"Tool response: {json.dumps(tool_response)[:500]}...")

    try:
        request_body = build_ingestion_payload(tool_name, tool_input, tool_response, session_info)
        result = post_payload_json(
            build_http_proxy_url(
                guardrails=False,
                response_guardrails=True,
                ingest_data=True,
            ),
            request_body,
        )

        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")

        if allowed:
            logger.info(f"Tool response ALLOWED for {tool_name}")
        else:
            logger.warning(f"Tool response DENIED for {tool_name}: {reason}")

        return allowed, reason
    except Exception as e:
        logger.error(f"Guardrails/ingestion error: {e}")
        return True, ""


def main():
    logger.info(f"=== AfterTool hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    hook_event = input_data.get("hook_event_name", "")
    if hook_event and hook_event != "AfterTool":
        sys.exit(0)

    tool_name = str(input_data.get("tool_name") or "")
    tool_input = input_data.get("tool_input") or {}
    tool_response = input_data.get("tool_response") or {}

    session_info = {}
    for field in ("session_id", "transcript_path", "cwd", "hook_event_name", "timestamp"):
        value = input_data.get(field)
        if value is not None:
            session_info[field] = value
    original_request_name = input_data.get("original_request_name")
    if original_request_name:
        session_info["original_request_name"] = original_request_name

    logger.info(f"Processing tool response: {tool_name}")

    allowed, reason = call_guardrails_and_ingest(
        tool_name, tool_input, tool_response, session_info
    )

    if not allowed:
        block_reason = reason or "Policy violation"
        output = {
            "decision": "deny",
            "reason": f"Blocked by Akto Guardrails: {block_reason}",
        }
        # gemini-cli's AfterTool docs: stderr replaces the tool result. Surface the reason.
        sys.stderr.write(f"Blocked by Akto Guardrails: {block_reason}\n")
        logger.warning(f"BLOCKING tool response - Tool: {tool_name}, Reason: {block_reason}")
        print(json.dumps(output))
        sys.exit(0)

    logger.info(f"Tool response allowed for {tool_name}")
    sys.exit(0)


if __name__ == "__main__":
    main()
