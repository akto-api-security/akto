#!/usr/bin/env bash
# Local benchmark for otel-ingestion-service hot path.
# Requires: hey (go install github.com/rakyll/hey@latest) or brew install hey
#
# Usage:
#   ./scripts/benchmark.sh
#   BASE_URL=http://localhost:4318 DURATION=30s CONCURRENCY=50 ./scripts/benchmark.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BASE_URL="${BASE_URL:-http://localhost:8080}"
DURATION="${DURATION:-15s}"
CONCURRENCY="${CONCURRENCY:-50}"
FIXTURE_JSON="$SCRIPT_DIR/fixtures/cowork_logs.json"
FIXTURE_PROTO="$SCRIPT_DIR/fixtures/cowork_logs.pb"
AUTH_HEADER="${AUTH_HEADER:-}"

if ! command -v hey >/dev/null 2>&1; then
  echo "hey not found. Install: go install github.com/rakyll/hey@latest"
  exit 1
fi

if [[ -z "$AUTH_HEADER" && -f "$ROOT/.dev-keys/env.snippet" ]]; then
  # shellcheck disable=SC1091
  source "$ROOT/.dev-keys/env.snippet"
  AUTH_HEADER="Bearer $JWT"
fi

if [[ ! -f "$FIXTURE_PROTO" ]]; then
  cd "$ROOT/container/src"
  go run ./cmd/devtools protobuf-fixture --out "$FIXTURE_PROTO"
fi

auth_args=()
if [[ -n "$AUTH_HEADER" ]]; then
  auth_args=(-H "Authorization: $AUTH_HEADER")
fi

echo "==> Benchmark: $BASE_URL"
echo "    duration=$DURATION concurrency=$CONCURRENCY"
echo ""

echo "=== JSON (http/json) ==="
hey_args=(-z "$DURATION" -c "$CONCURRENCY" -m POST)
if ((${#auth_args[@]})); then hey_args+=("${auth_args[@]}"); fi
hey_args+=(-H "Content-Type: application/json" -D "$FIXTURE_JSON" "$BASE_URL/v1/logs")
hey "${hey_args[@]}"

echo ""
echo "=== Protobuf (http/protobuf) ==="
hey_args=(-z "$DURATION" -c "$CONCURRENCY" -m POST)
if ((${#auth_args[@]})); then hey_args+=("${auth_args[@]}"); fi
hey_args+=(-H "Content-Type: application/x-protobuf" -D "$FIXTURE_PROTO" "$BASE_URL/v1/logs")
hey "${hey_args[@]}"

echo ""
curl -sf "$BASE_URL/backpressure" | python3 -m json.tool 2>/dev/null || curl -sf "$BASE_URL/backpressure"
