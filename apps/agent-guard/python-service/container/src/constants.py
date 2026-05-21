"""Module-level defaults applied at the request boundary.

Kept separate from model_map.py so the orchestrator stays focused on logic
and configuration changes don't churn that file.
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
            "modelRole": "FAST_FALLBACK_SAFE_FILTER",
        },
        {
            "provider": "anthropic",
            "model": "",
            "baseUrl": "",
            "safeDecisionThreshold": 0.9,
            "timeoutMs": 30000,
            "modelRole": "FINAL_ARBITER",
        }
],
    "parallelExecution": True,
    "storeAllResults": False,
}
