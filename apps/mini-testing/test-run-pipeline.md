# Single-test execution pipeline: `runTestFromMessage`

Traces what happens when the consumer processes **one Kafka message** = one
`(api, test)` cell. Scope is deliberately limited to two files:
[`ConsumerUtil.runTestFromMessage`](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java)
and [`TestExecutor`](src/main/java/com/akto/testing/TestExecutor.java). Everything
they call into (`YamlTestTemplate`, `Executor`, `VariableResolver`, `DataActor`,
`TestingIssuesHandler`, …) is treated as a black box.

`DataActor.*` calls are the persistence/lookup boundary (abstractor/Mongo I/O);
they're flagged **[I/O]** below since they dominate per-test wall time.

> **Running with `BLOCK_LOGS=true`?** Then the *logging* I/O is suppressed but
> the *result* I/O is not. `LoggerMaker.insert()` early-returns when
> `BLOCK_LOGS=true` ([LoggerMaker.java:305](../../libs/utils/src/main/java/com/akto/log/LoggerMaker.java#L305)),
> so every `infoAndAddToDb` / `errorAndAddToDb` / `warnAndAddToDb` becomes a
> local console log with **no DB write**. Flags below are marked **[I/O log]**
> (skipped under `BLOCK_LOGS`) vs **[I/O result]** (always writes). Note the one
> exception: `insertImportantTestingLog` does **not** go through `insert()`
> ([LoggerMaker.java:268](../../libs/utils/src/main/java/com/akto/log/LoggerMaker.java#L268)),
> so it still writes one testing-log per call (rate-limited to 1000/min), even
> with `BLOCK_LOGS=true`.

---

## Top-level flow

```
ConsumerUtil.runTestFromMessage(message)
  1. parse message               → SingleTestPayload
  2. set account + activity ctx
  3. lookup TestConfig (subcategory) + sample messages (apiInfoKey)
  4. no samples?  → markSkipped, return
  5. TestExecutor.runTestNew(...)            ← RUN THE TEST
  6. TestExecutor.persistTestLogsToDb(...)   ← write logs        [I/O]
  7. TestExecutor.insertResultsAndMakeIssues ← write result+issues [I/O]
  8. mark pass/vuln, record lastTested
  finally: clear activity context
```

---

## 1. `ConsumerUtil.runTestFromMessage(String message)`
([ConsumerUtil.java:74](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L74))

| Step | What | Ref |
|---|---|---|
| 1 | `parseTestMessage(message)` → `SingleTestPayload` (testingRunId, summaryId, apiInfoKey, subcategory, accountId, testLogs) | [:63](src/main/java/com/akto/testing/kafka_utils/ConsumerUtil.java#L63) |
| 2 | `Context.accountId.set(...)`; `TestExecutor.setTestRunActivityContext(summaryId)` | :76-78 |
| 3 | `new TestExecutor()`; read singletons: `TestingConfigurations.getInstance()` | :80-82 |
| 4 | `testConfig = instance.getTestConfigMap().get(subcategory)` (the YAML template for this test) | :84 |
| 5 | `messagesList = instance.getTestingUtil().getSampleMessages().get(apiInfoKey)` | :87 |
| 6 | **skip branch:** if no sample messages → `metrics.markSkipped()`, return | :89-91 |
| 7 | else pick the **last** sample: `sample = messagesList.get(size-1)` | :93 |
| 8 | **`runResult = executor.runTestNew(apiInfoKey, testingRunId, testingUtil, summaryId, testConfig, testingRunConfig, isDebug, testLogs, sample)`** | :95 |
| 9 | `executor.persistTestLogsToDb(runResult.getTestLogs())` — **[I/O log]**, skipped under `BLOCK_LOGS` | :96 |
| 10 | `executor.insertResultsAndMakeIssues([runResult], summaryId)` — **[I/O result]** | :97 |
| 11 | count outcome (`markVulnerable`/`markPassed`), `testedApisMap.put(apiInfoKey, now)`, `insertImportantTestingLog` "Test completed … in Ns" — **[I/O]** (writes even under `BLOCK_LOGS`) | :99-107 |
| — | `finally`: `TestExecutor.clearActivityContext()` | :110 |

---

## 2. `TestExecutor.runTestNew(...)` — setup overload
([TestExecutor.java:1226](src/main/java/com/akto/testing/TestExecutor.java#L1226))

Resolves the `RawApi` and attacker auth, then delegates to the core overload.

| Step | What | Ref |
|---|---|---|
| 2.1 | `RawApi` from `TestingConfigurations.getRawApiMap()` cache; else `RawApi.buildFromMessage(message)` and cache it | :1228-1232 |
| 2.2 | `Executor.fetchOrFindAttackerRole()` → attacker `TestRole` → `findMatchingAuthMechanism(rawApi)` (the attacker token) | :1234-1240 |
| 2.3 | delegate to core `runTestNew(apiInfoKey, testRunId, sampleMessageStore, attackerAuthMechanism, customAuthTypes, summaryId, testConfig, testingRunConfig, debug, testLogs, rawApi)` | :1241 |

---

## 3. `TestExecutor.runTestNew(...)` — core
([TestExecutor.java:1244](src/main/java/com/akto/testing/TestExecutor.java#L1244))

| Step | What | Ref |
|---|---|---|
| 3.1 | Pull `testSuperType`, `testSubType`, `agenticTestingAllowed`, `onlySmartTestingAllowed` from `testConfig.getInfo()` | :1246-1249 |
| 3.2 | Resolve `ApiCollection` (from `testingUtil` map, else `dataActor.fetchApiCollectionMeta` **[I/O]**) | :1253-1259 |
| 3.3 | **skip branches** → `generateFailedRunResultForMessage(...)`: Copilot-bot internal endpoint; out-of-testing-scope collection | :1260-1273 |
| 3.4 | *(optional)* `shouldCallClientLayerForSampleData` → `clientLayer.fetchLatestSample(apiInfoKey)` **[I/O]**, decrypt if packed, rebuild `rawApi`; record `setSampleDataFetchLatency` | :1275-1300 |
| 3.5 | `startTime = Context.now()`; `filterGraphQlPayload(rawApi, apiInfoKey)` | :1301-1308 |
| 3.6 | Build nodes from `testConfig`: `filterNode` (apiSelectionFilters), `validatorNode` (validation), `executorNode` (execute), `auth`, `wordListsMap` | :1310-1317 |
| 3.7 | Build `varMap`: seed `wordList_*` from `testConfig.getWordlists()`; `VariableResolver.resolveWordList(varMap, sampleDataMap, apiInfoKey)`; add testRunId, dashboardContext, summaryId, accountId, apiInfoKey, testSubType, agentic/onlySmart flags, yaml content | :1318-1353 |
| 3.8 | Merge collection-description context into `wordList_data_context` / `wordList_every_prompt` | :1355-1395 |
| 3.9 | `testExecutionLogId = UUID`; log "triggering test run …"; stamp `summaryId` on `testingRunConfig` | :1397-1404 |
| 3.10 | `new Executor()`; `executor.overrideTestUrl(rawApi, testingRunConfig)` | :1406-1407 |
| 3.11 | **`new YamlTestTemplate(...).run(debug, testLogs)` → `YamlTestResult`** — this is the actual test: builds attack requests, **sends HTTP to the target**, runs validators. Fallback `SOMETHING_WENT_WRONG` result if empty. | :1408-1415 |
| 3.12 | `endTime`; compute `vulnerable` across results; set `Confidence`/severity (unless automated-pentest) | :1416-1439 |
| 3.13 | Build **`TestingRunResult`** (ids, api, super/sub type, results, vulnerable, startTime, endTime, summaryId, workflowTest, testLogs); set `aiSummaryTraces`, `callbackUuids` / `callbackCheckPending` | :1441-1459 |
| 3.14 | *(optional)* `testingRunConfig.getCleanUp()` → `cleanUpTestArtifacts(...)` | :1461-1468 |
| 3.15 | return `TestingRunResult` | :1470 |

---

## 4. `TestExecutor.persistTestLogsToDb(List<TestLog>)`
([TestExecutor.java:1191](src/main/java/com/akto/testing/TestExecutor.java#L1191))

Iterates the test's logs; writes each via `loggerMaker.errorAndAddToDb` /
`infoAndAddToDb` **[I/O log per line]** (no-op if `testLogs` is null/empty).

Both route through `LoggerMaker.insert()`, so **with `BLOCK_LOGS=true` this whole
method does zero DB I/O** — it only emits to the local console logger
(`logger.error`/`logger.info`). This is the per-test logging tail that `BLOCK_LOGS`
removes.

---

## 5. `TestExecutor.insertResultsAndMakeIssues(List<TestingRunResult>, summaryId)`
([TestExecutor.java:1017](src/main/java/com/akto/testing/TestExecutor.java#L1017))

| Step | What | Ref |
|---|---|---|
| 5.1 | `trim(...)`; **rerun case:** if a prior result exists → `dataActor.deleteTestingRunResults(...)` **[I/O]** | :1021-1032 |
| 5.2 | Normalize `getTestResults()` into `singleTestResults` (TestResult) or `multiExecTestResults`; `recordTestExecutionMetrics(...)` | :1033-1050 |
| 5.3 | Null out `testResults`/`testLogs` on the doc (slim it before write) | :1051-1052 |
| 5.4 | **`dataActor.insertTestingRunResults(trr)`** — persist the result **[I/O]** | :1053 |
| 5.5 | **`dataActor.updateTestResultsCountInTestSummary(summaryId, resultSize)`** **[I/O]** | :1055 |
| 5.6 | **`new TestingIssuesHandler().handleIssuesCreationFromTestingRunResults(...)`** — create/update issues from the result (internally fetches + writes issues) **[I/O]** | :1058-1064 |

---

## Per-test I/O touchpoints (within these two files)

Each processed message can trigger, in order (✅ = still happens under
`BLOCK_LOGS=true`, ❌ = suppressed):

| # | Call | Kind | `BLOCK_LOGS=true` |
|---|---|---|---|
| 1 | `fetchApiCollectionMeta` (only if not cached) — step 3.2 | result/lookup | ✅ |
| 2 | `fetchLatestSample` (only if `shouldCallClientLayerForSampleData`) — step 3.4 | result/lookup | ✅ |
| 3 | **target HTTP request(s)** inside `YamlTestTemplate.run` — step 3.11 | target | ✅ |
| 4 | scattered `infoAndAddToDb` (e.g. "triggering test run", "Inserted results") | log | ❌ suppressed |
| 5 | `persistTestLogsToDb` — one write per log line — step 4 | log | ❌ suppressed |
| 6 | `insertImportantTestingLog` "Test completed …" — step 1.11 | log (special) | ✅ (bypasses `insert()`; rate-limited) |
| 7 | `insertTestingRunResults` — step 5.4 | result | ✅ |
| 8 | `updateTestResultsCountInTestSummary` — step 5.5 | result | ✅ |
| 9 | issue create/update via `TestingIssuesHandler` — step 5.6 | result | ✅ |

Steps 7–9 are the result-persistence tail that runs for every test after the
target call (3). **`BLOCK_LOGS=true` removes the logging writes (4, 5) but leaves
the target call, the result-persistence tail (7–9), and the `insertImportantTestingLog`
write (6) intact** — so it trims per-test I/O but doesn't eliminate the
result-write cost the flamegraph attributed to `insertResultsAndMakeIssues`.
