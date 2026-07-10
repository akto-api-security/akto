"""BanCode — heuristic code detection, pure-Python local scanner.

llm_guard's BanCode uses a transformer model (vishnun/codenlbert-*) to decide
whether text contains source code. That model can't run under Pyodide, so this
Worker scanner approximates it with a weighted regex signal set: each category
of code-ish syntax that appears contributes to a 0..1 score, capped at 1.0, and
the text is flagged when the score reaches `threshold`. No model download, no
network — runs entirely in the isolate like the other local scanners.

Config:
  - threshold: float    (default 0.5 — flag when score >= threshold; matches the
                         value used across agent-guard's BanCode examples)

Like llm_guard's BanCode, this never rewrites the text — it only flags. A flagged
result is is_valid=False with risk_score = the computed score.
"""

import re
from typing import Any

# (name, compiled pattern, weight). Each category contributes its weight at most
# once. Strong signals are near-unambiguous code markers; weak signals are common
# in prose too, so a lone weak hit must not cross the default threshold.
_SIGNALS: list[tuple[str, "re.Pattern", float]] = [
    # ── strong (≈unambiguous) ──────────────────────────────────────────────
    ("fenced_block", re.compile(r"```"), 0.5),
    ("function_def", re.compile(r"\b(?:def|function|func|fn)\s+\w+\s*\("), 0.5),
    ("type_def", re.compile(r"\b(?:class|struct|interface|enum)\s+\w+"), 0.5),
    ("import", re.compile(r"(?m)^\s*(?:import|from|#include|require|using|package)\b"), 0.5),
    ("shell", re.compile(r"(?:#!/|\bos\.system\b|\bsubprocess\b|\brm\s+-rf\b|\bsudo\b|\bchmod\b|\bapt-get\b)"), 0.5),
    (
        "sql",
        re.compile(
            r"\b(?:SELECT\s+.+\s+FROM|INSERT\s+INTO|UPDATE\s+\w+\s+SET|DELETE\s+FROM|"
            r"CREATE\s+TABLE|DROP\s+TABLE)\b",
            re.IGNORECASE,
        ),
        0.5,
    ),
    ("markup_tag", re.compile(r"</?[a-zA-Z][\w-]*(?:\s[^<>]*)?>"), 0.5),
    # ── medium ─────────────────────────────────────────────────────────────
    (
        "control_flow",
        re.compile(
            r"\b(?:if|elif|else|for|while|switch|case|try|catch|except|finally|return|"
            r"throw|raise|yield|await|async)\b"
        ),
        0.3,
    ),
    (
        "declaration",
        re.compile(
            r"\b(?:public|private|protected|static|void|const|let|var|int|float|double|"
            r"char|bool|boolean|string|String)\b"
        ),
        0.3,
    ),
    ("arrow_scope", re.compile(r"=>|->|::"), 0.3),
    (
        "emit",
        re.compile(
            r"(?:\bprint\s*\(|console\.log\s*\(|printf\s*\(|System\.out\.print|"
            r"\becho\b|fmt\.Print)"
        ),
        0.3,
    ),
    # ── weak (also common in prose; need company to matter) ─────────────────
    ("semicolon_eol", re.compile(r"(?m);\s*$"), 0.15),
    ("braces", re.compile(r"\{[^{}]*\}|\{\s*$"), 0.15),
    ("call", re.compile(r"\b[A-Za-z_]\w*\s*\([^)]*\)"), 0.15),
    ("assignment", re.compile(r"\b[A-Za-z_]\w*\s*(?:[-+*/|&]|<<|>>)?=\s*\S"), 0.15),
    ("indexing", re.compile(r"\b[A-Za-z_]\w*\[[^\]]+\]"), 0.15),
    ("comment", re.compile(r"(?m)//|/\*|\*/|^\s*#\s|--\s")),  # weight set below
]
# `comment` carries a weak weight; declared inline to keep the tuple readable.
_SIGNALS[-1] = ("comment", _SIGNALS[-1][1], 0.15)


def scan(scanner_type: str, text: str, config: dict[str, Any]) -> dict[str, Any]:
    threshold = float(config.get("threshold", 0.5))

    matched: list[str] = []
    score = 0.0
    if text.strip():
        for name, pattern, weight in _SIGNALS:
            if pattern.search(text):
                matched.append(name)
                score += weight
    score = round(min(score, 1.0), 3)

    flagged = score >= threshold
    return {
        "is_valid": not flagged,
        "risk_score": score,
        "sanitized_text": text,
        "details": {
            "scanner_type": scanner_type,
            "threshold": threshold,
            "matched_signals": matched,
        },
    }
