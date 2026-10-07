#!/usr/bin/env bash
# Start data-ingestion + guardrails for local Dify connector testing.
# Prerequisites: dashboard :8081, cyborg :8082, mongo :27017
#
# Usage:
#   cp apps/dashboard/scripts/dify-local.env.example apps/dashboard/scripts/dify-local.env
#   # fill in DATABASE_ABSTRACTOR_SERVICE_TOKEN (and NGROK_AUTHTOKEN for cloud Dify)
#   apps/dashboard/scripts/start-dify-local-stack.sh

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/dify-local.env"
LOG_DIR="${ROOT}/.cursor/dify-local-logs"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing ${ENV_FILE}"
  echo "Copy dify-local.env.example → dify-local.env and fill in required values."
  exit 1
fi

# shellcheck disable=SC1090
source "${ENV_FILE}"

: "${AKTO_MONGO_CONN:?AKTO_MONGO_CONN required}"
: "${DATABASE_ABSTRACTOR_SERVICE_URL:?DATABASE_ABSTRACTOR_SERVICE_URL required}"
# Token optional when cyborg auth is disabled locally
DATABASE_ABSTRACTOR_SERVICE_TOKEN="${DATABASE_ABSTRACTOR_SERVICE_TOKEN:-}"

DATA_INGESTION_PORT="${DATA_INGESTION_PORT:-8090}"
GUARDRAILS_PORT="${GUARDRAILS_PORT:-9091}"
START_KAFKA="${START_KAFKA:-false}"

mkdir -p "${LOG_DIR}"

# data-ingestion-service targets Java 8 (same as dashboard/cyborg local dev)
if [[ -z "${JAVA_HOME:-}" ]]; then
  if /usr/libexec/java_home -v 1.8 >/dev/null 2>&1; then
    export JAVA_HOME="$(/usr/libexec/java_home -v 1.8)"
  fi
fi
if [[ -n "${JAVA_HOME:-}" ]]; then
  export PATH="${JAVA_HOME}/bin:${PATH}"
  echo "Using JAVA_HOME=${JAVA_HOME}"
fi

is_up() {
  curl -sf -o /dev/null --max-time 2 "$1" 2>/dev/null
}

start_guardrails() {
  if is_up "http://127.0.0.1:${GUARDRAILS_PORT}/health"; then
    echo "guardrails already up on :${GUARDRAILS_PORT}"
    return
  fi

  echo "Starting guardrails-service on :${GUARDRAILS_PORT} ..."
  (
    export SERVER_PORT="${GUARDRAILS_PORT}"
    export DATABASE_ABSTRACTOR_SERVICE_URL
    export DATABASE_ABSTRACTOR_SERVICE_TOKEN
    export THREAT_BACKEND_TOKEN="${THREAT_BACKEND_TOKEN:-${DATABASE_ABSTRACTOR_SERVICE_TOKEN}}"
    export THREAT_BACKEND_URL="${THREAT_BACKEND_URL:-http://localhost:8081}"
    export AGENT_GUARD_ENGINE_URL="${AGENT_GUARD_ENGINE_URL:-https://akto-agent-guard-engine.billing-53a.workers.dev}"
    export LOG_LEVEL=info
    export KAFKA_ENABLED=false
    export AKTO_GR_AUTHENTICATE=false
    cd "${ROOT}/apps/guardrails-service/container/src"
    go run .
  ) > "${LOG_DIR}/guardrails.log" 2>&1 &
  echo $! > "${LOG_DIR}/guardrails.pid"
}

start_data_ingestion() {
  if is_up "http://127.0.0.1:${DATA_INGESTION_PORT}/healthCheck"; then
    echo "data-ingestion already up on :${DATA_INGESTION_PORT}"
    return
  fi

  echo "Building data-ingestion-service (first run may take a few minutes) ..."
  (
    cd "${ROOT}"
    mvn -q --projects :data-ingestion-service --also-make package -DskipTests
  ) > "${LOG_DIR}/data-ingestion-build.log" 2>&1

  echo "Starting data-ingestion-service on :${DATA_INGESTION_PORT} ..."
  (
    export AKTO_MONGO_CONN
    export AKTO_TRAFFIC_BATCH_SIZE="${AKTO_TRAFFIC_BATCH_SIZE:-100}"
    export AKTO_TRAFFIC_BATCH_TIME_SECS="${AKTO_TRAFFIC_BATCH_TIME_SECS:-10}"
    export AKTO_KAFKA_BROKER_URL="${AKTO_KAFKA_BROKER_URL:-localhost:29092}"
    export AKTO_KAFKA_PRODUCER_BATCH_SIZE="${AKTO_KAFKA_PRODUCER_BATCH_SIZE:-10}"
    export AKTO_KAFKA_PRODUCER_LINGER_MS="${AKTO_KAFKA_PRODUCER_LINGER_MS:-10}"
    export AKTO_KAFKA_TOPIC="${AKTO_KAFKA_TOPIC:-${AKTO_KAFKA_TOPIC_NAME:-akto.api.logs}}"
    export GUARDRAILS_SERVICE_URL="http://127.0.0.1:${GUARDRAILS_PORT}"
    export ENABLE_GUARDRAILS="${ENABLE_GUARDRAILS:-true}"
    export RUNTIME_MODE="${RUNTIME_MODE:-HYBRID}"
    export DATABASE_ABSTRACTOR_SERVICE_URL
    export DATABASE_ABSTRACTOR_SERVICE_TOKEN
    export LOG_LEVEL="${LOG_LEVEL:-DEBUG}"
    export AKTO_DI_AUTHENTICATE="${AKTO_DI_AUTHENTICATE:-false}"
    cd "${ROOT}/apps/data-ingestion-service"
    mvn -q jetty:run -Djetty.port="${DATA_INGESTION_PORT}"
  ) > "${LOG_DIR}/data-ingestion.log" 2>&1 &
  echo $! > "${LOG_DIR}/data-ingestion.pid"
}

