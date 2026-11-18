"""Shared httpx.AsyncClient for outbound HTTP (Vertex, anonymizer, etc.).

Reuses TCP connections under load instead of opening a new client per request.

Long-lived clients must be UncancellableAsyncClient. The cascade cuts model
calls off with asyncio.wait_for; with httpx 0.28.1 / httpcore 1.0.9, a native
asyncio cancellation that lands while the pool is saturated can leave a
connection permanently marked busy. Enough of those and every request on the
client fails with PoolTimeout until the process restarts (reproduced in the
1.3.4 image). UncancellableAsyncClient never lets a caller's cancellation reach
httpcore: the request runs to completion under httpx's own timeout, which
releases its connection cleanly, while the cancelled caller just stops waiting.
"""

import asyncio
import os
from collections.abc import Callable, Coroutine
from typing import Any

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

# Strong references to background tasks whose caller may have given up — the
# event loop only keeps weak ones, so an orphaned task could be collected mid-request.
_IN_FLIGHT: set[asyncio.Task] = set()


def _finish(task: asyncio.Task) -> None:
    _IN_FLIGHT.discard(task)
    # Nobody may be awaiting it any more; read the exception so asyncio doesn't
    # warn "never retrieved" (callers still waiting get it through the shield).
    if not task.cancelled():
        task.exception()


def spawn_uncancellable(coro: Coroutine[Any, Any, Any]) -> asyncio.Task:
    """Run `coro` as a task that outlives its callers. Await it via
    asyncio.shield() so a caller's cancellation never propagates into it."""
    task = asyncio.ensure_future(coro)
    _IN_FLIGHT.add(task)
    task.add_done_callback(_finish)
    return task


class UncancellableAsyncClient(httpx.AsyncClient):
    """AsyncClient whose requests (body read included) can't be cancelled by the caller.

    Overrides send() rather than taking a custom transport: passing `transport=`
    makes httpx ignore HTTPS_PROXY/NO_PROXY from the environment.
    """

    async def send(self, request: httpx.Request, **kwargs: Any) -> httpx.Response:
        if kwargs.get("stream"):  # a streamed body is read by the caller, outside our task
            return await super().send(request, **kwargs)
        return await asyncio.shield(spawn_uncancellable(super().send(request, **kwargs)))


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
    return _cached(_DEFAULT, lambda: UncancellableAsyncClient(timeout=_DEFAULT_TIMEOUT_S, limits=_limits()))


def get_credentials_client() -> httpx.AsyncClient:
    """Separate pool for IAM credential fetches (Pod Identity / ECS / STS).

    Every signed model call waits on these, so they must not queue behind the
    model calls themselves in the shared pool when it is saturated.
    """
    return _cached(
        _CREDENTIALS,
        lambda: UncancellableAsyncClient(
            timeout=_CREDENTIALS_TIMEOUT_S, limits=httpx.Limits(max_connections=_CREDENTIALS_MAX_CONNECTIONS)
        ),
    )


async def close_client() -> None:
    client = _clients.pop(_DEFAULT, None)
    if client is not None and not client.is_closed:
        await client.aclose()
