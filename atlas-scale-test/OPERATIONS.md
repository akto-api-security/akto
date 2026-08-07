# ATLAS scale-test — Operations (rescale + prod transfer)

All commands run from `atlas-scale-test/`. The scale account id is stored in `.acct` (created on first
`run.sh`) and reused by every script. Current local account: see `cat .acct`.

Data lives in 3 places (all keyed by device — see `SCALING.md`):
- **regular mongo** — account db (devices, assets, NHI) in the local `mongo` container
- **threat mongo** — threat collections in the same account db, read by the TBS service (`run-tbs.sh`)
- **elasticsearch** — `agent_query_logs` in the local `akto-es` container (traces)

---

# 1) Rescaling (50 / 100 / 500 / 1000 — up or down)

### One-shot (recommended) — rescales ALL three sources
```bash
./rescale.sh 500      # -> 500 devices everywhere
./rescale.sh 1000     # -> 1000
./rescale.sh 100      # back down to 100
./rescale.sh 50       # down to 50
```
`rescale.sh` figures out up vs down automatically:
- **up**   → `run.sh N fabricate` (adds devices incrementally; existing devices untouched)
- **down** → `run.sh N trim` (removes excess *fabricated* devices + their full mongo footprint; the
  original source/template devices are never removed)
then always re-runs **NHI**, **traces**, and **threat** (each wipes+regenerates for the *current*
device roster) and prints a verify report. Same account id throughout — no dashboard re-point needed.

**High counts — lower the graph-heavy per-device knobs** (the NHI node graph and ES topic graph draw
every identity/trace):
```bash
NHI_PER_MIN=3 NHI_PER_MAX=6 SESS_MIN=2 SESS_MAX=3 TURN_MIN=2 TURN_MAX=5 ./rescale.sh 1000
```

### Manual, per source (if you only want to touch one)
```bash
./run.sh 500 fabricate     # devices UP to 500 (incremental)
./run.sh 100 trim          # devices DOWN to 100
./run.sh 500 nhi           # regenerate NHI for current roster
./traces.sh load           # regenerate ES traces for current roster
./threat.sh scale          # regenerate threat/guardrail for current roster
./run.sh 500 verify        # coverage report
```
Order for a manual UP: `fabricate` → `nhi` → `traces.sh load` → `threat.sh scale`
(fabricate must run first because NHI/traces/threat derive the device list from `module_info`).

### Full clean rebuild at N (fresh account id)
```bash
./teardown.sh              # drop account DB + registry rows + this account's ES/threat docs
./run.sh 100               # setup + copy + fabricate + nhi + verify (new epoch account id)
./traces.sh && ./threat.sh all
```
Use only if you want a brand-new account; otherwise prefer `rescale.sh` (keeps the id).

### Rough volume per target (see SCALING.md for the full table)
| target | mongo device docs | threat events | ES docs | wall-clock* |
|---|--:|--:|--:|--:|
| 100  | ~0.6M | ~10k | ~8k  | ~2–3 min |
| 500  | ~0.6M | ~24k | ~35k | ~4–6 min |
| 1000 | ~1.2M | ~42k | ~70k | ~8–12 min |
\* approximate on local docker; dominated by `single_type_info` inserts and the mongo base copy.

---

# 2) Transfer the finalized dataset to PROD (dev account `1750019989`) — manual runbook

Prod mongo/ES are NOT directly reachable — they sit behind a **jump box**, and there are **three
separate prod VMs**. Run the commands below **by hand**, in order, on the machine named in each heading.
Replace `1785786030` with your local account id (`cat atlas-scale-test/.acct`) if different.

| target | prod VM | ssh key (lives ON the jump box) | collections |
|---|---|---|---|
| dashboard mongo | `10.2.32.9`  | `prod-mongo-ssh-key.pem`               | regular ATLAS collections |
| threat mongo (TBS) | `10.2.32.14` | `prod-dashboard-vmss-central-key.pem`  | malicious_events, actor_info, … |
| elasticsearch | `10.2.32.29` | `prod-mongo-ssh-key.pem`               | agent_query_logs (traces) |
| jump box | `20.98.156.7` | `~/.ssh/ssh-box-key.pem` (on your laptop) | — |

