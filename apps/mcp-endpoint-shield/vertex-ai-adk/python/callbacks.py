"""
Akto Guardrails callbacks for Google Vertex AI ADK.

Usage:
    from callbacks import akto_before_model_callback, akto_after_model_callback

    agent = LlmAgent(
        model="gemini-2.0-flash",
        name="my_agent",
        instruction="You are a helpful assistant.",
        before_model_callback=akto_before_model_callback,
        after_model_callback=akto_after_model_callback,
    )

Environment Variables:
    DATA_INGESTION_URL   : Base URL for Akto's data ingestion service (required).
    SYNC_MODE            : "true" (default) to block before the LLM call;
                           "false" to validate/log asynchronously after the response.
    TIMEOUT              : HTTP request timeout in seconds (default: 5).

Auto-detected (Vertex AI Agent Engine injects these at runtime):
    GOOGLE_CLOUD_PROJECT           : GCP project ID.
    GOOGLE_CLOUD_LOCATION          : Region (e.g. "us-central1").
    GOOGLE_CLOUD_AGENT_ENGINE_ID   : Reasoning Engine resource ID.
"""

import json
import os
import logging
import time
import httpx
from typing import Optional, Tuple, Any

from google.adk.agents.callback_context import CallbackContext
from google.adk.models import LlmRequest, LlmResponse
from google.genai import types

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

DATA_INGESTION_URL = os.getenv("DATA_INGESTION_URL")
SYNC_MODE = os.getenv("SYNC_MODE", "true").lower() == "true"
TIMEOUT = float(os.getenv("TIMEOUT", "5"))
AKTO_CONNECTOR_NAME = "vertex-ai-adk"
HTTP_PROXY_PATH = "/api/http-proxy"

# State key used to pass request data from before_model_callback to after_model_callback.
_AKTO_SNAPSHOT_KEY = "__akto_request_snapshot"

# Shared async HTTP client.
_client = httpx.AsyncClient(
    timeout=TIMEOUT,
    limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
)

logger.info(f"Akto Guardrails ADK callbacks initialized | sync_mode={SYNC_MODE}")


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

def _get_vertex_endpoint_info() -> tuple:
    """Derive host and path from Vertex AI Agent Engine env vars.

    Deployed: uses GOOGLE_CLOUD_PROJECT, GOOGLE_CLOUD_LOCATION, GOOGLE_CLOUD_AGENT_ENGINE_ID.
    Local:    falls back to defaults.
    """
    project = os.getenv("GOOGLE_CLOUD_PROJECT")
    location = os.getenv("GOOGLE_CLOUD_LOCATION")
    engine_id = os.getenv("GOOGLE_CLOUD_AGENT_ENGINE_ID")

    if project and location and engine_id:
        host = f"{location}-aiplatform.googleapis.com"
        path = f"/v1/projects/{project}/locations/{location}/reasoningEngines/{engine_id}:query"
        return host, path

    # Local dev fallback
    return "generativelanguage.googleapis.com", "/v1/chat/completions"


def _build_http_proxy_params(*, guardrails: bool, ingest_data: bool) -> dict:
    params: dict = {"akto_connector": AKTO_CONNECTOR_NAME}
    if guardrails:
        params["guardrails"] = "true"
    if ingest_data:
        params["ingest_data"] = "true"
    return params


def _parse_guardrails_result(result: Any) -> Tuple[bool, str]:
    if not isinstance(result, dict):
        return True, ""
    guardrails_result = result.get("data", {}).get("guardrailsResult", {}) or {}
    return guardrails_result.get("Allowed", True), guardrails_result.get("Reason", "")


def _contents_to_messages(contents) -> list:
    """Convert a list of ADK Content objects to OpenAI-style message dicts."""
    messages = []
    if not contents:
        return messages
    for content in contents:
        if not content:
            continue
        role = getattr(content, "role", "user")
        parts = getattr(content, "parts", []) or []
        text_parts = [p.text for p in parts if getattr(p, "text", None)]
        messages.append({"role": role, "content": " ".join(text_parts)})
    return messages


def _llm_response_to_dict(llm_response: LlmResponse) -> Optional[dict]:
    """Convert an LlmResponse to a plain dict suitable for ingestion."""
    if llm_response is None:
        return None
    content = getattr(llm_response, "content", None)
    if content is None:
        return None
    role = getattr(content, "role", "model")
    parts = getattr(content, "parts", []) or []
    text_parts = [p.text for p in parts if getattr(p, "text", None)]
    return {
        "choices": [{"message": {"role": role, "content": " ".join(text_parts)}}]
    }


