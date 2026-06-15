#!/usr/bin/env python3
import json
import logging
import os
import sys
import time
from typing import Any, Dict, Tuple

from akto_helpers import get_device_ip
from akto_ingestion_utility import (
    apply_warn_resubmit_flow,
    build_http_proxy_url,
    fingerprint,
    get_last_user_prompt,
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

file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-response.log"))
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
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_response_warn_pending.json")

DEVICE_IP = get_device_ip()
HOST_HEADER = AKTO_HOST.replace("https://", "").replace("http://", "")

logger.info(f"AKTO_HOST: {AKTO_HOST}, DEVICE_IP: {DEVICE_IP}")


def build_ingestion_payload(
    user_prompt: str, response_text: str, session_info: dict = None
) -> Dict[str, Any]:
    tags = {"gen-ai": "Gen AI", "source": CONTEXT_SOURCE}

    req_headers = {
        "host": HOST_HEADER,
        "x-claude-hook": "Stop",
        "content-type": "application/json",
    }
    if session_info:
        req_headers.update(installer_headers(session_info))

    return {
        "path": "/v1/messages",
        "requestHeaders": json.dumps(req_headers),
        "responseHeaders": json.dumps({
            "x-claude-hook": "Stop",
            "content-type": "application/json",
        }),
        "method": "POST",
        "requestPayload": json.dumps({"body": user_prompt}),
        "responsePayload": json.dumps({"body": response_text}),
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


def call_guardrails(
    user_prompt: str, response_text: str, session_info: dict = None
) -> Tuple[bool, str, str]:
    if not response_text.strip():
        return True, "", ""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing response (fail-open)")
        return True, "", ""

    logger.info("Validating assistant response against guardrails")
    if LOG_PAYLOADS:
        logger.debug(f"Response: {response_text}")
    else:
        logger.info(f"Response preview: {response_text[:100]}...")

    try:
        request_body = build_ingestion_payload(user_prompt, response_text, session_info)
        result = post_payload_json(
            build_http_proxy_url(
                guardrails=False,
                response_guardrails=True,
                ingest_data=False,
            ),
            request_body,
            logger,
        )

        allowed, reason, behaviour = parse_guardrails_result(result)
        if allowed:
            logger.info("Response ALLOWED by guardrails")
        else:
            logger.warning(f"Response DENIED by guardrails: {reason}")

        return allowed, reason, behaviour

    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, "", ""


def ingest_blocked_response(
    user_prompt: str,
    response_text: str,
    reason: str,
    session_info: dict = None,
):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    logger.info("Ingesting blocked request data")
    try:
        request_body = build_ingestion_payload(user_prompt, response_text, session_info)
        request_body["responseHeaders"] = json.dumps({
            "x-claude-hook": "Stop",
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


def send_ingestion_data(
    user_prompt: str, response_text: str, session_info: dict = None
):
    if not AKTO_DATA_INGESTION_URL:
        logger.info("AKTO_DATA_INGESTION_URL not set, skipping ingestion")
        return

    if not user_prompt.strip() or not response_text.strip():
        return

    logger.info("Ingesting conversation data")
    if LOG_PAYLOADS:
        logger.debug(f"Prompt: {user_prompt}")
        logger.debug(f"Response: {response_text}")
    else:
        logger.info(f"Prompt preview: {user_prompt[:100]}...")
        logger.info(f"Response preview: {response_text[:100]}...")

    try:
        request_body = build_ingestion_payload(user_prompt, response_text, session_info)
        post_payload_json(
            build_http_proxy_url(
                guardrails=False,
                response_guardrails=not AKTO_SYNC_MODE,
                ingest_data=True,
            ),
            request_body,
            logger,
        )
        logger.info("Conversation ingestion successful")

    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info(f"=== Hook execution started - Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    session_info = resolve_session_info(input_data, logger)

    try:
        transcript_path = input_data.get("transcript_path")

        if not transcript_path:
            logger.info("No transcript path provided")
            sys.exit(0)

        transcript_path = os.path.expanduser(transcript_path)
        logger.info(f"Reading transcript from: {transcript_path}")

        response_text = input_data.get("last_assistant_message", "").strip()
        stop_hook_active = bool(input_data.get("stop_hook_active"))

        user_prompt = get_last_user_prompt(transcript_path, logger)

        if not user_prompt or not response_text:
            logger.info("No complete interaction found in transcript")
            sys.exit(0)

        logger.info(
            f"Extracted interaction - Prompt: {len(user_prompt)} chars, "
            f"Response: {len(response_text)} chars"
        )

        if stop_hook_active:
            logger.info("stop_hook_active=true: skipping guardrails block to avoid Stop hook loops")

        if AKTO_SYNC_MODE and not stop_hook_active:
            gr_allowed, gr_reason, behaviour = call_guardrails(
                user_prompt, response_text, session_info
            )
            fp = fingerprint({"p": user_prompt, "r": response_text})
            allowed, _ = apply_warn_resubmit_flow(
                WARN_STATE_PATH, fp, gr_allowed, gr_reason, behaviour, logger
            )

            if not allowed:
                if is_warn_behaviour(behaviour):
                    block_reason = (
                        "Warning!!, response blocked, please review it. Send again to bypass. "
                        f"Reason for blocking: {gr_reason}"
                    )
                else:
                    block_reason = f"Response blocked: {gr_reason}"

                output = {"decision": "block", "reason": block_reason}
                logger.warning(f"BLOCKING Stop - Reason: {gr_reason}")
                print(json.dumps(output))
                ingest_blocked_response(user_prompt, response_text, gr_reason, session_info)
                sys.exit(0)

        send_ingestion_data(user_prompt, response_text, session_info)

    except Exception as e:
        logger.error(f"Main error: {e}")
        sys.exit(0)

    logger.info("Hook execution completed")
    sys.exit(0)


if __name__ == "__main__":
    main()
