"""
Configuration management for Hermes Akto Guardrails plugin.
Loads settings from environment variables with sensible defaults.
"""

import os
from typing import Optional


class Config:
    """Configuration for Hermes Akto Guardrails plugin."""

    def __init__(self):
        """Initialize configuration from environment variables."""
        # Required
        self.akto_url: str = os.getenv("AKTO_DATA_INGESTION_URL", "").strip()

        # Mode (argus=default, atlas=device-specific)
        self.mode: str = os.getenv("MODE", "argus").lower()
        self.device_id: Optional[str] = os.getenv("DEVICE_ID")

        # Behavior
        self.sync_mode: bool = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
        self.timeout: float = float(os.getenv("AKTO_TIMEOUT", "5"))

        # Connector info
        self.connector: str = os.getenv("AKTO_CONNECTOR", "hermes")
        self.token: str = os.getenv("AKTO_TOKEN", "")

        # Logging
        self.log_level: str = os.getenv("LOG_LEVEL", "INFO").upper()
        self.log_payloads: bool = os.getenv("LOG_PAYLOADS", "false").lower() == "true"
        self.log_dir: str = os.path.expanduser(os.getenv("LOG_DIR", "~/.config/hermes/akto/logs"))

        # Context
        self.context_source: str = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

        # SSL
        self.ssl_verify: bool = os.getenv("SSL_VERIFY", "true").lower() == "true"
        self.ssl_cert_path: Optional[str] = os.getenv("SSL_CERT_PATH")

    def is_configured(self) -> bool:
        """Check if Akto URL is configured."""
        return bool(self.akto_url)

    def get_api_url(self, path: str) -> str:
        """Build full API URL."""
        if not self.akto_url:
            return ""
        return f"{self.akto_url.rstrip('/')}/{path.lstrip('/')}"

    def get_headers(self) -> dict:
        """Get common API headers."""
        headers = {"Content-Type": "application/json"}
        if self.token:
            headers["Authorization"] = self.token
        return headers
