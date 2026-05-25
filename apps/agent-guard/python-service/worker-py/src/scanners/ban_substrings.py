"""BanSubstrings — pure-Python local scanner.

Mirrors llm_guard's BanSubstrings behaviour for the options Akto uses:
  - substrings: list[str]            (required)
  - match_type: "str" | "word"      (default "str")
  - case_sensitive: bool             (default False)
  - contains_all: bool               (default False — flag if ANY match;
                                       True — flag only if ALL present)
  - redact: bool                     (default False — replace matches in output)
"""

import re
from typing import Any, Dict, List

REDACTED = "[REDACTED]"


def _matches(text: str, sub: str, match_type: str, case_sensitive: bool) -> bool:
    hay = text if case_sensitive else text.lower()
    needle = sub if case_sensitive else sub.lower()
    if match_type == "word":
        return re.search(rf"\b{re.escape(needle)}\b", hay) is not None
    return needle in hay


def _redact(text: str, subs: List[str], case_sensitive: bool) -> str:
    flags = 0 if case_sensitive else re.IGNORECASE
    out = text
    for sub in subs:
        out = re.sub(re.escape(sub), REDACTED, out, flags=flags)
    return out


def scan(scanner_type: str, text: str, config: Dict[str, Any]) -> Dict[str, Any]:
    substrings: List[str] = list(config.get("substrings") or [])
    match_type = config.get("match_type", "str")
    case_sensitive = bool(config.get("case_sensitive", False))
    contains_all = bool(config.get("contains_all", False))
    redact = bool(config.get("redact", False))

    if not substrings:
        # Nothing to ban — pass through.
        return {
            "is_valid": True,
            "risk_score": 0.0,
            "sanitized_text": text,
            "details": {"scanner_type": scanner_type, "matched": []},
        }

    matched = [s for s in substrings if _matches(text, s, match_type, case_sensitive)]
    flagged = (len(matched) == len(substrings)) if contains_all else bool(matched)

    sanitized = _redact(text, matched, case_sensitive) if (redact and matched) else text
    return {
        "is_valid": not flagged,
        "risk_score": 1.0 if flagged else 0.0,
        "sanitized_text": sanitized,
        "details": {"scanner_type": scanner_type, "matched": matched},
    }