def _make_blocked_response(reason: str) -> LlmResponse:
    """Return an LlmResponse that represents a guardrails block."""
    msg = (
        f"Blocked by Akto Guardrails: {reason}"
        if reason
        else "Blocked by Akto Guardrails"
    )
    return LlmResponse(
        content=types.Content(
            role="model",
            parts=[types.Part(text=msg)],
        )
    )


def _build_payload(
    callback_context: CallbackContext,
    model: str,
    messages: list,
    response_body: Optional[Any],
    status_code: int = 200,
) -> dict:
    agent_name = callback_context.agent_name or "unknown-agent"
    invocation_id = str(getattr(callback_context, "invocation_id", ""))
    engine_id = os.getenv("GOOGLE_CLOUD_AGENT_ENGINE_ID")
    host, path = _get_vertex_endpoint_info()
    timestamp = str(int(time.time() * 1000))
    status_str = str(status_code)

    model_body_payload = {
        "model": model,
        "messages": messages,
    }

    metadata = {
        "call_type": "completion",
        "model": model,
        "agent_name": agent_name,
        "invocation_id": invocation_id,
    }

    tags = {"gen-ai": "Gen AI", "ai-agent": AKTO_CONNECTOR_NAME, "bot-name": engine_id, "source": "VERTEX_AI"}

    request_payload = json.dumps({"body": json.dumps(model_body_payload)})
    response_payload = (
        json.dumps({})
        if response_body is None
        else json.dumps({"body": json.dumps(response_body)})
    )

    return {
        "path": path,
        "requestHeaders": json.dumps({
            "host": host,
            "content-type": "application/json",
        }),
        "responseHeaders": json.dumps({
            "content-type": "application/json",
        }),
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": "0.0.0.0",
        "destIp": "127.0.0.1",
        "time": timestamp,
        "statusCode": status_str,
        "type": "HTTP/1.1",
        "status": status_str,
        "akto_account_id": "1000000",
        "akto_vxlan_id": "0",
        "is_pending": "false",
        "source": "MIRRORING",
        "direction": None,
        "process_id": None,
        "socket_id": None,
        "daemonset_id": None,
        "enabled_graph": None,
        "tag": json.dumps(tags),
        "metadata": json.dumps(metadata),
        "contextSource": "AGENTIC",
    }


async def _post_http_proxy(
    *,
    guardrails: bool,
    ingest_data: bool,
    payload: dict,
) -> httpx.Response:
    endpoint = f"{DATA_INGESTION_URL}{HTTP_PROXY_PATH}"
    return await _client.post(
        endpoint,
        params=_build_http_proxy_params(guardrails=guardrails, ingest_data=ingest_data),
        json=payload,
    )


async def _call_guardrails_validation(
    callback_context: CallbackContext,
    model: str,
    messages: list,
) -> Tuple[bool, str]:
    if not DATA_INGESTION_URL:
        return True, ""

    payload = _build_payload(callback_context, model, messages, None)
    try:
        response = await _post_http_proxy(guardrails=True, ingest_data=False, payload=payload)
        if response.status_code != 200:
            logger.info(
                f"Guardrails validation returned HTTP {response.status_code} (fail-open)"
            )
            return True, ""
        return _parse_guardrails_result(response.json())
    except (httpx.RequestError, httpx.TimeoutException, ValueError) as e:
        logger.info(f"Guardrails validation failed (fail-open): {e}")
        return True, ""
    except Exception as e:
        logger.error(f"Guardrails validation error (fail-open): {e}")
        return True, ""


async def _ingest_response_payload(
    callback_context: CallbackContext,
    model: str,
    messages: list,
    response_body: Any,
    status_code: int,
    *,
    log_http_error: bool = False,
) -> None:
    if not DATA_INGESTION_URL:
        return

    payload = _build_payload(callback_context, model, messages, response_body, status_code)
    try:
        response = await _post_http_proxy(
            guardrails=False, ingest_data=True, payload=payload
        )
        if log_http_error and response.status_code != 200:
            logger.error(f"Ingestion failed: HTTP {response.status_code}")
    except Exception as e:
        logger.error(f"Ingestion failed: {e}")


