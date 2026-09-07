#!/bin/bash

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="{{AKTO_DATA_INGESTION_URL}}"
export AKTO_API_TOKEN="{{AKTO_API_TOKEN}}"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="codex_cli"
export CONTEXT_SOURCE="ENDPOINT"

export LOG_LEVEL="INFO"
export LOG_PAYLOADS="false"

exec bash "$HOME/.codex/hooks/akto-validate-pre-tool.sh" "$@"
