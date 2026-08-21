# ATLAS scaling — per-device strategy across all 3 data sources

Scale-test account: **1785786030** ("Atlas Scale Test"), copied from source **1784850613**.
A "device" = one endpoint-shield agent. Identities threaded through everything:

| identity | example | where it's the key |
|---|---|---|
| **hostname** | `mikes-macbook-pro-f0929fe8` | `module_info.name`, `api_collections.hostName` prefix, `agent_users.devices[]`, threat `host` prefix, ES `deviceId` |
| **hardware deviceId** (32 hex; first 8 = hostname suffix) | `f0929fe8c0c15cb8…` | `module_info.additionalData.deviceId`, `user_analysis_data._id.deviceId`, `nhi_identities.deviceId` |
| **username** (OS user) | `michaelphilips` | `agent_users.userName`, ES `userName`, threat endpoint `actor` (after fix) |
| **agentId** (UUID) | `8275e65f-…` | `module_info._id` |
| **service client** | `claudecli`,`cursor` | ES `serviceId`, host `.ai-agent.<client>` |

Every fabricated device gets a **fresh, unique** identity (seeded-PRNG name pools), then its data bundle
is cloned/generated with consistent id remapping. Fabrication is idempotent per source.

---

## 1) Regular MongoDB — account db `1785786030`
Scripts: `02_fabricate.js` (devices/assets), `04_enrich_nhi.js` (NHI). Orchestrator: `run.sh`.

| Collection | Drives (UI) | Device key | Per-device scaling pattern | ~/device |
|---|---|---|---|---|
| `module_info` (MCP_ENDPOINT_SHIELD) | Endpoint Shield list, device count | `name`, `additionalData.deviceId/username` | Clone template doc → new UUID `_id`, new hostname/hwId/username/IPs/OS/version, rewrite `mcpServers[].collectionName`, recent heartbeat, future `expiresAt` | 1 |
| `api_collections` | Agentic assets, Endpoints | `hostName` prefix | Clone template's collections → new int `_id` (free-range alloc), rewrite `hostName` prefix, keep `tagsList`/`skills`; build oldColl→newColl map | ~26 |
| `single_type_info` | asset/endpoint counts, graphs | `apiCollectionId`,`collectionIds[]` | Clone → new ObjectId, remap `apiCollectionId`+`collectionIds` via collMap | ~880 |
| `api_info` | endpoints, posture counts | `_id.{apiCollectionId,method,url}`,`collectionIds[]` | Clone → rebuild composite `_id.apiCollectionId`, remap `collectionIds` | ~113 |
| `sample_data` | endpoint sample drill-down | `_id.{apiCollectionId,…}`,`collectionIds[]` | Clone → rebuild composite `_id`, remap ids (sample bodies kept) | ~113 |
| `sensitive_sample_data` | sensitive-data drill-down | `_id.{apiCollectionId,…}` | Clone → rebuild composite `_id` (present on few device colls) | ~0–1 |
| `traffic_info` | traffic timeline | `_id.{apiCollectionId,…}` | Clone if present (device colls have ~none) | ~0 |
| `endpoint_mcp_config` | mcp config mapping | `collectionName` prefix | Clone → new ObjectId, rewrite `collectionName`/`tempCollectionName` prefix | ~12 |
| `mcp_audit_info` | Audit Data (MCP/skills approvals) | `hostCollectionId`,`mcpHost` | Clone → new ObjectId, remap `hostCollectionId`, rewrite `mcpHost`/collectionName prefix | ~12 |
| `agent_users` | Users & devices | `userName`,`devices[]` | 1 fabricated user → new ObjectId, unique `userName`/email, `devices=[newHost]`, random team/role | 1 |
| `user_analysis_data` | per-user analytics (topics/tokens) | `_id.{serviceId,deviceId}`,`userName` | Clone template → new composite `_id` (new serviceId + hwId), new `userName`, varied tokens | ~1 |
| `nhi_identities` | NHI Identities | `deviceId`,`deviceLabel`,`owner.name` | **Generated fresh** (not cloned): pools from both accounts — 46 identity names, 30 agents (incl MCP/LLM), 4 types; recent `createdAt`(=Discovered); mixed expiry (future/expired/none); status incl INACTIVE (Disabled tab); risk/access/targetResource varied | 8–20 (tunable) |
| `nhi_violations` | NHI Violations + violation pills + graph color | `identities[].identityName`,`severity` | Generated for a **subset of identity-names** (`VIOL_RATIO`≈0.45), varied Crit/High/Med severities; keyed globally by identityName | ~ per name |
| `nhi_policies` | NHI Policies | `scope.agents[]` (agent names) | **Global, NOT per-device** — left as-is | — |

