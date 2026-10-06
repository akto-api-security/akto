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


# "json" is spelled as the empty responseFormat internally (see llm_scanner);
# accepting it as a word gives operators an explicit kill switch that beats a
# per-model responseFormat without editing the policy.
_JSON_ALIAS = "json"


def _requested_formats() -> list[str] | None:
    """Parse SCANNER_RESPONSE_FORMAT into the formats to stamp.

    Returns None for "no override", [] for the "json" kill switch, else the
    requested formats in order. Several may be named ("abcd,values") so one
    deployment-wide setting can ask every scanner for its own compact contract;
    each scanner takes the first it supports (prompts.resolve_response_format).
    """
    from prompts import known_formats

    raw = (settings.SCANNER_RESPONSE_FORMAT or "").strip().lower()
    if not raw:
        return None
    if raw == _JSON_ALIAS:
        return []
    formats = [part.strip() for part in raw.split(",") if part.strip()]
    unknown = [f for f in formats if f not in known_formats()]
    if unknown:
        logger.warning(
            f"[constants] SCANNER_RESPONSE_FORMAT names unknown format(s) {unknown}; "
            f"known: {sorted(known_formats())} (plus '{_JSON_ALIAS}'). "
            "Leaving per-model responseFormat untouched"
        )
        return None
    return formats


def apply_scanner_response_format(model_configs):
    """Stamp SCANNER_RESPONSE_FORMAT onto EVERY cascade entry, all roles.

    The FINAL_ARBITER is included deliberately. A compact contract returns no
    reason string and a coarse risk_score, so the verdict that reaches the threat
    report carries only the synthesised metadata from llm_scanner — the
    per-sample explanation is given up in exchange for the tokens, on the
    understanding that those metadata fields are regenerated asynchronously
    afterwards rather than inline on the blocking path. Nothing in this service
    performs that regeneration today.

    Scanner-agnostic: it sets responseFormat on the entries, and which scanners
    actually honour a given format is decided later by prompts._FORMAT_CAPABLE,
    so a scanner with no template in that format quietly stays on JSON.

    Empty env var (the default) returns the configs untouched, so the per-model
    ModelConfig.responseFormat set in the policy still decides. An unrecognised
    value is logged and ignored rather than guessed at — silently falling back to
    a format the operator did not ask for is how a rollback looks like a no-op.
    """
    formats = _requested_formats()
    if formats is None:
        return list(model_configs or [])
    # [] is the kill switch and correctly stamps "" (back to the JSON verdict).
    response_format = ",".join(formats)
    return [{**entry, "responseFormat": response_format} for entry in (model_configs or [])]


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
