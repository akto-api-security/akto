#!/usr/bin/env python3
"""
Akto OpenCode Prompt Validation Hook
Validates user prompts against Akto guardrails before sending to AI
Hook: experimental.chat.messages.transform
"""

import json
import logging
import os
import sys
import time
from typing import Tuple

# OpenCode launches hooks via a JS plugin that does not export AKTO_CONNECTOR,
# so default it before importing the shared module (which reads it at import time).
os.environ.setdefault("AKTO_CONNECTOR", "opencode")

from akto_machine_id import get_machine_id, get_username
from akto_ingestion_utility import (
    apply_warn_resubmit_flow,
    build_http_proxy_url,
    fingerprint,
    installer_headers,
    is_warn_behaviour,
    parse_guardrails_result,
    post_payload_json,
    resolve_session_info,
)

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.config/opencode/akto/logs"))
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

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_prompt_warn_pending.json")

# MODE configuration (for device tracking in atlas mode)
MODE = os.getenv("MODE", "argus").lower()

if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    OPENCODE_API_URL = f"https://{device_id}.opencode.local" if device_id else "https://api.opencode.ai"
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, OPENCODE_API_URL: {OPENCODE_API_URL}")
else:
    OPENCODE_API_URL = os.getenv("OPENCODE_API_URL", "https://api.opencode.ai")
    logger.info(f"MODE: {MODE}, OPENCODE_API_URL: {OPENCODE_API_URL}")


def build_validation_request(query: str, session_info: dict = None) -> dict:
    tags = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        tags["ai-agent"] = "opencode"
        tags["source"] = CONTEXT_SOURCE

    device_id = os.getenv("DEVICE_ID") or get_machine_id()

    host = OPENCODE_API_URL.replace("https://", "").replace("http://", "")

    req_headers = {
        "host": host,
        "x-claude-hook": "UserPromptSubmit",
        "content-type": "application/json"
    }
    if session_info:
        req_headers.update(installer_headers(session_info))

    request_headers = json.dumps(req_headers)

    response_headers = json.dumps({
        "x-claude-hook": "UserPromptSubmit"
    })

    request_payload = json.dumps({
        "body": query.strip()
    })

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
        "contextSource": CONTEXT_SOURCE
    }


def call_guardrails(query: str, session_info: dict = None) -> Tuple[bool, str, str]:
    if not query.strip():
        return True, "", ""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing prompt (fail-open)")
        return True, "", ""

    logger.info("Validating prompt against guardrails")
    if LOG_PAYLOADS:
        logger.debug(f"Prompt: {query[:200]}...")
    else:
        logger.info(f"Prompt preview: {query[:100]}...")

    try:
        request_body = build_validation_request(query, session_info)
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=True),
            request_body,
            logger,
        )

        allowed, reason, behaviour = parse_guardrails_result(result)
        if allowed:
            logger.info("Prompt ALLOWED by guardrails")
        else:
            logger.warning(f"Prompt DENIED by guardrails: {reason}")

        return allowed, reason, behaviour

    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, "", ""


def ingest_blocked_request(user_prompt: str, reason: str, session_info: dict = None):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    logger.info("Ingesting blocked request data")
    try:
        request_body = build_validation_request(user_prompt, session_info)
        request_body["responseHeaders"] = json.dumps({
            "x-claude-hook": "UserPromptSubmit",
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
            logger,
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

    session_info = resolve_session_info(input_data, logger, is_prompt_hook=True)

    if not prompt.strip():
        logger.info("Empty prompt, allowing")
        sys.exit(0)

    logger.info(f"Processing prompt (length: {len(prompt)} chars)")

    if AKTO_SYNC_MODE:
        gr_allowed, gr_reason, behaviour = call_guardrails(prompt, session_info)
        fp = fingerprint({"p": prompt, "a": []})
        allowed, _ = apply_warn_resubmit_flow(
            WARN_STATE_PATH, fp, gr_allowed, gr_reason, behaviour, logger
        )

        if not allowed:
            if is_warn_behaviour(behaviour):
                block_reason = (
                    "Warning!!, prompt blocked, please review it. Send again to bypass. "
                    f"Reason for blocking: {gr_reason}"
                )
            else:
                block_reason = f"Prompt blocked: {gr_reason}"

            output = {
                "decision": "block",
                "reason": block_reason,
            }
            logger.warning(f"BLOCKING prompt - Reason: {gr_reason}")
            logger.warning(f"BLOCK DECISION OUTPUT: {json.dumps(output)}")

            # CRITICAL: Print the JSON block decision to stdout (OpenCode reads this)
            print(json.dumps(output), flush=True)
            logger.info("Block decision printed to stdout - OpenCode plugin should receive this")

            ingest_blocked_request(prompt, gr_reason, session_info)
            logger.info("Block decision sent to OpenCode plugin and Akto ingested")
            sys.exit(0)

    logger.info("Prompt allowed - continuing to AI")
    sys.exit(0)


if __name__ == "__main__":
    main()
