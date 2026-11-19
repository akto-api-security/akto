#!/bin/bash
# Generic Akto observability hook wrapper -- shared by GitHub Copilot CLI and VS Code Copilot
# Usage: bash ./.github/hooks/akto-hook-wrapper.sh akto-hooks.py <hookName>

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="{{AKTO_DATA_INGESTION_URL}}"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
# Both surfaces report under the same "copilot" ai_agent_tag, so one hardcoded value is correct for both.
export AKTO_CONNECTOR="github_cli"
export CONTEXT_SOURCE="ENDPOINT"
export AKTO_API_TOKEN="{{AKTO_API_TOKEN}}"
export DEVICE_ID="{{DEVICE_ID (optional)}}"

export LOG_DIR="$HOME/akto/.github/akto/vscode/logs"
export LOG_LEVEL="INFO"
export LOG_PAYLOADS="false"
# export SSL_CERT_PATH="/path/to/ca-bundle.crt"
# export SSL_VERIFY="false"

exec python3 "./.github/hooks/$1" "${@:2}"
