#!/usr/bin/env bash
# Replay ACA otel_event logs through local otel-ingestion-service → data-ingestion HTTPSink.
#
# Prereqs (separate terminals):
#   1. data-ingestion on http://localhost:8081
#   2. mini-runtime, guardrails-service, database-abstractor (your local stack)
#   3. otel-ingestion-service with auth + HTTP sink:
#
#        export SERVER_PORT=8087
#        export AKTO_OTLP_AUTHENTICATE=true
#        export AKTO_MONGO_CONN=mongodb://localhost:27017/admin
#        export AKTO_MONGO_DB=common
#        export DEFAULT_DATA_INGESTION_URL=http://localhost:8081
#        export OTLP_HTTP_SINK_ENABLED=true
#        cd container/src && go run .
#
# Usage:
#   export DATABASE_ABSTRACTOR_SERVICE_TOKEN=<jwt for account 1000000>
#   ./scripts/local-sync.sh [path/to/logs-otel-good.txt]
#
# Guardrails (observe): data-ingestion MUST have ENABLE_GUARDRAILS=true so batches with
# publishToGuardrails=true are dual-published to Kafka topic akto.guardrails for the
# guardrails-service Kafka consumer. Without it, Cowork OTLP never gets policy detection.
# Collection registration (username tags) hits database-abstractor (akto3):
#   export DATABASE_ABSTRACTOR_SERVICE_URL=http://localhost:9000
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
LOGS_FILE="${1:-$ROOT/../../data-adhoc/logs-otel-good.txt}"
OTEL_URL="${OTEL_URL:-http://localhost:8087}"
LOCAL_ACCOUNT_ID="${LOCAL_ACCOUNT_ID:-1000000}"
OUT_DIR="${TMPDIR:-/tmp}/otel-replay-$$"
mkdir -p "$OUT_DIR"
OTLP_JSON="$OUT_DIR/cowork_replay.json"

if [[ ! -f "$LOGS_FILE" ]]; then
  echo "Logs file not found: $LOGS_FILE" >&2
  exit 1
fi

echo "==> Parsing ACA logs: $LOGS_FILE"
python3 "$SCRIPT_DIR/replay_aca_logs.py" "$LOGS_FILE" -o "$OTLP_JSON"

LOG_ACCOUNT_ID="$(python3 "$SCRIPT_DIR/replay_aca_logs.py" "$LOGS_FILE" --print-account-ids 2>/dev/null | python3 -c "import json,sys; ids=json.load(sys.stdin); print(ids[0] if ids else '')")"
RECORD_COUNT="$(python3 -c "import json; print(len(json.load(open('$OTLP_JSON'))['resourceLogs'][0]['scopeLogs'][0]['logRecords']))")"
echo "==> Built OTLP JSON: $RECORD_COUNT log records (source logs account=${LOG_ACCOUNT_ID:-unknown}, ingest account=$LOCAL_ACCOUNT_ID)"

echo "==> Checking otel-ingestion at $OTEL_URL"
if ! curl -sf "$OTEL_URL/health" | grep -q '"success":true'; then
  echo "FAIL: otel-ingestion not healthy at $OTEL_URL" >&2
  echo "Start with AKTO_OTLP_AUTHENTICATE=true AKTO_MONGO_CONN=mongodb://localhost:27017/admin" >&2
  exit 1
fi
echo "OK  otel-ingestion /health"

JWT="${JWT:-${DATABASE_ABSTRACTOR_SERVICE_TOKEN:-}}"
if [[ -z "$JWT" && -f "$ROOT/.dev-keys/env.snippet" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT/.dev-keys/env.snippet"
  JWT="${JWT:-}"
fi

if [[ -z "$JWT" ]]; then
  echo "FAIL: set DATABASE_ABSTRACTOR_SERVICE_TOKEN or JWT (account $LOCAL_ACCOUNT_ID)" >&2
  exit 1
fi

echo "==> Auth: JWT (account $LOCAL_ACCOUNT_ID)"

echo "==> POST $OTEL_URL/v1/logs → HTTPSink → ${DEFAULT_DATA_INGESTION_URL:-http://localhost:8081}/api/ingestData"
HTTP_CODE=$(curl -s -o "$OUT_DIR/response.txt" -w "%{http_code}" -X POST "$OTEL_URL/v1/logs" \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  --data-binary @"$OTLP_JSON")

if [[ "$HTTP_CODE" != "200" ]]; then
  echo "FAIL: POST /v1/logs returned $HTTP_CODE" >&2
  cat "$OUT_DIR/response.txt" >&2 || true
  exit 1
fi
echo "OK  POST /v1/logs → $HTTP_CODE"

if [[ -n "${DATABASE_ABSTRACTOR_SERVICE_TOKEN:-${JWT:-}}" ]]; then
  echo "==> Module heartbeats (OS tags → database-abstractor; collections created by mini-runtime on ingest)"
  python3 "$SCRIPT_DIR/register_cowork_collections.py" "$LOGS_FILE" \
    --token "$JWT" \
    --abstractor-url "${DATABASE_ABSTRACTOR_SERVICE_URL:-http://localhost:9000}" || true
fi

echo ""
echo "Replay complete. Check:"
echo "  - otel-ingestion logs: account_id=$LOCAL_ACCOUNT_ID, http sink POST"
echo "  - data-ingestion logs: ingestData batch size"
echo "  - mini-runtime / dashboard for collections host *.ai-agent.claude_cowork"
echo ""
echo "To wipe stale Cowork Mongo before replay:"
echo "  python3 $SCRIPT_DIR/purge_cowork_local_data.py --account-id $LOCAL_ACCOUNT_ID --execute --purge-module-info --purge-es"
echo ""
echo "OTLP payload saved at: $OTLP_JSON"