**Concept:** mirror of your prod→local dump, reversed. Mongo scopes an account by **db name**, so the
only change vs a normal restore is `--nsFrom='1785786030.*' --nsTo='1750019989.*'` (rename db) — no
per-doc edits. Locally both regular + threat live in one db `1785786030`, so we dump it **once**; the
**dashboard mongo restores everything except logs + threat-only collections** (an *omit* list, so no
ATLAS collection can be missed), and the **threat mongo restores just the threat slice** via
`--nsInclude`. ES scopes by an `accountId` **field**, so the trace file's `accountId` is rewritten to
`1750019989` before import. `1750019989` is empty, so `--drop` is safe.

> **Two ATLAS collections are NOT in the local account** (they were excluded when it was built):
> `agent_conversation_results` and `metrics_data` (the latter drives the Guardrail-latency chart). If you
> want them on prod, first copy them into the local account from the source, then re-dump:
> ```bash
> for c in agent_conversation_results metrics_data; do
>   docker exec mongo sh -c "mongodump --db 1784850613 --collection $c --archive | mongorestore --archive --nsFrom='1784850613.*' --nsTo='1785786030.*'"
> done
> ```

---
## A. MONGO — dashboard restore (omit logs + threat) + threat slice

### A1 — on your LOCAL machine
```bash
# dump the local scale account, SKIPPING the junk at dump time so the archive (and the scp) stays lean.
# --excludeCollection is repeatable, so we drop logs + the two bulky non-page collections here rather than
# shipping 5M+ docs only to exclude them at restore. Everything else — all ATLAS + API-security/testing
# + the (small) threat collections — stays in, so nothing ATLAS-relevant can be missed.
docker exec mongo mongodump --db 1785786030 --archive=/1785786030.archive --gzip \
  `# logs (junk — needed by neither mongo) ` \
  --excludeCollection=logs          --excludeCollection=logs_cyborg \
  --excludeCollection=logs_dashboard --excludeCollection=logs_runtime \
  --excludeCollection=protection_logs --excludeCollection=api_audit_logs \
  --excludeCollection=file_upload_logs \
  `# bulky, no visible page: api_distribution_data (5M, rate-limit cron only) ` \
  --excludeCollection=api_distribution_data \
  `# per-account INFRA keyed by _id==accountId: renaming the db does NOT rewrite the _id inside these ` \
  `# docs, so after transfer findOne(_id==newAccountId) returns null and derefs NPE (e.g. the inventory ` \
  `# page via AccountSettingsDao.getLastCronRunInfo). They also hold env-specific config (init stack, ` \
  `# integrations, telemetry, cron timers) — never carry them; the target account keeps its own. ` \
  --excludeCollection=accounts_settings --excludeCollection=aws_resources
docker cp mongo:/1785786030.archive .

# ship the archive to the jump box
scp -i ~/.ssh/ssh-box-key.pem 1785786030.archive azureuser@20.98.156.7:/home/azureuser/
```

### A2 — on the JUMP BOX (`ssh -i ~/.ssh/ssh-box-key.pem azureuser@20.98.156.7`)
```bash
# push the same archive to BOTH mongo VMs (each uses its own key)
scp -i prod-mongo-ssh-key.pem              1785786030.archive azureuser@10.2.32.9:/home/azureuser/
scp -i prod-dashboard-vmss-central-key.pem 1785786030.archive azureuser@10.2.32.14:/home/azureuser/
```

### A3 — on the DASHBOARD MONGO VM (from jump box: `ssh -i prod-mongo-ssh-key.pem azureuser@10.2.32.9`)
```bash
# logs + api_distribution_data are already absent from the archive (skipped at dump), so we only need to
# hold back the threat-only collections here — they go to the threat mongo (A4) instead. Everything else —
# every ATLAS collection (guardrail_policies, agentic_session_context, agent_query_data, query_topic_cache,
# spans, traces, testing_run_issues, file_inspection_*, mcp_recon_requests, module_info, api_collections,
# single_type_info, api_info, sample_data, sensitive_sample_data, traffic_info, endpoint_mcp_config,
# mcp_audit_info, agent_users, user_analysis_data, nhi_*, …) — transfers automatically.
docker cp /home/azureuser/1785786030.archive mongo:/
docker exec mongo mongorestore --archive=/1785786030.archive --gzip \
  `# threat-only → threat mongo instead (agentic_session_context is dual-home, kept here) ` \
  --nsExclude='1785786030.aggregate_sample_malicious_requests' \
  --nsExclude='1785786030.malicious_events'  --nsExclude='1785786030.archived_malicious_events' \
  --nsExclude='1785786030.actor_info'        --nsExclude='1785786030.threat_configuration' \
  --nsExclude='1785786030.splunk_integration_config' --nsExclude='1785786030.acto_info' \
  --nsFrom='1785786030.*' --nsTo='1750019989.*' --drop
