"""Per-scanner prompt templates and the unified build_scan_prompt dispatcher."""

from typing import Any, Dict, Optional

from . import ban_topics, code, gibberish, prompt_injection, toxicity


def build_scan_prompt(
    scanner_name: str,
    scanner_type: str,
    config: Dict[str, Any],
    text: str,
    provider_name: str = "",
) -> Optional[str]:
    if scanner_name == "PromptInjection":
        return prompt_injection.build(scanner_type, text)
    if scanner_name == "BanTopics":
        return ban_topics.build(config, provider_name, text)
    if scanner_name == "Toxicity":
        return toxicity.build(config, text)
    if scanner_name == "Gibberish":
        return gibberish.build(text)
    if scanner_name == "BanCode":
        return code.build(text)
    return None


__all__ = ["build_scan_prompt"]
