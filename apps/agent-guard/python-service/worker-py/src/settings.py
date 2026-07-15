"""Worker configuration.

Cloudflare Python Workers receive bindings/secrets on the `env` object passed
to on_fetch (`settings.init`). Portable deployments read the same fields from
`os.environ` via `settings.init_from_env`.
"""

import os

_FIELDS = (
    # OpenAI / openai-compatible
    "OPENAI_API_KEY",
    "OPENAI_MODEL",
    "OPENAI_COMPATIBLE_BASE_URL",
    # Anthropic
    "ANTHROPIC_API_KEY",
    "ANTHROPIC_MODEL",
    # Vertex (generic)
    "VERTEX_AI_SA_KEY_JSON",
    "VERTEX_AI_PROJECT",
    "VERTEX_AI_LOCATION",
    "VERTEX_AI_ENDPOINT_ID",
    # Gemma on Vertex
    "GEMMA_VERTEX_SA_KEY_JSON",
    "GEMMA_VERTEX_PROJECT",
    "GEMMA_VERTEX_LOCATION",
    "GEMMA_VERTEX_ENDPOINT_ID",
    "GEMMA_VERTEX_DEDICATED_DNS",
    # Qwen3Guard on Vertex
    "QWEN3GUARD_SA_KEY_JSON",
    "QWEN3GUARD_PROJECT",
    "QWEN3GUARD_LOCATION",
    "QWEN3GUARD_ENDPOINT_ID",
    "QWEN3GUARD_DEDICATED_DNS",
    # Integrations
    "SLACK_WEBHOOK_URL",
    "DATABASE_ABSTRACTOR_SERVICE_URL",
    # Per-deployment cascade default modelMap (JSON). Empty → built-in default.
    "DEFAULT_MODEL_CONFIG_JSON",
    # Portable anonymizer service URL (e.g. http://anonymizer:8093).
    "ANONYMIZER_URL",
    "DATABASE_ABSTRACTOR_SERVICE_TOKEN",
    # Seconds between metric pushes to database-abstractor. Default 60s.
    "METRICS_PUSH_INTERVAL_SEC",
)


class Settings:
    # Declared for mypy only — __init__ below is the actual runtime source of
    # truth, setting these (and only these, per _FIELDS) via setattr.
    OPENAI_API_KEY: str
    OPENAI_MODEL: str
    OPENAI_COMPATIBLE_BASE_URL: str
    ANTHROPIC_API_KEY: str
    ANTHROPIC_MODEL: str
    VERTEX_AI_SA_KEY_JSON: str
    VERTEX_AI_PROJECT: str
    VERTEX_AI_LOCATION: str
    VERTEX_AI_ENDPOINT_ID: str
    GEMMA_VERTEX_SA_KEY_JSON: str
    GEMMA_VERTEX_PROJECT: str
    GEMMA_VERTEX_LOCATION: str
    GEMMA_VERTEX_ENDPOINT_ID: str
    GEMMA_VERTEX_DEDICATED_DNS: str
    QWEN3GUARD_SA_KEY_JSON: str
    QWEN3GUARD_PROJECT: str
    QWEN3GUARD_LOCATION: str
    QWEN3GUARD_ENDPOINT_ID: str
    QWEN3GUARD_DEDICATED_DNS: str
    SLACK_WEBHOOK_URL: str
    DATABASE_ABSTRACTOR_SERVICE_URL: str
    DEFAULT_MODEL_CONFIG_JSON: str
    ANONYMIZER_URL: str
    DATABASE_ABSTRACTOR_SERVICE_TOKEN: str
    METRICS_PUSH_INTERVAL_SEC: str

    def __init__(self):
        for f in _FIELDS:
            setattr(self, f, "")
        self._loaded = False

    def _apply(self, values: dict[str, str]) -> None:
        for f in _FIELDS:
            setattr(self, f, values.get(f, ""))
        self._loaded = True

    def init(self, env) -> None:
        """Populate from the Worker `env` binding object (idempotent)."""
        if self._loaded:
            return
        values = {}
        for f in _FIELDS:
            v = getattr(env, f, None)
            values[f] = "" if v is None else str(v)
        self._apply(values)

    def init_from_env(self) -> None:
        """Populate from process environment (idempotent)."""
        if self._loaded:
            return
        self._apply({f: os.environ.get(f, "") for f in _FIELDS})


settings = Settings()
