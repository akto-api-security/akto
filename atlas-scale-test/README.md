# ATLAS device scale-test

Creates a throwaway account (copy of `1784850613`, enriched with NHI from `1000000`) and fabricates
distinct endpoint-shield devices up to a target count, so the ATLAS dashboard can be load/UI-tested.
Original accounts are never modified. Local mongo docker container = `mongo`.

**Companion docs:** `SCALING.md` (per-collection strategy + 100/500/1000 projections),
`OPERATIONS.md` (rescale up/down + transfer to prod account 1750019989).
**Rescale everything to N:** `./rescale.sh 500` (or 1000/100/50). See OPERATIONS.md.

## Usage
```bash

./run.sh 50                 # full pipeline: setup + copy + fabricate to 50 + nhi + verify
./run.sh 500 fabricate      # top up existing account to 500 devices (incremental)
./run.sh 1000 fabricate     # top up to 1000
./run.sh 100 nhi            # (re)generate rich NHI for all devices (idempotent wipe+regen)
./run.sh 100 verify         # coverage report only
./teardown.sh               # drop the account DB + remove registry rows (full revert)
```
- Account id = current epoch on first run, saved in `.acct` and reused after.
- Account name = "Atlas Scale Test" (shows in the dashboard account switcher).
- Phases: `all` (default) | `setup` | `copy` | `fabricate` | `nhi` | `verify`.
- `fabricate` is incremental & re-runnable: it tops up from the current device count to TARGET.
- `nhi` wipes and regenerates ALL NHI (identities + violations) for every device with rich,
  varied, recent data. Idempotent. Run it after `fabricate` (the `all` phase does).

### NHI tuning (env vars, consumed by the `nhi` phase)
```bash
NHI_PER_MIN=8 NHI_PER_MAX=20 NHI_VIOL_RATIO=0.45 ./run.sh 100 nhi
```
- `NHI_PER_MIN/MAX` — identities per device (raise for denser NHI; NOTE the graph renders every
  identity as a node, so at 1000 devices keep these low, e.g. 3-6, or the node graph will be huge).
- `NHI_VIOL_RATIO` — fraction of identity-names that carry violations (drives the % of identities
  showing violation pills / colored graph nodes; ~0.45 gives a ~47% split).
- Variety hits: `createdAt` (=Discovered) biased to last 7d; mixed expiry (future/expired/no-expiry);
  `status` mix incl. INACTIVE (Disabled tab); agent names chosen to render AI Agent + MCP Server + LLM
  node types; identity names / target-resources drawn from both source accounts.

## After running
Restart/refresh the dashboard, switch account to **Atlas Scale Test**, and check:
Agentic AI Discovery (Agentic assets / Endpoints / Audit Data / Endpoint Shield),
NHI Governance (Identities / Violations / Policies), AI Security Posture.

## Traces (Elasticsearch) — separate pipeline
The Traces / LLM-observability page reads from Elasticsearch (index `agent_query_logs`), not Mongo.
`traces.sh` stands up a local ES container, creates the index from the prod mapping, and loads+fabricates
agent-query logs organized into sessions/traces/spans for every device in the account.
```bash
./traces.sh                 # ensure ES + index + export devices + load/fabricate (idempotent)
./traces.sh es              # just start the ES container
./traces.sh index           # (re)create index + mapping
SESS_MIN=3 SESS_MAX=6 TURN_MIN=3 TURN_MAX=12 DAYS=7 ./traces.sh load   # tune volume, reload
```
- Requires `run-master.sh` to have `ES_HOST=http://localhost:9200` and `ES_INDEX_AGENT_QUERY=agent_query_logs`
  (already set). Restart the dashboard after loading.
- Loads the real prod dump (227 docs, account-remapped, timestamps rebased to now) PLUS fabricated
  sessions per device: coherent `sessionIdentifier` + multiple `traceId` messages + `spanId` spans,
  realistic prompts from the real Q&A pool (`traces/qa_pool.json`), varied topics/subTopics, service
  clients (claudecli/cursor/codex-cli/copilot/vscode/…), models, tokens, recent timestamps.
- Drives: session list + token totals, top-users-by-token, top-apps (serviceId), topic→subTopic
  hierarchy graph, span waterfall (trace detail), and the token/session sparklines.
- ES source data: `traces/raw_hits.ndjson` (dump), `traces/mapping.json`, `traces/qa_pool.json`,
  `traces/devices.json` (roster, regenerated each run). Loader: `05_load_traces.py`.
- Teardown removes this account's ES docs; to stop ES entirely: `docker rm -f akto-es`.