**Not scaled / skipped:** giant logs & hit-counts (`api_hit_count_info` 9.5M, `api_audit_logs` 7.8M,
`logs_*`, `metrics_data`) — not copied, don't drive any count/graph. Config/type/role collections copied
once (global).

---

## 2) Threat MongoDB — same mongo container, threat collections in account db `1785786030`
Read by the **threat-detection-backend service** (not the dashboard directly; `run-tbs.sh`,
`USE_ACTOR_INFO_TABLE=false`). Script: `07_scale_threat.js`. Orchestrator: `threat.sh`.
Device key = `host` = `<device>.ai-agent.<client>`. All timestamps stored as **BSON Long**.

| Collection | Drives (UI) | Device key | Per-device / volume scaling pattern | ~/device |
|---|---|---|---|---|
| `malicious_events` | Guardrail activity feed, Threat Detection, APIs-under-threat, **Actors** (aggregated by `actor`) | `host` prefix; `actor` | **Per device:** clone template device's guardrail events → new uuid `_id`+`refId`, rewrite `host` prefix, remap `latestApiCollectionId` to the device's collection, recent `detectedAt` (Long). **Actor fix:** rewrite non-IP `actor` (scanner/client/seed-user) → the device's **username** so endpoint actors scale ~1/device (real attacker IPs kept). **Volume (global):** `EXTRA_ACTORS` unique-IP actors (198.18.0.0/15) × 2–15 events each, mixed API+AGENTIC, varied category/severity/country/endpoint | ~32 + global volume |
| `actor_info` | Actors table (ONLY if `USE_ACTOR_INFO_TABLE=true` — unused under run-tbs.sh) | `actorId`+`contextSource` (UNIQUE) | Per-actor dedup table; **not per-device** (endpoint actors come from event aggregation). Volume: one row per unique-IP volume actor | — |
| `aggregate_sample_malicious_requests` | sample malicious request bodies | `actor`,`apiCollectionId` | Copied from source as-is; **not scaled per device** | — |
| `archived_malicious_events` | archived events | — | Copied as-is | — |
| `api_distribution_data` (5M) | rate-limit percentile **cron only** (no visible page) | `apiCollectionId`+window | Copied as-is; **not scaled** | — |
| `threat_configuration`, `acto_info`, `splunk_integration_config` | config | — | Copied as-is (1–4 docs) | — |

Actor counts after fix (100 devices): **ENDPOINT ~86 · API ~264 · AGENTIC ~136**.

---

## 3) Elasticsearch — index `agent_query_logs` (local `akto-es` :9200)
Drives the **Traces / LLM-observability** page. Script: `05_load_traces.py`. Orchestrator: `traces.sh`.
Device key = `deviceId` (=hostname), `userName`, `serviceId`.

| "Collection" | Drives (UI) | Device key | Per-device scaling pattern | ~/device |
|---|---|---|---|---|
| `agent_query_logs` (ES docs) | Sessions list, token totals & sparklines, top-users, top-apps (serviceId), topic→subTopic graph, trace/span waterfall | `deviceId`,`userName`,`serviceId`,`sessionIdentifier`,`traceId` | **Original 227 prod docs**: account-remapped, timestamps rebased to now. **Per device**: 3–6 **sessions**, each = coherent `sessionIdentifier` + service client + model, a `SessionStart` doc, 3–12 **turns** (each turn = one `traceId` "message" with a real Q&A prompt from `qa_pool.json` by topic/subTopic + 0–2 thought/stop **spans** sharing the traceId), a `SessionEnd`. Varied tokens, recent `timestamp`, `isAtlasTraffic=true` | ~4–6 sessions ≈ 50–75 docs |

