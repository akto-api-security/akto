# Red-team learnings

## Purpose
Agentic red teaming remembers what it learned about each target agent, identified by `{apiCollectionId, url, method}`.
- During a run, it records one observation per judged conversation.
- Every `LEARNINGS_BATCH_SIZE` (default 200) judged conversations, and once at the end of the run, it consolidates
  them into a versioned profile. A profile holds insights, counts, and a base-prompt × strategy grid.
- It gives the latest profile to the attack enhancer, never to the judge.
- At the end of a run it suggests template drafts. A user reviews a draft and saves it through the Test Editor.

## Repos and layers
| Repo (branch) | Owns |
|---|---|
| `akto` (`feature/enhance_red-teaming-dash`) | DTOs, dashboard DAOs (RBAC reads), `DaoInit` indexes, Liquibase `account/039-agent-redteam-learnings.xml`, `RedteamLearningsAction` + `RedteamLearningsService` (this folder), `AdminSettingsAction.updateRedteamLearningsEnabled`, frontend `pages/testing/redteam_learnings/`, run page card, ApiDetails "Learnings" tab, Test Editor draft hand-off, User config toggle |
| `akto-mini-2/akto` (`feature/enhance_red-teaming-cy`) | DTO copy, write-side DAOs (self-healing indexes), database-abstractor `RedteamLearningsAction` (5 `api/*` endpoints), `AgentConversationResultDao.findTurns`, `AccountSettings.redteamLearningsEnabled` copy |
| `akto-mini-services/akto` (`feature/enhance_red-teaming-mr`) | `AgentClient` (`/chat` identity fields, `consolidateLearningsInBackground`), `AgentLearningContext`, `TestCompletion` hook, utility-server relay (`DataActor.REDTEAM_LEARNING_APIS`, `RedteamLearningsHandler`), `UtilityAuthFilter` |
| `test-editor-services` (`feature/enhance-red-teaming-agent`) | `src/services/learnings/*`: recording, profile block, consolidation, drafts, `learningsSwitch.ts` |

## Data flow
```
mini-testing Executor --/chat + identity, testSubType, testCategory, strategy, basePrompt--> test-editor-services
  -> profile read (cached 5 min) -> enhancer only
  -> observation after the judge -> /utility/storeRedteamObservations -> abstractor -> agent_redteam_observations
  -> every N judged, and on POST /consolidateLearnings (TestCompletion, COMPLETED runs only)
       -> /utility/fetchRedteamConsolidationInput -> LLM -> /utility/saveRedteamProfile
       -> agent_redteam_profiles (+ agent_redteam_template_drafts at the end of a run)
dashboard: api/fetchRunRedteamLearnings (run page), api/fetchAgentRedteamLearnings (ApiDetails tab),
           api/updateRedteamDraftStatus, api/updateRedteamLearningsEnabled (User config)
```

## On/off
Learnings run only when **both** of these hold:
- `REDTEAM_LEARNINGS_ENABLED=true` is set on test-editor-services. It defaults to off.
- `AccountSettings.redteamLearningsEnabled` is not `false`. Null (never set) counts as on.

test-editor-services reads the account value at startup and then every 60 s, through
`/utility/fetchRedteamLearningsSettings`:
- A failed read keeps the value it last read.
- Until a first read succeeds, learnings stay off, and `/chat` reports `learnings.state: "unavailable"`.
- The other states it reports are `env_off` and `account_off`.

## Decisions
- **Deterministic ids.**
  - observation `_id` = conversationId
  - profile `_id` = `<apiCollectionId>:<method>:<urlHash>:<summaryId>:<batchNo>`
  - draft `_id` = identity + hash of prompt, strategies and mode

  So retries rewrite the same document, and a dismissed draft stays dismissed.
- **The abstractor refuses empty versions.** A version with nothing judged, or without insights, is rejected, so a
  failed consolidation never replaces the last good version.
- **Counts come from DB aggregation, never from the LLM.** The attack success rate is critical / judged, and higher
  means more vulnerable. Unjudged conversations are reported separately, outside the denominator.
- **Dashboard reads check access twice.** They go through `AccountsContextDaoWithRbac`, keyed on `apiCollectionId`,
  *and* an explicit `UsersCollectionsList` check in the action. If that list cannot be read, the request fails
  (fail closed).
- **A hidden draft looks absent.** A draft in a collection the user cannot see answers "No such draft", so the response
  does not confirm the draft exists.
- **SAVED needs a real template.** A draft can be marked SAVED only with the id of a `yaml_templates` document that
  exists.
- **Drafts reuse the existing save path.** A draft opens in the Test Editor as an unsaved CUSTOM template (editor id
  `REDTEAM_DRAFT_*`, stashed in this tab's sessionStorage) and is saved through the existing `api/saveTestEditorFile`.
  An `originalTestId` that does not exist takes the "insert new" branch. After saving, the editor marks the draft SAVED.

## Gotchas
- **`AccountsContextDaoWithRbac.modifyFilters` fails open.** It swallows exceptions and returns the unfiltered query.
  This is why the explicit collection check exists.
- **Index names must match in three places:** the dashboard DAOs, the abstractor DAOs and Liquibase 039.
  `TestAgentRedteamDaos` checks the dashboard DAOs against the changelog. The abstractor never runs `DaoInit`; it
  creates indexes once per account per JVM.
- **Keep the copies identical.** The DTO copies (dash ↔ cy) and the `AccountSettings.redteamLearningsEnabled` field
  must not drift. mr has no DTOs: the relay passes raw JSON.
- **Adding a learnings API takes three changes:**
  - the abstractor struts entry
  - the name in `DataActor.REDTEAM_LEARNING_APIS` (`RelayRedteamLearningsTest` counts them)
  - the `RedteamLearningsApi` type in `utilityServerService.ts`
- **End-of-run consolidation fires only for COMPLETED runs that this worker sent agentic tests for.**
  - After a restart mid-run, with no agentic test after the restart, it is skipped. The run keeps its batch versions
    but gets no final version and no drafts.
  - FAILED and STOPPED runs keep their last batch version.
- **The draft fixture is a snapshot.** `TestRedteamDraftYaml` parses
  `src/test/resources/redteam_learnings/rendered-draft.yaml`, which is the output of `renderDraftYaml`. Regenerate the
  fixture when the renderer changes.
- **No auth on two endpoints.** `/chat` and `/consolidateLearnings` on test-editor-services have no auth; Java sends
  none.
- **The utility-server token blocks ingest.** Setting `UTILITY_SERVER_TOKEN` also refuses data-ingestion-service's
  `/utility/ingestTraffic`, which does not send the token yet.

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
