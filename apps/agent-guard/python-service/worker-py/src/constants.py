"""Module-level defaults applied at the request boundary.

The tier-based modelMap the cascade runs when a request doesn't supply its own
modelConfigs. Each deployment can override it via the DEFAULT_MODEL_CONFIG_JSON
env var (see get_default_config) so two workers sharing this code can run
different model maps; BUILTIN_DEFAULT_CONFIG is the last-resort fallback.
"""

import json
import logging
from typing import Any

from settings import settings

logger = logging.getLogger(__name__)


BUILTIN_DEFAULT_CONFIG: dict[str, Any] = {
    "modelConfigs": [
        {
            "provider": "qwen3guard",
            "model": "",
            "baseUrl": "",
            "safeDecisionThreshold": 0.9,
            "timeoutMs": 5000,
            "modelRole": "FAST_THREAT_FILTER",
        },
        {
            "provider": "gemma_vertexai",
            "model": "",
            "baseUrl": "",
            "safeDecisionThreshold": 0.9,
            "timeoutMs": 30000,
            "modelRole": "FINAL_ARBITER",
        },
    ],
    "parallelExecution": False,
    "storeAllResults": False,
}


def get_default_config(raw_json: str = "") -> dict[str, Any]:
    """Resolve the fallback modelMap for a request with no modelConfigs.

    Prefers the per-deployment DEFAULT_MODEL_CONFIG_JSON env value (passed in as
    raw_json); falls back to BUILTIN_DEFAULT_CONFIG when it's unset, unparseable,
    or doesn't carry a modelConfigs list.
    """
    if raw_json and raw_json.strip():
        try:
            cfg = json.loads(raw_json)
            if isinstance(cfg, dict) and cfg.get("modelConfigs"):
                return cfg
            logger.warning("[constants] DEFAULT_MODEL_CONFIG_JSON has no modelConfigs; using built-in")
        except Exception as exc:
            logger.warning(f"[constants] DEFAULT_MODEL_CONFIG_JSON parse failed ({exc}); using built-in")
    return BUILTIN_DEFAULT_CONFIG


# Routing tables — the single source of truth for which backend handles a scan.
# BanCode is LLM-judged (code detection via the Gemma arbiter), not the old
# heuristic — see GEMMA_ONLY_SCANNERS for why it skips the Qwen tier.
CASCADE_SCANNERS = {"PromptInjection", "BanTopics", "Toxicity", "Gibberish", "BanCode", "Password"}
LOCAL_SCANNERS = {"BanSubstrings", "TokenLimit", "Secrets"}
# Scanners the Qwen3Guard tier cannot judge (it emits a safety verdict, not a
# code/quality verdict). For these, the Qwen FAST_THREAT_FILTER tier is stripped
# from modelConfigs so only the arbiter LLM (Gemma) decides — otherwise Qwen
# would fast-pass benign-but-flaggable input as "safe".
GEMMA_ONLY_SCANNERS = {"BanCode", "Password"}
# Password never uses a second-opinion arbiter (cost/latency) — enforced here so
# it holds regardless of caller (policy modelConfigs, DEFAULT_MODEL_CONFIG_JSON,
# or a direct /scan hit with no config at all), not just the Go gateway's own hardcode.
FORCE_GEMMA_ONLY_SCANNERS = {"Password"}


def _gemma_arbiter_provider() -> str:
    """Pick the configured Gemma backend: Vertex when set, else Azure Foundry."""
    if not settings.GEMMA_VERTEX_ENDPOINT_ID and settings.GEMMA_FOUNDRY_BASE_URL:
        return "gemma_foundry"
    return "gemma_vertexai"


def strip_qwen_tier(model_configs):
    """Drop Qwen (FAST_THREAT_FILTER) providers so only the arbiter judges.

    Returns the original list if filtering would leave nothing usable.
    """
    filtered = [m for m in (model_configs or []) if not str(m.get("provider", "")).lower().startswith("qwen")]
    return filtered or list(model_configs or [])


def force_gemma_only(_model_configs):
    """Replace whatever modelConfigs was supplied with the fixed Gemma-only map."""
    return [{"provider": _gemma_arbiter_provider(), "modelRole": "FINAL_ARBITER", "timeoutMs": 30000}]


# Scanners that proxy to a sibling Worker which in turn owns a Cloudflare
# Container. Used for any scanner that needs a real Python runtime (spaCy,
# torch, etc.) which Pyodide can't host.
REMOTE_SCANNERS = {"Anonymize"}
SUPPORTED_SCANNERS = CASCADE_SCANNERS | LOCAL_SCANNERS | REMOTE_SCANNERS

# Caller-supplied scanner_name aliases → canonical name. The container exposes a
# separate ML-based `Code` scanner (language classification); Pyodide can't host
# that model, so here `Code` is served by the `BanCode` heuristic (code-presence
# detection). Keys are lower-cased; matching is case-insensitive (see
# canonical_scanner). All code-detection spellings therefore behave identically.
SCANNER_ALIASES = {"code": "BanCode"}


def canonical_scanner(name: str) -> str:
    """Resolve a caller-supplied scanner_name to its canonical form.

    Case-insensitive, so "bancode" / "BANCODE" match "BanCode". Applies
    SCANNER_ALIASES (e.g. Code/code → BanCode) so equivalent names route to the
    same scanner. Unknown names are returned unchanged for the caller's
    unsupported-scanner handling.
    """
    if not name:
        return name
    lowered = name.lower()
    for canonical in SUPPORTED_SCANNERS:
        if canonical.lower() == lowered:
            return canonical
    return SCANNER_ALIASES.get(lowered, name)
