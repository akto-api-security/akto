"""Shared httpx.AsyncClient for outbound HTTP (Vertex, anonymizer, etc.).

Reuses TCP connections under load instead of opening a new client per request.
"""

import os
from collections.abc import Callable

import httpx

_DEFAULT_TIMEOUT_S = 120.0
_MAX_CONNECTIONS = 100
_MAX_KEEPALIVE = 50

# IAM credential fetches are one at a time per process (aws_auth single-flight),
# so a tiny pool is plenty.
_CREDENTIALS_TIMEOUT_S = 10.0
_CREDENTIALS_MAX_CONNECTIONS = 4

_DEFAULT = "default"
_CREDENTIALS = "credentials"
_clients: dict[str, httpx.AsyncClient] = {}


def _limits() -> httpx.Limits:
    max_conn = int(os.getenv("HTTP_MAX_CONNECTIONS", str(_MAX_CONNECTIONS)))
    max_keepalive = int(os.getenv("HTTP_MAX_KEEPALIVE", str(_MAX_KEEPALIVE)))
    return httpx.Limits(max_connections=max_conn, max_keepalive_connections=max_keepalive)


def _cached(name: str, make: Callable[[], httpx.AsyncClient]) -> httpx.AsyncClient:
    client = _clients.get(name)
    if client is None or client.is_closed:
        client = _clients[name] = make()
    return client


def get_client() -> httpx.AsyncClient:
    return _cached(_DEFAULT, lambda: httpx.AsyncClient(timeout=_DEFAULT_TIMEOUT_S, limits=_limits()))


def get_credentials_client() -> httpx.AsyncClient:
    """Separate pool for IAM credential fetches (Pod Identity / ECS / STS).

    Every signed model call waits on these, so they must not queue behind the
    model calls themselves in the shared pool when it is saturated.
    """
    return _cached(
        _CREDENTIALS,
        lambda: httpx.AsyncClient(
            timeout=_CREDENTIALS_TIMEOUT_S, limits=httpx.Limits(max_connections=_CREDENTIALS_MAX_CONNECTIONS)
        ),
    )


async def close_client() -> None:
    client = _clients.pop(_DEFAULT, None)
    if client is not None and not client.is_closed:
        await client.aclose()
