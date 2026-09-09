#!/usr/bin/env bash
# Traces (Elasticsearch) setup + scaling for the scale-test account.
# End-to-end & idempotent: ensures ES is up, index exists, exports the device roster, loads+fabricates.
#
# Usage: ./traces.sh            # full: ensure ES + index + export devices + load/fabricate
#        ./traces.sh es         # just ensure the ES container is running
#        ./traces.sh index      # (re)create the index + mapping (+ agent_query alias)
#        ./traces.sh load       # export devices + run the loader only
# Tunables (env): SESS_MIN SESS_MAX TURN_MIN TURN_MAX DAYS SEED WIPE
set -euo pipefail
MONGO=mongo
ES_CONTAINER=akto-es
ES_HOST=http://localhost:9200
INDEX=agent_query_logs
DIR="$(cd "$(dirname "$0")" && pwd)"
TDIR="$DIR/traces"
STATE="$DIR/.acct"
[[ -f "$STATE" ]] || { echo "no .acct (run ./run.sh first)"; exit 1; }
ACCOUNT="$(cat "$STATE")"
PHASE="${1:-all}"

ensure_es () {
  if ! curl -s -o /dev/null -w '' "$ES_HOST" 2>/dev/null; then
    if ! docker ps --format '{{.Names}}' | grep -q "^${ES_CONTAINER}$"; then
      echo "--- starting Elasticsearch container ($ES_CONTAINER) ---"
      docker rm -f "$ES_CONTAINER" >/dev/null 2>&1 || true
      docker run -d --name "$ES_CONTAINER" -p 9200:9200 \
        -e discovery.type=single-node -e xpack.security.enabled=false \
        -e "ES_JAVA_OPTS=-Xms512m -Xmx512m" elasticsearch:7.17.22 >/dev/null
    fi
    echo -n "waiting for ES"
    for i in $(seq 1 40); do
      [ "$(curl -s -o /dev/null -w '%{http_code}' "$ES_HOST" 2>/dev/null || echo 000)" = "200" ] && { echo " up"; break; }
      echo -n "."; sleep 3
    done
  fi
  curl -s "$ES_HOST" | python3 -c "import json,sys;print('ES version',json.load(sys.stdin)['version']['number'])"
}

ensure_index () {
  echo "--- ensuring index $INDEX (mapping + agent_query alias) ---"
  python3 - "$INDEX" "$ES_HOST" "$TDIR/mapping.json" <<'PY'
import json,sys,urllib.request,urllib.error
index,host,mapping_path=sys.argv[1],sys.argv[2],sys.argv[3]
props=json.load(open(mapping_path))['agent_query_logs']['mappings']['properties']
def es(m,p,b=None):
    data=json.dumps(b).encode() if b is not None else None
    try: return urllib.request.urlopen(urllib.request.Request(host+p,data=data,method=m,headers={'Content-Type':'application/json'})).read().decode()
    except urllib.error.HTTPError as e: return 'ERR %d %s'%(e.code,e.read().decode()[:200])
exists=es('HEAD','/'+index)
if not str(exists).startswith('ERR'):
    print('  index exists, keeping it')
else:
    body={'settings':{'number_of_shards':1,'number_of_replicas':0,'index.mapping.total_fields.limit':2000},'mappings':{'properties':props}}
    print('  create:',es('PUT','/'+index,body)[:120])
es('POST','/_aliases',{'actions':[{'add':{'index':index,'alias':'agent_query'}}]})
PY
}

export_devices () {
  echo "--- exporting device roster for account $ACCOUNT ---"
  docker exec "$MONGO" mongosh "$ACCOUNT" --quiet --eval '
    var out=[];
    db.module_info.find({moduleType:"MCP_ENDPOINT_SHIELD"}).forEach(function(d){
      var ad=d.additionalData||{}; var svcs=[];
      if(ad.mcpServers){ for(var k in ad.mcpServers){ var ct=ad.mcpServers[k].clientType; if(ct) svcs.push(ct); } }
      out.push({host:d.name, user:ad.username||"", hw:ad.deviceId||"", services:Array.from(new Set(svcs))});
    });
    print(JSON.stringify(out));' > "$TDIR/devices.json"
  python3 -c "import json;print('  devices:',len(json.load(open('$TDIR/devices.json'))))"
}

load () {
  echo "--- loading + fabricating traces into ES (account $ACCOUNT) ---"
  ACCOUNT="$ACCOUNT" INDEX="$INDEX" ES_HOST="$ES_HOST" python3 "$DIR/05_load_traces.py"
}

case "$PHASE" in
  es) ensure_es ;;
  index) ensure_es; ensure_index ;;
  load) export_devices; load ;;
  all) ensure_es; ensure_index; export_devices; load ;;
  *) echo "unknown phase: $PHASE"; exit 1 ;;
esac
echo "=== traces done for account $ACCOUNT (index $INDEX). Ensure run-master.sh has ES_HOST=$ES_HOST & ES_INDEX_AGENT_QUERY=$INDEX ==="
