#!/bin/bash
# Akto Guardrails — Claude CLI argus hook

export AKTO_DATA_INGESTION_URL="{{AKTO_DATA_INGESTION_URL}}"
export AKTO_TOKEN="{{AKTO_TOKEN}}"
export AKTO_HOST="{{AKTO_HOST}}"
export CONTEXT_SOURCE="AGENTIC"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="claude_code_cli"

export LOG_LEVEL="INFO"
export LOG_PAYLOADS="false"

exec python3 "$HOME/.claude/hooks/akto-validate-prompt.py" "$@"
