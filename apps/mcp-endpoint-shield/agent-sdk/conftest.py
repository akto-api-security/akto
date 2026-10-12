"""Test harness for the agent SDK.

Responsibilities:
  1. Put the SDK package, the existing shared utility, and the canonical
     device-identity module on sys.path so characterization tests can import the
     current (unchanged) code read-only.
  2. Pin a fixed environment BEFORE the env-driven utility is imported, so its
     import-time globals are deterministic. (That the utility computes config at
     import from env is exactly the "shape" problem the SDK fixes — here we just
     freeze it to characterize current behavior.)
  3. Provide a self-updating golden helper.
"""
import json
import os
import sys

import pytest

_HERE = os.path.dirname(os.path.abspath(__file__))                    # .../agent-sdk
_SHIELD = os.path.dirname(_HERE)                                      # .../mcp-endpoint-shield

# 1. import paths
sys.path.insert(0, _HERE)                                            # `import agent_sdk`
sys.path.insert(0, os.path.join(_SHIELD, "shared"))                 # akto_ingestion_utility
# canonical device identity (github's is the most complete — see DRIFT_REPORT.md)
sys.path.insert(0, os.path.join(_SHIELD, "github-cli-hooks"))        # akto_machine_id

# 2. deterministic env for anything imported at collection time
os.environ.setdefault("MODE", "atlas")
os.environ.setdefault("AKTO_CONNECTOR", "claude_code_cli")
os.environ.setdefault("AKTO_DATA_INGESTION_URL", "https://ingest.example.test")
os.environ.setdefault("AKTO_API_TOKEN", "test-token")
os.environ.setdefault("DEVICE_ID", "test-device")
os.environ.setdefault("CONTEXT_SOURCE", "ENDPOINT")
os.environ.setdefault("LOG_DIR", os.path.join(_HERE, ".pytest-logs"))

_GOLDEN_DIR = os.path.join(_HERE, "tests", "fixtures", "golden")
_UPDATE = os.environ.get("UPDATE_GOLDENS") == "1"


def assert_golden(name: str, actual):
    """Compare `actual` against tests/fixtures/golden/<name>.json.

    First run (or UPDATE_GOLDENS=1) writes the golden; later runs pin against it.
    This makes the current behavior the contract that phases 2-4 must not break.
    """
    os.makedirs(_GOLDEN_DIR, exist_ok=True)
    path = os.path.join(_GOLDEN_DIR, f"{name}.json")
    serialized = json.loads(json.dumps(actual, sort_keys=True))  # normalize tuples etc.
    if _UPDATE or not os.path.exists(path):
        with open(path, "w", encoding="utf-8") as f:
            json.dump(serialized, f, indent=2, sort_keys=True)
            f.write("\n")
    with open(path, encoding="utf-8") as f:
        expected = json.load(f)
    assert serialized == expected, (
        f"golden mismatch for {name!r}; run with UPDATE_GOLDENS=1 to refresh if intended"
    )


@pytest.fixture
def golden():
    """Inject the golden comparator into tests."""
    return assert_golden
