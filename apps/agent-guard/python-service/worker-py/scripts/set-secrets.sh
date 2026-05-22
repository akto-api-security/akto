#!/usr/bin/env bash
# Push every non-empty KEY=VALUE from a vars file into the Worker's secrets,
# so no credentials live in committed config. Targets the worker named in
# wrangler.jsonc (use --env / a different wrangler.jsonc for staging vs prod).
#
#   ./scripts/set-secrets.sh              # reads .dev.vars
#   ./scripts/set-secrets.sh prod.vars    # reads a different file
#
# Requires: wrangler auth (CLOUDFLARE_API_TOKEN or `wrangler login`).
set -euo pipefail
cd "$(dirname "$0")/.."

VARS_FILE="${1:-.dev.vars}"
[ -f "$VARS_FILE" ] || { echo "vars file not found: $VARS_FILE" >&2; exit 1; }

while IFS='=' read -r key val; do
  key="$(printf '%s' "$key" | xargs)"          # trim
  [ -z "$key" ] && continue
  case "$key" in \#*) continue ;; esac          # skip comments
  [ -z "$val" ] && { echo "skip (empty): $key"; continue; }
  echo "setting secret: $key"
  printf '%s' "$val" | npx wrangler secret put "$key"
done < "$VARS_FILE"

echo "done. verify with: npx wrangler secret list"
