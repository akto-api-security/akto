"""AbortSafeAsyncClient: cut-off requests must reach the upstream as a disconnect
(so vLLM drops them instead of computing answers nobody reads) without leaking
httpx pool slots.

With httpx 0.28.1 / httpcore 1.0.9, cancelling a request that is still queued for
a connection while the pool is saturated leaves a never-connected connection in
the pool forever; eventually every request on the client fails until restart.
"""

import asyncio
import random

import httpx
import pytest

import http_client


class _Upstream:
    """Behaves like vLLM: works on a request while its client stays connected,
    and drops it (an abort) if the client disconnects first."""

    def __init__(self, delay_s: float):
        self.delay_s = delay_s
        self.received = self.answered = self.aborted = 0

    async def handle(self, reader, writer):
        try:
            while True:
                await reader.readuntil(b"\r\n\r\n")
                self.received += 1
                eof = asyncio.ensure_future(reader.read(1))
                done, _ = await asyncio.wait({eof}, timeout=self.delay_s)
                if done:  # client went away mid-request
                    self.aborted += 1
                    return
                eof.cancel()
                writer.write(b"HTTP/1.1 200 OK\r\ncontent-length: 2\r\n\r\nok")
                await writer.drain()
                self.answered += 1
        except Exception:
            pass
        finally:
            writer.close()

    async def __aenter__(self):
        self._server = await asyncio.start_server(self.handle, "127.0.0.1", 0)
        self.url = f"http://127.0.0.1:{self._server.sockets[0].getsockname()[1]}/"
        return self

    async def __aexit__(self, *exc):
        self._server.close()


async def _drained():
    for _ in range(100):
        if not http_client._IN_FLIGHT:
            return
        await asyncio.sleep(0.02)
    raise AssertionError("background requests never finished")


async def test_request_returns_normally():
    async def handler(request):
        return httpx.Response(200, json={"path": request.url.path})

    async with http_client.AbortSafeAsyncClient(transport=httpx.MockTransport(handler)) as client:
        resp = await client.post("http://upstream/v1/x", json={})
    assert resp.json() == {"path": "/v1/x"}


async def test_cut_off_in_flight_request_is_aborted_upstream():
    async with _Upstream(delay_s=0.5) as up, http_client.AbortSafeAsyncClient(timeout=5) as client:
        with pytest.raises(TimeoutError):
            await asyncio.wait_for(client.get(up.url), 0.1)
        await _drained()
        for _ in range(50):
            if up.aborted:
                break
            await asyncio.sleep(0.02)
    assert (up.received, up.aborted, up.answered) == (1, 1, 0)


async def test_cut_off_queued_request_is_never_sent():
    limits = httpx.Limits(max_connections=1, max_keepalive_connections=1)
    async with _Upstream(delay_s=0.3) as up, http_client.AbortSafeAsyncClient(timeout=5, limits=limits) as client:
        first = asyncio.ensure_future(client.get(up.url))  # holds the only connection
        await asyncio.sleep(0.05)
        with pytest.raises(TimeoutError):
            await asyncio.wait_for(client.get(up.url), 0.05)  # still queued when cut off
        assert (await first).status_code == 200
        await _drained()
        assert up.received == 1  # the abandoned request never went out
        assert (await client.get(up.url)).status_code == 200  # and the pool is still healthy
    assert up.received == 2


async def test_pool_timeout_still_raised_and_request_never_sent():
    limits = httpx.Limits(max_connections=1, max_keepalive_connections=1)
    async with _Upstream(delay_s=0.3) as up, http_client.AbortSafeAsyncClient(timeout=5, limits=limits) as client:
        first = asyncio.ensure_future(client.get(up.url))  # holds the only connection
        await asyncio.sleep(0.05)
        with pytest.raises(httpx.PoolTimeout):
            await client.get(up.url, timeout=httpx.Timeout(5, pool=0.05))
        assert (await first).status_code == 200
        await _drained()
        assert up.received == 1  # the timed-out request never went out
        assert (await client.get(up.url)).status_code == 200
    assert up.received == 2


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
        for _ in range(200):  # let background requests finish
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
    stuck, fresh_ok = await _cancellation_storm(http_client.AbortSafeAsyncClient)
    assert stuck == 0
    assert fresh_ok
