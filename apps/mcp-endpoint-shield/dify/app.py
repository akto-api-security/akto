"""
Akto Guardrails adapter for Dify's Moderation API Extension.

Dify calls an external "API Extension" with a JSON body of the shape
``{ "point": <extension_point>, "params": {...} }`` and expects a moderation
verdict back (see
https://docs.dify.ai/en/use-dify/workspace/api-extension/moderation-api-extension).

This service is a thin translator: it converts Dify's input/output moderation
calls into Akto's existing ``/api/http-proxy`` flow (guardrails + ingestion) and
maps Akto's ``guardrailsResult`` back into Dify's moderation response contract.

Supported extension points:
  - ping                    -> health check
  - app.moderation.input    -> validates end-user input (inputs + query)
  - app.moderation.output   -> validates LLM output (text)

Environment variables:
  AKTO_DATA_INGESTION_URL   Base URL of the Akto data-ingestion service (required)
  AKTO_API_TOKEN            Authorization token sent to Akto (optional)
  AKTO_ACCOUNT_ID           Akto account id (default: "1000000")
  DIFY_EXTENSION_TOKEN      Bearer token Dify must present to this adapter (optional)
  TIMEOUT                   HTTP timeout in seconds for Akto calls (default: "5")
  LOG_LEVEL                 Logging level (default: "INFO")
"""

import json
import logging
import os
from typing import Any, Optional, Tuple

