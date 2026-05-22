"""Module-level defaults applied at the request boundary.

Ported verbatim from container/src/constants.py — the tier-based modelMap the
cascade runs when a request doesn't supply its own modelConfigs.
"""

from typing import Any, Dict


DEFAULT_CONFIG: Dict[str, Any] = {
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

# Routing tables — the single source of truth for which backend handles a scan.
CASCADE_SCANNERS = {"PromptInjection", "BanTopics", "Toxicity", "Gibberish"}
LOCAL_SCANNERS = {"BanSubstrings", "TokenLimit", "Secrets"}
SUPPORTED_SCANNERS = CASCADE_SCANNERS | LOCAL_SCANNERS
