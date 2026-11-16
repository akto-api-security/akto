# Login-flow placeholders and cookie jar

## Purpose

Test roles with a `LOGIN_REQUEST` auth mechanism run a chain of HTTP steps (`RequestData`) to obtain a token.
Real browser-style logins (e.g. Chargebee) need cookies carried between steps across several hosts, single
cookie values, sub-parts of values (SID = `client_token` before `_`) and URL-encoding. This package does that
with placeholder *filters* and a derived cookie jar instead of executing scripts (Nashorn is gone).

The package depends on the JDK only (no akto classes, no logging) so it can be copied byte-identical into
`akto-mini-services`. Callers log keys only, never values.

| File | Role |
|---|---|
| `PlaceholderResolver.java` | Parses `${KEY(|FILTER)*}`, looks keys up in the values map, applies filters, escapes. `com.akto.testing.Utils.replaceVariables` delegates here. |
| `LoginFlowCookieJar.java` | Rebuilds a browser-like jar from each previous node's raw `Set-Cookie` lines; answers `${cookies}` and `${cookies.<name>}`; owns the Set-Cookie / Cookie header parsers used by `populateValuesMap`. |
| `LoginFlowNodes.java` | Node-id helpers: step k is node `x(2k-1)`, labels like `x3 (step 2)`. |
| `PlaceholderException.java` | Checked failure with a message that names the key/filter and never a value. |

## Data flow

```
LoginForm (dashboard) -> AuthMechanismAction.triggerLoginFlowSteps / triggerSingleLoginFlowStep
 -> TestExecutor.executeLoginFlow -> convertToWorkflowGraph (nodes x1, x3, x5 ...)
 -> workflow_node_executor.Utils.runLoginFlow
    -> processNode(..., authMechanism) -> ApiNodeExecutor(loginFlow = true)
       -> Utils.buildHttpRequest(data, valuesMap, nodeId)       url, then body/headers/query
          -> PlaceholderResolver (loginFlow options: stepK alias, ${cookies...}, strict)
             -> LoginFlowCookieJar.fromValuesMap(valuesMap, nodeNumber)   only nodes < this one
       -> ApiExecutor.sendRequest (redirects NOT followed)
       -> Utils.populateValuesMap: values, lowercase header aliases, per-cookie keys,
          xN.response.set-cookie-raw (newline-joined raw Set-Cookie lines), step regex
    -> auth params resolved strictly (throw on anything unresolved) -> AuthMechanism cache
```

Failures: `ApiNodeExecutor` turns a resolver error into the node error `"Failed building request: <reason>"`
(and a send failure into `"API request failed: <cause>"`); `runLoginFlow` returns `success=false` with
`"Failed to process node x5 (step 3): <node errors joined by '; '>"`, e.g.
`Failed to process node x5 (step 3): Failed building request: Couldn't find x3.response.body.nope (x3 (step 2)
responded with status 200)`. Auth-param failures return `"auth param <key>: <reason>"`. Nothing is cached as a
token on failure. `Utils.resolvePlaceholders` logs the message (keys/patterns only) at error level.

## Placeholder grammar

```
${ KEY ( '|' FILTER )* }
KEY    := (x|step)\d+ '.' path          values map lookup (stepK -> x(2K-1), login flows only)
        | cookies                       login flows only: Cookie header value for THIS request's URL
        | cookies '.' name              login flows only: latest value of that cookie from any host
        | AKTO.changes_info.<...>       webhooks, unchanged
FILTER := regex:<pattern>   group 1 if the pattern has groups, else the whole match
        | urlencode         UTF-8 URLEncoder, then '+' -> %20
        | raw               skip the default JSON-string escaping for this placeholder
```

- Brace-depth aware: `regex:\d{2}` works. `\|` is a literal pipe inside a filter argument.
- Filters run left to right; escaping runs last unless `raw` is present.
- The template is resolved **before** JSON parsing of headers/body, so a regex inside a JSON string must be
  written as it appears in the JSON source (`\\d` in JSON source is the regex `\\d`, i.e. a backslash).
  Prefer character classes (`[^_]+`) or the step's Regex field.
- Login flows are strict: an unrecognised `${...}` is an error. Outside login flows unrecognised `${...}`
  text is left as-is, exactly as before.
- `#[ ... ]#` JS expressions: the resolver never evaluates or removes them; each repo's `Utils` decides.
  - akto: no JS evaluation in this path since `163210e` ("remove nashorn"). Login-flow callers pass
    `Options...withJsExpressionsRejected()`, so a block is an error (`JS_EXPRESSIONS_NOT_EVALUATED`) instead of
    being sent literally; outside login flows it is still sent literally.
  - akto-mini-services: `Utils.resolveTemplate` evaluates blocks written in the template with the JS engine
    (Nashorn; standalone `nashorn-core` on JDK 11+ via the `nashorn-standalone` profile in `libs/utils/pom.xml`),
    as before. Placeholders inside a block use `Options.forJsExpression()` (adds `'` -> `\'`) so values stay
    inside JS string literals; resolved values are never evaluated as JS. No engine -> explicit error.

