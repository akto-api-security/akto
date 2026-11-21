"""Shared httpx.AsyncClient for outbound HTTP (Vertex, anonymizer, etc.).

Reuses TCP connections under load instead of opening a new client per request.

Long-lived clients must be AbortSafeAsyncClient. The cascade cuts model calls
off with asyncio.wait_for, and how a cut-off request is ended matters twice:

- It must not leak pool slots. With httpx 0.28.1 / httpcore 1.0.9, when the
  pool is saturated httpcore can assign a queued request a brand-new,
  not-yet-connected connection; cancelling the request before it starts using
  that connection leaves the connection in the pool forever (never idle, never
  available). Enough of those and every request on the client fails until the
  process restarts.
- It must reach the upstream as a disconnect. Model servers (vLLM) only drop a
  queued request when its client goes away; keeping abandoned requests open
  makes the GPU process them for nobody, and under a burst the queue fills with
  dead work until every live request times out too (agent-guard 1.3.7).

So a cut-off request is cancelled once its HTTP/1.1 exchange has started
(cancellation is clean there and closes the socket), and one that hasn't started
yet is abandoned at its next start point, before anything is sent.
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


class _Abandoned(Exception):
    """Ends a request whose caller gave up before its HTTP exchange started."""


# httpcore trace events (httpcore 1.0.x, HTTP/1.1). Raising inside a ".started"
# event's hook unwinds through httpcore's own cleanup: before connect, the
# connection is marked failed and dropped; at send_request_headers, the ACTIVE
# connection is closed. Cancelling (task.cancel(), which httpcore's anyio shields
# don't stop) is only clean while the request is being sent or is waiting for the
# response headers — the wait an upstream like vLLM should be told to stop:
# - between connect and the exchange it leaves a connected but NEW (never idle,
#   never available) HTTP/1.1 connection in the pool forever;
# - once the response is in (or the exchange failed) it can land inside
#   httpcore's response_closed cleanup and leave the connection ACTIVE forever.
_START_EVENTS = frozenset(
    {
        "connection.connect_tcp.started",
        "connection.connect_unix_socket.started",
        "http11.send_request_headers.started",
    }
)
_CANCELLABLE_EVENTS = frozenset(
    {
        "http11.send_request_headers.started",
        "http11.send_request_headers.complete",
        "http11.send_request_body.started",
        "http11.send_request_body.complete",
        "http11.receive_response_headers.started",
    }
)
_PENDING, _CANCELLABLE, _FINISHING = "pending", "cancellable", "finishing"


class AbortSafeAsyncClient(httpx.AsyncClient):
    """AsyncClient that ends cut-off requests without leaking pool slots (see module docstring).

    The request runs in its own task so a caller's cancellation never lands on it
    directly. httpcore's per-request `trace` hook tracks its phase:
    - pending (queued / connecting): a cut-off request is abandoned at its next
      start event, so it is never sent;
    - cancellable (sending, or waiting for the response headers): it is cancelled,
      the connection closes, and the upstream sees the disconnect;
    - finishing (response in, or the exchange failed): it is left to complete,
      since the upstream's work is already done.

    Overrides send() rather than taking a custom transport: passing `transport=`
    makes httpx ignore HTTPS_PROXY/NO_PROXY from the environment.
    """

    async def send(self, request: httpx.Request, **kwargs: Any) -> httpx.Response:
        if kwargs.get("stream"):  # a streamed body is read by the caller, outside our task
            return await super().send(request, **kwargs)
        phase, abandoned, has_connection = _PENDING, False, False
        caller_trace = request.extensions.get("trace")
        timeouts = dict(request.extensions.get("timeout") or {})
        pool_timeout = timeouts.get("pool")

        async def trace(event: str, info: dict[str, Any]) -> None:
            nonlocal phase, has_connection
            if phase == _PENDING and event in _START_EVENTS:
                if abandoned:
                    raise _Abandoned()
                has_connection = True
            if event in _CANCELLABLE_EVENTS:
                if phase != _FINISHING:
                    phase = _CANCELLABLE
            elif phase == _CANCELLABLE:
                phase = _FINISHING
            if caller_trace is not None:
                await caller_trace(event, info)

        # httpcore's own pool timeout races connection assignment the same way a
        # cancellation does (a request timing out as it is handed a new connection
        # leaves that connection in the pool forever), so it is enforced here
        # instead: a request still without a connection at the deadline is
        # abandoned, and the caller gets the usual PoolTimeout.
        request.extensions = {**request.extensions, "trace": trace, "timeout": {**timeouts, "pool": None}}
        task = spawn_uncancellable(super().send(request, **kwargs))
        try:
            if pool_timeout is not None:
                await asyncio.wait({task}, timeout=pool_timeout)  # never cancels the task
                if not task.done() and not has_connection:
                    abandoned = True
                    raise httpx.PoolTimeout("Timed out waiting for a connection from the pool", request=request)
            return await asyncio.shield(task)
        except asyncio.CancelledError:
            abandoned = True
            if phase == _CANCELLABLE:
                task.cancel()  # closes the connection; the upstream sees the disconnect
            raise


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
    return _cached(_DEFAULT, lambda: AbortSafeAsyncClient(timeout=_DEFAULT_TIMEOUT_S, limits=_limits()))


def get_credentials_client() -> httpx.AsyncClient:
    """Separate pool for IAM credential fetches (Pod Identity / ECS / STS).

    Every signed model call waits on these, so they must not queue behind the
    model calls themselves in the shared pool when it is saturated.
    """
    return _cached(
        _CREDENTIALS,
        lambda: AbortSafeAsyncClient(
            timeout=_CREDENTIALS_TIMEOUT_S, limits=httpx.Limits(max_connections=_CREDENTIALS_MAX_CONNECTIONS)
        ),
    )


async def close_client() -> None:
    client = _clients.pop(_DEFAULT, None)
    if client is not None and not client.is_closed:
        await client.aclose()
