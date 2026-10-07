#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
LOG_DIR="${ROOT}/.cursor/dev-logs"
PORT="${JETTY_PORT:-8081}"

stop_pid_file() {
  local f="$1"
  local name="$2"
  if [[ -f "${f}" ]]; then
    local pid
    pid="$(cat "${f}")"
    if kill -0 "${pid}" 2>/dev/null; then
      echo "Stopping ${name} (pid ${pid})"
      kill "${pid}" 2>/dev/null || true
    fi
    rm -f "${f}"
  fi
}

stop_pid_file "${LOG_DIR}/jetty-${PORT}.pid" "Jetty"
stop_pid_file "${LOG_DIR}/polaris-hot.pid" "polaris hot"
echo "Done."