## Nil / absent / empty semantics

| Situation | Result |
|---|---|
| Key absent from the values map | error `Couldn't find <key>` (+ hint: node never ran / regex did not match, with status) when throwing; left as literal text when the caller asked not to throw (only non-login callers do) |
| Key present, value `""` | `""` — a real value |
| `${cookies}` and no cookie matches the URL | `""` — truly zero cookies (the header is still sent, empty) |
| `${cookies.x}` never set / deleted / ignored | error naming why (never set, deleted by xN via Max-Age/Expires, ignored: too large / bad Domain) |
| regex filter: no match, bad pattern, input > 1MB | error naming the key and pattern, never the value |
| `${cookies}` in a URL or an auth param | error (no request URL to scope cookies to); use `${cookies.<name>}` |

## Cookie jar rules

- Rebuilt per request from `xN.response.set-cookie-raw` + `xN.request.url` of nodes **before** the current
  one, in node order (later overrides earlier). No static state, nothing new persisted beyond the raw lines.
- Host-only unless `Domain=` is given; `Domain` must domain-match the request host (RFC 6265 5.1.3), else
  the cookie is ignored. `Domain=app.example.com` is never sent to `tenant.example.com`.
- Path match per RFC 6265 5.1.4; default path = directory of the request path.
- `Secure` cookies are sent only to https, or to `localhost`/`127.0.0.1`/`::1`/`*.localhost`
  (secure contexts, as browsers and curl treat them).
- `Max-Age<=0` or `Expires` in the past deletes the matching (name, domain, path) cookie. Max-Age wins over
  Expires. Because the jar is rebuilt from raw lines, positive Max-Age is not aged (login flows are short).
- Ports are ignored (cookies are not port-scoped). No public-suffix list.
- Bounded: 300 cookies (oldest evicted), name+value <= 4096 bytes (larger cookies ignored, like browsers).
  Stored raw lines: at most 300 per node, lines over 8192 chars dropped.
- `java.net.HttpCookie` holds each cookie (name validation, max-age). Its `parse` and `domainMatches` are not
  used: `parse` splits version-0 headers on commas and `domainMatches` follows RFC 2965 (it accepts
  `Domain=example.com` for host `badexample.com` and rejects `a.b.example.com`).

## Gotchas

- **Step k is node `x(2k-1)`** (`TestExecutor.convertToWorkflowGraph`: `edgeNumber = 1; += 2`). Step 2 is
  `x3`. Use `stepK.` in login flows or read the id shown in the LoginForm.
- Header keys keep the server's case; HTTP/2 lowercases (`set-cookie`). `populateValuesMap` writes both the
  original and the lowercase key, for headers and per-cookie keys.
- A single `Set-Cookie` header used to skip per-cookie extraction; now one or many behave the same.
- **Form-only endpoints** (e.g. Chargebee `/users/login_submit`): set `Content-Type:
  application/x-www-form-urlencoded` and write the body as JSON — `ApiExecutor` converts it with
  `jsonToFormUrlEncoded` (URL-encodes every value). `|urlencode` is for raw form bodies, URLs and queries.
- **302 failures**: redirects are not followed in login flows and a 302 is not an error. A failed
  `switch_domain` shows up at the next step that needs its cookie/regex; the error says which regex did not
  match and the status of that step.
- Default escaping no longer turns `'` into `\'` (that produced invalid JSON); everything else is unchanged.
- `constructValueMap` (single-step run) drops the re-run node's keys by `nodeId + "."` prefix — `x1` used to
  also drop `x11...`.

- Tests: `libs/utils` tests must be JUnit 5 (`org.junit.jupiter`): surefire's vintage engine fails to parse
  junit 4.13.1 there, so JUnit 4 classes are silently skipped. `apps/testing` runs JUnit 4.
- Wiring: `Utils.replaceVariables` -> `PlaceholderResolver.Options.standard`; `Utils.buildHttpRequest(data, map,
  nodeId)` -> `Options.loginFlow(nodeId, url)` (URL itself resolved with url=null); `workflow_node_executor.Utils
  .processNode(..., AuthMechanism)` -> `new ApiNodeExecutor(false, true)`; auth params ->
  `Options.loginFlow(null, null)`. akto adds `.withJsExpressionsRejected()` to every login-flow option.

## Open work

- Regex filters have no evaluation timeout (input is capped at 1MB; patterns are admin-supplied).
- SAMPLE_DATA auth params (`Executor.java`) still resolve with `shouldThrowException=false` (same class of
  bug as B7, outside login flows).
- akto: API workflow tests still send `#[ ... ]#` literally (no error outside login flows).
- No per-step success assertion (e.g. "expect Set-Cookie site_valid"); would change existing flows.
- `workflow_node_executor.Utils` still has its own test-only copies of `populateValuesMap`/`buildHttpRequest`
  (used by `ApiWorkflowExecutorTest`); production uses the `libs/utils` ones.
- The live dashboard path (`api/triggerLoginSteps`) was verified only through `TestExecutor.executeLoginFlow`
  (same call), not by driving a restarted dashboard.
