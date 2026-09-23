"""Per-scanner prompt templates and the unified build_scan_prompt dispatcher."""

from typing import Any

from . import ban_topics, code, gibberish, password, prompt_injection, toxicity

_FORMAT_CAPABLE: dict[str, frozenset[str]] = {
    "PromptInjection": frozenset({"abcd"}),
    "Toxicity": frozenset({"abcd"}),
    "BanTopics": frozenset({"abcd"}),
    "Gibberish": frozenset({"abcd"}),
    "BanCode": frozenset({"abcd"}),
    "Password": frozenset({"values"}),
}

# Convenience view for callers and tests that care specifically about letters.
_ABCD_CAPABLE = frozenset(name for name, formats in _FORMAT_CAPABLE.items() if "abcd" in formats)


def known_formats() -> frozenset[str]:
    """Every compact answer contract some scanner has a template for."""
    return frozenset().union(*_FORMAT_CAPABLE.values())


def resolve_response_format(scanner_name: str, scanner_type: str, requested: str = "") -> str:
    """The answer contract that will ACTUALLY be used for this scan.

    `requested` may name several formats, comma-separated ("abcd,values"): one
    deployment-wide setting can then ask every scanner for its own compact
    contract, and each scanner takes the first one it actually supports. Returns
    "" (the JSON verdict) when nothing matches.

    Both the prompt builder and the result parser resolve through here, so they
    cannot disagree about which contract is in force — a mismatch would parse a
    JSON body as a letter, or read a letter as JSON.

    Applies to both scan sides. Each side keeps its own rules — PromptInjection
    has a separate output-side template, and the scanners that share one template
    were already side-agnostic — so only the answer contract is shared.
    """
    supported = _FORMAT_CAPABLE.get(scanner_name, frozenset())
    for candidate in (requested or "").strip().lower().split(","):
        candidate = candidate.strip()
        if candidate and candidate in supported:
            return candidate
    return ""


def build_scan_prompt(
    scanner_name: str,
    scanner_type: str,
    config: dict[str, Any],
    text: str,
    provider_name: str = "",
    response_format: str = "",
) -> str | None:
    """Render one scanner's prompt.

    response_format is the per-model ModelConfig.responseFormat (""/"json" ->
    JSON verdict, "abcd" -> single letter). It is honoured only for scanners in
    _ABCD_CAPABLE; every other scanner answers JSON regardless of what was asked.
    """
    effective = resolve_response_format(scanner_name, scanner_type, response_format)
    if scanner_name == "PromptInjection":
        return prompt_injection.build(scanner_type, text, effective)
    if scanner_name == "BanTopics":
        return ban_topics.build(config, provider_name, text, effective)
    if scanner_name == "Toxicity":
        return toxicity.build(config, text, effective)
    if scanner_name == "Gibberish":
        return gibberish.build(text, effective)
    if scanner_name == "BanCode":
        return code.build(text, effective)
    if scanner_name == "Password":
        return password.build(text, effective)
    return None


REASON_ONLY_TEMPLATE = """You are a security analyst explaining a guardrail decision that has already been made — you are not deciding anything.

ATTACK TYPE: %s

TEXT THAT WAS FLAGGED:
%s

In one or two sentences, explain specifically what in the text matches this attack type. Describe the actual trigger, don't just restate the attack type name.

Respond ONLY with valid JSON in exactly this shape, nothing else:
{"reason": "your explanation here"}"""


def build_reason_prompt(attack_type: str, text: str) -> str:
    """One shared prompt for every scanner: given a verdict that's already been made,
    ask only for the free-text explanation. No isValid/confidence field is requested,
    so none is generated — cheaper than reusing a per-scanner detection prompt, and the
    same shape regardless of which scanner flagged the text."""
    return REASON_ONLY_TEMPLATE % (attack_type, text)


__all__ = ["build_scan_prompt", "build_reason_prompt", "known_formats", "resolve_response_format"]
