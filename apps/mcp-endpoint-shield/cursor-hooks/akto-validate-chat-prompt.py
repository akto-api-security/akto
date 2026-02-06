#!/usr/bin/env python3
"""
Cursor Chat Before Hook - Prompt Validation via Akto HTTP Proxy API
Validates user prompts before submission using Akto guardrails.
Triggered by beforeSubmitPrompt hook.
"""
import json
import logging
import os
import sys
import urllib.request
from typing import Any, Dict, Tuple, Union

from akto_machine_id import get_machine_id

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
AKTO_CONNECTOR = "claude_code_cli" # todo: update connector name to cursor

# Configure API_URL based on mode
if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    API_URL = f"https://{device_id}.ai-agent.cursor" if device_id else "https://api.anthropic.com"
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, API_URL: {API_URL}")
else:
    API_URL = os.getenv("API_URL", "https://api.anthropic.com")
    logger.info(f"MODE: {MODE}, API_URL: {API_URL}")


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    import time

    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

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
    # Build tags based on mode
    tags = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        tags["ai-agent"] = "cursor"
        tags["source"] = "ENDPOINT"

    # Build metadata with attachment info
    metadata = {
        "tag": tags,
        "interaction_type": "chat"
    }

    if attachments:
        metadata["attachments_count"] = len(attachments)
        metadata["attachment_types"] = [att.get("type", "unknown") for att in attachments]

    return {
        "url": API_URL,
        "path": "/v1/messages",
        "request": {
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "body": {
                "messages": [{"role": "user", "content": prompt}]
            },
            "queryParams": {},
            "metadata": metadata
        },
        "response": None
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

        if isinstance(response, dict) and response.get("blocked"):
            reason = response.get("reason", "Policy violation")
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
