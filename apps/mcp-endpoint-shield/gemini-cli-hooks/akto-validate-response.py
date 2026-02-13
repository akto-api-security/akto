#!/usr/bin/env python3

import json
import logging
import os
import re
import sys
import tempfile
import time
import urllib.request
from typing import Any, Dict, Optional, Union
from akto_machine_id import get_machine_id

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.gemini/akto/chat-logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-response.log"))
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

AKTO_CHUNKS_DIR = os.path.join(tempfile.gettempdir(), "akto_gemini_cli_chunks")


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


def extract_session_metadata(input_data: dict) -> Dict[str, Any]:
    metadata = {}
    for key in ("session_id", "transcript_path", "cwd", "hook_event_name", "timestamp"):
        value = input_data.get(key)
        if value:
            metadata["hook_timestamp" if key == "timestamp" else key] = value
    return metadata


def get_chunks_filepath(session_id: str) -> str:
    return os.path.join(AKTO_CHUNKS_DIR, f"{session_id}.txt")


THINKING_BLOCK_PATTERN = re.compile(
    r"^\*\*[^*]+\*\*\s*\n\n[\s\S]*?\n\n\n+",
    re.MULTILINE,
)


def strip_thinking_blocks(text: str) -> str:
    if not text or not text.strip():
        return text
    stripped = text
    while True:
        next_stripped = THINKING_BLOCK_PATTERN.sub("", stripped)
        if next_stripped == stripped:
            break
        stripped = next_stripped
    return stripped.strip()


def extract_chunk_text(llm_response: Dict[str, Any]) -> str:
    candidates = llm_response.get("candidates") or []
    if not candidates:
        return ""
    parts = (candidates[0].get("content") or {}).get("parts") or []
    texts = []
    for part in parts:
        if isinstance(part, str):
            texts.append(part)
        elif isinstance(part, dict) and not part.get("thought", False):
            text = part.get("text", "")
            if text:
                texts.append(text)
    return "".join(texts)


def append_chunk(session_id: str, chunk_text: str) -> None:
    os.makedirs(AKTO_CHUNKS_DIR, exist_ok=True)
    with open(get_chunks_filepath(session_id), "a", encoding="utf-8") as f:
        f.write(chunk_text)


def read_accumulated_text_and_clear_file(session_id: str) -> str:
    filepath = get_chunks_filepath(session_id)
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            text = f.read()
    except FileNotFoundError:
        text = ""
    try:
        os.remove(filepath)
    except OSError:
        pass
    return text

def build_ingestion_payload(
    user_prompt: str,
    full_response_text: str,
    model: Optional[str] = None,
    streaming: Optional[bool] = None,
    session_metadata: Optional[Dict[str, Any]] = None,
    usage_metadata: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    tags = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        tags["ai-agent"] = "gemini"
        tags["source"] = "ENDPOINT"

    request_metadata = {
        "model": model or "",
        "tag": tags,
        "interaction_type": "chat",
    }
    if MODE == "atlas":
        request_metadata["machine_id"] = os.getenv("DEVICE_ID") or get_machine_id()
        request_metadata["log_storage"] = {"type": "local_file", "path": LOG_DIR}
    if session_metadata:
        request_metadata["gemini_cli_session"] = session_metadata

    llm_response: Dict[str, Any] = {
        "candidates": [
            {
                "content": {"role": "model", "parts": [full_response_text]},
            }
        ]
    }
    if usage_metadata:
        llm_response["usageMetadata"] = usage_metadata

    return {
        "url": GEMINI_API_URL,
        "path": build_model_inference_path(model=model, streaming=streaming),
        "request": {
            "method": "POST",
            "headers": {"content-type": "application/json"},
            "body": {
                "messages": [{"role": "user", "content": user_prompt}]
            },
            "queryParams": {},
            "metadata": request_metadata
        },
        "response": {
            "body": llm_response,
            "headers": {"content-type": "application/json"},
            "statusCode": 200,
            "status": "OK"
        }
    }


def send_ingestion_data(
    user_prompt: str,
    full_response_text: str,
    model: Optional[str] = None,
    streaming: Optional[bool] = None,
    session_metadata: Optional[Dict[str, Any]] = None,
    usage_metadata: Optional[Dict[str, Any]] = None,
):
    if not user_prompt.strip():
        return

    logger.info(f"Ingesting response data (prompt_len={len(user_prompt)}, response_len={len(full_response_text)})")
    if LOG_PAYLOADS:
        logger.debug(f"Prompt preview: {user_prompt[:500]}...")
        logger.debug(f"Response preview: {full_response_text[:500]}...")

    try:
        request_body = build_ingestion_payload(
            user_prompt,
            full_response_text,
            model=model,
            streaming=streaming,
            session_metadata=session_metadata,
            usage_metadata=usage_metadata,
        )
        post_payload_json(
            build_http_proxy_url(
                guardrails=not AKTO_SYNC_MODE,
                ingest_data=True,
            ),
            request_body,
        )
        logger.info("Response ingestion successful")

    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def parse_user_prompt(messages: list) -> str:
    for message in reversed(messages):
        if message.get("role") == "user":
            content = message.get("content", "")
            if isinstance(content, str):
                return content
            return "".join(part.get("text", "") for part in content if isinstance(part, dict))
    return ""


def extract_from_after_model(input_data: dict) -> tuple[str, Dict[str, Any], Optional[str], Optional[bool], Dict[str, Any]]:
    llm_request = input_data.get("llm_request") or {}
    llm_response = input_data.get("llm_response") or {}
    prompt = parse_user_prompt(llm_request.get("messages") or [])

    streaming = (llm_request.get("config") or {}).get("streaming")
    session_metadata = extract_session_metadata(input_data)
    return prompt, llm_response, llm_request.get("model"), streaming, session_metadata


def main():
    logger.info(f"=== Response hook execution started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")
    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(2)

    hook_event = input_data.get("hook_event_name", "")

    if hook_event != "AfterModel":
        sys.stdout.write(json.dumps({}))
        sys.exit(0)

    session_id = input_data.get("session_id", "default")
    user_prompt, llm_response, model, streaming, session_metadata = extract_from_after_model(input_data)
    logger.info(f"AfterModel: model={model or 'unknown'}, streaming={streaming}")

    chunk_text = extract_chunk_text(llm_response)
    if chunk_text:
        append_chunk(session_id, chunk_text)
    elif LOG_PAYLOADS:
        logger.debug("No chunk text extracted for this event")

    candidates = llm_response.get("candidates") or []
    finish_reason = candidates[0].get("finishReason") if candidates else None

    if user_prompt and finish_reason:
        full_response_text = read_accumulated_text_and_clear_file(session_id)
        full_response_text = strip_thinking_blocks(full_response_text)
        usage_metadata = llm_response.get("usageMetadata")

        send_ingestion_data(
            user_prompt,
            full_response_text,
            model=model,
            streaming=streaming,
            session_metadata=session_metadata,
            usage_metadata=usage_metadata,
        )
    else:
        logger.info("No final response to ingest yet (waiting for finishReason/user_prompt)")

    sys.stdout.write(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
