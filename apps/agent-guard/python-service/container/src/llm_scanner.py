"""LLM-backed scanner entrypoint.

Public API (imported by scanner_service.py):
    - init_llm_scanner()      → Optional[LLMScanner]    (single-provider, env-driven)
    - scan_with_model_map(…)  → dict                    (multi-provider, modelMap-driven)
    - LLM_SUPPORTED_SCANNERS  → set[str]                (which scanner names go via LLM)
    - is_truthy(value)        → bool                    (env-var parsing helper)
    - LLMScanner              (one scanner = one provider + result parser)
"""

import json
import logging
import os
import time
from typing import Any, Dict, Optional

from model_map import ModelMapScanner
from prompts import build_scan_prompt
from providers import (
    LLMProvider,
    Qwen3GuardProvider,
    build_provider_from_env,
    parse_qwen3guard_result,
)

logger = logging.getLogger(__name__)

LLM_SUPPORTED_SCANNERS = {"PromptInjection", "BanTopics", "Toxicity", "Gibberish"}

# Each LLM scanner returns a different boolean key in its JSON response.
# Everything else about result parsing is identical.
_SCANNER_FLAG_KEYS = {
    "PromptInjection": "isInjection",
    "BanTopics": "isBanned",
    "Toxicity": "isToxic",
    "Gibberish": "isGibberish",
}


# ── Small helpers ────────────────────────────────────────────────────────────


def is_truthy(value: Optional[str]) -> bool:
    return (value or "").strip().lower() in ("1", "true", "yes", "on")


def _clean_json(raw: str) -> str:
    """Extract a JSON object from a possibly chatty LLM reply."""
    if not raw:
        raise ValueError("empty response")
    last = raw.rfind("}")
    if last != -1:
        raw = raw[: last + 1]
    first = raw.find("{")
    if first != -1:
        raw = raw[first:]
    raw = raw.strip()
    if not raw:
        raise ValueError("no valid JSON found in response")
    return raw


def parse_llm_result(scanner_name: str, raw: str) -> Dict[str, Any]:
    """Parse LLM JSON response into a result dict with is_valid, risk_score, details."""
    parsed = json.loads(_clean_json(raw))

    details: Dict[str, Any] = {}
    reason = parsed.get("reason", "")
    if reason:
        details["reason"] = reason

    flag_key = _SCANNER_FLAG_KEYS.get(scanner_name)
    if flag_key is None:
        return {"is_valid": True, "risk_score": 0.0, "details": details}

    flagged = bool(parsed.get(flag_key, False))
    confidence = float(parsed.get("confidence", 0.0))

    if scanner_name == "BanTopics":
        matched = parsed.get("matchedTopic", "")
        if matched:
            details["matchedTopic"] = matched

    return {
        "is_valid": not flagged,
        "risk_score": confidence,
        "decision_confidence": confidence if flagged else (1.0 - confidence),
        "details": details,
    }


# ── Single-scanner driver ────────────────────────────────────────────────────


class LLMScanner:
    """Evaluates one of LLM_SUPPORTED_SCANNERS against a single provider."""

    def __init__(self, provider: LLMProvider):
        self.provider = provider

    def scan(
        self,
        scanner_name: str,
        scanner_type: str,
        text: str,
        config: Dict[str, Any],
    ) -> Dict[str, Any]:
        """Return a result dict whose keys match the ScanResponse fields. Raises on hard failure."""
        if scanner_name not in LLM_SUPPORTED_SCANNERS:
            raise ValueError(f"Scanner {scanner_name} not supported by LLM path")

        start = time.time()
        if isinstance(self.provider, Qwen3GuardProvider):
            raw, logprobs = self.provider.complete_with_logprobs(text)
            result = parse_qwen3guard_result(scanner_name, raw, logprobs)
        else:
            prompt = build_scan_prompt(
                scanner_name, scanner_type, config, text,
                provider_name=self.provider.name,
            )
            if prompt is None:
                raise ValueError(f"Scanner {scanner_name} not supported by LLM path")
            raw = self.provider.complete(prompt)
            result = parse_llm_result(scanner_name, raw)

        elapsed_ms = (time.time() - start) * 1000
        result["details"]["llm_provider"] = self.provider.name
        result["details"]["scanner_type"] = scanner_type
        result["execution_time_ms"] = round(elapsed_ms, 2)

        logger.info(
            f"[LLMScanner] scan complete scanner={scanner_name} "
            f"isValid={result['is_valid']} risk={result['risk_score']:.2f} "
            f"elapsed_ms={elapsed_ms:.0f}"
        )
        return result


# ── Multi-model parallel scanner (thin wrapper) ──────────────────────────────


def scan_with_model_map(
    scanner_name: str,
    scanner_type: str,
    text: str,
    config: Dict[str, Any],
    store_fn: Optional[Any] = None,
) -> Dict[str, Any]:
    return ModelMapScanner(scanner_name, scanner_type, text, config, store_fn).run()


# ── Module-level initializer ─────────────────────────────────────────────────


def init_llm_scanner() -> Optional[LLMScanner]:
    """Build an LLMScanner from SCANNER_LLM_PROVIDER + per-provider env vars.

    Returns None when no provider is configured or required credentials are
    missing — callers should treat that as "LLM mode disabled".
    """
    provider_name = (os.getenv("SCANNER_LLM_PROVIDER") or "").strip().lower()
    if not provider_name:
        logger.info("[LLMScanner] SCANNER_LLM_PROVIDER not set; LLM mode disabled")
        return None

    provider = build_provider_from_env(provider_name)
    if provider is None:
        logger.warning(f"[LLMScanner] could not build provider '{provider_name}'; LLM mode disabled")
        return None
    return LLMScanner(provider)
