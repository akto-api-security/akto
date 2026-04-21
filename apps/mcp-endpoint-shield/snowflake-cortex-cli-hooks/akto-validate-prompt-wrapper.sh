#!/usr/bin/env bash
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1
export AKTO_DATA_INGESTION_URL="{{AKTO_DATA_INGESTION_URL}}"
export AKTO_API_TOKEN="{{AKTO_API_TOKEN}}"
export DATABASE_ABSTRACTOR_SERVICE_URL="{{DATABASE_ABSTRACTOR_SERVICE_URL}}"
if [ -f "$SCRIPT_DIR/.env" ]; then
  set -a
  # shellcheck source=/dev/null
  . "$SCRIPT_DIR/.env"
  set +a
fi
exec python3 "$SCRIPT_DIR/akto-validate-prompt.py" "$@"
