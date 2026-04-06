"""
Guardrails — validates requests and responses against the Akto guardrails service.

Required env vars:
  AKTO_BASE_URL   e.g. https://your-akto-instance.example.com
  AKTO_API_KEY    Bearer token for the Authorization header
"""

from __future__ import annotations

import json
import logging
import os
import time
from dataclasses import dataclass

import httpx
from fastapi import HTTPException

logger = logging.getLogger("uvicorn.error")

_BASE_URL = os.environ.get("AKTO_BASE_URL", "")

@dataclass
class GuardrailsResult:
    allowed: bool
    modified: bool
    modified_payload: str
    reason: str


@dataclass
class ProcessResult:
    modified: bool
    body: bytes


@dataclass
class ProcessSSEResult:
    modified: bool
    events: list[str]


def _build_entry(
    *,
    method: str,
    path: str,
    query: str,
    req_headers: dict[str, str],
    req_body: str,
    res_status: int,
    res_headers: dict[str, str],
    res_body: str,
    client_ip: str,
) -> dict:
    full_path = f"{path}?{query}" if query else path
    return {
        "path": full_path,
        "method": method,
        "requestHeaders": json.dumps(req_headers),
        "responseHeaders": json.dumps(res_headers),
        "requestPayload": req_body,
        "responsePayload": res_body,
        "ip": client_ip,
        "time": str(int(time.time())),
        "statusCode": str(res_status),
        "type": "HTTP/1.1",
        "status": "OK" if res_status < 400 else "ERROR",
        "akto_account_id": "1000000",
        "akto_vxlan_id": "0",
        "is_pending": "false",
        "source": "MIRRORING",
        "tag": json.dumps({"service": "replit-app", "gen-ai": "Gen AI"}),
        "contextSource": "AGENTIC",
    }


def _to_json_string(text: str) -> tuple[str, bool]:
    try:
        json.loads(text)
        return text, False
    except (json.JSONDecodeError, ValueError):
        return json.dumps({"response": text}), True


def _unwrap_if_needed(modified_payload: str, was_wrapped: bool) -> str:
    if not was_wrapped:
        return modified_payload
    try:
        return json.loads(modified_payload)["response"]
    except (json.JSONDecodeError, KeyError):
        return modified_payload


async def _call_guardrails(endpoint: str, entry: dict) -> GuardrailsResult:
    if not _BASE_URL:
        logger.info("[akto-proxy-server] AKTO_BASE_URL not set, allowing by default")
        return GuardrailsResult(allowed=True, modified=False, modified_payload="", reason="")

    try:
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                f"{_BASE_URL}{endpoint}",
                headers={
                    "content-type": "application/json",
                },
                content=json.dumps(entry),
            )

        if resp.status_code != 200:
            logger.error("[akto-proxy-server] guardrails %s returned %d, allowing by default", endpoint, resp.status_code)
            return GuardrailsResult(allowed=True, modified=False, modified_payload="", reason="")

        data = resp.json()
        return GuardrailsResult(
            allowed=data.get("Allowed", True),
            modified=data.get("Modified", False),
            modified_payload=data.get("ModifiedPayload", ""),
            reason=data.get("Reason", ""),
        )

    except Exception as exc:
        logger.error("[akto-proxy-server] guardrails error at %s: %s — allowing by default", endpoint, exc)
        return GuardrailsResult(allowed=True, modified=False, modified_payload="", reason="")


async def pre_process(
    method: str,
    path: str,
    query: str,
    headers: dict[str, str],
    body: bytes,
    client_ip: str,
) -> ProcessResult:
    """
    Validate the incoming request. Raises HTTPException(403) if blocked.
    Returns ProcessResult with modified=True if body was changed.
    """
    entry = _build_entry(
        method=method,
        path=path,
        query=query,
        req_headers=headers,
        req_body=body.decode("utf-8", errors="replace"),
        res_status=0,
        res_headers={},
        res_body="",
        client_ip=client_ip,
    )

    result = await _call_guardrails("/api/validate/request", entry)
    logger.info("[akto-proxy-server]    validate_request: allowed=%s modified=%s", result.allowed, result.modified)

    if not result.allowed:
        logger.info("[akto-proxy-server]    validate_request BLOCKED: %s", result.reason)
        raise HTTPException(status_code=403, detail=result.reason or "Blocked by guardrails")

    if result.modified and result.modified_payload:
        logger.info("[akto-proxy-server]    validate_request: request body modified")
        return ProcessResult(modified=True, body=result.modified_payload.encode("utf-8"))

    return ProcessResult(modified=False, body=body)


async def post_process_sse(
    events: list[str],
    *,
    method: str,
    path: str,
    query: str,
    req_headers: dict[str, str],
    req_body: bytes,
    res_status: int,
    res_headers: dict[str, str],
    client_ip: str,
) -> ProcessSSEResult | None:
    """
    Validate the SSE response. Returns None if blocked, else ProcessSSEResult.
    """
    raw_body = "".join(events)
    res_body, was_wrapped = _to_json_string(raw_body)

    entry = _build_entry(
        method=method,
        path=path,
        query=query,
        req_headers=req_headers,
        req_body=req_body.decode("utf-8", errors="replace"),
        res_status=res_status,
        res_headers=res_headers,
        res_body=res_body,
        client_ip=client_ip,
    )

    result = await _call_guardrails("/api/validate/response", entry)
    logger.info("[akto-proxy-server]    validate_response: allowed=%s modified=%s", result.allowed, result.modified)

    if not result.allowed:
        logger.info("[akto-proxy-server]    validate_response BLOCKED: %s", result.reason)
        return None

    if result.modified and result.modified_payload:
        logger.info("[akto-proxy-server]    validate_response: response body modified")
        return ProcessSSEResult(modified=True, events=[_unwrap_if_needed(result.modified_payload, was_wrapped)])

    return ProcessSSEResult(modified=False, events=events)


async def post_process_body(
    body: bytes,
    *,
    method: str,
    path: str,
    query: str,
    req_headers: dict[str, str],
    req_body: bytes,
    res_status: int,
    res_headers: dict[str, str],
    client_ip: str,
) -> ProcessResult | None:
    """
    Validate a non-SSE response body. Returns None if blocked, else ProcessResult.
    """
    raw_body = body.decode("utf-8", errors="replace")
    res_body, was_wrapped = _to_json_string(raw_body)

    entry = _build_entry(
        method=method,
        path=path,
        query=query,
        req_headers=req_headers,
        req_body=req_body.decode("utf-8", errors="replace"),
        res_status=res_status,
        res_headers=res_headers,
        res_body=res_body,
        client_ip=client_ip,
    )

    result = await _call_guardrails("/api/validate/response", entry)
    logger.info("[akto-proxy-server]    validate_response: allowed=%s modified=%s", result.allowed, result.modified)

    if not result.allowed:
        logger.info("[akto-proxy-server]    validate_response BLOCKED: %s", result.reason)
        return None

    if result.modified and result.modified_payload:
        logger.info("[akto-proxy-server]    validate_response: response body modified")
        return ProcessResult(modified=True, body=_unwrap_if_needed(result.modified_payload, was_wrapped).encode("utf-8"))

    return ProcessResult(modified=False, body=body)
