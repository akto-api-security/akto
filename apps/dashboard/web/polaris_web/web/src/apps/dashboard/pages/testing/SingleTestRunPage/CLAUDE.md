# SingleTestRunPage: CSV export

## Purpose
"Export results" (page header) and the CSV icon in the results table export **every** result of the
current tab, one row per test result (per endpoint), honouring the active filters.

## Data flow
table icon / header button -> `exportTestRunResults` (SingleTestRunPage.js)
-> `api.fetchTestRunResultsCount` (raw counts, bounds the paging)
-> `api.fetchTestingRunResults` pages of 500 (1000 on Ignored) in raw-result `skip` units
-> `transform.prepareTestRunResults` -> `transform.getTestRunResultsCsvRows`
-> `func.downloadAsCSV` (`csvCell` quoting, formula guard) -> file + toast.

The table icon goes through `GithubServerTable.handleExportCsv`, which hands its sort, filters and
query to `onExportCsv`. The header button rebuilds the same from `PersistStore.filtersMap`.

## Decisions
- Own loop instead of the table's generic export: the table shows grouped rows (`getPrettifiedTestRunResults`),
  the backend `skip`/`limit` count raw results, so stepping by returned rows overlaps pages.
- Page bound is the **raw** count (`rawCounts[queryMode]`), not the tab badge: the badge subtracts ignored
  issues (`withDerivedCounts`), and the backend drops ignored results *after* fetching a page, so a short
  page does not mean the end.
- A missing count is unknown, not zero. Any failed page, bad response shape or run over
  `EXPORT_MAX_RESULTS` aborts with a toast and no file.
- Row count != derived tab count is shown as "Exported X of Y", never as success.
- Result status column is the tab label: `vulnerable: false` also covers skipped runs, so it can't mean "secured".
- Cells are always quoted; strings starting with `= + - @` are prefixed with `'` (urls and names come from traffic).
  The `-` empty placeholder and plain numbers are exempt: `func.exportTableAsCSV` (also used by
  `threat_detection/utils/exportEvents.js`) writes `-` for empty cells and would otherwise get `'-`.

## Gotchas
- `StartTestAction.fetchTestingRunResults` forces `limit` 1000 on the Ignored tab and swallows its own
  exceptions into an empty list (`:987`, `:1005`). An empty page looks like success; the count check is the only guard.
- Backend sort has **no tiebreaker** (`prepareTestingRunResultCustomSorting`, only `severityValue` / `endTimestamp`).
  `skip` paging over ties can repeat or skip results between pages. Repeats are dropped by id; skips show up as the X-of-Y toast.
- `exportTestRunResults` must be defined above `resultTable` (a `const` used while rendering).

## Open work
- Add `_id` as a sort tiebreaker in `prepareTestingRunResultCustomSorting`.
- Export only supports one tab at a time; there is no "all tabs" export.
- "Started at" / "Scanned at" use `func.epochToDateTime` (account timezone, no zone label in the cell). Add the zone to the header if analysts compare across accounts.
- No frontend unit-test runner exists; the export was checked with Node fixtures and a mocked backend only.
