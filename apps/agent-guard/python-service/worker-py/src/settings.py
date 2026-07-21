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
    # Azure AI Foundry — generic (any OpenAI-compatible Foundry deployment)
    "AZURE_FOUNDRY_BASE_URL",
    "AZURE_FOUNDRY_API_KEY",
    "AZURE_FOUNDRY_DEPLOYMENT",
    "AZURE_FOUNDRY_MODEL",
    # Gemma on Azure Foundry
    "GEMMA_FOUNDRY_BASE_URL",
    "GEMMA_FOUNDRY_API_KEY",
    "GEMMA_FOUNDRY_DEPLOYMENT",
    "GEMMA_FOUNDRY_MODEL",
    # Qwen3Guard on Azure Foundry
    "QWEN3GUARD_FOUNDRY_BASE_URL",
    "QWEN3GUARD_FOUNDRY_API_KEY",
    "QWEN3GUARD_FOUNDRY_DEPLOYMENT",
    "QWEN3GUARD_FOUNDRY_MODEL",
    # Integrations
    "SLACK_WEBHOOK_URL",
    "DATABASE_ABSTRACTOR_SERVICE_URL",
    # Per-deployment cascade default modelMap (JSON). Empty → built-in default.
    "DEFAULT_MODEL_CONFIG_JSON",
    # Portable anonymizer service URL (e.g. http://anonymizer:8093).
    "ANONYMIZER_URL",
    # --- Per-scanner semantic cache (Redis vector store + embedder service) ---
    # CACHE_MODE: off | observe | decide (default observe; see cache.py).
    # CACHE_SHADOW_ENABLED is a back-compat alias: true/1 → observe.
    "CACHE_MODE",
    "CACHE_SHADOW_ENABLED",
    # CACHE_DISTANCE_THRESHOLD: safe (is_valid=True) match tolerance (default 0.15).
    # CACHE_BLOCK_DISTANCE_THRESHOLD: blocked (is_valid=False) match tolerance
    #   (default 0.0 = blocks never served). COSINE distance of identical text is
    #   ~1e-7, so use a small epsilon like 1e-4 to serve blocks only on exact repeat.
    "CACHE_DISTANCE_THRESHOLD",
    "CACHE_BLOCK_DISTANCE_THRESHOLD",
    "CACHE_TTL_SECONDS",
    # Portable embedder service URL (e.g. http://embedder:8094).
    "EMBEDDER_URL",
    # Redis with the RediSearch module (e.g. redis://redis:6379, rediss://... on Azure).
    "REDIS_URL",
    "CACHE_REDIS_INDEX",
    # Separate webhook for cache shadow/served alerts (keeps SLACK_WEBHOOK_URL clean).
    "CACHE_SHADOW_SLACK_WEBHOOK_URL",
    # INTENT_SCOPE_DISTANCE: still used by the semantic verdict cache's
    # cache-neighbour check (cache.py) — unrelated to the multi-class classifier.
    "INTENT_SCOPE_DISTANCE",
    # Max chunks used by payload.normalize() for the semantic cache's canonical
    # text (cache.py only — unrelated to intent/segmenter.py's unit extraction).
    "INTENT_MAX_CHUNKS",
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
    AZURE_FOUNDRY_BASE_URL: str
    AZURE_FOUNDRY_API_KEY: str
    AZURE_FOUNDRY_DEPLOYMENT: str
    AZURE_FOUNDRY_MODEL: str
    GEMMA_FOUNDRY_BASE_URL: str
    GEMMA_FOUNDRY_API_KEY: str
    GEMMA_FOUNDRY_DEPLOYMENT: str
    GEMMA_FOUNDRY_MODEL: str
    QWEN3GUARD_FOUNDRY_BASE_URL: str
    QWEN3GUARD_FOUNDRY_API_KEY: str
    QWEN3GUARD_FOUNDRY_DEPLOYMENT: str
    QWEN3GUARD_FOUNDRY_MODEL: str
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
