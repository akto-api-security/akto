#!/usr/bin/env bash
# Usage: ./generate_report.sh <account_id> [output.xlsx] [mongo_container_name]
#
# Pulls every agentic testing_run_result (issues AND non-issues) for the given
# account out of the local Mongo dump, joins in the conversation transcript and
# the probe's display name/severity, and writes an .xlsx in the same layout as
# the "Akamai Prompts Red Teaming.xlsx" reference sheet.
set -euo pipefail

ACCOUNT_ID="${1:?Usage: generate_report.sh <account_id> [output.xlsx] [mongo_container_name]}"
# Defaults to the directory this is RUN from (not where the script lives) -- pass an
# absolute/relative path as $2 to override.
OUTPUT="${2:-Agentic Prompts Report - ${ACCOUNT_ID}.xlsx}"
MONGO_CONTAINER="${3:-mongo}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

docker exec -i "$MONGO_CONTAINER" mongosh --quiet --eval "$(cat "$SCRIPT_DIR/export_agentic_results.js")" "$ACCOUNT_ID" \
  | python3 "$SCRIPT_DIR/build_xlsx.py" "$OUTPUT"
