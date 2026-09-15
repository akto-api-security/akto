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


# ── Single-letter (ABCD) verdict — any scanner with a letter template ────────

# letter -> (flagged, risk_score, decision_confidence).
#
# risk_score is kept on the SAME 0-1 scale the JSON prompt produces, using the
# confidence bands that prompt already calibrates (A <=0.09, B 0.10-0.49,
# C 0.50-0.89, D >=0.90), so a configured FilterRuleConfig.Threshold behaves the
# same whichever format the model answers in — only the granularity changes.
#
# decision_confidence is what model_map._classify compares against
# safeDecisionThreshold (default 0.8) to decide whether a fast tier may settle
# the call. B is deliberately below that line: "safe but unsure" is the one
# letter that must escalate to the arbiter rather than allow.
_ABCD_VERDICTS = {
    "A": (False, 0.02, 0.95),
    "B": (False, 0.35, 0.50),
    "C": (True, 0.70, 0.70),
    "D": (True, 0.95, 0.95),
}

# The letter contract carries no reason string, but details.reason feeds the
# threat report, the remediation prompt's BLOCK REASON and the evidence-line
# prompt. These name the scanner and what the letter means, and nothing more —
# inventing a per-sample explanation here would put words in the model's mouth.
# Safe letters get no reason: an allowed scan is never reported.
_ABCD_REASONS = {
    "A": "",
    "B": "",
    "C": "{scanner}: flagged by the single-letter scanner (C — low confidence).",
    "D": "{scanner}: flagged by the single-letter scanner (D — high confidence).",
}


def parse_abcd_result(scanner_name: str, raw: str) -> dict[str, Any]:
    """Read the single-letter verdict. Anything else raises, never guesses.

    A raise is the right failure mode: model_map._collect_majority counts a
    scanner exception as unsafe, so a model that ignores the one-character
    contract escalates to the arbiter instead of being read as "safe".
    """
    # The whole answer must BE the letter, modulo the wrappers small models put
    # around it ("**D**", "`D`", "D."). Reading the first letter out of a longer
    # string instead would let prose decide the verdict: "An injection attempt
    # was detected" begins with A and would be served as a confident ALLOW.
    # Prose means the model ignored the contract, so raise and let the arbiter
    # answer — that costs one escalation, where a wrong letter costs a miss.
    cleaned = (raw or "").strip().strip("*_`'\"([{)]}<> \t\r\n.,:;!")
    if len(cleaned) == 1 and cleaned.upper() in _ABCD_VERDICTS:
        letter = cleaned.upper()
        flagged, risk, confidence = _ABCD_VERDICTS[letter]
        details: dict[str, Any] = {"letter": letter, "response_format": "abcd"}
        if _ABCD_REASONS[letter]:
            details["reason"] = _ABCD_REASONS[letter].format(scanner=scanner_name)
        return {
            "is_valid": not flagged,
            "risk_score": risk,
            "decision_confidence": confidence,
            "details": details,
        }
    raise ValueError(f"expected one of A/B/C/D for {scanner_name}, got {(raw or '').strip()[:60]!r}")


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


class LLMScanner:
    """Evaluates one of LLM_SUPPORTED_SCANNERS against a single provider.

    response_format is that model's ModelConfig.responseFormat: "abcd" asks for
    the single-letter contract, anything else (the default) for JSON. It is
    per-model on purpose — a fast tier can answer in letters while the arbiter
    stays on JSON and keeps producing the reason string the threat report needs.
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
            # Parse whatever build_scan_prompt actually rendered: a scanner with
            # no letter template, or an output-side scan, was sent the JSON
            # template no matter what this model's responseFormat asked for.
            if resolve_response_format(scanner_name, scanner_type, self.response_format) == "abcd":
                result = parse_abcd_result(scanner_name, raw)
            else:
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
