"""Cheap, deterministic regex signals for task + risk intent.

Pure Python (Pyodide-safe), no models. Two jobs:

  attack_signal(text)  → high-precision malicious-task detector. A focused port
                         of the highest-signal patterns from the legacy
                         intent_analyzer (injection / exfiltration / auth-bypass
                         / system-override). Used to (a) fast-BLOCK obvious
                         attacks without the LLM and (b) corroborate the
                         per-agent classifier at cold start.
  risk_signal(text)    → the read / write / pii_possible axis the LLM also
                         checks, from keyword presence.

Both are intentionally conservative: regexes here should fire only on clear
signals, because a false BLOCK is user-visible. Anything uncertain is left for
the classifier + LLM cascade (ESCALATE).
"""

import re
from typing import Dict, List

# High-precision attack patterns (subset of intent_analyzer.ATTACK_PATTERNS,
# kept to the ones with very low false-positive rates on prompt text).
_ATTACK_PATTERNS: Dict[str, List[re.Pattern]] = {
    "sql_injection": [
        re.compile(r"(?i)\b(DROP|TRUNCATE)\s+TABLE\b"),
        re.compile(r"(?i)(UNION SELECT|DELETE FROM|INSERT INTO)"),
        re.compile(r"(?i)OR\s+['\"]?\d+['\"]?\s*=\s*['\"]?\d+['\"]?"),
    ],
    "command_injection": [
        re.compile(r"(?i)(\||&&|;|`|\$\()\s*(rm|cat|wget|curl|nc|bash|sh)\b"),
        re.compile(r"(?i)rm\s+-rf\s+/"),
        re.compile(r"(?i)(wget|curl)\b.*\|\s*(bash|sh)"),
    ],
    "data_exfiltration": [
        # Explicit theft/exfiltration intent — "export all records" or "get all
        # users" is a valid operation; only flag when the verb is unambiguously
        # malicious OR when credentials/secrets are being sent somewhere.
        re.compile(r"(?i)\b(steal|exfiltrate|leak)\b.*(data|records?|credentials?|password)"),
        re.compile(r"(?i)\bdump\b.*(password|credential|api.?key|secret|token|private.?key)"),
        re.compile(r"(?i)\bsend\b.*\b(password|credential|secret|api.?key|token)s?\b.*\bto\b"),
    ],
    "auth_bypass": [
        # "disable login" or "remove the security header" are legitimate admin
        # tasks; flag only explicit bypass/skip of auth flows, or disabling
        # multi-factor specifically (not just any security feature).
        re.compile(r"(?i)\b(bypass|skip|circumvent)\b.*\b(auth|authentication|login|verification|2fa|mfa)\b"),
        re.compile(r"(?i)\bdisable\b.*\b(2fa|mfa|two.?factor|multi.?factor|authenticator)\b"),
    ],
    "system_override": [
        # Classic prompt injection: "ignore/forget/override all previous instructions"
        re.compile(r"(?i)(ignore|disregard|forget|override)\b.*\b(previous|prior|above|all)?\b.*\b(instruction|rule|policy|guideline|system prompt)s?\b"),
        # Identity hijack: "you are now DAN / an unrestricted AI / a root user"
        re.compile(r"(?i)you are now\b.*\b(admin|root|superuser|unrestricted|dan|jailbreak)\b"),
        re.compile(r"(?i)\b(pretend|act|behave)\b.*\b(you are|you're|as if)\b.*\b(unrestricted|no (limit|filter|restriction)|without (rule|guideline|restriction))\b"),
        re.compile(r"(?i)\byour (new |true )?(identity|persona|role|name)\b.*\b(is|are)\b"),
        # System prompt extraction
        re.compile(r"(?i)reveal\b.*\b(system prompt|initial instructions)\b"),
        re.compile(r"(?i)(print|output|show|repeat|tell me)\b.*\b(system prompt|initial instruction|your instructions)\b"),
        # DAN / jailbreak mode triggers
        re.compile(r"(?i)\b(DAN|jailbreak|dev mode|developer mode|god mode|unrestricted mode)\b.*\b(mode|enabled?|on|activated?)\b"),
        re.compile(r"(?i)\benable\b.*\b(DAN|developer mode|jailbreak|unrestricted)\b"),
    ],
}

_READ_RE = re.compile(r"(?i)\b(read|get|list|show|fetch|view|describe|summari[sz]e|investigate|explain|check|look at)\b")
_WRITE_RE = re.compile(r"(?i)\b(write|create|update|delete|drop|insert|modify|change|restart|deploy|rollback|grant|revoke|set|remove|disable)\b")
_PII_RE = re.compile(r"(?i)\b(password|credential|api.?key|secret|token|ssn|social security|credit card|email address|phone number|customer record|private key)\b")


def attack_signal(text: str) -> Dict[str, object]:
    """Return {p_malicious, count, categories} from regex matches.

    p_malicious is a coarse estimate from the number of distinct attack
    categories matched (≥2 categories ⇒ near-certain). The decision layer is
    what turns this into BLOCK, not this function.
    """
    categories: List[str] = []
    count = 0
    for category, patterns in _ATTACK_PATTERNS.items():
        if any(p.search(text) for p in patterns):
            categories.append(category)
            count += 1
    # 0 cats → 0.0; 1 cat → 0.7; 2 → 0.9; 3+ → 0.97
    p = {0: 0.0, 1: 0.7, 2: 0.9}.get(count, 0.97)
    return {"p_malicious": p, "count": count, "categories": categories}


def risk_signal(text: str) -> Dict[str, bool]:
    """Return {read, write, pii} from keyword presence."""
    return {
        "read": bool(_READ_RE.search(text)),
        "write": bool(_WRITE_RE.search(text)),
        "pii": bool(_PII_RE.search(text)),
    }
