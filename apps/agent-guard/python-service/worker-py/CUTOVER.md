# Cutover runbook — replace the container worker with the Python worker

**Goal:** serve the existing URL
`https://akto-agent-guard-executor-v2.billing-53a.workers.dev/` from this
single Python worker, with no caller/URL change.

**Strategy (Option B — delete + recreate under the same name).** The current
worker owns a Durable Object + Container; you can't overwrite a DO-backed JS
worker in place with a DO-less Python one, so we delete then redeploy under the
same name. Brief downtime in steps 4–5 only.

All commands run from `apps/agent-guard/python-service/worker-py/`. Requires
wrangler auth (`wrangler login` or `CLOUDFLARE_API_TOKEN`) for the `billing-53a`
account.

---

## 0. Prerequisites
- [ ] `./tests/run.sh` green (offline suite).
- [ ] Credentials rotated per `SECRETS.md` (don't reuse the leaked keys).
- [ ] Confirm the account plan allows the bundle size (~7 MB; `pywrangler deploy`
      reports it — see step 1).

## 1. Rehearse on a staging name (zero prod impact)
`wrangler.jsonc` already uses `name: akto-agent-guard-executor-v2-staging`.

```bash
./scripts/set-secrets.sh .dev.vars          # seed staging secrets
uv run pywrangler deploy                     # note the reported script size
```
Smoke test the staging URL:
```bash
./scripts/smoke.sh https://akto-agent-guard-executor-v2-staging.billing-53a.workers.dev
```
- [ ] `/health` ok, all 7 scanners return expected verdicts.

## 2. Point config at the production name
Edit `wrangler.jsonc`: `name` → `akto-agent-guard-executor-v2`.
(Keep a copy of the staging name handy for future rehearsals.)

## 3. Pre-seed production secrets
```bash
./scripts/set-secrets.sh .dev.vars           # now targets the prod name
```
> Secrets persist across deploys of the same worker name, so set them BEFORE
> the recreate so the first request works.

## 4. Delete the old worker  ⚠️ downtime starts
```bash
npx wrangler delete --name akto-agent-guard-executor-v2
```
This removes the JS worker, its Durable Object, and the Container.

## 5. Deploy the Python worker under the same name  ⚠️ downtime ends
```bash
uv run pywrangler deploy
```
The URL is now served by the Python worker.

## 6. Smoke test production
```bash
./scripts/smoke.sh https://akto-agent-guard-executor-v2.billing-53a.workers.dev
```
- [ ] `/health` ok; cascade + local scanners correct; a Slack alert lands (if
      `SLACK_WEBHOOK_URL` is set).

## 7. Decommission the old stack
- [ ] Delete `worker/` (TS), `go-service/`, `container/`, `docker-compose.yml`.
- [ ] Update `PRODUCTION.md` / `EXAMPLES.md`.
- [ ] Remove plaintext creds from `docker-compose.yml` / `worker/src/index.ts`
      (already revoked in step 0, but delete the strings too).

---

## Rollback
The old worker's code/image are untouched until step 7. If the Python worker
misbehaves after step 5:
```bash
# from the old TS worker dir (apps/agent-guard/python-service/worker)
npx wrangler delete --name akto-agent-guard-executor-v2   # remove python worker
npx wrangler deploy                                       # redeploy the TS+container worker
```
URL returns to the previous behavior. (Same brief-downtime window as the
forward cutover.)
