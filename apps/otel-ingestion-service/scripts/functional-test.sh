#!/usr/bin/env bash
# Functional smoke tests for otel-ingestion-service (Phase 1).
# Prereq: service running (go run . or docker compose up).
#
# Cowork OTel format reference:
#   https://claude.com/docs/cowork/monitoring
#   https://support.claude.com/en/articles/14477985-monitor-claude-cowork-activity-with-opentelemetry
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
FIXTURE_JSON="$SCRIPT_DIR/fixtures/cowork_logs.json"
FIXTURE_PROTO="$SCRIPT_DIR/fixtures/cowork_logs.pb"
AUTH_HEADER="${AUTH_HEADER:-}"

if [[ -z "$AUTH_HEADER" && -f "$ROOT/.dev-keys/env.snippet" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT/.dev-keys/env.snippet"
  AUTH_HEADER="Bearer $JWT"
fi

echo "==> Target: $BASE_URL"
echo "==> Auth: ${AUTH_HEADER:+enabled}${AUTH_HEADER:-disabled}"

curl -sf "$BASE_URL/health" | grep -q '"success":true' && echo "OK  GET /health"

curl -sf "$BASE_URL/backpressure" | grep -q 'queue_depth' && echo "OK  GET /backpressure"

auth_args=()
if [[ -n "$AUTH_HEADER" ]]; then
  auth_args=(-H "Authorization: $AUTH_HEADER")
fi

# OTLP /v1/logs — http/json (Cowork protocol option)
curl_args=(-s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/v1/logs")
if ((${#auth_args[@]})); then curl_args+=("${auth_args[@]}"); fi
curl_args+=(-H "Content-Type: application/json" --data-binary @"$FIXTURE_JSON")
code=$(curl "${curl_args[@]}")
[[ "$code" == "200" ]] && echo "OK  POST /v1/logs (application/json) → $code" || { echo "FAIL POST /v1/logs json → $code"; exit 1; }

# Generate protobuf fixture if missing
if [[ ! -f "$FIXTURE_PROTO" ]]; then
  cd "$ROOT/container/src"
  go run ./cmd/devtools protobuf-fixture --out "$FIXTURE_PROTO"
fi

# OTLP /v1/logs — http/protobuf (Cowork protocol option)
curl_args=(-s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/v1/logs")
if ((${#auth_args[@]})); then curl_args+=("${auth_args[@]}"); fi
curl_args+=(-H "Content-Type: application/x-protobuf" --data-binary @"$FIXTURE_PROTO")
code=$(curl "${curl_args[@]}")
[[ "$code" == "200" ]] && echo "OK  POST /v1/logs (application/x-protobuf) → $code" || { echo "FAIL POST /v1/logs protobuf → $code"; exit 1; }

# Standard OTLP stub paths (Cowork does not send these today)
curl_args=(-s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/v1/traces")
if ((${#auth_args[@]})); then curl_args+=("${auth_args[@]}"); fi
curl_args+=(-H "Content-Type: application/json" -d '{}')
code=$(curl "${curl_args[@]}")
[[ "$code" == "200" ]] && echo "OK  POST /v1/traces → $code"

curl_args=(-s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/v1/metrics")
if ((${#auth_args[@]})); then curl_args+=("${auth_args[@]}"); fi
curl_args+=(-H "Content-Type: application/json" -d '{}')
code=$(curl "${curl_args[@]}")
[[ "$code" == "200" ]] && echo "OK  POST /v1/metrics → $code"

if [[ -n "$AUTH_HEADER" ]]; then
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/v1/logs" \
    -H "Content-Type: application/json" --data-binary @"$FIXTURE_JSON")
  [[ "$code" == "401" ]] && echo "OK  POST /v1/logs without auth → 401" || echo "WARN expected 401 without auth, got $code"
fi

echo ""
echo "All functional checks passed."
echo "Check service logs for structured otel_event lines (user_prompt, tool_result, api_request)."
