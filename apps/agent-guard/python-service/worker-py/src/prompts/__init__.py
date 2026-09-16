"""Per-scanner prompt templates and the unified build_scan_prompt dispatcher."""

from typing import Any

from . import ban_topics, code, gibberish, password, prompt_injection, toxicity

# Which compact answer contracts each scanner has a template for. This is the ONE
# switch: listing a format here makes responseFormat=<fmt> and
# SCANNER_RESPONSE_FORMAT=<fmt> take effect for that scanner, for both the prompt
# and the parser, because llm_scanner picks its parser from
# resolve_response_format too. A scanner that does not list a format silently
# stays on JSON rather than being asked for an answer it has no rules for.
#
# "abcd"   — one letter, A/B/C/D. No reason string, 4-valued risk_score.
# "values" — Password only: the secret substrings and nothing else.
#
# Password can never be "abcd": its verdict must carry the exact substrings the
# gateway masks (mcp/pii_password_llm.go reads details["values"]), and a single
# character cannot. "values" is its compact contract instead — same substrings,
# without the isPassword/riskScore/reason fields that are derivable or redundant.
#
# Both contracts drop the per-sample reason, and SCANNER_RESPONSE_FORMAT applies
# them to the FINAL_ARBITER as well as the fast tiers, so a reported block can
# carry only llm_scanner's synthesised metadata.
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
    """
    # The compact variants are input-side only; output scans keep the JSON
    # template so responses are never judged with input-side rules.
    if scanner_type == "output":
        return ""
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


__all__ = ["build_scan_prompt", "known_formats", "resolve_response_format"]
