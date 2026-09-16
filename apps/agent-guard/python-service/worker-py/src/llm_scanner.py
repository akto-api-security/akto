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

from prompts import build_scan_prompt, resolve_response_format
from providers import LLMProvider, Qwen3GuardOutput, parse_qwen3guard_result

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


_ABCD_VERDICTS = {
    "A": (False, 0.02, 0.95),
    "B": (False, 0.35, 0.50),
    "C": (True, 0.70, 0.70),
    "D": (True, 0.95, 0.95),
}

_ABCD_REASONS = {
    "A": "",
    "B": "",
    "C": "",
    "D": "",
}


def parse_abcd_result(scanner_name: str, raw: str) -> dict[str, Any]:
    """Read the single-letter verdict. Anything else raises, never guesses.

    A raise is the right failure mode: model_map._collect_majority counts a
    scanner exception as unsafe, so a model that ignores the one-character
    contract escalates to the arbiter instead of being read as "safe".
    """
    cleaned = (raw or "").strip().strip("*_`'\"([{)]}<> \t\r\n.,:;!")
    if len(cleaned) == 1 and cleaned.upper() in _ABCD_VERDICTS:
        letter = cleaned.upper()
        flagged, risk, confidence = _ABCD_VERDICTS[letter]
        details: dict[str, Any] = {
            "letter": letter,
            "response_format": "abcd",
            "reason": _ABCD_REASONS[letter],
        }
        return {
            "is_valid": not flagged,
            "risk_score": risk,
            "decision_confidence": confidence,
            "details": details,
        }
    raise ValueError(f"expected one of A/B/C/D for {scanner_name}, got {(raw or '').strip()[:60]!r}")


# ── Values-only verdict (Password) ───────────────────────────────────────────


def parse_values_result(scanner_name: str, raw: str) -> dict[str, Any]:
    """Read the values-only contract: the secret substrings and nothing else.

    isPassword and riskScore are derived rather than asked for — a verdict is
    "flagged" exactly when it names at least one value, so sending those fields
    only gave the model a way to contradict itself.

    The reason is emitted empty and filled asynchronously. The JSON contract
    required one that quoted every value verbatim, which put raw credentials in
    the threat report; the values still reach it structurally through the
    gateway's piiValueSchemaErrors, where masking is applied.
    """
    parsed = json.loads(_clean_json(raw))
    raw_values = parsed.get("values")
    if raw_values is None:
        raise ValueError(f"no 'values' key in {scanner_name} response: {raw[:120]!r}")
    if not isinstance(raw_values, list):
        raise ValueError(f"'values' is not a list in {scanner_name} response: {type(raw_values).__name__}")

    values = [v for v in raw_values if isinstance(v, str) and v]
    flagged = bool(values)
    details: dict[str, Any] = {"response_format": "values", "reason": ""}
    if flagged:
        details["values"] = values
    return {
        "is_valid": not flagged,
        "risk_score": 0.95 if flagged else 0.02,
        "decision_confidence": 0.95,
        "details": details,
    }


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

    if scanner_name == "Toxicity":
        matched = parsed.get("matchedCategory", "")
        if matched:
            details["matchedCategory"] = matched

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


# Compact answer contract -> its parser. Anything not listed (including "") is
# the JSON verdict. Keep the keys in step with prompts._FORMAT_CAPABLE: a format
# with a template but no parser here would be rendered and then misread.
_FORMAT_PARSERS = {
    "abcd": parse_abcd_result,
    "values": parse_values_result,
}


class LLMScanner:
    """Evaluates one of LLM_SUPPORTED_SCANNERS against a single provider.

    response_format is that model's ModelConfig.responseFormat: "abcd" asks for
    the single-letter contract, "values" for Password's, anything else (the
    default) for JSON. It stays per-model so a cascade can mix contracts, but
    SCANNER_RESPONSE_FORMAT applies to every role including FINAL_ARBITER: a
    compact verdict then reaches the threat report with a derived risk_score and
    NO reason at all, on the understanding that the missing metadata is
    regenerated asynchronously rather than on the blocking path.
    """

    def __init__(self, provider: LLMProvider, response_format: str = ""):
        self.provider = provider
        self.response_format = (response_format or "").strip().lower()

    async def scan(self, scanner_name: str, scanner_type: str, text: str, config: dict[str, Any]) -> dict[str, Any]:
        if scanner_name not in LLM_SUPPORTED_SCANNERS:
            raise ValueError(f"Scanner {scanner_name} not supported by LLM path")

        start = time.time()
        if isinstance(self.provider, Qwen3GuardOutput):
            raw, logprobs = await self.provider.complete_with_logprobs(text)
            result = parse_qwen3guard_result(scanner_name, raw, logprobs)
        else:
            prompt = build_scan_prompt(
                scanner_name,
                scanner_type,
                config,
                text,
                provider_name=self.provider.name,
                response_format=self.response_format,
            )
            if prompt is None:
                raise ValueError(f"Scanner {scanner_name} not supported by LLM path")
            raw = await self.provider.complete(prompt)
            effective = resolve_response_format(scanner_name, scanner_type, self.response_format)
            result = _FORMAT_PARSERS.get(effective, parse_llm_result)(scanner_name, raw)

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