start_kafka() {
  if [[ "${START_KAFKA}" != "true" ]]; then
    return
  fi
  if ! docker info >/dev/null 2>&1; then
    echo "WARN: START_KAFKA=true but Docker is not running — skipping Kafka/mini-runtime"
    return
  fi
  echo "Starting Kafka (docker compose) ..."
  docker compose -f "${ROOT}/apps/guardrails-service/container/docker-compose.yml" up -d zoo1 kafka1
}

start_ngrok() {
  if [[ -z "${NGROK_AUTHTOKEN:-}" ]]; then
    echo ""
    echo "NGROK_AUTHTOKEN not set — skipping ngrok."
    echo "Cloud Dify cannot reach localhost. Run manually:"
    echo "  ngrok http ${DATA_INGESTION_PORT}"
    return
  fi

  ngrok config add-authtoken "${NGROK_AUTHTOKEN}" >/dev/null 2>&1 || true

  if pgrep -f "ngrok http ${DATA_INGESTION_PORT}" >/dev/null 2>&1; then
    echo "ngrok already running for :${DATA_INGESTION_PORT}"
  else
    echo "Starting ngrok for :${DATA_INGESTION_PORT} ..."
    if [[ -n "${NGROK_DOMAIN:-}" ]]; then
      ngrok http "${DATA_INGESTION_PORT}" --domain="${NGROK_DOMAIN}" > "${LOG_DIR}/ngrok.log" 2>&1 &
    else
      ngrok http "${DATA_INGESTION_PORT}" > "${LOG_DIR}/ngrok.log" 2>&1 &
    fi
    echo $! > "${LOG_DIR}/ngrok.pid"
    sleep 2
  fi

  NGROK_URL="$(curl -sf http://127.0.0.1:4040/api/tunnels 2>/dev/null | python3 -c "
import sys, json
try:
    d = json.load(sys.stdin)
    for t in d.get('tunnels', []):
        if t.get('proto') == 'https':
            print(t['public_url']); break
except: pass
" 2>/dev/null || true)"

  if [[ -n "${NGROK_URL}" ]]; then
    echo ""
    echo "=== Dify API Endpoint (paste in Dify cloud) ==="
    echo "${NGROK_URL}/api/http-proxy/dify"
    echo ""
  fi
}

wait_for() {
  local url="$1" label="$2" secs="${3:-120}"
  echo "Waiting for ${label} (${url}) ..."
  for _ in $(seq 1 $((secs / 2))); do
    if is_up "${url}"; then
      echo "${label} ready."
      return 0
    fi
    sleep 2
  done
  echo "Timed out waiting for ${label}. Check ${LOG_DIR}/"
  return 1
}

echo "== Dify local stack =="
echo "Dashboard:  http://localhost:8081"
echo "Cyborg:     ${DATABASE_ABSTRACTOR_SERVICE_URL}"
echo "Mongo:      ${AKTO_MONGO_CONN}"
echo ""

start_kafka
start_guardrails
start_data_ingestion

wait_for "http://127.0.0.1:${GUARDRAILS_PORT}/health" "guardrails-service" 90 || true
wait_for "http://127.0.0.1:${DATA_INGESTION_PORT}/healthCheck" "data-ingestion-service" 180

echo ""
echo "=== Local endpoints ==="
echo "Dify moderation: http://localhost:${DATA_INGESTION_PORT}/api/http-proxy/dify"
echo "Health check:    curl -X POST http://localhost:${DATA_INGESTION_PORT}/api/http-proxy/dify -H 'Content-Type: application/json' -d '{\"point\":\"ping\"}'"
echo "Logs:            ${LOG_DIR}/"
echo ""

start_ngrok
