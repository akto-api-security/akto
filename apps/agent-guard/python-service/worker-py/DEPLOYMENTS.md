# Deployments

Both workers run the **same code** (`src/`, `pyproject.toml`). They differ only
by name, secrets/env, and the cascade default modelMap — none of which live in
source. One codebase, two thin wrangler configs.

| | executor-v2 | executor |
|---|---|---|
| wrangler config | `wrangler.jsonc` | `wrangler-exec.jsonc` |
| worker name | `akto-agent-guard-executor-v2` | `akto-agent-guard-executor` |
| URL | `…executor-v2.billing-53a.workers.dev` | `…executor.billing-53a.workers.dev` |
| local vars | `.dev.vars` | `.dev.vars.exec` |

## What makes them differ

1. **Name** — set in each `wrangler*.jsonc`.
2. **Secrets / env** — stored per-worker on Cloudflare, seeded from the matching
   vars file (see [Secrets](#secrets)).
3. **Cascade default modelMap** — the `DEFAULT_MODEL_CONFIG_JSON` env var. Code
   reads it per request via `constants.get_default_config()`, falling back to
   `BUILTIN_DEFAULT_CONFIG` when unset. A different model lineup / thresholds /
   `parallelExecution` / `storeAllResults` is just a different value of this one
   secret — **no code change, no code divergence.**

> A per-request `modelConfigs` in the scan body still overrides everything;
> `DEFAULT_MODEL_CONFIG_JSON` only changes the fallback used when the request
> omits it.

## Prerequisites

- `uv` on PATH (`export PATH="$HOME/.local/bin:$PATH"` if needed).
- wrangler authenticated for the `billing-53a` account
  (`wrangler login` or `CLOUDFLARE_API_TOKEN`). Python Workers require a
  Workers **Paid** plan.
- A local vars file per worker (both gitignored): `.dev.vars` and
  `.dev.vars.exec`. Copy from the `.example` templates and fill in. Each
  includes its own `DEFAULT_MODEL_CONFIG_JSON` (leave empty for the built-in
  default).

## Deploy — executor-v2

```bash
cd apps/agent-guard/python-service/worker-py
./scripts/set-secrets.sh .dev.vars                 # seed/refresh secrets
uv run pywrangler deploy                           # deploy
npx wrangler secret list                           # verify creds present
./scripts/smoke.sh https://akto-agent-guard-executor-v2.billing-53a.workers.dev
```

## Deploy — executor

```bash
cd apps/agent-guard/python-service/worker-py
./scripts/set-secrets.sh .dev.vars.exec -c wrangler-exec.jsonc
uv run pywrangler deploy -c wrangler-exec.jsonc
npx wrangler secret list --config wrangler-exec.jsonc
./scripts/smoke.sh https://akto-agent-guard-executor.billing-53a.workers.dev
```

> Secret changes take effect on the next request — no redeploy needed. Seed
> secrets **before** the first deploy so the first request works. A passing
> cascade scan shows `is_valid` with the per-model `qwen`/`gemma` blocks
> populated; an empty/`error` cascade means creds are missing.

## Local dev

```bash
uv run pywrangler dev                          # uses .dev.vars
uv run pywrangler dev -c wrangler-exec.jsonc   # uses wrangler-exec.jsonc + its env
```

## Tests

```bash
./tests/run.sh                                 # offline suite
# live cascade (opt-in): hits real Vertex
set -a; source .dev.vars; set +a
AGW_LIVE=1 ./tests/run.sh tests/integration -v
```

---

# Secrets

The worker reads all credentials from its `env` bindings (`settings.py`).
**Nothing sensitive is committed** — local dev uses `.dev.vars*` (gitignored),
deploys use Worker secrets seeded by `scripts/set-secrets.sh`.

| Secret | Used by | Notes |
|--------|---------|-------|
| `QWEN3GUARD_SA_KEY_JSON` | qwen3guard provider | base64 GCP service-account key JSON |
| `QWEN3GUARD_PROJECT` / `_LOCATION` / `_ENDPOINT_ID` / `_DEDICATED_DNS` | qwen3guard | Vertex endpoint coordinates |
| `GEMMA_VERTEX_SA_KEY_JSON` | gemma provider | base64 GCP SA key JSON |
| `GEMMA_VERTEX_PROJECT` / `_LOCATION` / `_ENDPOINT_ID` / `_DEDICATED_DNS` | gemma | Vertex endpoint coordinates |
| `ANTHROPIC_API_KEY` (`ANTHROPIC_MODEL`) | anthropic provider | only if used in a modelMap |
| `OPENAI_API_KEY` (`OPENAI_MODEL`, `OPENAI_COMPATIBLE_BASE_URL`) | openai provider | only if used |
| `DEFAULT_MODEL_CONFIG_JSON` | cascade fallback | per-deployment modelMap JSON; empty → built-in default |
| `SLACK_WEBHOOK_URL` | alerts | optional; alerts no-op when unset |
| `DATABASE_ABSTRACTOR_SERVICE_URL` | alerts (storeAllResults) | optional |