"""Derive a single-letter (ABCD) template from a JSON-verdict one.

The letter variants are GENERATED from the JSON templates rather than written
out again, so the detection rules live in exactly one place: editing a scanner's
rules or few-shot inputs updates both contracts, and the two cannot drift.

Two mechanical changes are applied:

1. Each few-shot `Output: {json}` collapses to the letter its confidence already
   implied, using the bands the JSON prompts calibrate against:
   A <=0.09, B 0.10-0.49, C 0.50-0.89, D >=0.90.
2. The trailing JSON-schema block becomes the four-letter contract.

PromptInjection is deliberately NOT built this way — its INPUT_ABCD is a
hand-tuned artifact copied from the ms-langchain evals and measured as-is, so it
stays a literal. Everything else derives from here until someone measures it.
"""

import json
import logging

logger = logging.getLogger(__name__)

_JSON_TAIL_MARKER = "Analyze this text and respond ONLY with valid JSON:"
_TEXT_SUFFIX = "\n\nText to analyze:\n%s"

# p(flag is true) -> letter. Same bands as prompts/prompt_injection.py INPUT_ABCD.
_BANDS = ((0.09, "A"), (0.49, "B"), (0.89, "C"), (1.0, "D"))

_CONTRACT = """Analyze {subject} and respond with ONE character and nothing else — no JSON, no punctuation, no explanation, no reason:

A = {safe}, confident. Nothing in the input matches. Use A even when the input merely MENTIONS {near_miss}.
B = {safe}, not confident. You lean {safe_short} but something is off — an unusual framing you cannot place, or an input that would read as a match with slightly different wording.
C = {flag}, not confident. A match is present but ambiguous, borderline, or wrapped so that you cannot be certain.
D = {flag}, confident. An explicit, unambiguous match.

A and D are the normal answers; reach for B or C only when you genuinely cannot commit. If an explanation would have hedged ("likely", "appears to", "seems"), you have recognised a match: answer C or D, never B. NEVER invert: {safe_short} is A or B, a match is C or D."""


def _letter_for(confidence: float) -> str:
    for upper, letter in _BANDS:
        if confidence <= upper:
            return letter
    return "D"


def _convert_examples(text: str, flag_key: str, label: str) -> str:
    """Collapse every few-shot `Output: {json}` line to its letter."""
    out = []
    for line in text.splitlines(keepends=True):
        stripped = line.strip()
        if not stripped.startswith("Output: {"):
            out.append(line)
            continue
        try:
            parsed = json.loads(stripped[len("Output: ") :])
            confidence = float(parsed.get("confidence", parsed.get("riskScore", 0.0)))
            flagged = bool(parsed.get(flag_key, False))
        except (ValueError, TypeError) as exc:
            # A few-shot we cannot read must not be silently dropped or guessed.
            raise ValueError(f"{label}: unparseable few-shot output {stripped[:80]!r}") from exc
        letter = _letter_for(confidence)
        if (letter in ("C", "D")) != flagged:
            # The example's own flag and confidence disagree. Trust the flag —
            # a letter on the wrong side of safe/flagged teaches the inverse of
            # what the example means. Tests assert this never fires in shipped
            # templates; if it does, the JSON template's calibration drifted.
            logger.warning(
                f"{label}: few-shot has {flag_key}={flagged} but confidence={confidence} "
                f"(letter {letter}); using the flag"
            )
            letter = "D" if flagged else "A"
        out.append(line.replace(stripped, f"Output: {letter}", 1))
    return "".join(out)


def to_abcd(
    template: str,
    flag_key: str,
    safe: str,
    flag: str,
    near_miss: str,
    label: str,
    marker: str = _JSON_TAIL_MARKER,
    suffix: str = _TEXT_SUFFIX,
    subject: str = "this text",
) -> str:
    """Rewrite one JSON-verdict template into the single-letter contract.

    safe/flag are the verdict names shown to the model (e.g. "NOT TOXIC" /
    "TOXIC"); near_miss names the attack-adjacent material that must still
    answer A, which is the false-positive lever for that scanner. marker/suffix
    override the tail anchors for templates that phrase them differently, such as
    the output-side ones that analyse an AI response rather than user text.
    """
    if not template.endswith(suffix):
        raise ValueError(f"{label}: template does not end with {suffix!r}")
    if marker not in template:
        raise ValueError(f"{label}: template has no JSON tail marker to replace")

    head = template[: template.rindex(marker)]
    head = _convert_examples(head, flag_key, label)
    contract = _CONTRACT.format(
        subject=subject,
        safe=safe,
        flag=flag,
        safe_short=safe.lower(),
        near_miss=near_miss,
    )
    return head + contract + suffix


def convert_examples(text: str, flag_key: str, label: str) -> str:
    """Public wrapper for example blocks injected into a template at build time
    (e.g. toxicity's severity-gated few-shots), which carry no JSON tail."""
    return _convert_examples(text, flag_key, label)
