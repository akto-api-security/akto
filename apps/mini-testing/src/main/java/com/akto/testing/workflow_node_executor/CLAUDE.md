# Login flow execution in mini-testing (LOGIN_REQUEST)

The placeholder grammar is shared with the akto repo and lives in
`libs/utils/src/main/java/com/akto/testing/login_flow/CLAUDE.md`. That covers `${xN.…}`, `${stepK.…}`,
`${cookies}`, `${cookies.<name>}`, the `|regex:`, `|urlencode` and `|raw` filters, the cookie jar, and the
nil/absent/empty table. The package is copied byte-for-byte from `~/akto-code/akto`; change it there and copy it
again. This file covers only what is specific to mini-testing.

## Flow

```
Main.handleLoginFlowTestPlayground / TestExecutor.triggerLoginFlow / prefetch
  -> TestExecutor.executeLoginFlow
  -> TestExecutor.convertToWorkflowGraph      step k (RequestData[k-1]) becomes node x(2k-1): x1, x3, x5 ...
  -> Utils.runLoginFlow (this package)        nodes in order, stop at the first node with errors
       -> Utils.processNode(..., AuthMechanism)          API steps -> new ApiNodeExecutor(true)  (login-flow mode)
                                                         OTP / RECORDED / YAML -> their usual executors
            -> com.akto.testing.Utils.buildHttpRequest(data, values, nodeId)
                   URL with Options.loginFlow(nodeId, null); body/headers/query with Options.loginFlow(nodeId, url)
            -> ApiExecutor.sendRequest                    redirects never followed for login steps
            -> com.akto.testing.Utils.populateValuesMap   header keys + lowercase alias, per-cookie keys, xN.response.set-cookie-raw
            -> com.akto.testing.Utils.populateRegexValues xN.response.regex[.N] from the step's regex field
       -> resolveAuthParamValue per auth param (Options.loginFlow(null, null)) -> AuthMechanism.authParamsCached
```

`ApiNodeExecutor` imports `com.akto.testing.Utils` (libs/utils), which shadows this package's `Utils`. The
`buildHttpRequest`/`executeCode`/`replaceVariables` methods here only delegate to libs/utils. API workflow
tests use `new ApiNodeExecutor()`, which keeps standard mode: no `stepK`, `${cookies}` left literal.

## Mini-only behaviour (differs from akto)

- **Always strict.** `com.akto.testing.Utils.replaceVariables` has only the 3-argument form. A missing key always
  throws; there is no `shouldThrowException=false` mode.
- **`#[ ... ]#` JS expressions are still evaluated** (akto removed this in `163210e`; mini keeps it). The engine is
  Nashorn: built in on Java 8, and on JDK 11+ builds the standalone `nashorn-core` from the `nashorn-standalone`
  profile in `libs/utils/pom.xml` (mini-testing's JDK 17 build has it; `UtilsPlaceholderWrapperTest
  .jsEngineIsOnTheClasspath` guards this). `com.akto.testing.Utils.resolveTemplate` handles URL, body, headers,
  query and auth params, as `executeCode` did before. Changes from the original:
  - Only blocks written in the template are evaluated. A resolved value containing `#[ ]#` is never run as JS.
  - Placeholders inside a block use `Options.forJsExpression()` (`'` -> `\'`), so values stay inside JS strings.
    Outside blocks the `'` is no longer escaped (valid JSON).
  - No engine in the runtime: `Utils.JS_ENGINE_UNAVAILABLE` error instead of a NullPointerException.
  - As before, a script that fails (or returns null) leaves its block in place. The failure is logged with
    line and column only, because the message can quote substituted values.
  Prefer the `|regex:` filter for new flows, e.g. `${cookies.client_token|regex:^([^_]+)}` for the SID.
- **`${x0.unique_<suffix>}`.** When the key is absent, it resolves to `<suffix>_<nanoTime>` (the `unique_` prefix
  is matched case-insensitively). One template gets one value per key. A copy of the caller's map is used, so
  the caller's values map is never modified. Resolution happens in `withUniqueValues`, so the shared package is
  untouched.
- **Auth param resolving to `""`.** When every placeholder resolves to `""`, the auth param fails with
  `auth param <key>: ${…} resolved to an empty value`, so `"Bearer "` is never cached.
- **`testValidatorCode`.** Evaluated with the JS engine as before: a validator returning `true` means "not
  vulnerable". `Utils.evaluateTestValidator` returns `null` when it could not be evaluated (no engine, script
  error, a missing key, or a non-boolean result). `ApiNodeExecutor` then adds `Utils.TEST_VALIDATOR_NOT_EVALUATED`
  as a node error, so the step reads as "not evaluated", not "not vulnerable". Only the outcome is logged; the
  resolved code is not. Login steps never set a validator.

