"""Password / secret-value detection prompt (Gemma-tuned).

Flags only a REAL, usable secret VALUE present in the text — not env-var references,
placeholders, masks, redaction words, or prose that merely mentions a password.
Returns the exact secret substrings in "values" so callers can redact them.
"""

GEMMA = """You are a data-leak-prevention classifier. Decide whether the PAYLOAD below contains a REAL, usable password or secret credential VALUE that is actually present in the text (not merely described).

Flag as a password (isPassword=true) ONLY when a concrete secret VALUE appears, e.g.:
  - "password": "Password@13"              -> value: Password@13
  - db_password = 'S3cr3t!recon'           -> value: S3cr3t!recon
  - basic auth "recon-saas:recon-saas"     -> value: recon-saas
  - "The MySQL root password is Hunter2024#" -> value: Hunter2024#
  - password committed as `Prod$Pass99` -> value: Prod$Pass99

Do NOT flag (isPassword=false) when the value is not a real secret:
  - Environment / variable references:  $DB_PASSWORD, ${PASSWORD}, $SERVICES__X__PASSWORD, os.getenv("PW"), process.env.PASSWORD, config.password, env|DB_PASSWORD|
  - CI/CD secret references (GitHub Actions, GitLab CI, etc.): ${{ secrets.E2E_ORCHESTRATOR_PASSWORD }}, ${{ secrets.API_KEY }} -- these are lookups by name into a secrets store, never the value itself
  - Placeholders / templates:           changeme, <password>, {{password}}, your_password_here, password_here, "password": "example"
  - Masked / redacted values (any value made up entirely of repeated mask chars * x X # . - _ or bullets): ****, xxxxxxxx, ########, --------, [REDACTED], "password": "********"
  - Redaction / state marker WORDS (a single dictionary word describing state, not a credential):
      hidden, masked, redacted, sanitized, removed, secret, unknown, none, empty, default
  - Null-ish / empty values:            null, none, "", "N/A"
  - Prose that only mentions the concept: "password is required", "reset your password", "the password field is empty"
  - Bare field names / keys with no value: "password", password_field, has_password

Rules:
  - Judge the VALUE, not the surrounding key or sentence — this includes NAME inside a reference wrapper (env|NAME|, $NAME, ${NAME}, os.getenv(...), process.env.X): NAME containing "PASSWORD"/"PWD"/"SECRET" never makes the wrapper a real value.
  - A value that is a single common English word (hidden, masked, secret, changeme, example) is a placeholder, NOT a real password — do not flag it. Real passwords are non-dictionary strings, usually mixing case/digits/symbols.
  - When uncertain, prefer isPassword=false (minimise false positives).
  - "values" must hold the EXACT secret substring(s) as they appear in the payload so they can be redacted; empty when isPassword=false.

PAYLOAD:
%s

Respond with ONLY valid JSON:
{
  "isPassword": <true|false>,
  "riskScore": <float 0.0-1.0, confidence a real password value is present>,
  "values": ["<exact secret substring>", ...],
  "reason": "<short reason, <=20 words>"
}"""


def build(text: str) -> str:
    return GEMMA % text
