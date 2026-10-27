import logging

import pytest

import providers
from providers import GemmaFastArbiterProvider, GemmaFastProvider, OpenAIProvider

_HOST = "http://arbiter-host:80/v1"


class _FakeResponse:
    def __init__(self, payload):
        self._payload = payload

    def raise_for_status(self):
        pass

    def json(self):
        return self._payload


class _FakeClient:
    posts = []
    payload = {}

    async def post(self, url, headers=None, json=None):
        _FakeClient.posts.append({"url": url, "json": json})
        return _FakeResponse(_FakeClient.payload)


@pytest.fixture(autouse=True)
def patch_http(monkeypatch):
    _FakeClient.posts = []
    _FakeClient.payload = {}
    providers._PROVIDER_CACHE.clear()
    monkeypatch.setattr(providers.http_client, "get_client", lambda: _FakeClient())
    yield
    providers._PROVIDER_CACHE.clear()


def _completion(metrics=None):
    body = {"id": "chatcmpl-abc", "choices": [{"message": {"content": "A"}}]}
    if metrics is not None:
        body["metrics"] = metrics
    return body


async def test_arbiter_logs_metrics_and_sends_include_metrics(caplog):
    _FakeClient.payload = _completion(
        {"time_to_first_token_ms": 100.0, "generation_time_ms": 50.0, "queue_time_ms": 1.5}
    )
    provider = GemmaFastArbiterProvider("k", "gemma-4-26b-a4b-it", base_url=_HOST)
    with caplog.at_level(logging.INFO, logger="providers"):
        assert await provider.complete("hi") == "A"
    assert _FakeClient.posts[0]["json"]["include_metrics"] is True
    line = next(r.getMessage() for r in caplog.records if "[vllm-metrics]" in r.getMessage())
    assert "provider=gemma_fast_arbiter" in line
    assert "model=gemma-4-26b-a4b-it" in line
    assert "host=arbiter-host:80" in line
    assert "req_id=chatcmpl-abc" in line
    assert "time_to_first_token_ms=100.0" in line
    assert "generation_time_ms=50.0" in line
    assert "queue_time_ms=1.5" in line
    assert "llm_ms=150.0" in line
    assert "network_ms=" in line
    assert "client_ms=" in line


async def test_missing_metrics_is_visible(caplog):
    _FakeClient.payload = _completion()
    provider = GemmaFastArbiterProvider("k", "m", base_url=_HOST)
    with caplog.at_level(logging.INFO, logger="providers"):
        await provider.complete("hi")
    line = next(r.getMessage() for r in caplog.records if "[vllm-metrics]" in r.getMessage())
    assert "metrics=missing" in line


async def test_non_dict_metrics_does_not_raise(caplog):
    _FakeClient.payload = _completion("oops")
    provider = GemmaFastArbiterProvider("k", "m", base_url=_HOST)
    with caplog.at_level(logging.INFO, logger="providers"):
        assert await provider.complete("hi") == "A"
    line = next(r.getMessage() for r in caplog.records if "[vllm-metrics]" in r.getMessage())
    assert "metrics=invalid" in line


async def test_partial_metrics_skips_derived_fields(caplog):
    _FakeClient.payload = _completion({"queue_time_ms": 2.0})
    provider = GemmaFastArbiterProvider("k", "m", base_url=_HOST)
    with caplog.at_level(logging.INFO, logger="providers"):
        await provider.complete("hi")
    line = next(r.getMessage() for r in caplog.records if "[vllm-metrics]" in r.getMessage())
    assert "queue_time_ms=2.0" in line
    assert "llm_ms=" not in line


@pytest.mark.parametrize("cls", [GemmaFastProvider, OpenAIProvider])
async def test_other_providers_do_not_send_or_log(cls, caplog):
    _FakeClient.payload = _completion({"time_to_first_token_ms": 1.0, "generation_time_ms": 1.0})
    provider = cls("k", "m", base_url=_HOST)
    with caplog.at_level(logging.INFO, logger="providers"):
        await provider.complete("hi")
    assert "include_metrics" not in _FakeClient.posts[0]["json"]
    assert not any("[vllm-metrics]" in r.getMessage() for r in caplog.records)
