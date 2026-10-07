#!/usr/bin/env bash
# Start dashboard backend + polaris frontend (background). Safe to re-run.
# Logs: .cursor/dev-logs/  PIDs: .cursor/dev-logs/*.pid

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/load-config-env.sh"

PORT="${JETTY_PORT:-8081}"
LOG_DIR="${ROOT}/.cursor/dev-logs"
POLARIS="${ROOT}/apps/dashboard/web/polaris_web"
BACKEND_PID="${LOG_DIR}/jetty-${PORT}.pid"
FRONTEND_PID="${LOG_DIR}/polaris-hot.pid"

mkdir -p "${LOG_DIR}"

export AKTO_MONGO_CONN="${AKTO_MONGO_CONN:-mongodb://localhost:27017/admini}"
export DASHBOARD_MODE="${DASHBOARD_MODE:-local_deploy}"
unset NODE_ENV

is_up() {
  curl -sf -o /dev/null --max-time 2 "http://localhost:${PORT}/" 2>/dev/null
}

start_if_dead() {
  local pid_file="$1"
  if [[ -f "${pid_file}" ]] && kill -0 "$(cat "${pid_file}")" 2>/dev/null; then
    return 0
  fi
  return 1
}

echo "== Akto dashboard dev stack =="

if is_up; then
  echo "Jetty already up: http://localhost:${PORT}"
elif start_if_dead "${BACKEND_PID}"; then
  echo "Jetty already running (pid $(cat "${BACKEND_PID}"))"
else
  echo "Starting Jetty on :${PORT} ..."
  nohup "${ROOT}/apps/dashboard/scripts/dev-local.sh" > "${LOG_DIR}/jetty.log" 2>&1 &
  echo $! > "${BACKEND_PID}"
fi

if start_if_dead "${FRONTEND_PID}"; then
  echo "polaris hot already running (pid $(cat "${FRONTEND_PID}"))"
else
  echo "Starting npm run hot ..."
  (
    cd "${POLARIS}"
    nohup npm run hot > "${LOG_DIR}/polaris-hot.log" 2>&1
  ) &
  echo $! > "${FRONTEND_PID}"
fi

echo "Waiting for http://localhost:${PORT} (up to 180s; first mvn start can be slow) ..."
for i in $(seq 1 90); do
  if is_up; then
    echo "Ready: http://localhost:${PORT}"
    echo "Logs: ${LOG_DIR}/jetty.log  ${LOG_DIR}/polaris-hot.log"
    exit 0
  fi
  sleep 2
done

echo "Timed out. Check ${LOG_DIR}/jetty.log"
exit 1
