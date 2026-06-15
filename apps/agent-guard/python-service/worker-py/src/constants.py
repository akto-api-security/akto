"""Module-level defaults applied at the request boundary.

The tier-based modelMap the cascade runs when a request doesn't supply its own
modelConfigs. Each deployment can override it via the DEFAULT_MODEL_CONFIG_JSON
env var (see get_default_config) so two workers sharing this code can run
different model maps; BUILTIN_DEFAULT_CONFIG is the last-resort fallback.
"""

import json
import logging
from typing import Any, Dict

logger = logging.getLogger(__name__)


BUILTIN_DEFAULT_CONFIG: Dict[str, Any] = {
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


def get_default_config(raw_json: str = "") -> Dict[str, Any]:
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
CASCADE_SCANNERS = {"PromptInjection", "BanTopics", "Toxicity", "Gibberish"}
LOCAL_SCANNERS = {"BanSubstrings", "TokenLimit", "Secrets"}
# Scanners that proxy to a sibling Worker which in turn owns a Cloudflare
# Container. Used for any scanner that needs a real Python runtime (spaCy,
# torch, etc.) which Pyodide can't host.
REMOTE_SCANNERS = {"Anonymize"}
SUPPORTED_SCANNERS = CASCADE_SCANNERS | LOCAL_SCANNERS | REMOTE_SCANNERS
