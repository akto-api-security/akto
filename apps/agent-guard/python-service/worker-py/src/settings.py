"""Worker configuration.

Cloudflare Python Workers receive bindings/secrets on the `env` object passed
to on_fetch (`settings.init`). Portable deployments read the same fields from
`os.environ` via `settings.init_from_env`.
"""

import os

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
    # Per-deployment cascade default modelMap (JSON). Empty → built-in default.
    "DEFAULT_MODEL_CONFIG_JSON",
    # Portable anonymizer service URL (e.g. http://anonymizer:8093).
    "ANONYMIZER_URL",
    # --- Per-scanner semantic cache (Redis vector store + embedder service) ---
    # CACHE_MODE: off | observe | decide (default observe; see cache.py).
    # CACHE_SHADOW_ENABLED is a back-compat alias: true/1 → observe.
    "CACHE_MODE", "CACHE_SHADOW_ENABLED",
    # CACHE_DISTANCE_THRESHOLD: safe (is_valid=True) match tolerance (default 0.15).
    # CACHE_BLOCK_DISTANCE_THRESHOLD: blocked (is_valid=False) match tolerance
    #   (default 0.0 = blocks never served). COSINE distance of identical text is
    #   ~1e-7, so use a small epsilon like 1e-4 to serve blocks only on exact repeat.
    "CACHE_DISTANCE_THRESHOLD", "CACHE_BLOCK_DISTANCE_THRESHOLD", "CACHE_TTL_SECONDS",
    # Portable embedder service URL (e.g. http://embedder:8094).
    "EMBEDDER_URL",
    # Redis with the RediSearch module (e.g. redis://redis:6379, rediss://... on Azure).
    "REDIS_URL", "CACHE_REDIS_INDEX",
    # Separate webhook for cache shadow/served alerts (keeps SLACK_WEBHOOK_URL clean).
    "CACHE_SHADOW_SLACK_WEBHOOK_URL",
    # --- Intent prefilter (task/risk/scope) layered on the semantic cache ---
    # INTENT_ENABLED: master switch for JSON-strip → chunk → intent decisioning.
    "INTENT_ENABLED",
    # INTENT_EMBED_MODEL: informational; the embedder container owns the model.
    "INTENT_EMBED_MODEL",
    # Decision thresholds (see intent/decision.py). ALLOW must clear a higher bar
    # than ESCALATE because an ALLOW skips the LLM cascade (no safety net behind it).
    "INTENT_ALLOW_THRESHOLD", "INTENT_BLOCK_THRESHOLD", "INTENT_SCOPE_DISTANCE",
    # Max chunks embedded/classified per request (caps miss-path cost).
    "INTENT_MAX_CHUNKS",
    # INTENT_ACT: when falsy (default), ALLOW/BLOCK decisions are only logged
    # (shadow mode) and the request still ESCALATEs to the LLM cascade. Set to
    # "true" to enforce intent decisions and skip the cascade.
    "INTENT_ACT",
)


class Settings:
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