import httpx
from fastapi import FastAPI, Header, HTTPException, Request

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL", "").rstrip("/")
AKTO_API_TOKEN = os.getenv("AKTO_API_TOKEN", "")
AKTO_ACCOUNT_ID = os.getenv("AKTO_ACCOUNT_ID", "1000000")
DIFY_EXTENSION_TOKEN = os.getenv("DIFY_EXTENSION_TOKEN", "")
TIMEOUT = float(os.getenv("TIMEOUT", "5"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()

AKTO_CONNECTOR_NAME = "dify"
HTTP_PROXY_PATH = "/api/http-proxy"
DEFAULT_PRESET_RESPONSE = "Your content violates our usage policy."

logging.basicConfig(level=getattr(logging, LOG_LEVEL, logging.INFO))
logger = logging.getLogger("akto_dify_adapter")

app = FastAPI(title="Akto Guardrails - Dify Adapter")

_client = httpx.AsyncClient(
    timeout=TIMEOUT,
    limits=httpx.Limits(max_connections=100, max_keepalive_connections=20),
    headers={"Authorization": AKTO_API_TOKEN} if AKTO_API_TOKEN else {},
)


def build_http_proxy_params(*, guardrails: bool, response_guardrails: bool, ingest_data: bool) -> dict:
    params = {"akto_connector": AKTO_CONNECTOR_NAME}
    if guardrails:
        params["guardrails"] = "true"
    if response_guardrails:
        params["response_guardrails"] = "true"
    if ingest_data:
        params["ingest_data"] = "true"
    return params


def build_envelope(
    *,
    app_id: str,
    path: str,
    request_body: Any,
    response_body: Any = None,
) -> dict:
    """Build the flat traffic envelope expected by /api/http-proxy.

    Gateway requires a non-empty requestPayload even for response guardrails, so
    request_body must always be provided by the caller.
    """
    host = f"{app_id or 'unknown'}.dify.agent"
    tags = {"gen-ai": "Gen AI", "ai-agent": "dify", "source": "AGENTIC"}

    response_payload = (
        json.dumps({"body": response_body}) if response_body is not None else json.dumps({})
    )

    return {
        "path": path,
        "method": "POST",
        "requestPayload": json.dumps({"body": request_body}),
        "responsePayload": response_payload,
        "requestHeaders": json.dumps({"host": host, "content-type": "application/json"}),
        "responseHeaders": json.dumps({"content-type": "application/json"}),
        "ip": "0.0.0.0",
        "destIp": "127.0.0.1",
        "time": str(_now_ms()),
        "statusCode": "200",
        "type": "HTTP/1.1",
        "status": "200",
        "akto_account_id": AKTO_ACCOUNT_ID,
        "akto_vxlan_id": "0",
        "is_pending": "false",
        "source": "MIRRORING",
        "tag": json.dumps(tags),
        "metadata": json.dumps(tags),
        "contextSource": "AGENTIC",
    }


def _now_ms() -> int:
    import time

    return int(time.time() * 1000)


def parse_guardrails_result(result: Any) -> Tuple[bool, str, Optional[str]]:
    """Parse Akto's response. Returns (allowed, reason, modified_payload)."""
    if not isinstance(result, dict):
        return True, "", None

    guardrails_result = result.get("data", {}).get("guardrailsResult", {}) or {}
    allowed = guardrails_result.get("Allowed", True)
    reason = guardrails_result.get("Reason", "")
    modified_payload = guardrails_result.get("ModifiedPayload")
    return allowed, reason, modified_payload


def extract_modified_body(modified_payload: Optional[str]) -> Any:
    """Akto returns the full redacted request as {"body": ...} in ModifiedPayload."""
    if not modified_payload:
        return None
    try:
        obj = json.loads(modified_payload) if isinstance(modified_payload, str) else modified_payload
    except (ValueError, TypeError):
        return None
    if isinstance(obj, dict) and "body" in obj:
        return obj["body"]
    return obj


async def call_akto(
    *,
    guardrails: bool,
    response_guardrails: bool,
    ingest_data: bool,
    envelope: dict,
) -> Optional[dict]:
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not configured - skipping (fail-open)")
        return None

    endpoint = f"{AKTO_DATA_INGESTION_URL}{HTTP_PROXY_PATH}"
    params = build_http_proxy_params(
        guardrails=guardrails,
        response_guardrails=response_guardrails,
        ingest_data=ingest_data,
    )
    try:
        resp = await _client.post(endpoint, params=params, json=envelope)
        if resp.status_code != 200:
            logger.info("Akto returned HTTP %s (fail-open)", resp.status_code)
            return None
        return resp.json()
    except (httpx.RequestError, httpx.TimeoutException, ValueError) as e:
        logger.info("Akto call failed (fail-open): %s", e)
        return None
    except Exception as e:  # noqa: BLE001 - never let guardrails break the host app
        logger.error("Unexpected Akto error (fail-open): %s", e)
        return None


def allow_response() -> dict:
    return {"flagged": False, "action": "direct_output", "preset_response": ""}


def blocked_response(reason: str) -> dict:
    return {
        "flagged": True,
        "action": "direct_output",
        "preset_response": reason or DEFAULT_PRESET_RESPONSE,
    }


def _verify_auth(authorization: Optional[str]) -> None:
    if not DIFY_EXTENSION_TOKEN:
        return
    expected = f"Bearer {DIFY_EXTENSION_TOKEN}"
    if authorization != expected:
        raise HTTPException(status_code=401, detail="Invalid API extension token")


async def handle_input(params: dict) -> dict:
    app_id = params.get("app_id", "")
    content = {"inputs": params.get("inputs", {}), "query": params.get("query")}

    envelope = build_envelope(
        app_id=app_id,
        path=f"/dify/apps/{app_id or 'unknown'}/moderation/input",
        request_body=content,
    )

    result = await call_akto(
        guardrails=True,
        response_guardrails=False,
        ingest_data=True,
        envelope=envelope,
    )
    allowed, reason, modified_payload = parse_guardrails_result(result)

    if not allowed:
        return blocked_response(reason)

    modified_body = extract_modified_body(modified_payload)
    if modified_body is not None:
        redacted = modified_body if isinstance(modified_body, dict) else {}
        return {
            "flagged": True,
            "action": "overridden",
            "inputs": redacted.get("inputs", params.get("inputs", {})),
            "query": redacted.get("query", params.get("query")),
        }

    return allow_response()


async def handle_output(params: dict) -> dict:
    app_id = params.get("app_id", "")
    text = params.get("text", "")

    # Gateway requires a non-empty requestPayload; reuse the output text as the
    # request body context since Dify does not pass the original query here.
    envelope = build_envelope(
        app_id=app_id,
        path=f"/dify/apps/{app_id or 'unknown'}/moderation/output",
        request_body=text,
        response_body=text,
    )

    result = await call_akto(
        guardrails=False,
        response_guardrails=True,
        ingest_data=True,
        envelope=envelope,
    )
    allowed, reason, modified_payload = parse_guardrails_result(result)

    if not allowed:
        return blocked_response(reason)

    modified_body = extract_modified_body(modified_payload)
    if modified_body is not None:
        return {
            "flagged": True,
            "action": "overridden",
            "text": modified_body if isinstance(modified_body, str) else text,
        }

    return allow_response()


@app.get("/health")
async def health() -> dict:
    return {"status": "healthy"}


@app.post("/")
async def moderation(request: Request, authorization: Optional[str] = Header(default=None)) -> dict:
    _verify_auth(authorization)

    try:
        body = await request.json()
    except (ValueError, TypeError):
        raise HTTPException(status_code=400, detail="Invalid JSON body")

    point = body.get("point")
    params = body.get("params", {}) or {}

    if point == "ping":
        return {"result": "pong"}

    if point == "app.moderation.input":
        return await handle_input(params)

    if point == "app.moderation.output":
        return await handle_output(params)

    logger.info("Unsupported extension point: %s", point)
    return allow_response()


@app.on_event("shutdown")
async def _shutdown() -> None:
    await _client.aclose()
