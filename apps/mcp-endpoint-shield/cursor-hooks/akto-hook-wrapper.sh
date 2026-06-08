#!/bin/bash
# Generic Akto Cursor hook wrapper
# Usage: bash ~/.cursor/hooks/akto/akto-hook-wrapper.sh akto-<name>.py

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="{{AKTO_DATA_INGESTION_URL}}"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="cursor"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="{{DEVICE_ID (optional)}}"

export LOG_LEVEL="INFO"
export LOG_PAYLOADS="false"
# export SSL_CERT_PATH="/path/to/ca-bundle.crt"
# export SSL_VERIFY="false"

exec python3 "$HOME/.cursor/hooks/akto/$1" "${@:2}"
