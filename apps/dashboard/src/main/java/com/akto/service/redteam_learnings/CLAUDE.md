# Red-team learnings

## Purpose
Agentic red teaming remembers what it learned about each target agent, identified by `{apiCollectionId, url, method}`.
- During a run, it records one observation per conversation that ended (judged, target error or judge error).
- Every `LEARNINGS_BATCH_SIZE` (default 200) judged conversations, and once at the end of the run, it consolidates
  them into a versioned profile. A profile holds insights, counts, and a base-prompt × strategy grid.
- It gives the latest profile to the attack enhancer, never to the judge.
- At the end of a run it suggests template drafts. A user reviews a draft and saves it through the Test Editor.

## Repos and layers
| Repo (branch) | Owns |
|---|---|
| `akto` (`feature/enhance_red-teaming-dash`) | DTOs, dashboard DAOs (RBAC reads), `DaoInit` indexes, Liquibase `account/039-agent-redteam-learnings.xml`, `RedteamLearningsAction` + `RedteamLearningsService` (this folder), `AdminSettingsAction.updateRedteamLearningsEnabled`, frontend `pages/testing/redteam_learnings/`, run page card, ApiDetails "Learnings" tab, Test Editor draft hand-off, User config toggle |
| `akto-mini-2/akto` (`feature/enhance_red-teaming-cy`) | DTO copy, write-side DAOs (self-healing indexes), database-abstractor `RedteamLearningsAction` (5 `api/*` endpoints), `AgentConversationResultDao.findTurns`, `AccountSettings.redteamLearningsEnabled` copy |
| `akto-mini-services/akto` (`feature/enhance_red-teaming-mr`) | `AgentClient` (`/chat` identity fields, `consolidateLearningsInBackground`), `AgentLearningContext`, `TestCompletion` hook, utility-server relay (`DataActor.REDTEAM_LEARNING_APIS`, `RedteamLearningsHandler`), `UtilityAuthFilter`, `ApiExecutor.sendRequestBackOff` status-aware overload |
| `test-editor-services` (`feature/enhance-red-teaming-agent`) | `src/services/learnings/*`: recording, profile block, consolidation, drafts, `learningsSwitch.ts`, `runState.ts` |

## Data flow
```
mini-testing Executor --/chat + identity, testSubType, testCategory, strategy, basePrompt--> test-editor-services
  -> profile read (cached 5 min, one read per agent at a time, 5 s timeout) -> enhancer only
  -> observation after the last turn (judged or not) -> /utility/storeRedteamObservations -> abstractor -> agent_redteam_observations
  -> every N judged, and on POST /consolidateLearnings (TestCompletion, COMPLETED runs only)
       -> /utility/fetchRedteamConsolidationInput -> LLM -> /utility/saveRedteamProfile
       -> agent_redteam_profiles (+ agent_redteam_template_drafts at the end of a run)
dashboard: api/fetchRunRedteamLearnings (run page), api/fetchAgentRedteamLearnings (ApiDetails tab),
           api/updateRedteamDraftStatus, api/updateRedteamLearningsEnabled (User config)
```
At the end of a run the final consolidation starts only after that run's batch consolidations have finished, because
both would otherwise write the same version document.

## On/off
Learnings run only when **both** of these hold:
- `REDTEAM_LEARNINGS_ENABLED=true` is set on test-editor-services. It defaults to off.
- `AccountSettings.redteamLearningsEnabled` is `true`. Null (never set) counts as off, so an account opts in on Testing → User config.

test-editor-services reads the account value at startup and then every 60 s, through
`/utility/fetchRedteamLearningsSettings`:
- A failed read keeps the value it last read.
- Until a first read succeeds, learnings stay off, and `/chat` reports `learnings.state: "unavailable"`.
- The other states it reports are `env_off` and `account_off`.
- `/consolidateLearnings` answers 202 `accepted:false` for `env_off` / `account_off` (a decision) and 503 for
  `unavailable` (not read yet, so the caller retries). `AgentClient` logs a declined call and does not retry it.
- A run whose state file cannot be read or written runs without learnings and `/chat` reports `unavailable`.

## Decisions
- **Deterministic ids.**
  - observation `_id` = conversationId
  - profile `_id` = `<apiCollectionId>:<method>:<urlHash>:<summaryId>:<batchNo>`
  - draft `_id` = identity + hash of prompt, strategies and mode

  So retries rewrite the same document, and a dismissed draft stays dismissed.
