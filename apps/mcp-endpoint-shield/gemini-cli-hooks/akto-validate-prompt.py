#!/usr/bin/env python3

import json
import logging
import os
import sys
import time
import urllib.request
from typing import Any, Dict, Optional, Tuple, Union
from akto_machine_id import get_machine_id

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.gemini/akto/chat-logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

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

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
GEMINI_API_URL = os.getenv("GEMINI_API_URL", "https://generativelanguage.googleapis.com")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
MODE = os.getenv("MODE", "argus").lower()
AKTO_CONNECTOR = "gemini_cli"

if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    GEMINI_API_URL = f"https://{device_id}.ai-agent.gemini" if device_id else GEMINI_API_URL
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, GEMINI_API_URL: {GEMINI_API_URL}")
else:
    logger.info(f"MODE: {MODE}, GEMINI_API_URL: {GEMINI_API_URL}")


def build_model_inference_path(model: Optional[str] = None, streaming: Optional[bool] = None) -> str:
    use_streaming = True if streaming is None else streaming
    suffix = "streamGenerateContent" if use_streaming else "generateContent"
    return f"/v1/models/{model}:{suffix}"


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
        logger.debug(f"Request payload: {json.dumps(payload, default=str)[:1000]}...")

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


def build_validation_request(
    query: str,
    model: Optional[str] = None,
    streaming: Optional[bool] = None,
    session_metadata: Optional[Dict[str, Any]] = None,
) -> dict:
    tags = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        tags["ai-agent"] = "geminicli"
        tags["source"] = "ENDPOINT"

    request_metadata = {
        "model": model or "",
        "tag": tags,
    }
    if MODE == "atlas":
        request_metadata["machine_id"] = os.getenv("DEVICE_ID") or get_machine_id()
        request_metadata["log_storage"] = {"type": "local_file", "path": LOG_DIR}
    if session_metadata:
        request_metadata["gemini_cli_session"] = session_metadata
    return {
        "url": GEMINI_API_URL,
        "path": build_model_inference_path(model=model, streaming=streaming),
        "request": {
            "method": "POST",
            "headers": {"content-type": "application/json"},
            "body": {
                "messages": [{"role": "user", "content": query.strip()}],
            },
            "queryParams": {},
            "metadata": request_metadata
        },
        "response": None
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
) -> Tuple[bool, str]:
    if not query.strip():
        return True, ""

    logger.info("Validating prompt against guardrails")
    if LOG_PAYLOADS:
        logger.debug(f"Prompt: {query[:200]}...")
    else:
        logger.info(f"Prompt preview: {query[:100]}...")

    try:
        request_body = build_validation_request(
            query, model=model, streaming=streaming, session_metadata=session_metadata
        )
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
        logger.error(f"Guardrails validation error: {e}", exc_info=True)
        return True, ""


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
        blocked_response_payload = {
            "body": {"x-blocked-by": "Akto Proxy"},
            "headers": {"content-type": "application/json"},
            "statusCode": 403,
            "status": "forbidden"
        }

        request_body = build_validation_request(
            user_prompt, model=model, streaming=streaming, session_metadata=session_metadata
        )
        request_body["response"] = blocked_response_payload
        post_payload_json(
            build_http_proxy_url(guardrails=False, ingest_data=True),
            request_body,
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
        allowed, reason = call_guardrails(
            prompt, model=model, streaming=streaming, session_metadata=session_metadata
        )
        if not allowed:
            output = {
                "decision": "deny",
                "reason": "Blocked by Akto Guardrails"
            }
            logger.warning(f"BLOCKING prompt - Reason: {reason}")
            sys.stdout.write(json.dumps(output))
            ingest_blocked_request(
                prompt, model=model, streaming=streaming, session_metadata=session_metadata
            )
            sys.exit(0)

    logger.info("Prompt allowed")
    sys.stdout.write(json.dumps({}))
    sys.exit(0)
