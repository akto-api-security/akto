"""Per-scanner prompt templates and the unified build_scan_prompt dispatcher."""

from typing import Any

from . import ban_topics, code, gibberish, password, prompt_injection, toxicity


def build_scan_prompt(
    scanner_name: str,
    scanner_type: str,
    config: dict[str, Any],
    text: str,
    provider_name: str = "",
) -> str | None:
    if scanner_name == "PromptInjection":
        return prompt_injection.build(scanner_type, text)
    if scanner_name == "BanTopics":
        return ban_topics.build(config, provider_name, text)
    if scanner_name == "Toxicity":
        return toxicity.build(text)
    if scanner_name == "Gibberish":
        return gibberish.build(text)
    if scanner_name == "BanCode":
        return code.build(text)
    if scanner_name == "Password":
        return password.build(text)
    return None


__all__ = ["build_scan_prompt"]
