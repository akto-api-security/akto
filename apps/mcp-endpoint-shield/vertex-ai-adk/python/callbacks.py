"""
Akto Guardrails callbacks for Google Vertex AI ADK.

Usage:
    from callbacks import (
        akto_before_model_callback,
        akto_after_model_callback,
        akto_after_agent_callback,
    )

    agent = LlmAgent(
        model="gemini-2.0-flash",
        name="my_agent",
        instruction="You are a helpful assistant.",
        before_model_callback=akto_before_model_callback,
        after_model_callback=akto_after_model_callback,
        after_agent_callback=akto_after_agent_callback,
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
from .graph import _normalize as _normalize_event

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
# State key for accumulating tool_call / tool_result trace events across model callbacks.
_AKTO_TOOL_TRACES_KEY = "__akto_tool_traces"
# State key for the final model text response (captured in after_model_callback).
_AKTO_FINAL_RESPONSE_KEY = "__akto_final_response"

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


def _response_has_function_calls(llm_response: LlmResponse) -> bool:
    """Return True if the LLM response contains function calls.

    When the model responds with function calls it is an intermediate step
    (tools still need to execute).  The final response will contain only text.
    """
    content = getattr(llm_response, "content", None)
    if not content:
        return False
    for part in (getattr(content, "parts", []) or []):
        if getattr(part, "function_call", None):
            return True
    return False


def _llm_response_to_tool_call_event(
    llm_response: LlmResponse, callback_context: CallbackContext,
) -> Optional[dict]:
    """Convert function_call parts from an LlmResponse to a normalized trace event."""
    content = getattr(llm_response, "content", None)
    if not content:
        return None
    parts = getattr(content, "parts", []) or []
    normalized = []
    for part in parts:
        fc = getattr(part, "function_call", None)
        if fc:
            name = getattr(fc, "name", None) or ""
            args = dict(getattr(fc, "args", None) or {})
            normalized.append({"type": "tool_call", "name": name, "args": args})
    if not normalized:
        return None
    return {
        "author": "assistant",
        "invocation_id": str(getattr(callback_context, "invocation_id", "")),
        "is_final": False,
        "parts": normalized,
    }


def _extract_tool_results_from_contents(
    contents, callback_context: CallbackContext,
) -> Optional[dict]:
    """Extract function_response parts from LlmRequest contents as a tool_result trace event.

    On the second model call after tool execution, the request contents include
    function_response parts with tool results.  Only inspects the last Content
    object that carries function_response parts to avoid re-extracting results
    from the full conversation history.
    """
    last_fr_content = None
    for content in reversed(contents or []):
        parts = getattr(content, "parts", []) or []
        if any(getattr(p, "function_response", None) for p in parts):
            last_fr_content = content
            break
    if last_fr_content is None:
        return None

    normalized = []
    for part in (getattr(last_fr_content, "parts", []) or []):
        fr = getattr(part, "function_response", None)
        if fr:
            name = getattr(fr, "name", None) or ""
            response = dict(getattr(fr, "response", None) or {})
            normalized.append({"type": "tool_result", "name": name, "response": response})
    if not normalized:
        return None
    return {
        "author": "assistant",
        "invocation_id": str(getattr(callback_context, "invocation_id", "")),
        "is_final": False,
        "parts": normalized,
    }


def _event_to_dict(event) -> dict:
    """Convert an ADK Event (Pydantic model or dict) to a plain dict."""
    if isinstance(event, dict):
        return event
    if hasattr(event, "model_dump"):
        return event.model_dump()
    return vars(event)


def _fetch_invocation_traces(callback_context: CallbackContext) -> list:
    """Fetch and normalize traces for the current invocation from the session.

    Reads callback_context.session.events, filters by the current invocation_id,
    and normalizes each event using graph.py's _normalize.
    """
    try:
        session = callback_context.session
        invocation_id = callback_context.invocation_id or ""
        if not session or not invocation_id:
            return []

        events = getattr(session, "events", []) or []
        traces = []
        seen_ids: set = set()
        for event in events:
            event_dict = _event_to_dict(event)
            event_inv_id = (
                event_dict.get("invocation_id")
                or event_dict.get("invocationId")
                or ""
            )
            if event_inv_id != invocation_id:
                continue
            event_id = event_dict.get("id", "")
            if event_id and event_id in seen_ids:
                continue
            if event_id:
                seen_ids.add(event_id)
            traces.append(_normalize_event(event_dict))
        return traces
    except Exception as e:
        logger.error(f"Failed to fetch invocation traces: {e}")
        return []


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
    traces: Optional[list] = None,
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
    response_data: dict = {}
    if response_body is not None:
        response_data["body"] = json.dumps(response_body)
    if traces:
        response_data["traces"] = traces
    response_payload = json.dumps(response_data)

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
    traces: Optional[list] = None,
) -> None:
    if not DATA_INGESTION_URL:
        return

    payload = _build_payload(callback_context, model, messages, response_body, status_code, traces=traces)
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
    traces: Optional[list] = None,
) -> None:
    """Validate and ingest in a single request (used in async/non-blocking mode)."""
    if not DATA_INGESTION_URL:
        return

    try:
        payload = _build_payload(callback_context, model, messages, response_dict, traces=traces)
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
    current_inv_id = str(getattr(callback_context, "invocation_id", ""))
    messages: list = _contents_to_messages(getattr(llm_request, "contents", []))

    # Reset accumulated state when a new invocation starts so traces from
    # a previous turn don't leak into the current one.
    prev_snapshot = callback_context.state.get(_AKTO_SNAPSHOT_KEY) or {}
    if prev_snapshot.get("invocation_id", "") != current_inv_id:
        callback_context.state[_AKTO_TOOL_TRACES_KEY] = []
        callback_context.state[_AKTO_FINAL_RESPONSE_KEY] = None

    # Extract only the latest user message for guardrails validation.
    latest_user_msg = [m for m in messages if m.get("role") == "user"][-1:] or messages[-1:]

    # On subsequent LLM calls within a tool-calling flow, the last "user" Content
    # carries function_response parts (no text), leaving latest_user_msg with empty
    # content.  Fall back to the original user message from the prior snapshot so
    # both guardrails validation and ingestion always receive the real user prompt.
    if not any(m.get("content") for m in latest_user_msg):
        prev = prev_snapshot.get("messages")
        if prev:
            latest_user_msg = prev

    # Capture function_response parts (tool results) from subsequent model calls.
    # Only extract tool results if we already have a tool_call in the accumulated
    # traces for this invocation — this prevents picking up stale function_response
    # parts from previous turns that remain in the conversation history.
    contents = getattr(llm_request, "contents", []) or []
    accumulated = callback_context.state.get(_AKTO_TOOL_TRACES_KEY) or []
    has_tool_calls = any(
        any(p.get("type") == "tool_call" for p in ev.get("parts", []))
        for ev in accumulated
    )
    if has_tool_calls:
        tool_result_event = _extract_tool_results_from_contents(contents, callback_context)
        if tool_result_event:
            accumulated.append(tool_result_event)
            callback_context.state[_AKTO_TOOL_TRACES_KEY] = accumulated

    # Snapshot request data for akto_after_model_callback.
    callback_context.state[_AKTO_SNAPSHOT_KEY] = {
        "model": model,
        "messages": latest_user_msg,
        "invocation_id": current_inv_id,
        "blocked": False,
    }

    if not SYNC_MODE:
        return None

    try:
        allowed, reason = await _call_guardrails_validation(
            callback_context, model, latest_user_msg
        )
        if not allowed:
            traces = _fetch_invocation_traces(callback_context)

            await _ingest_response_payload(
                callback_context,
                model,
                latest_user_msg,
                {"x-blocked-by": "Akto Proxy"},
                403,
                traces=traces,
            )
            # Mark snapshot so after_model_callback skips duplicate ingestion.
            callback_context.state[_AKTO_SNAPSHOT_KEY] = {
                "model": model,
                "messages": latest_user_msg,
                "invocation_id": current_inv_id,
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
    """ADK after_model_callback — captures tool_call events and validates responses.

    Accumulates tool_call trace events in session state so that
    akto_after_agent_callback can include them in the final ingestion payload.
    Data ingestion itself is handled by the agent-level callback.

    Always returns None so the original LlmResponse is used unchanged.
    """
    try:
        snapshot: dict = callback_context.state.get(_AKTO_SNAPSHOT_KEY) or {}
        if snapshot.get("blocked"):
            return None

        # Capture function_call parts as a tool_call trace event.
        if _response_has_function_calls(llm_response):
            tool_event = _llm_response_to_tool_call_event(llm_response, callback_context)
            if tool_event:
                accumulated = callback_context.state.get(_AKTO_TOOL_TRACES_KEY) or []
                accumulated.append(tool_event)
                callback_context.state[_AKTO_TOOL_TRACES_KEY] = accumulated
            return None

        # Final text response (no function_calls) — save for after_agent_callback.
        response_dict = _llm_response_to_dict(llm_response)
        if response_dict is not None:
            callback_context.state[_AKTO_FINAL_RESPONSE_KEY] = response_dict

        # Non-sync mode: validate the response against guardrails (no ingestion).
        if not SYNC_MODE and response_dict is not None:
            model: str = snapshot.get("model", "")
            messages: list = snapshot.get("messages", [])
            if DATA_INGESTION_URL:
                payload = _build_payload(callback_context, model, messages, response_dict)
                try:
                    response = await _post_http_proxy(
                        guardrails=True, ingest_data=False, payload=payload
                    )
                    if response.status_code == 200:
                        allowed, reason = _parse_guardrails_result(response.json())
                        if not allowed:
                            logger.info(
                                f"Response flagged by guardrails (async mode, logged only): {reason}"
                            )
                except Exception as e:
                    logger.error(f"Guardrails async validation error: {e}")
    except Exception as e:
        logger.error(f"Guardrails after-model error: {e}")

    return None


async def akto_after_agent_callback(
    callback_context: CallbackContext,
) -> Optional[types.Content]:
    """ADK after_agent_callback — ingests the complete interaction after the agent turn.

    Fires after ALL model calls and tool executions for the turn are complete.
    Builds traces purely from callback-accumulated data to avoid duplicates
    that arise from merging with session events.
    """
    try:
        snapshot: dict = callback_context.state.get(_AKTO_SNAPSHOT_KEY) or {}
        model: str = snapshot.get("model", "")
        messages: list = snapshot.get("messages", [])

        # Skip if this request was already blocked and ingested in before_model_callback.
        if snapshot.get("blocked"):
            return None

        inv_id = str(getattr(callback_context, "invocation_id", ""))

        # Build traces from callback-accumulated data only.
        traces: list = []

        # 1. User prompt (from the snapshot saved in before_model_callback).
        user_text = ""
        for msg in messages:
            if msg.get("content"):
                user_text = msg["content"]
        if user_text:
            traces.append({
                "author": "user",
                "invocation_id": inv_id,
                "is_final": False,
                "parts": [{"type": "text", "content": user_text}],
            })

        # 2. Tool call / tool result events accumulated by the callbacks.
        tool_traces: list = callback_context.state.get(_AKTO_TOOL_TRACES_KEY) or []
        traces.extend(tool_traces)

        # 3. Final model text response.
        saved_response = callback_context.state.get(_AKTO_FINAL_RESPONSE_KEY)
        if saved_response:
            final_text = (
                saved_response.get("choices", [{}])[0]
                .get("message", {})
                .get("content", "")
            )
            if final_text:
                traces.append({
                    "author": "assistant",
                    "invocation_id": inv_id,
                    "is_final": True,
                    "parts": [{"type": "text", "content": final_text}],
                })

        response_dict = saved_response

        if SYNC_MODE:
            await _ingest_response_payload(
                callback_context,
                model,
                messages,
                response_dict,
                200,
                log_http_error=True,
                traces=traces,
            )
        else:
            await _async_validate_and_ingest(
                callback_context, model, messages, response_dict, traces=traces
            )
    except Exception as e:
        logger.error(f"Guardrails after-agent error: {e}")

    return None
