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
            "provider": "gemma_foundry",
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


def _parse_deployment_config(raw_json: str) -> dict[str, Any] | None:
    """The per-deployment DEFAULT_MODEL_CONFIG_JSON, or None when it's unset,
    unparseable, or doesn't carry a modelConfigs list."""
    if not raw_json or not raw_json.strip():
        return None
    try:
        cfg = json.loads(raw_json)
    except Exception as exc:
        logger.warning(f"[constants] DEFAULT_MODEL_CONFIG_JSON parse failed ({exc}); using built-in")
        return None
    if isinstance(cfg, dict) and cfg.get("modelConfigs"):
        return cfg
    logger.warning("[constants] DEFAULT_MODEL_CONFIG_JSON has no modelConfigs; using built-in")
    return None


def get_default_config(raw_json: str = "") -> dict[str, Any]:
    """Resolve the fallback modelMap for a request with no modelConfigs.

    Prefers the per-deployment DEFAULT_MODEL_CONFIG_JSON env value (passed in as
    raw_json); falls back to BUILTIN_DEFAULT_CONFIG when it's unset, unparseable,
    or doesn't carry a modelConfigs list.
    """
    return _parse_deployment_config(raw_json) or BUILTIN_DEFAULT_CONFIG


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
# Password runs on exactly ONE model — no fast tiers, no second-opinion arbiter
# (cost/latency) — and never one the caller picks: the deployment's own
# DEFAULT_MODEL_CONFIG_JSON decides (see single_model_config). Enforced here so it
# holds regardless of caller (policy modelConfigs, or a direct /scan hit with no
# config at all), not just the Go gateway's own hardcode.
SINGLE_MODEL_SCANNERS = {"Password"}


def _gemma_arbiter_provider() -> str:
    """Pick the configured Gemma backend: Vertex when set, else Azure Foundry."""
    if settings.GEMMA_VERTEX_ENDPOINT_ID and not settings.GEMMA_FOUNDRY_BASE_URL:
        return "gemma_vertexai"
    return "gemma_foundry"


def strip_qwen_tier(model_configs):
    """Drop Qwen (FAST_THREAT_FILTER) providers so only the arbiter judges.

    Returns the original list if filtering would leave nothing usable.
    """
    filtered = [m for m in (model_configs or []) if not str(m.get("provider", "")).lower().startswith("qwen")]
    return filtered or list(model_configs or [])


def _is_qwen(entry: dict[str, Any]) -> bool:
    return str(entry.get("provider", "")).lower().startswith("qwen")


# Roles a single-model scanner draws its one model from, in preference order.
_SINGLE_MODEL_ROLE_PREFERENCE = ("FAST_FALLBACK_SAFE_FILTER", "FINAL_ARBITER")


def _pick_single_model(model_configs: list[dict[str, Any]]) -> dict[str, Any] | None:
    """Choose the one entry a single-model scanner runs on: the first
    FAST_FALLBACK_SAFE_FILTER entry, else the first FINAL_ARBITER entry.
    Qwen3Guard is never eligible — it emits a safety verdict, not a
    secret-value judgement (see GEMMA_ONLY_SCANNERS)."""
    candidates = [m for m in model_configs if not _is_qwen(m)]
    for role in _SINGLE_MODEL_ROLE_PREFERENCE:
        for entry in candidates:
            if entry.get("modelRole") == role:
                return entry
    return None


def single_model_config() -> list[dict[str, Any]]:
    """The fixed one-entry modelMap for SINGLE_MODEL_SCANNERS.

    Picked from the deployment's DEFAULT_MODEL_CONFIG_JSON — its
    FAST_FALLBACK_SAFE_FILTER, else its FINAL_ARBITER, with that entry's
    model/baseUrl — and when that's unset or has neither, the configured Gemma
    backend. Caller-supplied modelConfigs are deliberately not consulted.
    """
    deployment = _parse_deployment_config(settings.DEFAULT_MODEL_CONFIG_JSON)
    chosen = _pick_single_model(deployment["modelConfigs"]) if deployment else None
    if chosen is None:
        return [{"provider": _gemma_arbiter_provider(), "modelRole": "FINAL_ARBITER", "timeoutMs": 30000}]
    return [{**chosen, "modelRole": "FINAL_ARBITER", "timeoutMs": chosen.get("timeoutMs") or 30000}]


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
