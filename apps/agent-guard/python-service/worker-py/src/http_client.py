"""Shared httpx.AsyncClient for outbound HTTP (Vertex, anonymizer, etc.).

Reuses TCP connections under load instead of opening a new client per request.
"""

import os
from typing import Optional

import httpx

_DEFAULT_TIMEOUT_S = 120.0
_MAX_CONNECTIONS = 100
_MAX_KEEPALIVE = 50

_client: Optional[httpx.AsyncClient] = None


def _limits() -> httpx.Limits:
    max_conn = int(os.getenv("HTTP_MAX_CONNECTIONS", str(_MAX_CONNECTIONS)))
    max_keepalive = int(os.getenv("HTTP_MAX_KEEPALIVE", str(_MAX_KEEPALIVE)))
    return httpx.Limits(max_connections=max_conn, max_keepalive_connections=max_keepalive)


def get_client() -> httpx.AsyncClient:
    global _client
    if _client is None or _client.is_closed:
        _client = httpx.AsyncClient(timeout=_DEFAULT_TIMEOUT_S, limits=_limits())
    return _client


async def close_client() -> None:
    global _client
    if _client is not None and not _client.is_closed:
        await _client.aclose()
        _client = None