Per-device fields: `accountId, deviceId, userName, serviceId, sessionIdentifier, traceId, spanId, topic,
subTopic, inputTokens, outputTokens, queryPayload, responsePayload, model, timestamp, topicProcessed`.

---

## Projected volume — 100 (actual) vs 500 / 1000 devices
Marginal **per-device** rate × device count (device-driven collections). Copied-once collections are
constant regardless of device count.

### Regular MongoDB (account db)
| Collection | ~/device | 100 (actual) | 500 (proj) | 1000 (proj) |
|---|--:|--:|--:|--:|
| `module_info` (devices) | 1 | 100 | 500 | 1,000 |
| `api_collections` | 26 | ~1.3k | ~13k | ~26k |
| `single_type_info` | 880 | ~56k | ~440k | ~880k |
| `api_info` | 113 | ~5.2k | ~56k | ~113k |
| `sample_data` | 113 | ~5.2k | ~56k | ~113k |
| `endpoint_mcp_config` | 12 | ~0.65k | ~6k | ~12k |
| `mcp_audit_info` | 12 | ~2.7k | ~6k | ~12k |
| `agent_users` | 1 | ~0.2k | ~500 | ~1k |
| `user_analysis_data` | 1 | ~0.2k | ~500 | ~1k |
| `nhi_identities` | 8–20 (≈13) | ~1.3k | ~6.5k | ~13k |
| `nhi_violations` | ≈7 | ~0.72k | ~3.5k | ~7k |
| **regular-mongo device docs total** | ≈1,180 | **~0.6M** | **~0.6M** | **~1.2M** |

### Threat MongoDB (read by TBS)
| Collection | scaling | 100 (actual) | 500 (proj) | 1000 (proj) |
|---|---|--:|--:|--:|
| `malicious_events` (device-linked) | ~36/device | ~3.6k | ~18k | ~36k |
| `malicious_events` (volume) | `EXTRA_ACTORS`×~8 (config) | ~3.5k | ~3.5k* | ~3.5k* |
| `malicious_events` **total** | device + volume + copied | ~9.7k | ~24k | ~42k |
| distinct actors (ENDPOINT/API/AGENTIC) | ~1/device endpoint + volume | 86/264/136 | ~460/264/136 | ~910/264/136 |
| `actor_info` (volume only) | `EXTRA_ACTORS` | 745 | 745* | 745* |
| `aggregate_sample_malicious_requests` | copied once | 127k | 127k | 127k |
| `api_distribution_data` | copied once (not scaled) | 5.0M | 5.0M | 5.0M |
\* bump `EXTRA_ACTORS` to raise volume/actor counts proportionally at higher device counts.

### Elasticsearch (`agent_query_logs`)
| Metric | ~/device | 100 (actual) | 500 (proj) | 1000 (proj) |
|---|--:|--:|--:|--:|
| ES docs | ~70 | ~7.7k | ~35k | ~70k |
| sessions | ~4.5 | 469 | ~2.3k | ~4.5k |
| messages (`traceId`) | ~34 | ~3.4k | ~17k | ~34k |

### Grand total new docs written to scale to N devices
- **500 devices:** ~0.6M mongo + ~20k threat + ~35k ES ≈ **~0.65M docs**
- **1000 devices:** ~1.2M mongo + ~38k threat + ~70k ES ≈ **~1.3M docs**
(`api_distribution_data` 5M and `aggregate_sample_malicious_requests` 127k are copied once, not multiplied.)

## To extend to 500 / 1000 devices
`./run.sh 500 fabricate` → `./run.sh 500 nhi` → `./traces.sh load` → `./threat.sh scale`
(each idempotent, re-derives the current device roster). **Rendering caveats at high counts:** the NHI
node graph draws every identity (~6.5k @500 / ~13k @1000) and the ES topic graph draws every trace —
lower `NHI_PER_MIN/MAX` (e.g. 3/6) and `SESS_MIN/MAX`,`TURN_MIN/MAX` so the graphs stay usable. Mongo
volume (~1.2M device docs @1000) is comfortable; watch `single_type_info` (~880k @1000) as the largest.