## Errors (same wording as akto's apps/testing)

- Build failure: `Failed building request: <resolver message>`.
- Transport failure: `API request failed: <cause>`.
- Status 400 or above when that step's `allowAllStatusCodes` is false: `process node failed with status code N`.
  The flag is read from the step's own `RequestData` (index-matched and bounds-checked).
- `LoginFlowResponse.error` is `Failed to process node xN (step k): <errors joined by "; ">`. For auth params it is
  `auth param <key>: <resolver message>`. Nothing is cached on failure.
- `responses[i]` is `{"headers": …, "body": …}`, the same shape as akto's `buildStepResponse`, because the akto
  dashboard renders mini's result in hybrid mode. A step that never sent a request gets `"{}"` for both. Its
  error is only in `LoginFlowResponse.error`.

## Single-step runs (`loginFlowParams.fetchValueMap`)

The only node is renamed to the UI's node id. The saved values map is loaded without that node's own keys:
`removeNodeKeys` matches the prefix `xN.`, so re-running `x1` keeps the keys of `x11`, `x13` and so on. The map is
saved again after a successful step.

## Logging (DB logs: `LogDb.TESTING`)

Logs carry names, counts and sizes only, never values. That covers `VALUES_MAP_*` (key count),
`API_NODE_REQUEST`/`API_NODE_RESPONSE` (method, URL path, status, header names, body length), the OTP node (OTP
text length and whether a code was found) and recorded-flow keys. Resolver messages name keys and patterns,
never values. `buildHttpRequest` logs only the lengths of resolved query strings.
Not changed: `ApiExecutor` still logs `Final url is: <url with query>` for every request.

## Tests

- In libs/utils:
  - `login_flow/PlaceholderResolverTest` and `LoginFlowCookieJarTest`: byte-identical to akto.
  - `PopulateValuesMapTest`: akto's file, except that it calls the 3-argument `replaceVariables`.
  - `UtilsPlaceholderWrapperTest`: the mini-only rules above (JS evaluation and the no-engine error, strictness,
    `x0.unique_`, header case, validator).
- In apps/mini-testing: the server and E2E tests (in-JVM mock, full chain, failure paths, `CB_MOCK_E2E=1` run
  against the Python mock in `~/akto-code/login_flow_mock/`) were removed from the working tree on 2026-11-16,
  pending a decision on whether to restore them. Until then, end-to-end coverage is the Python mock plus
  `reference_login.sh`.

## Gotchas

- Use lowercase header names (`set-cookie`). Servers send `Set-Cookie`, HTTP/2 `set-cookie`, or `Set-cookie` (the
  JDK server). An exact spelling only resolves if the server used it.
- Step 3's URL is resolved before its headers, so a failure in the step before usually shows up as the URL key.
- libs/utils surefire runs only JUnit 5 tests. Its 11 JUnit 4 classes (`org.junit.Test`) are silently skipped
  because there is no vintage engine, so new libs/utils tests must use JUnit 5. mini-testing itself runs JUnit 4.
- `apps/testing` in this repo is compiled but has no image published from this repo. Its own
  `workflow_node_executor` copies were not changed. It does pick up the libs/utils changes.
