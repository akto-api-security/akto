#!/usr/bin/env bash
# One-shot rescale of the scale-test account to N devices across ALL three data sources
# (regular mongo + NHI, elasticsearch traces, threat mongo). Works UP or DOWN.
#
#   ./rescale.sh 500          # scale to 500 devices everywhere
#   ./rescale.sh 1000         # scale to 1000
#   ./rescale.sh 100          # scale back down to 100
#   ./rescale.sh 50           # down to 50
#
# High device counts: lower the graph-heavy per-device knobs, e.g.
#   NHI_PER_MIN=3 NHI_PER_MAX=6 SESS_MIN=2 SESS_MAX=3 TURN_MIN=2 TURN_MAX=5 ./rescale.sh 1000
# (those env vars pass through to the NHI and traces scalers.)
set -euo pipefail
N="${1:?usage: ./rescale.sh <deviceCount>}"
DIR="$(cd "$(dirname "$0")" && pwd)"
MONGO=mongo
[[ -f "$DIR/.acct" ]] || { echo "No .acct yet — run './run.sh $N' once to create the account first."; exit 1; }
ACCT="$(cat "$DIR/.acct")"

cur=$(docker exec "$MONGO" mongosh "$ACCT" --quiet --eval 'print(db.module_info.countDocuments({moduleType:"MCP_ENDPOINT_SHIELD"}))')
echo "=== rescale account $ACCT: current=$cur target=$N ==="

# 1) devices (regular mongo) — up = fabricate, down = trim
if   [ "$N" -gt "$cur" ]; then "$DIR/run.sh" "$N" fabricate
elif [ "$N" -lt "$cur" ]; then "$DIR/run.sh" "$N" trim
else echo "--- devices already at $N ---"; fi

# 2) users (regular mongo) — rebuild exactly one unique user per device (cleanup + repopulate)
#    runs BEFORE traces/threat so the deduped usernames propagate to those sources
"$DIR/run.sh" "$N" users

# 3) NHI (regular mongo) — wipe+regenerate for the current roster
"$DIR/run.sh" "$N" nhi

# 3) ES traces — re-export roster + wipe+regenerate
"$DIR/traces.sh" load

# 4) threat/guardrail mongo — clean fabricated + regenerate for current roster
"$DIR/threat.sh" scale

# 5) report
"$DIR/run.sh" "$N" verify
echo "=== rescaled account $ACCT to $N devices across mongo + NHI + ES + threat ==="
