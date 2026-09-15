"""Per-scanner prompt templates and the unified build_scan_prompt dispatcher."""

from typing import Any

from . import ban_topics, code, gibberish, password, prompt_injection, toxicity

# Scanners that have a single-letter (ABCD) template as well as the JSON one.
# This is the ONE switch: adding a scanner here makes responseFormat="abcd" and
# SCANNER_RESPONSE_FORMAT=abcd take effect for it, for both the prompt and the
# parser, because llm_scanner picks its parser from resolve_response_format too.
# A scanner absent from this set silently stays on JSON rather than being asked
# for a letter it has no rules for.
_ABCD_CAPABLE = {"PromptInjection"}


def resolve_response_format(scanner_name: str, scanner_type: str, requested: str = "") -> str:
    """The answer contract that will ACTUALLY be used for this scan.

    Returns "abcd" only when the caller asked for it AND this scanner has an
    input-side letter template; otherwise "" (the JSON verdict). Both the prompt
    builder and the result parser resolve through here, so they cannot disagree
    about which contract is in force — a mismatch would parse a JSON body as a
    letter, or read prose as a verdict.
    """
    if (requested or "").strip().lower() != "abcd":
        return ""
    # The ABCD variants are input-side only; output scans keep the JSON template
    # so responses are never judged with input-side rules.
    if scanner_type == "output":
        return ""
    return "abcd" if scanner_name in _ABCD_CAPABLE else ""


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
        return ban_topics.build(config, provider_name, text)
    if scanner_name == "Toxicity":
        return toxicity.build(config, text)
    if scanner_name == "Gibberish":
        return gibberish.build(text)
    if scanner_name == "BanCode":
        return code.build(text)
    if scanner_name == "Password":
        return password.build(text)
    return None


__all__ = ["build_scan_prompt", "resolve_response_format"]