## Threat / Guardrail / Actors (separate Mongo collections + separate SERVICE)
The Threat Detection, Guardrail-activity, and Actors pages are driven by `malicious_events`,
`actor_info`, `aggregate_sample_malicious_requests`, `archived_malicious_events`, `threat_configuration`
(restored from the prod `*-tbs.archive`). Scale with `threat.sh`.
```bash
# one-time: restore the prod tbs archive into the SOURCE account first
docker cp 1784850613-tbs.archive mongo:/ && docker exec mongo mongorestore --archive=/1784850613-tbs.archive --gzip
./threat.sh copy            # copy threat collections SRC(1784850613) -> scale account
EXTRA_ACTORS=400 ./threat.sh scale   # per-device guardrail events + volume actors/events (idempotent)
./threat.sh verify
./threat.sh all             # copy + scale + verify
```
- Per fabricated device: clones the device-linked guardrail `malicious_events` (host rewritten to the
  new device), `detectedAt` rebased recent and stored as **BSON Long** (the backend reads it via
  `Document.getLong()` — Int32 would 500 the Actor/Threat-API pages).
  - **Actor scaling**: in the seed data the ENDPOINT `actor` is a shared scanner/client/seed-author
    name (`claude`, `cursor`, `settings-scanner`, `vrt`…) reused across every device — so cloning alone
    yields only ~8 endpoint actors. The scaler rewrites each cloned device's NON-IP actor to that
    device's own username, so endpoint actors scale per device (~1 per device). Real-IP actors (external
    attackers) are preserved. The agent-client is still in `host` (`<device>.ai-agent.<client>`).
  - `actor_info` (UNIQUE on `actorId`+`contextSource`) is populated for volume actors only; with
    `USE_ACTOR_INFO_TABLE=false` (run-tbs.sh) it is NOT read — the Actors page aggregates
    `malicious_events` by `actor`, which is where all the per-device/volume variety lives.
- Volume: `EXTRA_ACTORS` unique actors (IPs in 198.18.0.0/15) mixed API + AGENTIC context, each with
  several events — drives the actors table, world map (country), severity donut, category counts,
  threat-APIs table, and the activity timeline. Reserved-range IPs make re-runs idempotent.
- `api_distribution_data` (5M) is copied but NOT scaled — it feeds only a rate-limit percentile cron,
  no visible page.
- **CRITICAL wiring — run via `run-tbs.sh`**: the dashboard does NOT read these collections from Mongo
  directly. It proxies to the threat-detection-backend (`apps/threat-detection-backend`) via
  `THREAT_DETECTION_BACKEND_URL` (run-master.sh: `http://localhost:9090`). Start the backend with
  `./run-tbs.sh` (or `--full` on first build). Verified compatible with its env:
    - `AKTO_THREAT_PROTECTION_MONGO_CONN=mongodb://localhost:27017` → THIS mongo container, db = accountId
      (`1785786030`). ✅ our scaled data is read.
    - `THREAT_DETECTION_BACKEND_SERVER_PORT=9090` ↔ run-master.sh `THREAT_DETECTION_BACKEND_URL`. ✅
    - `USE_ACTOR_INFO_TABLE=false` → the Actors page AGGREGATES `malicious_events` by `actor` (it does
      NOT read `actor_info`). Our scaler puts all the actor/country/severity/category variety into
      `malicious_events`, so this is fully populated. `actor_info` is left populated too (harmless; used
      only if `USE_ACTOR_INFO_TABLE=true`).
    - Context tabs bucket by the `contextSource` field only (account 1785786030 is not a "legacy
      account", so no filterId fallback). Our events set API/AGENTIC/ENDPOINT/MCP correctly.
    - run-tbs.sh also sets Kafka (`localhost:29092`) — needed only for the live ingest/consume path, not
      for the dashboard READ queries against seeded data.
- Teardown: these live in the account DB, so `./teardown.sh` (dropDatabase) removes them.

## What gets scaled (per fabricated device)
module_info, api_collections (~26), single_type_info (~880), api_info (~113), sample_data (~113),
sensitive_sample_data (when present), endpoint_mcp_config (~12), mcp_audit_info (~12),
agent_users (1), user_analysis_data (1), nhi_identities (2–6) + nhi_violations. `nhi_policies` stays global.

## Skipped (don't drive counts/graphs; not copied, not multiplied)
api_hit_count_info, api_audit_logs, logs_*, metrics_data, agent_conversation_results, crawler_urls,
protection_logs, spans/traces (ES/TBS), guardrail activity (separate TBS mongo).

## Notes
- Source account `1784850613` already contains 2 devices with duplicate hostnames
  (`ashleys-macbook-pro-73ce9b4a`, `roberts-macbook-air-81f3d6b9` — one UUID + one numeric module_info
  id each). These are real re-registrations copied verbatim; fabricated devices are always unique.
- Config at top of `run.sh`: SRC_ACCT, OTHER_ACCT, ACCT_NAME, SEED, EXCLUDES.
