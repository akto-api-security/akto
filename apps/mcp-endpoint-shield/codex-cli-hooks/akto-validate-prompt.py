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

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.codex/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

# Create log directory if it doesn't exist
os.makedirs(LOG_DIR, exist_ok=True)

# Setup logging with both file and console handlers
logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

# File handler
file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-prompt.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
file_handler.setFormatter(file_formatter)
logger.addHandler(file_handler)

# Console handler
console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)
logger.addHandler(console_handler)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "codex_cli")
AKTO_TOKEN = os.getenv("AKTO_TOKEN", "")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

# SSL Configuration
SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"

# Auto-detect Codex API host and path from the same env vars Codex CLI uses
def _detect_codex_api():
    base_url = os.getenv("OPENAI_BASE_URL")
    if base_url:
        return base_url.rstrip("/"), "/v1/responses"
    if os.getenv("OPENAI_API_KEY"):
        return "https://api.openai.com", "/v1/responses"
    return "https://chatgpt.com", "/backend-api/codex/responses"

_detected_host, CODEX_API_PATH = _detect_codex_api()
if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    CODEX_API_HOST = f"https://{device_id}.ai-agent.codexcli" if device_id else _detected_host
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, CODEX_API_HOST: {CODEX_API_HOST}, CODEX_API_PATH: {CODEX_API_PATH}")
else:
    CODEX_API_HOST = _detected_host
    logger.info(f"MODE: {MODE}, CODEX_API_HOST: {CODEX_API_HOST}, CODEX_API_PATH: {CODEX_API_PATH}")


def create_ssl_context():
    return ssl._create_unverified_context()


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
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


def build_validation_request(query: str) -> dict:
    tags = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        tags["ai-agent"] = "codexcli"
        tags["source"] = CONTEXT_SOURCE

    host = CODEX_API_HOST.replace("https://", "").replace("http://", "")

    request_headers = json.dumps({
        "host": host,
        "x-codex-hook": "UserPromptSubmit",
        "content-type": "application/json"
    })

    response_headers = json.dumps({
        "x-codex-hook": "UserPromptSubmit"
    })

    request_payload = json.dumps({
        "body": query.strip()
    })

    response_payload = json.dumps({})

    return {
        "path": CODEX_API_PATH,
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
        "akto_vxlan_id": 0,
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


def call_guardrails(query: str) -> Tuple[bool, str]:
    if not query.strip():
        return True, ""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing prompt (fail-open)")
        return True, ""

    logger.info("Validating prompt against guardrails")
    if LOG_PAYLOADS:
        logger.debug(f"Prompt: {query[:200]}...")
    else:
        logger.info(f"Prompt preview: {query[:100]}...")

    try:
        request_body = build_validation_request(query)
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=False),
            request_body,
        )

        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")

        if allowed:
            logger.info("Prompt ALLOWED by guardrails")
        else:
            logger.warning(f"Prompt DENIED by guardrails: {reason}")

        return allowed, reason

    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, ""


def ingest_blocked_request(user_prompt: str, reason: str):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    logger.info("Ingesting blocked request data")
    try:
        request_body = build_validation_request(user_prompt)
        request_body["responseHeaders"] = json.dumps({
            "x-codex-hook": "UserPromptSubmit",
            "x-blocked-by": "Akto Proxy",
            "content-type": "application/json"
        })
        request_body["responsePayload"] = json.dumps({
            "body": json.dumps({
                "x-blocked-by": "Akto Proxy",
                "reason": reason or "Policy violation"
            })
        })
        request_body["statusCode"] = "403"
        request_body["status"] = "403"
        post_payload_json(
            build_http_proxy_url(guardrails=False, ingest_data=True),
            request_body,
        )
        logger.info("Blocked request ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info(f"=== Hook execution started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    prompt = input_data.get("prompt", "")
    session_id = input_data.get("session_id", "")
    logger.info(f"Session: {session_id}, Hook: {input_data.get('hook_event_name', 'UserPromptSubmit')}")

    if not prompt.strip():
        logger.info("Empty prompt, allowing")
        sys.exit(0)

    logger.info(f"Processing prompt (length: {len(prompt)} chars)")

    if AKTO_SYNC_MODE:
        allowed, reason = call_guardrails(prompt)
        if not allowed:
            block_reason = reason or "Policy violation"
            output = {
                "decision": "block",
                "reason": block_reason,
                "systemMessage": f"Blocked by Akto Guardrails: {block_reason}" if block_reason else "Blocked by Akto Guardrails",
            }
            logger.warning(f"BLOCKING prompt - Reason: {block_reason}")
            print(json.dumps(output))
            ingest_blocked_request(prompt, block_reason)
            sys.exit(0)

    logger.info("Prompt allowed")
    sys.exit(0)


if __name__ == "__main__":
    main()
