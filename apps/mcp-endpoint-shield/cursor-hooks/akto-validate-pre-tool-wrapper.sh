#!/bin/bash
# Akto pre-tool hook wrapper for Cursor

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="{{AKTO_DATA_INGESTION_URL}}"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="cursor"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="{{DEVICE_ID (optional)}}"

# Logging Configuration
export LOG_LEVEL="INFO"
export LOG_PAYLOADS="false"

# SSL Configuration
# Optional: Path to custom CA certificate bundle for SSL verification
# export SSL_CERT_PATH="/path/to/ca-bundle.crt"
# Optional: Disable SSL verification (INSECURE - use only for testing)
# export SSL_VERIFY="false"

exec python3 "$HOME/.cursor/hooks/akto/akto-validate-pre-tool.py" "$@"
