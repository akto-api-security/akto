#!/bin/bash
# Generic wrapper for Akto Claude CLI hooks
# Usage: HOOK_SCRIPT=akto-<name>.py bash akto-hook-wrapper.sh
# Or invoked directly per hook via settings.json passing the script name

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="{{AKTO_DATA_INGESTION_URL}}"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="claude_code_cli"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="{{DEVICE_ID (optional)}}"

# Logging Configuration
export LOG_DIR="~/.claude/akto/logs"
export LOG_LEVEL="INFO"
export LOG_PAYLOADS="false"

# SSL Configuration
# export SSL_CERT_PATH="/path/to/ca-bundle.crt"
# export SSL_VERIFY="false"

exec python3 "$HOME/.claude/hooks/$1" "${@:2}"
