#!/usr/bin/env bash
# Standard local data-ingestion-service startup (matches team IntelliJ run config).
# Requires: Java 8, Kafka on localhost:29092, guardrails on localhost:9091
#
# Usage:
#   apps/dashboard/scripts/start-data-ingestion-local.sh
#   # or source env and run mvn manually (see exports below)

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ENV_FILE="${SCRIPT_DIR}/dify-local.env"

if [[ -f "${ENV_FILE}" ]]; then
  # shellcheck disable=SC1090
  source "${ENV_FILE}"
fi

export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 1.8 2>/dev/null || true)}"
if [[ -n "${JAVA_HOME}" ]]; then
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

export AKTO_MONGO_CONN="${AKTO_MONGO_CONN:-mongodb://localhost:27017/admini}"
export AKTO_TRAFFIC_BATCH_SIZE="${AKTO_TRAFFIC_BATCH_SIZE:-100}"
export AKTO_TRAFFIC_BATCH_TIME_SECS="${AKTO_TRAFFIC_BATCH_TIME_SECS:-10}"
export AKTO_KAFKA_BROKER_URL="${AKTO_KAFKA_BROKER_URL:-localhost:29092}"
export AKTO_KAFKA_PRODUCER_BATCH_SIZE="${AKTO_KAFKA_PRODUCER_BATCH_SIZE:-10}"
export AKTO_KAFKA_PRODUCER_LINGER_MS="${AKTO_KAFKA_PRODUCER_LINGER_MS:-10}"
# data-ingestion-service reads AKTO_KAFKA_TOPIC (not AKTO_KAFKA_TOPIC_NAME)
export AKTO_KAFKA_TOPIC="${AKTO_KAFKA_TOPIC:-${AKTO_KAFKA_TOPIC_NAME:-akto.api.logs}}"
export GUARDRAILS_SERVICE_URL="${GUARDRAILS_SERVICE_URL:-http://localhost:9091}"
export LOG_LEVEL="${LOG_LEVEL:-DEBUG}"
export ENABLE_GUARDRAILS="${ENABLE_GUARDRAILS:-true}"
export DATABASE_ABSTRACTOR_SERVICE_URL="${DATABASE_ABSTRACTOR_SERVICE_URL:-http://localhost:8082}"
export DATABASE_ABSTRACTOR_SERVICE_TOKEN="${DATABASE_ABSTRACTOR_SERVICE_TOKEN:-}"
export RUNTIME_MODE="${RUNTIME_MODE:-HYBRID}"
export AKTO_DI_AUTHENTICATE="${AKTO_DI_AUTHENTICATE:-false}"
export DATA_INGESTION_PORT="${DATA_INGESTION_PORT:-8090}"

cd "${ROOT}"
exec mvn --projects :data-ingestion-service --also-make jetty:run -Djetty.port="${DATA_INGESTION_PORT}"
