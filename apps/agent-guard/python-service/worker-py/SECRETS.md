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

## ⚠️ Rotate the committed keys

The pre-existing `apps/agent-guard/.../docker-compose.yml` and the old
`worker/src/index.ts` contain **live GCP SA keys and an Anthropic key in
plaintext, committed to git history**. These must be rotated:

### GCP service-account keys (`qwen-access@prod-433206`)
1. GCP Console → IAM & Admin → Service Accounts → `qwen-access@prod-433206…`
2. **Keys** tab → create a new JSON key.
3. base64-encode it: `base64 -i key.json | tr -d '\n'`, set as the new
   `QWEN3GUARD_SA_KEY_JSON` / `GEMMA_VERTEX_SA_KEY_JSON` secret.
4. **Delete the old key** in the console (this is what revokes the leaked one).
5. Delete the local `key.json`.

### Anthropic key (`sk-ant-…`)
1. console.anthropic.com → API keys → revoke the leaked key.
2. Create a new key, set as the `ANTHROPIC_API_KEY` secret.

### After rotating
- Remove the plaintext creds from `docker-compose.yml` and `worker/src/index.ts`
  (the old container/TS worker are retired by the cutover anyway).
- Git history still contains the old keys — rotation (revocation) is what makes
  them harmless; history rewriting is optional and separate.
