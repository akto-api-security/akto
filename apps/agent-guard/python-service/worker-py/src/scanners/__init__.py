"""Local (non-cascade) scanners that run entirely inside the Worker.

Each scanner exposes `scan(scanner_type, text, config) -> dict` returning the
ScanResponse-shaped fields: is_valid, risk_score, sanitized_text, details.
"""

from typing import Any

from . import ban_code, ban_substrings, secrets, token_limit


def scan_local(scanner_name: str, scanner_type: str, text: str, config: dict[str, Any]) -> dict[str, Any]:
    if scanner_name == "BanCode":
        return ban_code.scan(scanner_type, text, config)
    if scanner_name == "BanSubstrings":
        return ban_substrings.scan(scanner_type, text, config)
    if scanner_name == "Secrets":
        return secrets.scan(scanner_type, text, config)
    if scanner_name == "TokenLimit":
        return token_limit.scan(scanner_type, text, config)
    raise ValueError(f"local scanner not yet implemented: {scanner_name}")
