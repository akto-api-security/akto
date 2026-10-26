# Default endpoint guardrails seeding

## Purpose
Every 30 minutes, for each account that has at least one `ApiCollection` with tag `source=ENDPOINT`
(`ApiCollection.isEndpointCollection()`), insert 4 default guardrail policies into `guardrail_policies`
with `contextSource=ENDPOINT`:
`Default-Customer PII`, `Default-Source code detection`, `Default-Credentials Alert`, `Default-Financial Advice`.

## Data flow
`InitializerListener` (`runJobFunctions == 2` block) -> `EndpointDefaultGuardrailsCron` (30 min, `callDibs`
`Cluster.ENDPOINT_DEFAULT_GUARDRAILS_CRON_INFO`, expiry 25 min) -> `AccountTask.executeTask`
-> `DefaultEndpointGuardrailsService.seedCurrentAccount()`
-> `ApiCollectionsDao.hasEndpointCollection()`
-> `GuardrailPoliciesDao.existsByCreatedByOrName()` (skip rule) -> `insertOne` x4.

## Skip rule (the only idempotency mechanism: policies have no fixed `_id`)
The account is skipped **as a whole** if any policy with `contextSource=ENDPOINT` either was created by
`default@akto.io` or has the name of one of the 4 defaults. Both checks are scoped to ENDPOINT, so a same-named policy
in another dashboard context does not block seeding.
- Nothing is ever updated, so customer edits are kept (an edit that keeps `createdBy` or a name still skips).
- Deleting ONE default policy: the others still match, so it is NOT recreated. Deleting ALL of them reseeds on the next run.
- A customer policy named like a default blocks seeding of all 4.
- Inserts are all-or-none: if one insert throws, the already inserted defaults are deleted again
  (`deleteByCreatedByAndName`), because a partial seed would leave a `createdBy` match and hide the missing
  policies forever. If the rollback also fails it is logged, and the account stays partially seeded.

## Rules
- A DB failure throws. The cron logs it per account and continues with the next account, so it is retried on the next
  tick and never reads as "nothing to seed".
- `createdBy` is `default@akto.io`; there is no `source` value, so that is what tells seeded rows apart.
- The policy definitions are Java objects (`DefaultEndpointGuardrailPolicies.build`), not a resource file.

## Gotchas
- No uniqueness is enforced by the database. Two nodes seeding the same account at once could both pass the skip check.
  `callDibs` keeps the cron to one node at a time; no other code path calls the service.
- The skip query ORs `createdBy` (unindexed) with `name`. It is fine because it is filtered by `contextSource` first
  and a single account has few policies. Revisit if that stops being true.
- `hasEndpointCollection()` relies on `AccountsContextDaoWithRbac.modifyFilters`: it scopes `findAll` by
  `Context.contextSource`, but only if `Context.userId` is non-null. With a null user the NPE is swallowed and the
  query is silently unscoped. The method sets a placeholder user (0, no RBAC row, so it sees the whole ENDPOINT scope)
  and restores both thread-locals afterwards. It also re-checks `isEndpointCollection()` on the returned row so an
  unscoped fallback fails closed instead of seeding.
- `UsersCollectionsList` caches the per-account context collections for 120s, so a brand-new endpoint collection is
  seen up to 2 minutes late. That is fine for a 30-minute cron. Tests clear it with `deleteContextCollectionsForUser`,
  before and after each test.
- Deactivated endpoint collections count as "has an endpoint collection".
- `GuardrailPoliciesDao.getContextSourceFilter()` reads `Context.contextSource`; the seeder writes `contextSource`
  explicitly on the document and does not rely on it.
- `Default-Customer PII` has policy `behaviour=alert` but every `piiTypes[].behavior=block`. Which one wins is decided
  in the external mcp library, not this repo (**unverified**). Check before changing either.
- `Default-Financial Advice` enables an LLM rule, so it costs an LLM call on every customer's endpoint traffic.
- All 4 policies are seeded `active=true` and `applyToAllServers=true`.

## Tests
`TestDefaultEndpointGuardrailsService` (embedded Mongo): definitions, eligibility, seeding, skip rule (createdBy, name,
other context, unrelated policies, delete one vs delete all), no-overwrite, rollback delete scope, thread-local restore.
The all-or-none rollback on an insert failure is not covered by a test (no failure injection).

## Open work
- Verify the PII `behaviour` versus per-type `behavior` semantics.
- Add a failure-injection test for the rollback.
