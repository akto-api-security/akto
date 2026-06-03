"""Centralized environment-variable configuration.

All env-var reads outside of intent_analyzer.py go through `settings` (the
module-level instance below). One canonical schema, validated at startup,
type-safe access everywhere else.

Usage:
    from settings import settings
    api_key = settings.OPENAI_API_KEY
"""

from pydantic import field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        case_sensitive=True,
        env_file=".env",
        extra="ignore",
    )

    # ── Service ──────────────────────────────────────────────────────────
    PORT: int = 8092

    # ── Routing / mode toggles ───────────────────────────────────────────
    FORCE_LLM_MODE: bool = False
    SCANNER_LLM_PROVIDER: str = ""

    # docker-compose's `${VAR:-}` expands to an empty string when the host env
    # var is unset; pydantic refuses to coerce '' to bool/int. Map empty → typed
    # default for the non-string fields so the container boots cleanly.
    @field_validator("FORCE_LLM_MODE", mode="before")
    @classmethod
    def _force_llm_mode_default(cls, v: object) -> object:
        return False if isinstance(v, str) and v.strip() == "" else v

    @field_validator("PORT", mode="before")
    @classmethod
    def _port_default(cls, v: object) -> object:
        return 8092 if isinstance(v, str) and v.strip() == "" else v

    # ── External services ────────────────────────────────────────────────
    DATABASE_ABSTRACTOR_SERVICE_URL: str = ""
    SLACK_WEBHOOK_URL: str = ""

    # ── OpenAI / Ollama / openai-compatible ──────────────────────────────
    OPENAI_API_KEY: str = ""
    OPENAI_MODEL: str = ""
    OPENAI_COMPATIBLE_BASE_URL: str = ""

    # ── Ollama (CPU-based async evaluation) ──────────────────────────────
    OLLAMA_BASE_URL: str = ""
    OLLAMA_MODEL: str = "gemma4:e4b"

    # ── Anthropic ────────────────────────────────────────────────────────
    ANTHROPIC_API_KEY: str = ""
    ANTHROPIC_MODEL: str = ""

    # ── Vertex AI (generic — backs the `vertexai` provider) ──────────────
    VERTEX_AI_SA_KEY_JSON: str = ""
    VERTEX_AI_PROJECT: str = ""
    VERTEX_AI_LOCATION: str = ""
    VERTEX_AI_ENDPOINT_ID: str = ""

    # ── Gemma on Vertex ──────────────────────────────────────────────────
    GEMMA_VERTEX_SA_KEY_JSON: str = ""
    GEMMA_VERTEX_PROJECT: str = ""
    GEMMA_VERTEX_LOCATION: str = ""
    GEMMA_VERTEX_ENDPOINT_ID: str = ""
    GEMMA_VERTEX_DEDICATED_DNS: str = ""

    # ── Qwen3Guard on Vertex ─────────────────────────────────────────────
    QWEN3GUARD_SA_KEY_JSON: str = ""
    QWEN3GUARD_PROJECT: str = ""
    QWEN3GUARD_LOCATION: str = ""
    QWEN3GUARD_ENDPOINT_ID: str = ""
    QWEN3GUARD_DEDICATED_DNS: str = ""


settings = Settings()
