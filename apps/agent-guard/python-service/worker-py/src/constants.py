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
    if settings.GEMMA_VERTEX_ENDPOINT_ID and not settings.GEMMA_FOUNDRY_BASE_URL:
        return "gemma_vertexai"
    return "gemma_foundry"


def strip_qwen_tier(model_configs):
    """Drop Qwen (FAST_THREAT_FILTER) providers so only the arbiter judges.

    Returns the original list if filtering would leave nothing usable.
    """
    filtered = [m for m in (model_configs or []) if not str(m.get("provider", "")).lower().startswith("qwen")]
    return filtered or list(model_configs or [])


def force_gemma_only(_model_configs):
    """Replace whatever modelConfigs was supplied with the fixed Gemma-only map."""
    return [{"provider": _gemma_arbiter_provider(), "modelRole": "FINAL_ARBITER", "timeoutMs": 30000}]


# Cascade roles whose answer contract the env var may override.
# FINAL_ARBITER is normally absent: the letter contract returns no reason string,
# and the arbiter's verdict is the one that reaches the threat report, the
# remediation prompt's BLOCK REASON and the evidence-line prompt. An operator who
# really wants a letter-answering arbiter must say so per-model in the policy,
# where the consequence is visible.
_OVERRIDABLE_ROLES = {"FAST_THREAT_FILTER", "FAST_FALLBACK_SAFE_FILTER"}

# Formats that MAY also be stamped on the FINAL_ARBITER. "values" qualifies for
# two reasons: the gateway falls back to the policy reason when the model returns
# none (pii_password_llm.go:226) and the values still reach the report through
# piiValueSchemaErrors; and Password runs arbiter-only (force_gemma_only strips
# the fast tiers), so restricting it to the fast tiers would mean the override
# never reached Password at all.
_ARBITER_SAFE_FORMATS = {"values"}

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
    """Stamp SCANNER_RESPONSE_FORMAT onto the cascade entries.

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

    fast = ",".join(formats)
    arbiter = ",".join(f for f in formats if f in _ARBITER_SAFE_FORMATS)

    stamped = []
    for entry in model_configs or []:
        role = str(entry.get("modelRole", ""))
        if not formats:  # kill switch: clear every role, including the arbiter
            stamped.append({**entry, "responseFormat": ""})
        elif role in _OVERRIDABLE_ROLES:
            stamped.append({**entry, "responseFormat": fast})
        elif arbiter:
            stamped.append({**entry, "responseFormat": arbiter})
        else:
            stamped.append(entry)
    return stamped


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
