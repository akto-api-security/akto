#!/usr/bin/env bash
set -euo pipefail

export AKTO_MONGO_CONN=${AKTO_MONGO_CONN:-"mongodb://localhost:27017"}
export AKTO_LOG_LEVEL=${AKTO_LOG_LEVEL:-DEBUG}

echo "AKTO_MONGO_CONN=$AKTO_MONGO_CONN"

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR"

# Use no-auth web.xml for local runs
WEB_DIR="$ROOT_DIR/apps/database-abstractor/web/WEB-INF"
AUTH_XML="$WEB_DIR/web.xml"
NOAUTH_XML="$WEB_DIR/web.local-noauth.xml"
BACKUP_XML="$WEB_DIR/web.auth-backup.xml"

if [[ -f "$NOAUTH_XML" ]]; then
  echo "Using no-auth web descriptor"
  if [[ -f "$AUTH_XML" ]]; then
    cp -f "$AUTH_XML" "$BACKUP_XML"
  fi
  cp -f "$NOAUTH_XML" "$AUTH_XML"
  trap '[[ -f "$BACKUP_XML" ]] && cp -f "$BACKUP_XML" "$AUTH_XML" && rm -f "$BACKUP_XML"' EXIT
else
  echo "WARN: $NOAUTH_XML not found; auth may block requests"
fi

mvn --projects :database-abstractor --also-make -DskipTests=true jetty:run -Djetty.port=9000 | cat