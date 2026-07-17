"""LLM-backed scanner driver (async port).

Public API:
    - scan_with_model_map(...)  -> dict   (multi-provider cascade)
    - LLM_SUPPORTED_SCANNERS    -> set[str]
    - LLMScanner                (one scanner = one provider + result parser)
"""

import json
import logging
import time
from typing import Any

from prompts import build_scan_prompt
from providers import LLMProvider, Qwen3GuardProvider, parse_qwen3guard_result

logger = logging.getLogger(__name__)

LLM_SUPPORTED_SCANNERS = {"PromptInjection", "BanTopics", "Toxicity", "Gibberish", "BanCode", "Password"}

_SCANNER_FLAG_KEYS = {
    "PromptInjection": "isInjection",
    "BanTopics": "isBanned",
    "Toxicity": "isToxic",
    "Gibberish": "isGibberish",
    "BanCode": "isCode",
    "Password": "isPassword",
}


def _clean_json(raw: str) -> str:
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


def parse_llm_result(scanner_name: str, raw: str) -> dict[str, Any]:
    parsed = json.loads(_clean_json(raw))

    details: dict[str, Any] = {}
    reason = parsed.get("reason", "")
    if reason:
        details["reason"] = reason

    flag_key = _SCANNER_FLAG_KEYS.get(scanner_name)
    if flag_key is None:
        return {"is_valid": True, "risk_score": 0.0, "details": details}

    flagged = bool(parsed.get(flag_key, False))
    # Password uses "riskScore"; the other scanners use "confidence".
    confidence = float(parsed.get("confidence", parsed.get("riskScore", 0.0)))

    if scanner_name == "BanTopics":
        matched = parsed.get("matchedTopic", "")
        if matched:
            details["matchedTopic"] = matched

    if scanner_name == "Password":
        # Exact secret substrings for the caller to redact.
        values = parsed.get("values") or []
        if values:
            details["values"] = values

    return {
        "is_valid": not flagged,
        "risk_score": confidence,
        "decision_confidence": confidence if flagged else (1.0 - confidence),
        "details": details,
    }


class LLMScanner:
    """Evaluates one of LLM_SUPPORTED_SCANNERS against a single provider."""

    def __init__(self, provider: LLMProvider):
        self.provider = provider

    async def scan(self, scanner_name: str, scanner_type: str, text: str, config: dict[str, Any]) -> dict[str, Any]:
        if scanner_name not in LLM_SUPPORTED_SCANNERS:
            raise ValueError(f"Scanner {scanner_name} not supported by LLM path")

        start = time.time()
        if isinstance(self.provider, Qwen3GuardProvider):
            raw, logprobs = await self.provider.complete_with_logprobs(text)
            result = parse_qwen3guard_result(scanner_name, raw, logprobs)
        else:
            prompt = build_scan_prompt(scanner_name, scanner_type, config, text, provider_name=self.provider.name)
            if prompt is None:
                raise ValueError(f"Scanner {scanner_name} not supported by LLM path")
            raw = await self.provider.complete(prompt)
            result = parse_llm_result(scanner_name, raw)

        elapsed_ms = (time.time() - start) * 1000
        result["details"]["llm_provider"] = self.provider.name
        result["details"]["scanner_type"] = scanner_type
        result["execution_time_ms"] = round(elapsed_ms, 2)
        logger.info(
            f"[LLMScanner] {scanner_name} provider={self.provider.name} "
            f"isValid={result['is_valid']} risk={result['risk_score']:.2f} ms={elapsed_ms:.0f}"
        )
        return result


async def scan_with_model_map(
    scanner_name: str,
    scanner_type: str,
    text: str,
    config: dict[str, Any],
    store_fn: Any | None = None,
) -> dict[str, Any]:
    from model_map import ModelMapScanner

    return await ModelMapScanner(scanner_name, scanner_type, text, config, store_fn).run()
