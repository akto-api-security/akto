"""Worker configuration.

Unlike the container (which read os.environ via pydantic-settings), Cloudflare
Python Workers receive bindings/secrets on the `env` object passed to on_fetch.
We populate a module-level singleton once per isolate from that `env`, so the
rest of the code can keep doing `from settings import settings`.
"""

_FIELDS = (
    # OpenAI / openai-compatible
    "OPENAI_API_KEY", "OPENAI_MODEL", "OPENAI_COMPATIBLE_BASE_URL",
    # Anthropic
    "ANTHROPIC_API_KEY", "ANTHROPIC_MODEL",
    # Vertex (generic)
    "VERTEX_AI_SA_KEY_JSON", "VERTEX_AI_PROJECT", "VERTEX_AI_LOCATION", "VERTEX_AI_ENDPOINT_ID",
    # Gemma on Vertex
    "GEMMA_VERTEX_SA_KEY_JSON", "GEMMA_VERTEX_PROJECT", "GEMMA_VERTEX_LOCATION",
    "GEMMA_VERTEX_ENDPOINT_ID", "GEMMA_VERTEX_DEDICATED_DNS",
    # Qwen3Guard on Vertex
    "QWEN3GUARD_SA_KEY_JSON", "QWEN3GUARD_PROJECT", "QWEN3GUARD_LOCATION",
    "QWEN3GUARD_ENDPOINT_ID", "QWEN3GUARD_DEDICATED_DNS",
    # Integrations
    "SLACK_WEBHOOK_URL", "DATABASE_ABSTRACTOR_SERVICE_URL",
)


class Settings:
    def __init__(self):
        for f in _FIELDS:
            setattr(self, f, "")
        self._loaded = False

    def init(self, env) -> None:
        """Populate from the Worker `env` binding object (idempotent)."""
        if self._loaded:
            return
        for f in _FIELDS:
            v = getattr(env, f, None)
            setattr(self, f, "" if v is None else str(v))
        self._loaded = True


settings = Settings()