async def _async_validate_and_ingest(
    callback_context: CallbackContext,
    model: str,
    messages: list,
    response_dict: Optional[dict],
) -> None:
    """Validate and ingest in a single request (used in async/non-blocking mode)."""
    if not DATA_INGESTION_URL:
        return

    try:
        payload = _build_payload(callback_context, model, messages, response_dict)
        response = await _post_http_proxy(
            guardrails=True, ingest_data=True, payload=payload
        )
        if response.status_code == 200:
            allowed, reason = _parse_guardrails_result(response.json())
            if not allowed:
                logger.info(
                    f"Response flagged by guardrails (async mode, logged only): {reason}"
                )
    except Exception as e:
        logger.error(f"Guardrails async validation error: {e}")


# ---------------------------------------------------------------------------
# Public callback functions
# ---------------------------------------------------------------------------

async def akto_before_model_callback(
    callback_context: CallbackContext,
    llm_request: LlmRequest,
) -> Optional[LlmResponse]:
    """ADK before_model_callback — integrates Akto Guardrails pre-call validation.

    Behaviour depends on SYNC_MODE:

    - SYNC_MODE=true  (default):
        Sends the request to Akto's guardrails endpoint before forwarding it to
        the LLM.  If the request is denied, a blocking LlmResponse is returned
        and the LLM is never called.  The blocked interaction is also ingested
        for observability.

    - SYNC_MODE=false:
        Snapshots the request data in session state so that
        akto_after_model_callback can perform a combined validate-and-ingest
        call after the LLM responds.  Returns None so the LLM call proceeds
        uninterrupted.

    Fail-open: any exception during validation allows the request through.
    """
    model: str = getattr(llm_request, "model", "") or ""
    messages: list = _contents_to_messages(getattr(llm_request, "contents", []))

    # Extract only the latest user message for guardrails validation.
    latest_user_msg = [m for m in messages if m.get("role") == "user"][-1:] or messages[-1:]

    # Snapshot request data for akto_after_model_callback.
    callback_context.state[_AKTO_SNAPSHOT_KEY] = {
        "model": model,
        "messages": latest_user_msg,
        "blocked": False,
    }

    if not SYNC_MODE:
        return None

    try:
        allowed, reason = await _call_guardrails_validation(
            callback_context, model, latest_user_msg
        )
        if not allowed:
            # Ingest the blocked interaction before surfacing the error.
            await _ingest_response_payload(
                callback_context,
                model,
                latest_user_msg,
                {"x-blocked-by": "Akto Proxy"},
                403,
            )
            # Mark snapshot so after_model_callback skips duplicate ingestion.
            callback_context.state[_AKTO_SNAPSHOT_KEY] = {
                "model": model,
                "messages": latest_user_msg,
                "blocked": True,
            }
            logger.info(f"Request blocked by Akto Guardrails: {reason}")
            return _make_blocked_response(reason)
    except Exception as e:
        logger.error(f"Guardrails before-model error (fail-open): {e}")

    return None


async def akto_after_model_callback(
    callback_context: CallbackContext,
    llm_response: LlmResponse,
) -> Optional[LlmResponse]:
    """ADK after_model_callback — ingests or validates the completed interaction.

    Behaviour depends on SYNC_MODE:

    - SYNC_MODE=true  (default):
        Ingests the request + LLM response pair for observability.

    - SYNC_MODE=false:
        Sends a combined validate-and-ingest request.  If the response is
        flagged by guardrails it is logged but NOT blocked (the LLM response
        is already being returned to the caller).

    Always returns None so the original LlmResponse is used unchanged.
    """
    try:
        snapshot: dict = callback_context.state.get(_AKTO_SNAPSHOT_KEY) or {}
        model: str = snapshot.get("model", "")
        messages: list = snapshot.get("messages", [])

        # Skip if this request was already blocked and ingested in before_model_callback.
        if snapshot.get("blocked"):
            return None

        response_dict = _llm_response_to_dict(llm_response)

        if SYNC_MODE:
            await _ingest_response_payload(
                callback_context,
                model,
                messages,
                response_dict,
                200,
                log_http_error=True,
            )
        else:
            await _async_validate_and_ingest(
                callback_context, model, messages, response_dict
            )
    except Exception as e:
        logger.error(f"Guardrails after-model error: {e}")

    return None
