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

## One-time setup — Vectorize index

Both `wrangler*.jsonc` declare a `VECTORIZE` binding to an index named
`guardrails-shadow-cache`, so the index **must exist before the first deploy** —
`pywrangler deploy` fails on a binding to a missing index. Create it (and the
embedder) per
[embedder-container/DEPLOYMENTS.md](../embedder-container/DEPLOYMENTS.md).

Provisioning only sets up the store; the cache stays **off** until you enable it
per worker — see [Semantic cache](#semantic-cache-shadow-mode).

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

## Semantic cache (shadow mode)

A per-scanner semantic cache runs in **shadow mode** on the cascade scanners: for
each LLM scan it computes what a cache *would* have answered and Slack-alerts how
that compares to the real verdict — but never serves the cached answer, so `/scan`
behaviour is unchanged. Logic lives in `src/cache_shadow.py`.

It's **off by default** and needs the Vectorize index + embedder provisioned per
[embedder-container/DEPLOYMENTS.md](../embedder-container/DEPLOYMENTS.md). With
those in place, turn shadow mode on per worker with the env vars below.

### 1. Embedder service

A **deployed** worker reaches the embedder through the **`ANONYMIZER_WORKER`
service binding** — the sibling worker hosts both containers and routes `/embed`
to the embedder. (A Worker can't fetch another Worker over its public
`workers.dev` URL, so the binding is mandatory in prod.) Nothing to configure
beyond having that binding — already in `wrangler*.jsonc` — and the sibling worker
deployed; provision it + the Vectorize index per
[embedder-container/DEPLOYMENTS.md](../embedder-container/DEPLOYMENTS.md).

`EMBEDDER_URL` is only a **local-dev fallback** (e.g. `http://localhost:8094`,
used when the binding isn't reachable under `wrangler dev`); leave it unset in
prod.

### 2. Enable per worker

Add to the worker's vars file (`.dev.vars` / `.dev.vars.exec`) and re-seed secrets
(`./scripts/set-secrets.sh .dev.vars` / `… .dev.vars.exec -c wrangler-exec.jsonc`):

```
CACHE_SHADOW_ENABLED=true
CACHE_DISTANCE_THRESHOLD=0.15
CACHE_TTL_SECONDS=21600
EMBEDDER_URL=                          # local-dev only; prod uses the ANONYMIZER_WORKER binding
CACHE_SHADOW_SLACK_WEBHOOK_URL=https://hooks.slack.com/services/...
```

`CACHE_SHADOW_SLACK_WEBHOOK_URL` is a **separate** webhook from `SLACK_WEBHOOK_URL`
on purpose, so shadow noise stays out of the production scan-alert channel; unset →
no shadow alerts. Leave `CACHE_SHADOW_ENABLED` empty to keep the feature off.

> Shadow work is fire-and-forget (`waitUntil`), so it never adds to the `/scan`
> response latency. Watch the miss / hit-match / hit-mismatch mix in the shadow
> channel and tune `CACHE_DISTANCE_THRESHOLD` before ever wiring it to serve.

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
| `CACHE_SHADOW_ENABLED` | semantic cache | `true`/`1` enables shadow mode; empty → off |
| `CACHE_DISTANCE_THRESHOLD` | semantic cache | cosine-distance hit threshold (default `0.15`) |
| `CACHE_TTL_SECONDS` | semantic cache | entry expiry, enforced on read (default `21600` = 6h) |
| `EMBEDDER_URL` | semantic cache | local-dev fallback only (e.g. `http://localhost:8094`); prod uses the `ANONYMIZER_WORKER` binding |
| `CACHE_SHADOW_SLACK_WEBHOOK_URL` | semantic cache | separate webhook for shadow alerts; unset → none |

> `VECTORIZE` is a **binding** (in `wrangler*.jsonc`), not a secret — it ships with
> the worker config, not via `set-secrets.sh`.