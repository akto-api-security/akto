# Staged changes — context

Generated from `git diff --staged` for this branch. Scope: **Snowflake connector** (`ai-agent-framework-shield/pkg/snowflake`).

## Files touched (staged)

| File | Summary |
|------|---------|
| `ai-agent-framework-shield/pkg/snowflake/client.go` | Trace graph fetch for agents + Cortex Search; `EventRecord.TraceGraph`; 5s pre-fetch delay |
| `ai-agent-framework-shield/pkg/snowflake/extractor.go` | `snowflakeTraceMetadata` + ingest payload wiring; minor header / vxlan tweaks |
| `ai-agent-framework-shield/pkg/snowflake/observability.go` | Default lookback 600s; `FetchRecordTraceEnabled` |

---

## Intent

1. **Richer observability for Akto** — Mirror the n8n pattern: keep synthetic `response` text, and add **`snowflakeTraceMetadata`** on the response payload so DIS / dashboard parsers can read full Snowflake context and a **correlated trace** (“thinking graph”) when enabled.

2. **No Snowflake `GET_AI_RECORD_TRACE` UDTF** in this diff — Correlation is done with extra **`AI_OBSERVABILITY_EVENTS`** queries keyed by `record_id` / `request_id` and object name.

---

## `client.go`

### Cortex Agent (`FetchAgentEvents`)

- After each accepted `record_root` row, if **`FetchRecordTraceEnabled()`** and **`ai.observability.record_id`** is non-empty:
  - Calls **`fetchThinkingGraphFromObservabilityEvents`**: second SQL selecting `TIMESTAMP`, `RECORD_ATTRIBUTES` for the same agent object name and `record_id`, within the **same** `lookback` as the main poll, `ORDER BY TIMESTAMP ASC`.
  - **Does not** filter `RECORD_TYPE` on this follow-up query (avoids accounts where `RECORD_TYPE = 'SPAN'` does not match).

### Delay before graph SQL

- **`sleepBeforeThinkingGraphFetch`**: waits **`thinkingGraphFetchDelay` (5s)**, interruptible via **`ctx.Done()`**, then runs the graph query. Intended to allow Snowflake to flush correlated rows.

### Cortex Search (`FetchCortexSearchEvents`)

- Uses **`tid := recordTraceID(attrs)`** once for dedup and graph fetch.
- If **`FetchRecordTraceEnabled()`** and **`tid != ""`**:
  - **`fetchThinkingGraphForCortexSearch`**: rows for same service (`object.name`), matching **`record_id` OR `request_id`** to `tid`, same `lookback`, ordered by time ascending.
  - Selects **`RECORD:name`**, **`RECORD_ATTRIBUTES`**, **`VALUE`**; emits `record_name` plus parsed maps where JSON unmarshals cleanly.

### `EventRecord`

- **`TraceGraph json.RawMessage`** — JSON **array** of graph row envelopes (same shape as `AI_OBSERVABILITY_EVENTS` / CSV: `TIMESTAMP` + `RECORD_ATTRIBUTES`). May be serialized as a JSON **string** in the HTTP payload field `snowflakeTraceMetadata` (see Akto `SnowflakeTraceParser`).

---

## `extractor.go`

### Response payload

- **Agent** and **Cortex Search** paths now set:

  ```text
  responsePayload.response
  responsePayload.snowflakeTraceMetadata  // new
  ```

### `snowflakeTraceMetadata(...)` (shield → Akto)

- Prefer a JSON **array** of rows: `{ "TIMESTAMP": "…", "RECORD_ATTRIBUTES": <object or JSON string> }` per row (matches `observability.csv` / SQL export).
- Optionally send that array as a JSON **string** (stringified array); Akto parses it and ignores the field if parsing fails or the value is not an array.
- Optional **`event_timestamp_unix`** on each envelope when present.
- Optional **`value`** (Cortex Search VALUE column object).
- Optional **`agent`** or **`cortex_search_service`** identity maps (if still embedded in metadata objects).
- **Do not** wrap rows in a `thinking_graph` property; the Java parser no longer reads that shape.

### Helpers

- **`agentRef`**, **`cortexSearchRef`** — small pointers for the metadata builder.

### Other staged edits in this file

- **`time`** import added (metadata timestamps).
- Agent path: **`RequestHeaders["host"]`** lowercased (was `"Host"`).
- Agent path: **`AktoVxlanID`** set to **`strconv.Itoa(0)`** (was `hash(agent.Name)`).

---

## `observability.go`

- **`ObsLookbackSeconds`**: default **600** seconds (was 300). Still overridden by **`SNOWFLAKE_OBS_LOOKBACK_SECONDS`**; invalid values fall back to **600**.
- **`FetchRecordTraceEnabled`**: reads **`SNOWFLAKE_FETCH_RECORD_TRACE`**; **`false` / `0` / `no` / `off`** disables the extra graph SQL; otherwise **enabled** (default).

---

## Environment variables (staged behavior)

| Variable | Effect |
|----------|--------|
| `SNOWFLAKE_OBS_LOOKBACK_SECONDS` | Primary + graph query `DATEDIFF` upper bound; default **600** if unset/invalid |
| `SNOWFLAKE_FETCH_RECORD_TRACE` | **`false`** disables graph fetch and leaves `TraceGraph` empty |

---

## Downstream JSON shape (response payload excerpt)

```json
{
  "response": "<synthetic agent or search summary>",
  "snowflakeTraceMetadata": [
    {
      "TIMESTAMP": "2026-04-21 15:52:18.792",
      "RECORD_ATTRIBUTES": {
        "ai.observability.record_id": "…",
        "snow.ai.observability.agent.planning.step_number": 1
      }
    }
  ]
}
```

Or `snowflakeTraceMetadata` may be a **string** whose contents are the JSON array above. `RECORD_ATTRIBUTES` may instead be a **string** containing serialized JSON (Snowflake CSV-style).

(`value`, `agent`, and `cortex_search_service` appear only when applicable.)

With no graph, emit only `{ "response": "…" }`.

When the array is present on the payload, Akto's `SnowflakeTraceParser` treats it as the **only** observability row source (row `0` = `record_root` by contract); it does **not** merge tag `snowflakeObservability` rows into the same list.

## Review / ops notes

- **Cost**: One extra round-trip per ingested agent root and per Cortex Search row when graph fetch is enabled, plus a **fixed 5s wait** per graph call (scales with number of primary rows per poll).
- **Primary agent SQL** in `FetchAgentEvents` is **outside** the excerpt above; confirm whether **`RECORD_TYPE = 'SPAN'`** remains on the main query in your working tree and matches your Snowflake event table.
- **Unstaged** files (e.g. `snowflake-shield/main.go`, shell scripts) are not described here unless you stage them too.
