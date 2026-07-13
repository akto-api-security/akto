"""Secrets — detect-secrets, mirroring llm_guard's Secrets scanner.

Faithful to llm_guard: same curated `plugins_used` config and the full
`SecretsCollection.scan_file` pipeline — which applies detect-secrets' default
false-positive filters. (scan_line alone bypasses those filters and the
high-entropy detectors then flag ordinary prose, so we must scan a file.)

Coverage note vs llm_guard: the ~50 custom-regex plugins llm_guard loads from
bundled files (niche provider tokens) are omitted — they need on-disk plugin
files. The built-in detectors below cover the common, high-impact secret types.
"""

import os
import re
import tempfile
from typing import Any

REDACT_MASK = "******"

_PLUGINS = [
    {"name": "SoftlayerDetector"},
    {"name": "StripeDetector"},
    {"name": "NpmDetector"},
    {"name": "IbmCosHmacDetector"},
    {"name": "DiscordBotTokenDetector"},
    {"name": "BasicAuthDetector"},
    {"name": "AzureStorageKeyDetector"},
    {"name": "ArtifactoryDetector"},
    {"name": "AWSKeyDetector"},
    {"name": "CloudantDetector"},
    {"name": "IbmCloudIamDetector"},
    {"name": "JwtTokenDetector"},
    {"name": "MailchimpDetector"},
    {"name": "SquareOAuthDetector"},
    {"name": "PrivateKeyDetector"},
    {"name": "TwilioKeyDetector"},
    # Modern prefix-anchored detectors built into detect-secrets but absent from
    # llm_guard's default list. All regex/prefix based (low false-positive), so
    # safe to add and they close real coverage gaps (GitHub/GitLab/OpenAI/etc).
    {"name": "GitHubTokenDetector"},
    {"name": "GitLabTokenDetector"},
    {"name": "OpenAIDetector"},
    {"name": "SlackDetector"},
    {"name": "SendGridDetector"},
    {"name": "PypiTokenDetector"},
    {"name": "TelegramBotTokenDetector"},
    {"name": "Base64HighEntropyString", "limit": 4.5},
    {"name": "HexHighEntropyString", "limit": 3.0},
]


def _redact(text: str, values: list[str]) -> str:
    """Mask each detected secret.

    detect-secrets returns only the captured group for regex detectors with a
    group (e.g. GitHubTokenDetector yields "ghp", not the full token), so we
    mask the entire non-whitespace token containing each hit — never leave part
    of a secret visible. Longest values first so short hits don't pre-empt them.
    """
    out = text
    for v in sorted(set(values), key=len, reverse=True):
        if not v:
            continue
        out = re.sub(r"\S*" + re.escape(v) + r"\S*", REDACT_MASK, out)
    return out


def scan(scanner_type: str, text: str, config: dict[str, Any]) -> dict[str, Any]:
    from detect_secrets.core.secrets_collection import SecretsCollection
    from detect_secrets.settings import transient_settings

    if not text.strip():
        return {
            "is_valid": True,
            "risk_score": 0.0,
            "sanitized_text": text,
            "details": {"scanner_type": scanner_type, "types": [], "secrets": []},
        }

    secrets = SecretsCollection()
    tmp = tempfile.NamedTemporaryFile(delete=False)
    try:
        tmp.write(text.encode("utf-8"))
        tmp.close()
        with transient_settings({"plugins_used": _PLUGINS}):
            secrets.scan_file(tmp.name)

        hits = []  # (type, line, value)
        for f in secrets.files:
            for s in secrets[f]:
                if s.secret_value is None:
                    continue
                hits.append((s.type, s.line_number, s.secret_value))
    finally:
        os.remove(tmp.name)

    flagged = bool(hits)
    sanitized = _redact(text, [v for _t, _ln, v in hits]) if flagged else text
    return {
        "is_valid": not flagged,
        "risk_score": 1.0 if flagged else 0.0,
        "sanitized_text": sanitized,
        "details": {
            "scanner_type": scanner_type,
            "types": sorted({t for t, _ln, _v in hits}),
            "secrets": [{"type": t, "line": ln} for t, ln, _v in hits],
        },
    }
