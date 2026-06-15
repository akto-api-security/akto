#!/usr/bin/env python3
import json
import logging
import os
import sys
import time
from typing import Tuple

from akto_helpers import get_device_ip
from akto_ingestion_utility import (
    apply_warn_resubmit_flow,
    build_http_proxy_url,
    fingerprint,
    installer_headers,
    is_warn_behaviour,
    parse_guardrails_result,
    post_payload_json,
    resolve_host_url,
    resolve_session_info,
)

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.claude/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-prompt.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
logger.addHandler(file_handler)

console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)
logger.addHandler(console_handler)

AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_HOST = resolve_host_url("https://api.anthropic.com", legacy_env="AKTO_HOST")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR_VALUE = os.getenv("AKTO_CONNECTOR_VALUE", "claudecli")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "AGENTIC")
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_prompt_warn_pending.json")

DEVICE_IP = get_device_ip()
HOST_HEADER = AKTO_HOST.replace("https://", "").replace("http://", "")

logger.info(f"AKTO_HOST: {AKTO_HOST}, DEVICE_IP: {DEVICE_IP}")


def build_validation_request(query: str, session_info: dict = None) -> dict:
    tags = {"gen-ai": "Gen AI", "source": CONTEXT_SOURCE}

    req_headers = {
        "host": HOST_HEADER,
        "x-claude-hook": "UserPromptSubmit",
        "content-type": "application/json",
    }
    if session_info:
        # raw x-akto-installer-* fields plus normalized akto_* correlation aliases
        req_headers.update(installer_headers(session_info))

    return {
        "path": "/v1/messages",
        "requestHeaders": json.dumps(req_headers),
        "responseHeaders": json.dumps({"x-claude-hook": "UserPromptSubmit"}),
        "method": "POST",
        "requestPayload": json.dumps({"body": query.strip()}),
        "responsePayload": json.dumps({}),
        "ip": DEVICE_IP,
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
        "contextSource": CONTEXT_SOURCE,
    }


def call_guardrails(query: str, session_info: dict = None) -> Tuple[bool, str, str]:
    if not query.strip():
        return True, "", ""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing prompt (fail-open)")
        return True, "", ""

    logger.info("Validating prompt against guardrails")
    if LOG_PAYLOADS:
        logger.debug(f"Prompt: {query}")
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
            "content-type": "application/json",
        })
        request_body["responsePayload"] = json.dumps({
            "body": json.dumps({
                "x-blocked-by": "Akto Proxy",
                "reason": reason or "Policy violation",
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
    logger.info(f"=== Hook execution started - Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    prompt = input_data.get("prompt", "")

    # Persist + open the message turn, and backfill any missing ids from prior events.
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

            output = {"decision": "block", "reason": block_reason}
            logger.warning(f"BLOCKING prompt - Reason: {gr_reason}")
            print(json.dumps(output))
            ingest_blocked_request(prompt, gr_reason, session_info)
            sys.exit(0)

    logger.info("Prompt allowed")
    sys.exit(0)


if __name__ == "__main__":
    main()
