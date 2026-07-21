"""scan_payload request-boundary rules — fake providers, no network.

Pins the Password gemma-only invariant at the worker boundary: whatever
modelConfigs a caller supplies (guardrail policy, the Go gateway, or the
DEFAULT_MODEL_CONFIG_JSON env), Password must run on a Gemma backend only —
never the Qwen tier or another arbiter. The Go gateway used to hardcode this
client-side; the worker is the enforcement point.
"""

import pytest

import llm_scanner
import model_map
import scan_handler
from settings import settings

UNSAFE = {"is_valid": False, "risk_score": 0.95, "decision_confidence": 0.95, "details": {"values": ["hunter2"]}}


class FakeProvider:
    def __init__(self, name):
        self.name = name


class FakeScanner:
    calls = []

    def __init__(self, provider):
        self.provider = provider

    async def scan(self, scanner_name, scanner_type, text, config):
        FakeScanner.calls.append(self.provider.name)
        r = {k: v for k, v in UNSAFE.items()}
        r["details"] = dict(r["details"], llm_provider=self.provider.name)
        return r


@pytest.fixture(autouse=True)
def patch_providers(monkeypatch):
    FakeScanner.calls = []
    monkeypatch.setattr(model_map, "build_provider_from_config", lambda entry: FakeProvider(entry["provider"]))
    monkeypatch.setattr(llm_scanner, "LLMScanner", FakeScanner)
    yield


# A hostile caller config: neither entry may ever be consulted for Password.
HOSTILE_CONFIG = {
    "modelConfigs": [
        {"provider": "qwen3guard", "modelRole": "FAST_THREAT_FILTER", "safeDecisionThreshold": 0.9},
        {"provider": "anthropic", "modelRole": "FINAL_ARBITER", "safeDecisionThreshold": 0.9},
    ]
}


async def _scan_password():
    return await scan_handler.scan_payload(
        {"scanner_name": "Password", "scanner_type": "prompt", "text": "pwd=hunter2", "config": HOSTILE_CONFIG}
    )


async def test_password_ignores_caller_models_and_runs_vertex_gemma(monkeypatch):
    monkeypatch.setattr(settings, "GEMMA_VERTEX_ENDPOINT_ID", "1234567890")
    monkeypatch.setattr(settings, "GEMMA_FOUNDRY_BASE_URL", "")
    r = await _scan_password()
    assert FakeScanner.calls == ["gemma_vertexai"]
    assert r["is_valid"] is False


async def test_password_ignores_caller_models_and_runs_foundry_gemma(monkeypatch):
    monkeypatch.setattr(settings, "GEMMA_VERTEX_ENDPOINT_ID", "")
    monkeypatch.setattr(settings, "GEMMA_FOUNDRY_BASE_URL", "https://ep.eastus2.inference.ml.azure.com/v1")
    r = await _scan_password()
    assert FakeScanner.calls == ["gemma_foundry"]
    assert r["is_valid"] is False


async def test_cascade_exception_still_schedules_slack_alert(monkeypatch):
    # A hard exception escaping scan_with_model_map (bad config, unexpected
    # bug — not the graceful "arbiter unreachable" _error_result path) must
    # still reach alerts.post_slack. Before this fix, only the success path
    # scheduled it, so a genuinely broken cascade was invisible outside logs.
    async def _boom(*_a, **_kw):
        raise RuntimeError("boom: unexpected cascade failure")

    monkeypatch.setattr(scan_handler, "scan_with_model_map", _boom)

    scheduled = []
    result = await scan_handler.scan_payload(
        {"scanner_name": "PromptInjection", "scanner_type": "prompt", "text": "hi", "config": HOSTILE_CONFIG},
        schedule_fn=scheduled.append,
    )

    assert result["is_valid"] is True
    assert "cascade failed" in result["details"]["error"]
    assert len(scheduled) == 1
    await scheduled[0]  # SLACK_WEBHOOK_URL unset in tests -> no-op, just avoids "never awaited"
