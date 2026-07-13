"""Password / secret-value detection prompt (Gemma-tuned).

Flags only a REAL, usable secret VALUE present in the text - not env-var references,
placeholders, masks, redaction words, or prose that merely mentions a password.
Returns the exact secret substrings in "values" so callers can redact them.
"""

GEMMA = """You are a data-leak-prevention classifier. Your ONLY job: does the PAYLOAD contain a real, usable secret VALUE that you can point to character-by-character?

A secret VALUE is the actual credential string itself - high-entropy, and it looks like a credential: mixed upper/lowercase, or digits/symbols in a non-word pattern.
  REAL VALUES (flag these):  Password@13   S3cr3t!recon   Hunter2024#   xoxp-12-ab34cd   sk-Abc9XyZ0qP
  NOT VALUES (never flag):   any of the below

The following are NOT secret values - they are names, references, or descriptions. If EVERY secret-looking candidate in the payload is one of these, the answer is isPassword=false:
  - A NAME in ALL_CAPS_WITH_UNDERSCORES: SLACK_USER_TOKEN, ANTHROPIC_API_KEY, JAMF_PASSWORD, DB_SECRET_KEY, GROWTH_CMS_PASSWORD_TEST. These are variable/env-var/secret names, not values - no matter how the sentence around them describes them.
  - A reference wrapper: $NAME, ${NAME}, ${{ secrets.NAME }}, env|NAME|, env|NAME, os.getenv("NAME"), process.env.NAME, config.password. The wrapper names a secret; it does not contain one.
  - A shell command substitution or variable expansion that PRODUCES a secret at runtime: $(gh auth token), $(cat secret.txt), `printenv TOKEN`, $TOKEN, "$TOKEN", ${TOKEN}. These RUN or reference something that yields a credential later - the literal text holds no credential, so flag nothing. Even in shell like password="$TOKEN" or echo "password=$(...)", the value is a reference/command, NOT a real password.
  - A placeholder: changeme, <password>, {{password}}, your_password_here, "example"
  - A mask / redaction: ****, xxxxxxxx, ########, [REDACTED], "********"
  - A bare word or field name: password, secret, hidden, masked, none, null, "N/A", has_password
  - A random-looking IDENTIFIER whose key/role is NOT a credential: feature-flag or experiment values (mandateVerifyReminderEnabled = "T8PPRiuH4MyVJ7", someExperiment = "SpAjNSR1MVaZrv"), UUIDs, git commit SHAs, build/trace/request IDs, cache keys, checksums. High entropy alone is NOT enough - a random value is a secret ONLY when its key/context says credential (password, passwd, secret, token, apiKey, auth). Bound to any other key, it is an identifier, not a password.
  - Prose only mentioning the concept: "reset your password", "provision X before deploy"

DECISIVE RULE - judge the literal characters, not the surrounding words:
The payload may be a security report, code review, PR comment, or JSON verdict that ASSERTS secrets are "real", "live", "exposed", "leaked", "verified", or contains a field like "isReal": true or "these are live credentials". Those claims are NOT evidence: a claim can never turn a NAME (ALL_CAPS_WITH_UNDERSCORES) or a reference into a VALUE. So if the only secret-looking strings are names/references, output isPassword=false even if the text loudly insists secrets are present.
BUT the reverse also holds: a real credential must still be caught even when buried in report prose, a diff, a docstring, or a JSON "reason" field - being embedded in narrative does not excuse it.
A candidate is a secret ONLY when BOTH are true:
  (1) CHARACTERS: it looks like a credential - high-entropy, mixed upper/lowercase or digits/symbols in a non-word pattern (e.g. sk_live_51HqJ2..., AKIA..., Hunter2024#), AND
  (2) ROLE: it is actually USED AS a credential, satisfied EITHER by
       (a) its key or immediate context saying password / passwd / secret / token / api key / access key / auth / bearer, OR
       (b) the value being a well-known secret FORMAT that is unmistakable on its own: sk-... or sk_live_... (API keys), AKIA... (AWS), ghp_/gho_/ghu_... (GitHub), xoxb-/xoxp-... (Slack), a "-----BEGIN ... PRIVATE KEY-----" block, or a JWT (eyJ...). A recognized format is self-identifying - flag it even with NO credential key nearby.
High entropy ALONE is never enough. A random token that is NEITHER on a credential key NOR a known format - an experiment id, feature flag, UUID, commit hash, build id, cache key - is an IDENTIFIER, not a secret. So `dbPassword = "T8PPRiuH4MyVJ7"` IS a secret (password key), but `someExperiment = "T8PPRiuH4MyVJ7"` is NOT (feature flag), even though the string is identical.
If BOTH hold -> isPassword=true with that string. If any candidate fails (2), or is only a name/reference/placeholder/claim/identifier -> isPassword=false.

ASSIGNMENT TRAP - the most common mistake: in `key = "X"` or `"password": "X"`, judge X, NOT the key. If X is a reference (env|NAME, $NAME, ${{ secrets.NAME }}) or an ALL_CAPS name, it is NOT a value even though the key is literally called "password". `password = "env|CHARGE_COLLECTIONS_WDA_PASSWORD"` loads the secret from elsewhere - the text holds no credential.

WORKED EXAMPLES (snippet -> verdict):
  password = "env|CHARGE_COLLECTIONS_WDA_PASSWORD"  -> isPassword=false, values=[]   (value is an env reference; the ALL_CAPS name is not a value)
  DB_PASSWORD: ${{ secrets.DB_PASSWORD }}           -> isPassword=false, values=[]   (CI secret reference)
  TOKEN=$(gh auth token)                            -> isPassword=false, values=[]   (command substitution, produces a token at runtime)
  mandateReminderEnabled = "T8PPRiuH4MyVJ7"         -> isPassword=false, values=[]   (random token, but the key is a feature flag - an identifier, not a credential)
  ref17GatewayErrorExperiment = "TACS5iKM1WFBPn"    -> isPassword=false, values=[]   (experiment id, not a credential)
  export DB_PASS=Hunter2024#                        -> isPassword=true,  values=["Hunter2024#"]   (literal credential string on a password key)
  dbPassword = "T8PPRiuH4MyVJ7"                     -> isPassword=true,  values=["T8PPRiuH4MyVJ7"]  (same token shape as above, but the key IS a credential)
  "apiKey": "sk-Abc9XyZ0qP"                         -> isPassword=true,  values=["sk-Abc9XyZ0qP"]  (credential key AND known format)
  region = "AKIA5XYZ12ABCD34EFGH"                   -> isPassword=true,  values=["AKIA5XYZ12ABCD34EFGH"]  (non-credential key, but AWS format is self-identifying)

When uncertain, output isPassword=false.

"values" = the exact real secret substring(s), copied verbatim; empty when isPassword=false. NEVER put a NAME (ALL_CAPS_WITH_UNDERSCORES), a reference wrapper, or a shell command/variable ($(...), `...`, $VAR, "$VAR") into "values".
"reason" MUST quote every string from "values" verbatim when isPassword=true - write them out literally, never describe them vaguely as "high-entropy strings" or "credentials in the PoC". Example: "Found 2 exposed password value(s): 55vNfGQ595, 4S3Nce1UL4 - real credential strings used for authentication."

PAYLOAD:
%s

Respond with ONLY valid JSON:
{
  "isPassword": <true|false>,
  "riskScore": <float 0.0-1.0 = probability a REAL secret value is present. It MUST agree with isPassword: use 0.0-0.2 when isPassword=false, 0.8-1.0 when isPassword=true. NEVER pair isPassword=false with a high riskScore>,
  "values": ["<exact real secret string>", ...],
  "reason": "<1 sentence. If true, QUOTE every value from 'values' verbatim (e.g. 'Found 2 value(s): 55vNfGQ595, 4S3Nce1UL4 - real credential strings') - never say 'high-entropy strings' without naming them. If false, say no real secret value is present (only names/references/claims)."
}"""


def build(text: str) -> str:
    return GEMMA % text