- **Version writes are guarded.** The abstractor keeps a version's first `createdAtMs` on a retry (the dashboard's
  "latest" is ordered by it), and refuses a non-final save over a final version of the same id.
- **The abstractor refuses empty versions.** A version with nothing judged, or without insights, is rejected, so a
  failed consolidation never replaces the last good version. Node also refuses a model answer without an operations
  list (`llm_invalid`) and reports a failed call as `llm_failed`, never as "nothing new".
- **Failed drafts are named.** A failed drafts call saves the final version as `saved_without_drafts`; an empty list
  of drafts is a real answer (`saved`).
- **Counts come from DB aggregation, never from the LLM.** The attack success rate is critical / judged, and higher
  means more vulnerable. Unjudged conversations are reported separately, outside the denominator. Insight text that
  states a count ("45 attempts", "3 of 5", a percentage) is dropped.
- **Cited ids are checked in Node only.** `applyInsightOperations` and `validateDrafts` accept only ids from the
  conversations this consolidation sampled. The abstractor cannot check them: an insight keeps evidence ids from
  earlier runs, which may be past the observation TTL.
- **Spend is fail-closed.** Consolidation usage is kept in `summary-usage/<id>.learnings.json`, written only by the
  server process (the orchestrator process writes the conversations' file). An unreadable usage file gives
  `budget_unknown` and no LLM call. Each LLM call is abandoned after `LEARNINGS_LLM_TIMEOUT_MS` (120 s; the clients
  cannot cancel the request).
- **Dashboard reads check access twice.** They go through `AccountsContextDaoWithRbac`, keyed on `apiCollectionId`,
  *and* an explicit `UsersCollectionsList` check in the action. If that list cannot be read, the request fails
  (fail closed).
- **A hidden draft looks absent.** A draft in a collection the user cannot see answers "No such draft", so the response
  does not confirm the draft exists.
- **SAVED needs a real custom template, and is final.** A draft can be marked SAVED only with the id of a `yaml_templates`
  document with `source: CUSTOM`. A saved draft is never set back to DRAFT (conditional update); it can be dismissed.
- **Drafts reuse the existing save path.** A draft opens in the Test Editor as an unsaved CUSTOM template (editor id
  `REDTEAM_DRAFT_*`, stashed in this tab's sessionStorage) and is saved through the existing `api/saveTestEditorFile`.
  An `originalTestId` that does not exist takes the "insert new" branch. After saving, the editor marks the draft SAVED
  (one retry).
- **The abstractor answers 4xx for a refusal and 5xx for its own failure.** The relay retries only an unreachable
  abstractor, 429 and 5xx; a 422 goes back to the caller at once.
- **A category or sub type that is not a plain token is ignored.** It is written into a generated template, so
  `parseLearningContext` accepts only `[A-Za-z0-9_][A-Za-z0-9_ .:-]*`, and the renderer quotes every value.

## Deploy order and rollback
1. **Cyborg** (database-abstractor): new endpoints and collections; nothing calls them yet.
2. **Dashboard and test-editor-services**: Liquibase 039, APIs and screens. With `REDTEAM_LEARNINGS_ENABLED` unset
   nothing changes for tests.
3. **Mini-testing**: the relay, the identity fields and the end-of-run call. Then set `REDTEAM_LEARNINGS_ENABLED=true`.

Older builds: mini-testing without this change sends no identity, so those conversations learn nothing; an older agent
server ignores the extra `/chat` fields and answers 404 to `/consolidateLearnings`, which mini-testing logs once per run.
Kill switch: unset the env, or turn the account toggle off; no deploy. Rollback of Liquibase 039 drops the three
`agent_redteam_*` collections (the learnings are lost; nothing else reads them).

## Is it broken in prod?
- test-editor-services logs `[learnings] <run>|<agent> (batch N | final): consolidation started` and
  `<outcome> in <ms>` with a running count per outcome. Outcomes other than `saved` and `nothing_judged` deserve a look.
- `/chat` returns `learnings.state` (`ready`, `none`, `fetch_failed`, `unavailable`, `env_off`, `account_off`).
- The run page card and the Learnings tab show off / unavailable / no access / none / ready separately.
- No log line carries an account id: this service has none on `/chat`. Use the summary id.

## Gotchas
- **`AccountsContextDaoWithRbac.modifyFilters` fails open.** It swallows exceptions and returns the unfiltered query.
  This is why the explicit collection check exists.
- **Index names must match in three places:** the dashboard DAOs, the abstractor DAOs and Liquibase 039.
  `TestAgentRedteamDaos` checks the dashboard DAOs against the changelog. The abstractor never runs `DaoInit`; it
  creates indexes once per account per JVM. Master has another `039-` changelog; the ids differ and `includeAll`
  loads both.
- **Keep the copies identical.** The DTO copies (dash ↔ cy) and the `AccountSettings.redteamLearningsEnabled` field
  must not drift. mr has no DTOs: the relay passes raw JSON.
- **Adding a learnings API takes three changes:**
  - the abstractor struts entry (with both the `ERROR` 422 and `SERVER_ERROR` 500 results)
  - the name in `DataActor.REDTEAM_LEARNING_APIS` (`RelayRedteamLearningsTest` counts them)
  - the `RedteamLearningsApi` type in `utilityServerService.ts`
- **The relay passes at most 1 MB.** `ApiExecutor` reads that much of a response; a longer one gets a 502 from the relay.
  The abstractor caps each turn's text at 2000 characters and a conversation at 10 turns (`turnsTruncated` says so).
- **A run's "newest" version is by `createdAtMs`,** not `batchNo`, which counts per agent.
- **End-of-run consolidation fires only for COMPLETED runs that this worker sent agentic tests for.**
  - After a restart mid-run, with no agentic test after the restart, it is skipped silently (non-agentic runs take the
    same path, so it is not logged). The run keeps its batch versions but gets no final version and no drafts.
  - FAILED and STOPPED runs keep their last batch version.
- **The draft fixture is a snapshot.** `TestRedteamDraftYaml` parses
  `src/test/resources/redteam_learnings/rendered-draft.yaml`, which is the output of `renderDraftYaml`. Regenerate the
  fixture when the renderer changes.
- **No auth on two endpoints.** `/chat` and `/consolidateLearnings` on test-editor-services have no auth; Java sends
  none.
- **The utility server is open until `UTILITY_SERVER_TOKEN` is set,** and the relay then writes with the server's own
  abstractor token. Setting the token also refuses data-ingestion-service's `/utility/ingestTraffic`, which does not
  send it yet.
- **Last-turn errors are recorded as `TARGET_ERROR`** (judged = no), so the unjudged count includes tester failures.

## Accepted risks
- **Prompt-injection loop.** Target replies reach the digest and insight LLM calls as quoted data, and the insights are
  stored and later placed in the enhancer's system prompt. Length caps, tag stripping, cited-id checks and the count
  filter limit it; a hostile target can still plant advice that steers later attacks. The judge never sees insights.
- **Off by default per account.** A never-set account counts as off; an admin opts in on User config. Accounts that
  were learning before the default changed stop until they turn it on.
- **Concurrent runs on one agent** each start from the profile they read, so the later version can lack the other's
  newest insights. Both versions are kept.
- **The slot hand-over and the full-queue outcome (`queue_full`) have no test.**
- **Cells past 700 are not stored** (the least-tested ones); the totals still count every conversation.
- **Timeouts do not nest at the relay.** test-editor-services gives up on a utility call after 20 s, while the relay can
  keep retrying a hung abstractor for longer (three attempts at the shared client's timeout). The writes are
  idempotent, so the extra work is harmless; it is not cancelled.
- **Profiles and drafts have no retention.** Only observations expire. Versions are one small document per batch, run
  and agent; revisit when that count matters.
- **Observations queue in memory.** A restart loses up to 5 s of them (at most 19 per agent); an outage keeps the
  newest 2000 per agent and counts the drops.

## Open work
- **End-to-end is not run.** Planned check: two runs against one stub agent.
  - Run 1 writes a profile and drafts.
  - Run 2's enhance prompts carry the profile block.
  - A saved draft runs as a normal judged test in run 3.
- **The frontend is not checked in a browser.** It passes the production webpack build, but the panels and the
  draft → editor → save flow need a manual pass covering all of these states:
  - off
  - unavailable
  - forbidden
  - none
  - ready
- **Observation TTL is a guess.** It is `AgentRedteamObservation.RETENTION_DAYS`.
- **The Learnings tab shows for `gen-ai` collections only,** like the prompt-template tab. Collections of other kinds
  that agentic tests run against are unverified.
- **Unverified: the library templates' wordlist names.** Java reads `wordList_strategy` and `wordList_base_prompt`, and the
  draft renderer writes `wordLists: base_prompt` / `strategy`; no library template is in a local checkout to confirm it.
  If a template names them differently, its conversations are recorded without a grid key (cells fall back to the test sub
  type) and no drafts can be built from them.