```

### A4 — on the THREAT MONGO VM (from jump box: `ssh -i prod-dashboard-vmss-central-key.pem azureuser@10.2.32.14`)
```bash
docker cp /home/azureuser/1785786030.archive mongo:/
# restore ONLY the threat collections (the tbs set), renamed to db 1750019989
docker exec mongo mongorestore --archive=/1785786030.archive --gzip \
  --nsInclude='1785786030.malicious_events' \
  --nsInclude='1785786030.archived_malicious_events' \
  --nsInclude='1785786030.actor_info' \
  --nsInclude='1785786030.aggregate_sample_malicious_requests' \
  --nsInclude='1785786030.agentic_session_context' \
  --nsInclude='1785786030.threat_configuration' \
  --nsInclude='1785786030.splunk_integration_config' \
  --nsFrom='1785786030.*' --nsTo='1750019989.*' --drop
```
> Threat set = the tbs collections that back a visible threat page: `actor_info, agentic_session_context,
> aggregate_sample_malicious_requests, archived_malicious_events, malicious_events,
> splunk_integration_config, threat_configuration`. `agentic_session_context` is dual-home (A3 also lands
> it on the dashboard mongo), matching the source.
> **Skipped at dump** (not in the archive, so absent from both mongos): `api_distribution_data` (5M,
> rate-limit percentile cron only — no page) and `logs_runtime` (a log). If you later want rate-limit
> stats or runtime logs on prod, drop the matching `--excludeCollection` from A1 and add an `--nsInclude`
> here.

> **Already ran a transfer that included `accounts_settings`?** Symptom: `NullPointerException` on
> `/dashboard/observe/inventory` (and other pages) for the target account. The transferred doc still
> carries the *source* `_id`, so `AccountSettingsDao.findOne(_id==targetAccountId)` returns null. Fix in
> the dashboard mongo's `mongosh`:
> ```js
> use 1750019989
> var d = db.accounts_settings.findOne({_id: 1785786030});   // source account id
> db.accounts_settings.deleteMany({});
> if (d) { d._id = NumberInt(1750019989); db.accounts_settings.insertOne(d); }
> ```

---
## B. ELASTICSEARCH — export (remap accountId) → ship → import

### B1 — on your LOCAL machine
```bash
# export local traces for our account with elasticdump (local ES has no auth)
docker run --rm --network host -v "$PWD:/data" elasticdump/elasticsearch-dump \
  --input="http://localhost:9200/agent_query_logs" \
  --output="/data/1785786030.es.ndjson" --type=data \
  --searchBody='{"query":{"term":{"accountId":1785786030}}}' --limit=1000 --scrollTime=10m

# rewrite accountId 1785786030 -> 1750019989 (only the accountId field carries this number)
perl -pi -e 's/"accountId":\s*1785786030/"accountId":1750019989/g' 1785786030.es.ndjson
grep -c '"accountId":1750019989' 1785786030.es.ndjson    # should equal the doc count
grep -c '"accountId":1785786030' 1785786030.es.ndjson    # must be 0

# ship to the jump box
scp -i ~/.ssh/ssh-box-key.pem 1785786030.es.ndjson azureuser@20.98.156.7:/home/azureuser/
```

### B2 — on the JUMP BOX
```bash
scp -i prod-mongo-ssh-key.pem 1785786030.es.ndjson azureuser@10.2.32.29:/home/azureuser/
```

### B3 — on the ES VM (from jump box: `ssh -i prod-mongo-ssh-key.pem azureuser@10.2.32.29`)
```bash
export ES_USER=elastic ES_PASSWORD='***'          # prod ES basic-auth password
BASIC_AUTH=$(printf '%s:%s' "$ES_USER" "$ES_PASSWORD" | base64 | tr -d '\n')

