# Agentic Red-Team Report Generator

Pulls every ARGUS (Agentic Security) test result — issues **and** non-issues —
out of a local Mongo dump and writes them into an `.xlsx` in the same layout
as the manually-curated "Akamai Prompts Red Teaming.xlsx" reference sheet.

## Files

| File | Role |
|---|---|
| `generate_report.sh` | Entry point. Wires the other two together. |
| `export_agentic_results.js` | Mongo aggregation/join, run inside the `mongo` container via `mongosh`. Prints one JSON object per line (JSONL) to stdout. |
| `build_xlsx.py` | Reads that JSONL from stdin and writes the `.xlsx`. No `pandas` dependency — just `openpyxl`. |

## Usage

```bash
./generate_report.sh <account_id> [output.xlsx] [mongo_container_name]

# e.g.
./generate_report.sh 1783401773
./generate_report.sh 1783401773 ~/Downloads/report.xlsx
./generate_report.sh 1783401773 ~/Downloads/report.xlsx mongo
```

- `output.xlsx` defaults to `~/Downloads/Agentic Prompts Report - <account_id>.xlsx`.
- `mongo_container_name` defaults to `mongo` (the container name in `docker ps`).
- Requires: a running `mongo` Docker container with the account's dump loaded, and `python3` with `openpyxl` installed (`pip3 install openpyxl` if missing).

## Output columns

`Issue Name · Category · Severity · API Endpoint · Testing Run · Prompt 1..4 · Response 1..4 · Validation Message · Result Type · Issue Status`

- **Result Type**: `Issue` / `Non-Issue` / `Skipped/Error`.
- **Issue Status**: `OPEN` / `IGNORED` (only populated for `Issue` rows).
- Up to 4 conversation turns per row (matches the reference sheet and the max
  session-turn cap in `test-editor-services`).

## Where each row comes from, and why

This isn't a single collection — three different sources feed the sheet, because
the dashboard itself uses three different scopes for "how many tests/issues are there":

### `Issue` rows — the full, all-time registry

Sourced from **`testing_run_issues`** (one doc per distinct `(apiInfoKey, testSubCategory)`
pair, deduped, persists forever until a human resolves/ignores it — this is exactly
what the **Issues page** shows: `Total = 38`, `Open = 26`, `Ignored = 12`).

For each issue, the actual prompt/response transcript is pulled from its
`latestTestingRunSummaryId` → the matching doc in **`vulnerable_testing_run_results`**
(a durable, uncapped collection — the main `testing_run_result` collection is capped
and can silently evict older vulnerable entries, so don't join transcripts through it
for issues). If the exact summary link doesn't resolve, falls back to the most recent
vulnerable doc for that same `(apiInfoKey, testSubCategory)` key.

**Important:** ignoring an issue does **not** stop future scans from re-detecting it —
it only hides it from the Issues page. 8 of this account's 12 `IGNORED` issues were
still reproducing in the latest scan; only 4 have actually stopped failing since being
ignored. `Issue Status` surfaces this directly per-row.

### `Non-Issue` / `Skipped-Error` rows — latest scan only

Sourced from **`testing_run_result`**, but scoped to only the **latest**
`testing_run_result_summaries` doc per `testingRunId` — reproducing the exact dedup
the dashboard's Scan Results page uses (`TestingRunResultSummariesDao.fetchLatestTestingRunResultSummaries`:
sort by `startTimestamp` desc, group by `testingRunId`, keep first). Older reruns of
the same job are excluded — this is why a raw, unscoped count of `testing_run_result`
(2,401 agentic docs) is roughly 10x too high; only each job's most recent run counts.

- `Non-Issue`: `vulnerable: false` within that scope.
- `Skipped/Error`: `testResults[].errors` non-empty (timeouts, API call failures —
  a technical failure to execute the probe, not a security verdict). Excluded from
  the dashboard's pass/fail total, kept here since every prompt matters for this report.

### Every collection touched

| Collection | Used for |
|---|---|
| `testing_run` | `dashboardContext == "AGENTIC"` filter; resolving a result back to its job name |
| `testing_run_result_summaries` | finding the latest summary per job |
| `testing_run_result` | non-issue / skipped rows |
| `vulnerable_testing_run_results` | issue transcripts (durable, uncapped) |
| `testing_run_issues` | the all-time issue registry (name, status) |
| `agent_conversation_results` | the actual prompt/response turns, joined by `conversationId` |
| `yaml_templates` | human-readable issue name, category, severity, joined by `testSubType` |

## Known discrepancies vs. the live dashboard

- **Failed (35) vs unique failing tests (34)**: the dashboard's raw "Failed" count
  includes duplicate executions of the same test within one run (verified: one test,
  `SECURITY_CONTEXT_POISONING_TERM_REMAP_CODEWORD`, produced two vulnerable docs in the
  same latest-run scope). This script's `Issue` count (38) sidesteps that by using the
  deduped issue registry instead.
- **Passed count (~2,172 here vs ~2,182 on the dashboard, ~0.5% gap)**: even the
  dashboard's own two widgets (the summary tile vs. the per-category donut breakdown)
  don't cross-foot exactly against each other on this account, so a small residual gap
  here is a frontend display/category-mapping nuance, not a dedup logic error — the
  `Failed` count matches the dashboard exactly (35, before the issue-registry switch),
  which confirms the join/scoping key is correct.

## Re-running for a different account

Nothing account-specific is hardcoded — just point it at a different `account_id`
that has a Mongo dump loaded into the same container:

```bash
./generate_report.sh <other_account_id>
```
