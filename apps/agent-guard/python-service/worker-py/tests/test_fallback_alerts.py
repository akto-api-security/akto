"""Model-fallback Slack alerts — recording, throttling, flushing and the provider hooks (network mocked)."""

import asyncio

import pytest

import alerts
import providers


@pytest.fixture
def slack(monkeypatch):
    """Webhook set, clean alert state, and a fake HTTP client that captures posted texts."""
    monkeypatch.setattr(alerts.settings, "FALLBACK_SLACK_WEBHOOK_URL", "https://hooks.example/fallback")
    monkeypatch.setattr(alerts, "_fallbacks", {})
    monkeypatch.setattr(alerts, "_transitions", [])
    posted: list[str] = []

    class _Client:
        def __init__(self, *a, **k):
            pass

        async def __aenter__(self):
            return self

        async def __aexit__(self, *a):
            return False

        async def post(self, url, headers=None, json=None):
            posted.append(json["text"])
            return type("R", (), {"status_code": 200})()

    monkeypatch.setattr(alerts.httpx, "AsyncClient", _Client)
    return posted


def _advance(monkeypatch, seconds):
    now = alerts.time.time()
    monkeypatch.setattr(alerts.time, "time", lambda: now + seconds)


async def test_noop_without_webhook(monkeypatch):
    monkeypatch.setattr(alerts.settings, "FALLBACK_SLACK_WEBHOOK_URL", "")
    monkeypatch.setattr(alerts, "_fallbacks", {})
    monkeypatch.setattr(alerts, "_transitions", [])
    alerts.note_fallback("gemma_fast", "gemma_foundry", "timeout")
    alerts.note_availability("vllm:80", False)
    assert not alerts.has_pending_fallback_alerts()
    assert alerts._fallbacks == {} and alerts._transitions == []


async def test_first_fallback_posts_then_throttles_then_summarises(slack, monkeypatch):
    alerts.note_fallback("gemma_fast", "gemma_foundry", "timeout")
    assert alerts.has_pending_fallback_alerts()
    await alerts.flush_fallback_alerts()
    assert len(slack) == 1 and "`gemma_fast` fell back to `gemma_foundry` 1x" in slack[0]

    for reason in ("timeout", "timeout", "ConnectError"):  # inside the window: counted, not posted
        alerts.note_fallback("gemma_fast", "gemma_foundry", reason)
    assert not alerts.has_pending_fallback_alerts()
    await alerts.flush_fallback_alerts()
    assert len(slack) == 1

    _advance(monkeypatch, alerts._FALLBACK_WINDOW_S + 1)  # window over: one summary with the count and causes
    assert alerts.has_pending_fallback_alerts()
    await alerts.flush_fallback_alerts()
    assert len(slack) == 2
    assert "3x" in slack[1] and "timeout x2" in slack[1] and "ConnectError x1" in slack[1]
    assert not alerts.has_pending_fallback_alerts()


async def test_pairs_are_throttled_independently(slack):
    alerts.note_fallback("gemma_fast", "gemma_foundry", "timeout")
    alerts.note_fallback("qwen3guard_fast", "qwen3guard", "ConnectError")
    await alerts.flush_fallback_alerts()
    assert len(slack) == 1
    assert "`gemma_fast`" in slack[0] and "`qwen3guard_fast`" in slack[0]


async def test_health_flips_post_at_once(slack):
    alerts.note_availability("vllm:80", False)
    alerts.note_availability("vllm:80", True)
    assert alerts.has_pending_fallback_alerts()
    await alerts.flush_fallback_alerts()
    assert "DOWN" in slack[0] and "AVAILABLE again" in slack[0]
    assert not alerts.has_pending_fallback_alerts()


async def test_concurrent_flushes_post_once(slack):
    alerts.note_fallback("gemma_fast", "gemma_foundry", "timeout")
    await asyncio.gather(alerts.flush_fallback_alerts(), alerts.flush_fallback_alerts())
    assert len(slack) == 1


async def test_failed_post_never_raises(slack, monkeypatch):
    class _Down:
        def __init__(self, *a, **k):
            raise RuntimeError("slack unreachable")

    monkeypatch.setattr(alerts.httpx, "AsyncClient", _Down)
    alerts.note_fallback("gemma_fast", "gemma_foundry", "timeout")
    await alerts.flush_fallback_alerts()  # logged, not raised


def test_reason_groups_timeouts():
    assert alerts.fallback_reason(TimeoutError()) == "timeout"
    assert alerts.fallback_reason(providers.httpx.ReadTimeout("slow")) == "timeout"
    assert alerts.fallback_reason(providers.httpx.ConnectError("refused")) == "ConnectError"


@pytest.mark.parametrize("status", [400, 429, 503])
def test_reason_names_the_http_status(status):
    httpx = providers.httpx
    request = httpx.Request("POST", "http://vllm:80/v1/chat/completions")
    exc = httpx.HTTPStatusError("failed", request=request, response=httpx.Response(status, request=request))
    assert alerts.fallback_reason(exc) == f"HTTP {status}"


# ── provider hooks ────────────────────────────────────────────────────────────


class _Backup:
    name = "backup"

    async def complete(self, prompt):
        return "from backup"


def _arbiter(monkeypatch, primary):
    monkeypatch.setattr(providers, "build_provider_from_config", lambda entry: _Backup())
    monkeypatch.setattr(providers, "_ensure_health_monitor", lambda url: None)
    monkeypatch.setattr(providers, "_PRIMARY_AVAILABLE", {})
    return providers.FallbackProvider(primary, {"provider": "anthropic"})


class _Primary:
    name = "primary"
    base_url = "http://vllm:80/v1"
    health_path = "/health"

    async def complete(self, prompt):
        raise providers.httpx.ConnectError("refused")


async def test_arbiter_fallback_is_recorded_and_flip_reported(slack, monkeypatch):
    fb = _arbiter(monkeypatch, _Primary())
    assert await fb.complete("hi") == "from backup"
    assert alerts._fallbacks[("primary", "backup")]["reasons"] == {"ConnectError": 1}
    assert any("`vllm:80` is DOWN" in t for t in alerts._transitions)  # ConnectError marks it down

    await fb.complete("hi")  # now skipped by the down flag: recorded as "marked down"
    assert alerts._fallbacks[("primary", "backup")]["reasons"] == {"ConnectError": 1, "marked down": 1}


async def test_gemma_fast_fallback_is_recorded(slack, monkeypatch):
    class _Foundry:
        name = "gemma_foundry"

        async def complete(self, prompt):
            return "from foundry"

    async def _slow(self, prompt):
        raise TimeoutError

    monkeypatch.setattr(providers.OpenAIProvider, "complete", _slow)
    monkeypatch.setattr(providers, "_build_foundry", lambda *a: _Foundry())
    p = providers.GemmaFastProvider("key", "gemma", base_url="http://vllm:80/v1")
    assert await p.complete("hi") == "from foundry"
    assert alerts._fallbacks[("gemma_fast", "gemma_foundry")]["reasons"] == {"timeout": 1}
