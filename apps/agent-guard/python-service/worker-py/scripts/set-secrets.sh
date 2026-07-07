#!/usr/bin/env bash
# Push every non-empty KEY=VALUE from a vars file into a Worker's secrets.
#
#   ./scripts/set-secrets.sh                              # .dev.vars → wrangler.jsonc
#   ./scripts/set-secrets.sh .dev.vars.exec -c wrangler-exec.jsonc
#   ./scripts/set-secrets.sh ../../.env                   # use root .env directly
#
# Setup: cp ../../.env.example ../../.env && cp ../../.env .dev.vars
# See ../../ENV.md
set -euo pipefail
cd "$(dirname "$0")/.."

VARS_FILE=".dev.vars"
WRANGLER_ARGS=()
while [ $# -gt 0 ]; do
  case "$1" in
    -c|--config) WRANGLER_ARGS+=(--config "$2"); shift 2 ;;
    -*) echo "unknown flag: $1" >&2; exit 2 ;;
    *) VARS_FILE="$1"; shift ;;
  esac
done

[ -f "$VARS_FILE" ] || { echo "vars file not found: $VARS_FILE" >&2; exit 1; }

while IFS='=' read -r key val; do
  key="$(printf '%s' "$key" | xargs)"          # trim
  [ -z "$key" ] && continue
  case "$key" in \#*) continue ;; esac          # skip comments
  [ -z "$val" ] && { echo "skip (empty): $key"; continue; }
  echo "setting secret: $key"
  # ${arr[@]+...} guards empty-array expansion under `set -u` on bash 3.2 (macOS).
  printf '%s' "$val" | npx wrangler secret put "$key" ${WRANGLER_ARGS[@]+"${WRANGLER_ARGS[@]}"}
done < "$VARS_FILE"

echo "done. verify with: npx wrangler secret list ${WRANGLER_ARGS[@]+"${WRANGLER_ARGS[@]}"}"
