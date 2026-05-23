# Secrets & credential rotation

The worker reads all credentials from its `env` bindings (see `settings.py`).
**Nothing sensitive is committed** — local dev uses `.dev.vars` (gitignored),
deploys use Worker secrets.

## The secrets

| Secret | Used by | Notes |
|--------|---------|-------|
| `QWEN3GUARD_SA_KEY_JSON` | qwen3guard provider | base64 GCP service-account key JSON |
| `QWEN3GUARD_PROJECT` / `_LOCATION` / `_ENDPOINT_ID` / `_DEDICATED_DNS` | qwen3guard | Vertex endpoint coordinates |
| `GEMMA_VERTEX_SA_KEY_JSON` | gemma provider | base64 GCP SA key JSON |
| `GEMMA_VERTEX_PROJECT` / `_LOCATION` / `_ENDPOINT_ID` / `_DEDICATED_DNS` | gemma | Vertex endpoint coordinates |
| `ANTHROPIC_API_KEY` (`ANTHROPIC_MODEL`) | anthropic provider | only if used in a modelMap |
| `OPENAI_API_KEY` (`OPENAI_MODEL`, `OPENAI_COMPATIBLE_BASE_URL`) | openai provider | only if used |
| `SLACK_WEBHOOK_URL` | alerts | optional; alerts no-op when unset |
| `DATABASE_ABSTRACTOR_SERVICE_URL` | alerts (storeAllResults) | optional |

## Setting them

Local dev: copy `.dev.vars.example` → `.dev.vars`, fill in, run `pywrangler dev`.

Deployed: authenticate wrangler (`wrangler login` or `CLOUDFLARE_API_TOKEN`),
then seed from a vars file:

```bash
./scripts/set-secrets.sh .dev.vars       # or a prod-specific file
npx wrangler secret list                 # verify
```