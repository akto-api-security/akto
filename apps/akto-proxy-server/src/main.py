"""
Proxy server — transparent pass-through with guardrails hooks.

All incoming requests are forwarded to TARGET_URL (the api-server).
For SSE responses, all events are collected first, guardrails are applied,
then the events are re-streamed to the client one by one.
"""

from __future__ import annotations

import asyncio
import logging
import os
import time
from typing import AsyncGenerator

import httpx
from fastapi import FastAPI, HTTPException, Request
from fastapi.responses import Response, StreamingResponse

from src import guardrails, discovery

TARGET_URL = ""
GUARDRAILS_ENABLED = False
GUARDRAILS_URL_FILTER: list[str] = []

logger = logging.getLogger("uvicorn.error")

app = FastAPI()


@app.on_event("startup")
async def _check_required_env() -> None:
    global TARGET_URL, GUARDRAILS_ENABLED, GUARDRAILS_URL_FILTER
    TARGET_URL = os.environ.get("TARGET_URL", "")
    GUARDRAILS_ENABLED = os.environ.get("AKTO_ENABLE_GUARDRAILS", "").lower() == "true"
    GUARDRAILS_URL_FILTER = [u.strip() for u in os.environ.get("AKTO_ENABLE_GUARDRAILS_ON_URL", "").split(",") if u.strip()]

    if not TARGET_URL:
        raise RuntimeError("TARGET_URL is not set — cannot start proxy")
    if not os.environ.get("AKTO_BASE_URL"):
        logger.warning("[akto-proxy-server] WARNING: AKTO_BASE_URL is not set — guardrails and discovery will be skipped")
    if not os.environ.get("AKTO_DATABASE_ABSTRACTOR_TOKEN"):
        logger.warning("[akto-proxy-server] WARNING: AKTO_DATABASE_ABSTRACTOR_TOKEN is not set — guardrails and discovery will be skipped")


def _fire(coro) -> None:
    asyncio.get_event_loop().create_task(coro)


