#!/usr/bin/env bash
# Reverts everything the scale-test created: drops the account DB and removes registry rows.
set -euo pipefail
MONGO=mongo
DIR="$(cd "$(dirname "$0")" && pwd)"
STATE="$DIR/.acct"
[[ -f "$STATE" ]] || { echo "no .acct state file; nothing to tear down"; exit 0; }
NEW_ACCT="$(cat "$STATE")"
echo "Tearing down scale-test account $NEW_ACCT ..."

# --- Elasticsearch traces cleanup (delete this account's docs; leave the shared container/index) ---
if curl -s -o /dev/null -w '' http://localhost:9200 2>/dev/null; then
  echo "Deleting ES trace docs for account $NEW_ACCT ..."
  curl -s -XPOST "http://localhost:9200/agent_query_logs/_delete_by_query?refresh=true" \
    -H 'Content-Type: application/json' -d "{\"query\":{\"term\":{\"accountId\":$NEW_ACCT}}}" >/dev/null || true
fi
# To also stop Elasticsearch entirely: docker rm -f akto-es

docker exec -i "$MONGO" mongosh --quiet "mongodb://localhost:27017/common" <<JS
db.getSiblingDB("$NEW_ACCT").dropDatabase();
db.getSiblingDB("common").accounts.deleteOne({ _id: $NEW_ACCT });
db.getSiblingDB("common").rbac.deleteMany({ accountId: $NEW_ACCT });
db.getSiblingDB("common").users.updateMany({}, { \$unset: { ["accounts.$NEW_ACCT"]: "" } });
db.getSiblingDB("billing").organizations.updateMany({}, { \$pull: { accounts: $NEW_ACCT } });
print("dropped db $NEW_ACCT and removed registry rows");
JS
rm -f "$STATE"
echo "teardown complete."
