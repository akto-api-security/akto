"""LLM-backed scanner driver (async port).

Public API:
    - scan_with_model_map(...)  -> dict   (multi-provider cascade)
    - LLM_SUPPORTED_SCANNERS    -> set[str]
    - LLMScanner                (one scanner = one provider + result parser)
"""

import json
import logging
import time
from typing import Any, Dict, Optional

from prompts import build_scan_prompt
from providers import LLMProvider, Qwen3GuardProvider, parse_qwen3guard_result

logger = logging.getLogger(__name__)

LLM_SUPPORTED_SCANNERS = {"PromptInjection", "BanTopics", "Toxicity", "Gibberish", "BanCode"}

_SCANNER_FLAG_KEYS = {
    "PromptInjection": "isInjection",
    "BanTopics": "isBanned",
    "Toxicity": "isToxic",
    "Gibberish": "isGibberish",
    "BanCode": "isCode",
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


def parse_llm_result(scanner_name: str, raw: str, config: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
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

    # Toxicity/Gibberish/BanCode's confidence-threshold sliders (the latter backs both
    # "Ban code detection" and "Code Detection Level" — both build a BanCode FilterRule,
    # just with a different Threshold source) are implemented as a required confidence
    # bar rather than trusting the model's own boolean — confidence is already a
    # calibrated p(flagged=true) per the prompt contract, so a lower threshold means
    # "flag on weaker signal" (stricter); matches the dashboard's own "higher = more
    # permissive, lower = stricter" copy. Scoped to these scanners only: PromptInjection
    # and BanTopics keep trusting their own boolean.
    if scanner_name in ("Toxicity", "Gibberish", "BanCode"):
        threshold = (config or {}).get("threshold")
        if threshold is not None:
            flagged = confidence >= float(threshold)
            category = (config or {}).get("category")
            if category:
                details["category"] = category

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

    async def scan(self, scanner_name: str, scanner_type: str, text: str,
                   config: Dict[str, Any]) -> Dict[str, Any]:
        if scanner_name not in LLM_SUPPORTED_SCANNERS:
            raise ValueError(f"Scanner {scanner_name} not supported by LLM path")

        start = time.time()
        if isinstance(self.provider, Qwen3GuardProvider):
            raw, logprobs = await self.provider.complete_with_logprobs(text)
            result = parse_qwen3guard_result(scanner_name, raw, logprobs)
        else:
            prompt = build_scan_prompt(scanner_name, scanner_type, config, text,
                                       provider_name=self.provider.name)
            if prompt is None:
                raise ValueError(f"Scanner {scanner_name} not supported by LLM path")
            raw = await self.provider.complete(prompt)
            result = parse_llm_result(scanner_name, raw, config)

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
    scanner_name: str, scanner_type: str, text: str,
    config: Dict[str, Any], store_fn: Optional[Any] = None,
) -> Dict[str, Any]:
    from model_map import ModelMapScanner
    return await ModelMapScanner(scanner_name, scanner_type, text, config, store_fn).run()
