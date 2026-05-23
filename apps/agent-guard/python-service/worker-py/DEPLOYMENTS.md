# Managing the two deployments

Both workers run the **same code** (`src/`, `pyproject.toml`). They differ only
by name, secrets/env, and the cascade default modelMap — none of which live in
source. So there is one codebase and two thin wrangler configs.

| | executor-v2 | executor |
|---|---|---|
| wrangler config | `wrangler.jsonc` | `wrangler-exec.jsonc` |
| worker name | `akto-agent-guard-executor-v2` | `akto-agent-guard-executor` |
| URL | `…executor-v2.billing-53a.workers.dev` | `…executor.billing-53a.workers.dev` |
| local vars | `.dev.vars` | `.dev.vars.exec` |

## What makes them differ

1. **Name** — set in each `wrangler*.jsonc`.
2. **Secrets / env** — stored per-worker on Cloudflare, seeded from the matching
   vars file (`SECRETS.md`).
3. **Cascade default modelMap** — the `DEFAULT_MODEL_CONFIG_JSON` env var. Code
   reads it per request via `constants.get_default_config()`, falling back to
   `BUILTIN_DEFAULT_CONFIG` when unset. So a different model lineup / thresholds
   / `parallelExecution` / `storeAllResults` is just a different value of this
   one secret — **no code change, no code divergence.**

> Note: a per-request `modelConfigs` in the scan body still overrides everything;
> `DEFAULT_MODEL_CONFIG_JSON` only changes the fallback used when the request
> omits it.

## Deploy

```bash
# executor-v2 (default config)
uv run pywrangler deploy

# executor
uv run pywrangler deploy -c wrangler-exec.jsonc
```

## Secrets

Keep two local vars files (both gitignored): `.dev.vars` and `.dev.vars.exec`.
Each includes its own `DEFAULT_MODEL_CONFIG_JSON` (leave empty to use the
built-in default).

```bash
# executor-v2
./scripts/set-secrets.sh .dev.vars
npx wrangler secret list

# executor
./scripts/set-secrets.sh .dev.vars.exec -c wrangler-exec.jsonc
npx wrangler secret list --config wrangler-exec.jsonc
```

## Local dev

```bash
uv run pywrangler dev                          # uses .dev.vars
uv run pywrangler dev -c wrangler-exec.jsonc   # uses wrangler-exec.jsonc (+ its env)
```

## Smoke test either

```bash
./scripts/smoke.sh https://akto-agent-guard-executor-v2.billing-53a.workers.dev
./scripts/smoke.sh https://akto-agent-guard-executor.billing-53a.workers.dev
```
