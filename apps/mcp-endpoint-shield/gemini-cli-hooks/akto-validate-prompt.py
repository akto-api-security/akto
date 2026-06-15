#!/usr/bin/env python3

import json
import logging
import os
import sys
import time
from typing import Any, Dict, Optional, Tuple
from akto_machine_id import get_machine_id, get_username
from akto_ingestion_utility import (
    apply_warn_resubmit_flow,
    build_http_proxy_url,
    fingerprint,
    is_warn_behaviour,
    parse_guardrails_result,
    post_payload_json,
    resolve_host_url,
)

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.gemini/akto/chat-logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_prompt_warn_pending.json")

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-prompt.log"))
    file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    file_formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
    file_handler.setFormatter(file_formatter)
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    logger.addHandler(console_handler)

AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
MODE = os.getenv("MODE", "argus").lower()
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

GEMINI_API_URL = resolve_host_url("https://generativelanguage.googleapis.com", legacy_env="GEMINI_API_URL")
logger.info(f"MODE: {MODE}, GEMINI_API_URL: {GEMINI_API_URL}")


def build_akto_request(
    query: str,
    model: Optional[str] = None,
    session_metadata: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    tags = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        tags["ai-agent"] = "geminicli"
        tags["source"] = CONTEXT_SOURCE

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = GEMINI_API_URL.replace("https://", "").replace("http://", "")

    request_headers = json.dumps({
        "host": host,
        "x-geminicli-hook": "BeforeModel",
        "content-type": "application/json"
    })

    response_headers = json.dumps({
        "x-geminicli-hook": "BeforeModel"
    })

    request_payload = json.dumps({
        "body": query.strip()
    })

    response_payload = json.dumps({})

    metadata: Dict[str, Any] = {"model": model or ""}
    if MODE == "atlas":
        metadata["machine_id"] = device_id
        metadata["log_storage"] = {"type": "local_file", "path": LOG_DIR}
    if session_metadata:
        metadata["gemini_cli_session"] = session_metadata

    return {
        "path": "/gemini/chat",
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
        "metadata": json.dumps(metadata),
        "contextSource": CONTEXT_SOURCE
    }


def parse_user_prompt(messages: list) -> str:
    for message in reversed(messages):
        if message.get("role") == "user":
            content = message.get("content", "")
            if isinstance(content, str):
                return content
            return "".join(part.get("text", "") for part in content if isinstance(part, dict))
    return ""


def call_guardrails(
    query: str,
    model: Optional[str] = None,
    streaming: Optional[bool] = None,
    session_metadata: Optional[Dict[str, Any]] = None,
) -> Tuple[bool, str, str]:
    if not query.strip():
        return True, "", ""

    logger.info("Validating prompt against guardrails")
    if LOG_PAYLOADS:
        logger.debug(f"Prompt: {query[:200]}...")
    else:
        logger.info(f"Prompt preview: {query[:100]}...")

    try:
        request_body = build_akto_request(query, model=model, session_metadata=session_metadata)
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=False),
            request_body,
            logger,
        )

        allowed, reason, behaviour = parse_guardrails_result(result)

        if allowed:
            logger.info("Prompt ALLOWED by guardrails")
        else:
            logger.warning(f"Prompt DENIED by guardrails: {reason} (behaviour={behaviour!r})")

        return allowed, reason, behaviour

    except Exception as e:
        logger.error(f"Guardrails validation error: {e}", exc_info=True)
        return True, "", ""


def ingest_blocked_request(
    user_prompt: str,
    model: Optional[str] = None,
    streaming: Optional[bool] = None,
    session_metadata: Optional[Dict[str, Any]] = None,
):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    logger.info("Ingesting blocked request data")
    try:
        request_body = build_akto_request(user_prompt, model=model, session_metadata=session_metadata)
        request_body["responsePayload"] = json.dumps({
            "body": json.dumps({"x-blocked-by": "Akto Proxy"})
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


def extract_session_metadata(input_data: dict) -> Dict[str, Any]:
    metadata = {}
    for key in ("session_id", "transcript_path", "cwd", "hook_event_name", "timestamp"):
        value = input_data.get(key)
        if value:
            metadata["hook_timestamp" if key == "timestamp" else key] = value
    return metadata


def extract_from_before_model(input_data: dict) -> Tuple[str, Optional[str], Optional[bool], Dict[str, Any]]:
    llm_request = input_data.get("llm_request") or {}
    model = llm_request.get("model")
    messages = llm_request.get("messages") or []
    prompt = parse_user_prompt(messages)

    streaming = (llm_request.get("config") or {}).get("streaming")
    session_metadata = extract_session_metadata(input_data)
    return prompt, model, streaming, session_metadata


def main():
    logger.info(f"=== Hook execution started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")
    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(2)

    hook_event = input_data.get("hook_event_name", "")

    if hook_event != "BeforeModel":
        sys.stdout.write(json.dumps({}))
        sys.exit(0)

    prompt, model, streaming, session_metadata = extract_from_before_model(input_data)
    logger.info(f"BeforeModel: model={model or 'unknown'}, streaming={streaming}")

    if not prompt.strip():
        logger.info("Empty prompt, allowing")
        sys.stdout.write(json.dumps({}))
        sys.exit(0)

    logger.info(f"Processing prompt (length: {len(prompt)} chars)")

    if AKTO_SYNC_MODE:
        gr_allowed, gr_reason, behaviour = call_guardrails(
            prompt, model=model, streaming=streaming, session_metadata=session_metadata
        )
        fp = fingerprint({"p": prompt})
        allowed, _ = apply_warn_resubmit_flow(
            WARN_STATE_PATH, fp, gr_allowed, gr_reason, behaviour, logger
        )
        if not allowed:
            if is_warn_behaviour(behaviour):
                deny_reason = (
                    f"Warning!! Prompt blocked, please review it. Send the same prompt again to bypass. "
                    f"Reason: {gr_reason or 'Policy violation'}"
                )
            else:
                deny_reason = f"Blocked by Akto Guardrails: {gr_reason or 'Policy violation'}"
            output = {
                "decision": "deny",
                "reason": deny_reason,
            }
            logger.warning(f"BLOCKING prompt - behaviour={behaviour!r}, Reason: {gr_reason}")
            sys.stdout.write(json.dumps(output))
            ingest_blocked_request(
                prompt, model=model, streaming=streaming, session_metadata=session_metadata
            )
            sys.exit(0)

    logger.info("Prompt allowed")
    sys.stdout.write(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
