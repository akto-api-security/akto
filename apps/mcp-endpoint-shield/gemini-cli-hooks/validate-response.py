#!/usr/bin/env python3

import json
import logging
import os
import re
import sys
import tempfile
import urllib.request
from typing import Any, Dict, Optional, Union

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
GEMINI_API_URL = os.getenv("GEMINI_API_URL", "https://generativelanguage.googleapis.com")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = "gemini-cli"

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
    headers = {"Content-Type": "application/json"}
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )

    with urllib.request.urlopen(request, timeout=AKTO_TIMEOUT) as response:
        raw = response.read().decode("utf-8")
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return raw


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
    request_metadata = {
        "model": model or "",
        "tag": {"gen-ai": "Gen AI"}
    }
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
        logger.info("Data ingestion successful")

    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def extract_from_after_model(input_data: dict) -> tuple[str, Dict[str, Any], Optional[str], Optional[bool], Dict[str, Any]]:
    llm_request = input_data.get("llm_request") or {}
    llm_response = input_data.get("llm_response") or {}

    prompt = ""
    for message in reversed(llm_request.get("messages") or []):
        if message.get("role") == "user":
            content = message.get("content", "")
            prompt = content if isinstance(content, str) else "".join(
                part.get("text", "") for part in content if isinstance(part, dict)
            )
            break

    streaming = (llm_request.get("config") or {}).get("streaming")
    session_metadata = extract_session_metadata(input_data)
    return prompt, llm_response, llm_request.get("model"), streaming, session_metadata


def main():
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

    sys.stdout.write(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
