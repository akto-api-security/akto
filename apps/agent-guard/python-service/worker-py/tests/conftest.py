"""Make the worker's `src/` importable as top-level modules in tests."""

import pathlib
import sys

SRC = pathlib.Path(__file__).resolve().parent.parent / "src"
if str(SRC) not in sys.path:
    sys.path.insert(0, str(SRC))


import pytest  # noqa: E402


@pytest.fixture(autouse=True)
def _no_background_health_probe(monkeypatch):
    """Keep the arbiter /health probe out of unrelated tests: their fake HTTP clients have no GET, so a
    probe would flag the primary down and leak that module-global flag into later tests."""
    import providers

    monkeypatch.setattr(providers, "_ensure_health_monitor", lambda url: None)
    monkeypatch.setattr(providers, "_PRIMARY_AVAILABLE", {})
