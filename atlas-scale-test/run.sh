#!/usr/bin/env bash
# ATLAS device scale-test orchestrator.
# Usage: ./run.sh [TARGET_DEVICES] [PHASE]
#   TARGET_DEVICES : total endpoint-shield devices desired (default 50; try 100/500/1000)
#   PHASE          : all (default) | setup | copy | fabricate | verify
#
# Creates/reuses a throwaway account (id = current epoch, saved in .acct) that is a copy of
# SRC_ACCT, then fabricates devices up to TARGET. Original accounts are never modified.
set -euo pipefail

MONGO=mongo                       # docker container name
SRC_ACCT=1784850613               # Acorns Demo (source)
OTHER_ACCT=1000000                # My account (NHI enrichment)
ACCT_NAME="Atlas Scale Test"
SEED=1337
# NHI enrichment tunables (04_enrich_nhi.js)
NHI_PER_MIN="${NHI_PER_MIN:-8}"      # min identities per device
NHI_PER_MAX="${NHI_PER_MAX:-20}"     # max identities per device
NHI_VIOL_RATIO="${NHI_VIOL_RATIO:-0.45}"  # fraction of identity-names that carry violations
DIR="$(cd "$(dirname "$0")" && pwd)"
STATE="$DIR/.acct"

TARGET="${1:-50}"
PHASE="${2:-all}"

# Stable account id across re-runs (current epoch on first run, reused after).
if [[ -f "$STATE" ]]; then NEW_ACCT="$(cat "$STATE")"; else NEW_ACCT="$(date +%s)"; echo "$NEW_ACCT" > "$STATE"; fi
NOW="$(date +%s)"

# Collections excluded from the base copy (giant / unrelated logs & hit-counts).
EXCLUDES=(--excludeCollectionsWithPrefix=logs_
  --excludeCollection=api_audit_logs
  --excludeCollection=api_hit_count_info
  --excludeCollection=metrics_data
  --excludeCollection=agent_conversation_results
  --excludeCollection=crawler_urls
  --excludeCollection=protection_logs)

echo "=== ATLAS scale | acct=$NEW_ACCT name='$ACCT_NAME' src=$SRC_ACCT target=$TARGET phase=$PHASE ==="

run_js () { # <db> <configHeader> <jsfile>
  { printf '%s\n' "$2"; cat "$3"; } | docker exec -i "$MONGO" mongosh --quiet "mongodb://localhost:27017/$1"
}

phase_setup () {
  echo "--- [setup] registering account ---"
  run_js common "var NEW_ACCT=$NEW_ACCT; var ACCT_NAME='$ACCT_NAME'; var SRC_ACCT=$SRC_ACCT;" "$DIR/01_setup.js"
}

phase_copy () {
  echo "--- [copy] $SRC_ACCT -> $NEW_ACCT (excluding giant log collections) ---"
  docker exec "$MONGO" sh -c "mongodump --quiet --db=$SRC_ACCT --archive ${EXCLUDES[*]} | mongorestore --quiet --archive --nsInclude='$SRC_ACCT.*' --nsFrom='$SRC_ACCT.*' --nsTo='$NEW_ACCT.*' --drop"
  echo "copy done."
}

phase_fabricate () {
  echo "--- [fabricate] scaling to $TARGET devices ---"
  run_js "$NEW_ACCT" "var TARGET=$TARGET; var OTHER=$OTHER_ACCT; var NOW=$NOW; var SEED=$SEED;" "$DIR/02_fabricate.js"
}

phase_trim () {
  echo "--- [trim] reducing to $TARGET devices ---"
  run_js "$NEW_ACCT" "var TARGET=$TARGET; var SRC=$SRC_ACCT;" "$DIR/08_trim_devices.js"
}

phase_users () {
  echo "--- [users] rebuilding one unique user per device (cleanup + repopulate) ---"
  run_js "$NEW_ACCT" "" "$DIR/09_rebuild_users.js"
}

phase_nhi () {
  echo "--- [nhi] regenerating rich NHI data ---"
  run_js "$NEW_ACCT" "var OTHER=$OTHER_ACCT; var NOW=$NOW; var SEED=$SEED; var PER_MIN=$NHI_PER_MIN; var PER_MAX=$NHI_PER_MAX; var VIOL_RATIO=$NHI_VIOL_RATIO;" "$DIR/04_enrich_nhi.js"
}

phase_verify () {
  echo "--- [verify] ---"
  run_js "$NEW_ACCT" "" "$DIR/03_verify.js"
}

case "$PHASE" in
  setup) phase_setup ;;
  copy) phase_copy ;;
  fabricate) phase_fabricate ;;
  trim) phase_trim ;;
  users) phase_users ;;
  nhi) phase_nhi ;;
  verify) phase_verify ;;
  all) phase_setup; phase_copy; phase_fabricate; phase_nhi; phase_verify ;;
  *) echo "unknown phase: $PHASE"; exit 1 ;;
esac
echo "=== done. Account id: $NEW_ACCT  (switch to '$ACCT_NAME' in the dashboard) ==="
