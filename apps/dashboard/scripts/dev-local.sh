#!/usr/bin/env bash
# Start Akto dashboard (Jetty 12 EE8) for local UI development.
# Run frontend separately: cd apps/dashboard/web/polaris_web && npm run hot
#
# Env: apps/dashboard/scripts/config.env (copy from config.env.example if missing)
# Port: set JETTY_PORT (or jetty.port) externally — default 8081.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "${SCRIPT_DIR}/../../.." && pwd)"
DASHBOARD_DIR="${ROOT}/apps/dashboard"

# shellcheck disable=SC1091
source "${SCRIPT_DIR}/load-config-env.sh"

export AKTO_MONGO_CONN="${AKTO_MONGO_CONN:-mongodb://localhost:27017/admini}"
export DASHBOARD_MODE="${DASHBOARD_MODE:-local_deploy}"
PORT="${JETTY_PORT:-8081}"

# Unset NODE_ENV so login.jsp serves /polaris_web/web/dist/ (npm run hot), not localhost:3000.
unset NODE_ENV

echo "Dashboard: http://localhost:${PORT}"
echo "Mongo:     ${AKTO_MONGO_CONN}"
echo "Mode:      ${DASHBOARD_MODE}"
echo "Frontend:  cd apps/dashboard/web/polaris_web && npm run hot"
echo ""

cd "${ROOT}"

# Build dashboard + deps from repo root.
mvn --projects :dashboard --also-make package -DskipTests

# Run Jetty from the dashboard module so the EE8 plugin resolves.
# Use the fully-qualified plugin (prefix "jetty-ee8" is not in Maven's default pluginGroups).
cd "${DASHBOARD_DIR}"
exec mvn org.eclipse.jetty.ee8:jetty-ee8-maven-plugin:12.0.37:run \
  -Djetty.port="${PORT}" \
  -DskipTests
