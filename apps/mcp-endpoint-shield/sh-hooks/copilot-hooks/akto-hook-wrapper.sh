#!/bin/bash

export MODE="atlas"
export AKTO_DATA_INGESTION_URL="{{AKTO_DATA_INGESTION_URL}}"
export AKTO_API_TOKEN="{{AKTO_API_TOKEN}}"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="{{DEVICE_ID (optional)}}"

export LOG_DIR="$HOME/.copilot/akto/logs"
export LOG_LEVEL="INFO"
export LOG_PAYLOADS="false"

SCRIPT_DIR="$HOME/.copilot/hooks/akto"
exec bash "$SCRIPT_DIR/$1" "${@:2}"
