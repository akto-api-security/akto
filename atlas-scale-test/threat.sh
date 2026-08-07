#!/usr/bin/env bash
# Threat/guardrail data (malicious_events, actor_info, ...) copy + scale for the scale-test account.
#   copy  : copy threat collections from SRC account into the scale account (one-time, after tbs restore)
#   scale : clone per-device guardrail events + add volume actors/events (idempotent)
#   verify: summary counts
#   all   : copy + scale + verify
# Tunables (env): EXTRA_ACTORS (default 250), SEED
set -euo pipefail
MONGO=mongo
SRC_ACCT=1784850613
SEED=1337
EXTRA_ACTORS="${EXTRA_ACTORS:-250}"
DIR="$(cd "$(dirname "$0")" && pwd)"
STATE="$DIR/.acct"
[[ -f "$STATE" ]] || { echo "no .acct (run ./run.sh first)"; exit 1; }
NEW_ACCT="$(cat "$STATE")"
NOW="$(date +%s)"
PHASE="${1:-all}"

# Threat collections that drive the dashboard pages (api_distribution_data feeds only a rate-limit
# cron -> not scaled here; copy it once if you want rate-limit stats, else skip for speed).
COLLS=(malicious_events archived_malicious_events actor_info aggregate_sample_malicious_requests threat_configuration acto_info splunk_integration_config)

run_js () { { printf '%s\n' "$2"; cat "$3"; } | docker exec -i "$MONGO" mongosh --quiet "mongodb://localhost:27017/$1"; }

phase_copy () {
  echo "--- [copy] threat collections $SRC_ACCT -> $NEW_ACCT ---"
  for c in "${COLLS[@]}"; do
    docker exec "$MONGO" sh -c "mongodump --quiet --db=$SRC_ACCT --collection=$c --archive | mongorestore --quiet --archive --nsFrom='$SRC_ACCT.*' --nsTo='$NEW_ACCT.*' --drop" 2>&1 | grep -iE "error|fail" || true
    echo "  copied $c"
  done
}

phase_scale () {
  echo "--- [scale] threat/guardrail data (extra actors=$EXTRA_ACTORS) ---"
  run_js "$NEW_ACCT" "var SRC=$SRC_ACCT; var NOW=$NOW; var SEED=$SEED; var EXTRA_ACTORS=$EXTRA_ACTORS;" "$DIR/07_scale_threat.js"
}

phase_verify () {
  echo "--- [verify] ---"
  docker exec "$MONGO" mongosh "$NEW_ACCT" --quiet --eval '
    print("malicious_events: "+db.malicious_events.countDocuments({}));
    print("actor_info: "+db.actor_info.countDocuments({}));
    print("device-linked events: "+db.malicious_events.countDocuments({host:/-macbook-/}));
    print("distinct actors: "+db.malicious_events.distinct("actor").length+" | distinct countries: "+db.actor_info.distinct("country").length);
    print("by contextSource:"); db.malicious_events.aggregate([{$group:{_id:"$contextSource",n:{$sum:1}}}]).forEach(d=>print("  "+d._id+": "+d.n));
    print("by severity:"); db.malicious_events.aggregate([{$group:{_id:"$severity",n:{$sum:1}}}]).forEach(d=>print("  "+d._id+": "+d.n));'
}

case "$PHASE" in
  copy) phase_copy ;;
  scale) phase_scale ;;
  verify) phase_verify ;;
  all) phase_copy; phase_scale; phase_verify ;;
  *) echo "unknown phase: $PHASE"; exit 1 ;;
esac
echo "=== threat done for account $NEW_ACCT. NOTE: the dashboard reads threat data via the"
echo "    threat-detection-backend (THREAT_DETECTION_BACKEND_URL, e.g. http://localhost:9090)."
echo "    That service must be running and its AKTO_THREAT_PROTECTION_MONGO_CONN must point at THIS mongo. ==="
