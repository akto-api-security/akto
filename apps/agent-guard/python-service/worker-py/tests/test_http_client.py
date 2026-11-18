"""UncancellableAsyncClient: a caller's asyncio cancellation must never reach httpcore.

With httpx 0.28.1 / httpcore 1.0.9, cancelling requests (asyncio.wait_for, as the
cascade does) while the pool is saturated leaves connections permanently busy;
eventually every request fails with PoolTimeout until the process restarts.
"""

import asyncio
import random

import httpx
import pytest

import http_client


async def test_caller_cancellation_does_not_cancel_the_request():
    finished = asyncio.Event()

    async def handler(request):
        await asyncio.sleep(0.1)
        finished.set()
        return httpx.Response(200, json={"ok": True})

    async with http_client.UncancellableAsyncClient(transport=httpx.MockTransport(handler)) as client:
        with pytest.raises(TimeoutError):
            await asyncio.wait_for(client.get("http://upstream/"), 0.01)
        await asyncio.wait_for(finished.wait(), 1)  # request ran to completion anyway
        await asyncio.sleep(0)
    assert not http_client._IN_FLIGHT  # background task released


async def test_uncancelled_request_returns_normally():
    async def handler(request):
        return httpx.Response(200, json={"path": request.url.path})

    async with http_client.UncancellableAsyncClient(transport=httpx.MockTransport(handler)) as client:
        resp = await client.post("http://upstream/v1/x", json={})
    assert resp.json() == {"path": "/v1/x"}


async def _cancellation_storm(client_cls, requests=600, max_connections=3):
    """Hammer a saturated pool with requests cut off mid-flight, then report
    (stuck_busy_connections, fresh_request_ok).

    With a plain httpx.AsyncClient this ends (3, False) every time: a queued
    request assigned a brand-new connection and cancelled before using it leaves
    that never-connected connection in the pool, neither idle nor available, forever.
    """
    calm = False

    async def handle(reader, writer):
        try:
            while True:
                await reader.readuntil(b"\r\n\r\n")
                if not calm:
                    await asyncio.sleep(random.uniform(0.01, 0.03))
                writer.write(b"HTTP/1.1 200 OK\r\ncontent-length: 2\r\n\r\nok")
                await writer.drain()
        except Exception:
            pass
        finally:
            writer.close()

    server = await asyncio.start_server(handle, "127.0.0.1", 0)
    url = f"http://127.0.0.1:{server.sockets[0].getsockname()[1]}/"
    limits = httpx.Limits(max_connections=max_connections, max_keepalive_connections=max_connections)
    async with client_cls(timeout=5, limits=limits) as client:
        sem = asyncio.Semaphore(max_connections * 4)

        async def one():
            async with sem:
                try:
                    await asyncio.wait_for(client.get(url), random.uniform(0.005, 0.02))
                except TimeoutError:
                    pass

        await asyncio.gather(*(one() for _ in range(requests)))
        calm = True
        for _ in range(200):  # let orphaned (uncancelled) requests drain
            if not http_client._IN_FLIGHT:
                break
            await asyncio.sleep(0.05)
        pool = client._transport._pool
        stuck = sum(1 for c in pool.connections if not c.is_idle())
        try:
            fresh_ok = (await client.get(url, timeout=httpx.Timeout(5, pool=1))).status_code == 200
        except httpx.PoolTimeout:
            fresh_ok = False
    server.close()
    return stuck, fresh_ok


async def test_pool_survives_a_cancellation_storm():
    stuck, fresh_ok = await _cancellation_storm(http_client.UncancellableAsyncClient)
    assert stuck == 0
    assert fresh_ok