# (OPTIONAL, for a clean re-load) delete ONLY our account's docs first — scoped, never global:
curl -s -XPOST "http://localhost:9200/agent_query_logs/_delete_by_query" \
  -H "Authorization: Basic ${BASIC_AUTH}" -H 'Content-Type: application/json' \
  -d '{"query":{"term":{"accountId":1750019989}}}'

# import the traces (additive; index/mapping already exist in prod, so --type=data only)
docker run --rm --network host -v "/home/azureuser:/data" elasticdump/elasticsearch-dump \
  --input="/data/1785786030.es.ndjson" \
  --output="http://localhost:9200/agent_query_logs" --type=data \
  --headers="{\"Authorization\":\"Basic ${BASIC_AUTH}\"}" --limit=1000
```
(File > ~100MB, i.e. beyond ~1000–1500 devices? `split -l 20000 1785786030.es.ndjson chunk.` and run the
`elasticdump --input=chunk.XX` once per chunk.)

---
## C. Verify on prod
```bash
# dashboard mongo VM (10.2.32.9):
docker exec mongo mongosh 1750019989 --quiet --eval 'print("devices="+db.module_info.countDocuments({moduleType:"MCP_ENDPOINT_SHIELD"})+" api_collections="+db.api_collections.countDocuments({})+" nhi_identities="+db.nhi_identities.countDocuments({}))'
# threat mongo VM (10.2.32.14):
docker exec mongo mongosh 1750019989 --quiet --eval 'print("malicious_events="+db.malicious_events.countDocuments({})+" actor_info="+db.actor_info.countDocuments({}))'
# ES VM (10.2.32.29):
curl -s -H "Authorization: Basic ${BASIC_AUTH}" -H 'Content-Type: application/json' \
  "http://localhost:9200/agent_query_logs/_count" -d '{"query":{"term":{"accountId":1750019989}}}'
```
Then log into prod → switch to account **1750019989** → walk Agentic AI Discovery / NHI Governance /
Traces / Threat-Guardrail-Actor pages.

---
### Blast radius — nothing touches other accounts
| step | scope | affects other accounts? |
|---|---|---|
| `mongorestore --nsTo='1750019989.*' --drop` | writes only to **db 1750019989**; `--drop` replaces only the restored collections in that db | **No** — mongo scopes an account by db name; other account dbs are never named |
| `elasticdump --type=data` | bulk **index by `_id`** of our ~docs (all `accountId=1750019989`, all fresh unique ids) | **No** — `--type=data` is additive; it never deletes/recreates the index or other docs |

The only theoretical ES risk is an `_id` collision with an existing prod doc (would overwrite it with our
`accountId`). Our local ES `_id`s are ES-auto-generated (the loader never sets `_id`), verified to have
**0 overlap** with the prod-origin ids — so this can't happen in practice. To be bulletproof for a
**clean re-load** (idempotent, no reliance on id stability), first delete ONLY our account's docs on the
ES VM (scoped by `accountId`, never global):
```bash
curl -s -XPOST "http://localhost:9200/agent_query_logs/_delete_by_query" \
  -H "Authorization: Basic ${BASIC_AUTH}" -H 'Content-Type: application/json' \
  -d '{"query":{"term":{"accountId":1750019989}}}'
```
(mongo is already idempotent — the restore `--drop` replaces 1750019989's collections each run.)

### Caveats & prod-side wiring
- **api_distribution_data (5M)** is excluded (`--excludeCollection`) — it feeds only a rate-limit cron,
  no visible page. Drop that flag in A1 if you want it (goes to the threat VM slice).
- **Backup first (optional):** 1750019989 is empty, but to snapshot before overwriting, on a target VM:
  `docker exec mongo mongodump --db 1750019989 --archive=/1750019989.bak.gz --gzip`.
- Account 1750019989's org needs the ATLAS Stigg entitlements (NHI_GOVERNANCE, AGENT_TRAFFIC_LOGS,
  AI_AGENTS) — normally already set on a real prod org.
- Threat pages: prod TBS running; our timestamps are BSON **Long** (required). With
  `USE_ACTOR_INFO_TABLE=false` the Actors page aggregates `malicious_events` (where our variety lives).
- Traces: prod dashboard `ES_INDEX_AGENT_QUERY=agent_query_logs`, `ES_HOST` = the prod ES you loaded.
