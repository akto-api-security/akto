"""
Akto API discovery — logs request/response traffic to the Akto ingestion endpoint.

Called after the response is received from the api-server so both request and
response are available. Runs fire-and-forget; errors are logged but never
propagate to the client.

Required env vars:
  AKTO_BASE_URL   e.g. https://your-akto-instance.example.com
  AKTO_DATABASE_ABSTRACTOR_TOKEN    Bearer token for the Authorization header
"""

from __future__ import annotations

import json
import logging
import os
import time

import httpx

logger = logging.getLogger("uvicorn.error")

_BASE_URL = os.environ.get("AKTO_BASE_URL", "")
_API_KEY = os.environ.get("AKTO_DATABASE_ABSTRACTOR_TOKEN", "")

_CAPTURE_TYPES = ("application/json", "text/plain", "text/event-stream")


def _should_capture(content_type: str) -> bool:
    ct = content_type.lower()
    return any(t in ct for t in _CAPTURE_TYPES)


def _build_log_entry(
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
    }


async def log_traffic(
    *,
    method: str,
    path: str,
    query: str,
    req_headers: dict[str, str],
    req_body: bytes,
    res_status: int,
    res_headers: dict[str, str],
    res_body: str,
    client_ip: str,
) -> None:
    try:
        if not _BASE_URL:
            logger.warning("[akto-proxy-server] discovery: AKTO_BASE_URL not set, skipping ingest")
            return

        if not (200 <= res_status < 400):
            logger.info("[akto-proxy-server] discovery: skipped (status %d)", res_status)
            return

        req_ct = req_headers.get("content-type", "")
        res_ct = res_headers.get("content-type", "")
        if not _should_capture(req_ct) and not _should_capture(res_ct):
            logger.info("[akto-proxy-server] discovery: skipped (content-type not captured)")
            return

        entry = _build_log_entry(
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

        logger.info("[akto-proxy-server] discovery: sending log entry method=%s path=%s", method, path)

        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.post(
                f"{_BASE_URL}/api/ingestData",
                headers={
                    "content-type": "application/json",
                    "Authorization": _API_KEY,
                },
                content=json.dumps({"batchData": [entry]}),
            )

        if resp.status_code == 200:
            logger.info("[akto-proxy-server] discovery: log sent to Akto")
        else:
            logger.warning("[akto-proxy-server] discovery: Akto returned status %d", resp.status_code)

    except Exception as exc:
        logger.error("[akto-proxy-server] discovery: error sending log — %s", exc)