@app.api_route("/proxy/{path:path}", methods=["GET", "POST", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS"])
async def proxy(path: str, request: Request) -> Response:
    full_path = f"/{path}"  # /proxy is already stripped by FastAPI
    body = await request.body()
    req_headers = dict(request.headers)
    query = request.url.query
    client_ip = request.headers.get("x-forwarded-for", "").split(",")[0].strip()
    run_guardrails = _should_run_guardrails(full_path)

    logger.info("[akto-proxy-server] >> REQUEST  %s %s  body=%d bytes  guardrails=%s", request.method, full_path, len(body), run_guardrails)

    # --- Pre-process (guardrails) ---
    if run_guardrails:
        logger.info("[akto-proxy-server]    pre_process: running guardrails on request")
        pre = await guardrails.pre_process(
            method=request.method,
            path=full_path,
            query=query,
            headers=req_headers,
            body=body,
            client_ip=client_ip,
        )
        logger.info("[akto-proxy-server]    pre_process: done  modified=%s", pre.modified)
        if pre.modified:
            req_headers.pop("content-length", None)
        forward_body = pre.body
    else:
        logger.info("[akto-proxy-server]    pre_process: skipped (guardrails not enabled for this path)")
        forward_body = body

    target = f"{TARGET_URL}/{path}"
    if query:
        target = f"{target}?{query}"

    logger.info("[akto-proxy-server]    forwarding -> %s %s  body=%d bytes", request.method, target, len(forward_body))
    start = time.monotonic()

    async with httpx.AsyncClient(timeout=None) as client:
        upstream_resp = await client.request(
            method=request.method,
            url=target,
            headers=req_headers,
            content=forward_body,
        )

    elapsed = (time.monotonic() - start) * 1000
    content_type = upstream_resp.headers.get("content-type", "")
    is_sse = "text/event-stream" in content_type

    logger.info(
        "[akto-proxy-server] << RESPONSE  status=%d  content-type=%s  sse=%s  %.1fms",
        upstream_resp.status_code, content_type, is_sse, elapsed,
    )

    if is_sse:
        return await _handle_sse(
            upstream_resp,
            method=request.method,
            full_path=full_path,
            query=query,
            req_body=forward_body,
            req_headers=req_headers,
            client_ip=client_ip,
            run_guardrails=run_guardrails,
        )
    else:
        return await _handle_regular(
            upstream_resp,
            method=request.method,
            full_path=full_path,
            query=query,
            req_body=forward_body,
            req_headers=req_headers,
            client_ip=client_ip,
            run_guardrails=run_guardrails,
        )


async def _handle_sse(
    upstream_resp: httpx.Response,
    *,
    method: str,
    full_path: str,
    query: str,
    req_body: bytes,
    req_headers: dict[str, str],
    client_ip: str,
    run_guardrails: bool,
) -> Response:
    raw = upstream_resp.text

    events: list[str] = []
    current: list[str] = []
    for line in raw.splitlines(keepends=True):
        current.append(line)
        if line == "\n" and current:
            events.append("".join(current))
            current = []
    if current:
        events.append("".join(current))

    events = [e for e in events if e.strip()]

    logger.info("[akto-proxy-server]    SSE: collected %d events", len(events))

    # --- Post-process (guardrails) ---
    if run_guardrails:
        logger.info("[akto-proxy-server]    post_process: running guardrails on SSE response")
        post = await guardrails.post_process_sse(
            events,
            method=method,
            path=full_path,
            query=query,
            req_headers=req_headers,
            req_body=req_body,
            res_status=upstream_resp.status_code,
            res_headers=dict(upstream_resp.headers),
            client_ip=client_ip,
        )

        if post is None:
            logger.warning("[akto-proxy-server]    post_process: SSE response BLOCKED by guardrails")
            raise HTTPException(status_code=403, detail="Blocked by guardrails")

        logger.info("[akto-proxy-server]    post_process: done  modified=%s  events=%d", post.modified, len(post.events))
        final_events = post.events
    else:
        logger.info("[akto-proxy-server]    post_process: skipped (guardrails not enabled for this path)")
        final_events = events

    # --- Discovery (fire-and-forget) ---
    _fire(discovery.log_traffic(
        method=method,
        path=full_path,
        query=query,
        req_headers=req_headers,
        req_body=req_body,
        res_status=upstream_resp.status_code,
        res_headers=dict(upstream_resp.headers),
        res_body="".join(final_events),
        client_ip=client_ip,
    ))

    logger.info("[akto-proxy-server]    streaming %d SSE events to client", len(final_events))

    async def event_stream() -> AsyncGenerator[str, None]:
        for event in final_events:
            yield event
            await asyncio.sleep(0)

    sse_headers = dict(upstream_resp.headers)
    sse_headers.pop("content-length", None)

    return StreamingResponse(
        event_stream(),
        status_code=upstream_resp.status_code,
        headers=sse_headers,
        media_type="text/event-stream",
    )


async def _handle_regular(
    upstream_resp: httpx.Response,
    *,
    method: str,
    full_path: str,
    query: str,
    req_body: bytes,
    req_headers: dict[str, str],
    client_ip: str,
    run_guardrails: bool,
) -> Response:
    body = upstream_resp.content

    logger.info("[akto-proxy-server]    body: received %d bytes", len(body))

    # --- Post-process (guardrails) ---
    if run_guardrails:
        logger.info("[akto-proxy-server]    post_process: running guardrails on response")
        post = await guardrails.post_process_body(
            body,
            method=method,
            path=full_path,
            query=query,
            req_headers=req_headers,
            req_body=req_body,
            res_status=upstream_resp.status_code,
            res_headers=dict(upstream_resp.headers),
            client_ip=client_ip,
        )

        if post is None:
            logger.warning("[akto-proxy-server]    post_process: response BLOCKED by guardrails")
            raise HTTPException(status_code=403, detail="Blocked by guardrails")

        logger.info("[akto-proxy-server]    post_process: done  modified=%s", post.modified)
        final_body = post.body
        body_modified = post.modified
    else:
        logger.info("[akto-proxy-server]    post_process: skipped (guardrails not enabled for this path)")
        final_body = body
        body_modified = False

    # --- Discovery (fire-and-forget) ---
    _fire(discovery.log_traffic(
        method=method,
        path=full_path,
        query=query,
        req_headers=req_headers,
        req_body=req_body,
        res_status=upstream_resp.status_code,
        res_headers=dict(upstream_resp.headers),
        res_body=final_body.decode("utf-8", errors="replace"),
        client_ip=client_ip,
    ))

    res_headers = dict(upstream_resp.headers)
    if body_modified:
        res_headers.pop("content-length", None)

    logger.info("[akto-proxy-server]    sending response to client  status=%d  body=%d bytes", upstream_resp.status_code, len(final_body))

    return Response(
        content=final_body,
        status_code=upstream_resp.status_code,
        headers=res_headers,
        media_type=upstream_resp.headers.get("content-type"),
    )


def _should_run_guardrails(path: str) -> bool:
    if not GUARDRAILS_ENABLED:
        return False
    if not GUARDRAILS_URL_FILTER:
        return True
    return any(path == u or path.startswith(u) for u in GUARDRAILS_URL_FILTER)
