#!/usr/bin/env python3

import json
import logging
import os
import sys
import urllib.request
from typing import Any, Dict, Optional, Tuple, Union

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
GEMINI_API_URL = os.getenv("GEMINI_API_URL", "https://generativelanguage.googleapis.com")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = "gemini-cli"


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


def build_validation_request(
    query: str,
    model: Optional[str] = None,
    streaming: Optional[bool] = None,
    session_metadata: Optional[Dict[str, Any]] = None,
) -> dict:
    request_metadata = {
        "model": model or "",
        "tag": {"gen-ai": "Gen AI"}
    }
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


def call_guardrails(
    query: str,
    model: Optional[str] = None,
    streaming: Optional[bool] = None,
    session_metadata: Optional[Dict[str, Any]] = None,
) -> Tuple[bool, str]:
    if not query.strip():
        return True, ""

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
        logger.info("Data ingestion successful")
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

    prompt = ""
    for message in reversed(messages):
        if message.get("role") == "user":
            content = message.get("content", "")
            prompt = content if isinstance(content, str) else "".join(
                part.get("text", "") for part in content if isinstance(part, dict)
            )
            break

    streaming = (llm_request.get("config") or {}).get("streaming")
    session_metadata = extract_session_metadata(input_data)
    return prompt, model, streaming, session_metadata


def main():
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
        sys.stdout.write(json.dumps({}))
        sys.exit(0)

    if AKTO_SYNC_MODE:
        allowed, reason = call_guardrails(
            prompt, model=model, streaming=streaming, session_metadata=session_metadata
        )
        if not allowed:
            output = {
                "decision": "deny",
                "reason": "Blocked by Akto Guardrails"
            }
            sys.stdout.write(json.dumps(output))
            ingest_blocked_request(
                prompt, model=model, streaming=streaming, session_metadata=session_metadata
            )
            sys.exit(0)

    sys.stdout.write(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
