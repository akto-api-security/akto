# Environment variables

One template, one local file:

```bash
cd apps/agent-guard
cp .env.example .env   # edit once — never commit .env
```

## Where `.env` is used

| Runtime | How |
|---------|-----|
| **Docker compose (current)** | [`docker-compose.yml`](docker-compose.yml) — worker + anonymizer |
| **Legacy ONNX compose** | `docker compose -f docker-compose.legacy.yml` — see [PRODUCTION.md](PRODUCTION.md) |
| **Cloudflare `pywrangler dev`** | Copy or link into worker-py: `cp .env python-service/worker-py/.dev.vars` |
| **Cloudflare deploy** | `cd python-service/worker-py && ./scripts/set-secrets.sh` (reads `.dev.vars`) |
| **Integration tests** | `set -a; source .env; set +a` then `AGW_LIVE=1 ./tests/run.sh tests/integration` |

## Two Cloudflare workers, one secrets file

`executor-v2` and `executor` share the same keys. They differ only in `DEFAULT_MODEL_CONFIG_JSON` (see [README.md](README.md#cloudflare-workers)).

- **executor-v2:** leave `DEFAULT_MODEL_CONFIG_JSON` empty in `.env` / `.dev.vars`
- **executor:** use a second local file only if the model map differs:  
  `cp .env python-service/worker-py/.dev.vars.exec` and edit that one key, then  
  `./scripts/set-secrets.sh .dev.vars.exec -c wrangler-exec.jsonc`

## Docker-only vs Cloudflare-only keys

| Variable | Docker | Cloudflare |
|----------|--------|------------|
| `AGENT_GUARD_WORKER_TAG` | yes (current stack) | no |
| `AGENT_GUARD_ANONYMIZER_TAG` | yes (current stack) | no |
| `AGENT_GUARD_EMBEDDER_TAG` | yes (semantic cache) | no |
| `AGENT_GUARD_EXECUTOR_TAG` | yes (legacy ONNX only) | no |
| `FORCE_LLM_MODE` / `SCANNER_LLM_PROVIDER` | legacy ONNX only | no |
| `VERTEX_AI_*` | legacy ONNX (generic Vertex provider) | rarely |
| `ANONYMIZER_URL` | set in compose | leave empty |
| `EMBEDDER_URL` / `REDIS_URL` | set in compose | leave empty (no Redis on Cloudflare) |
| `CACHE_MODE` / `CACHE_*` | yes | cache no-ops without Redis |
| Vertex / Slack / model map keys | yes | yes |

## Semantic cache (`CACHE_MODE`)

The per-scanner semantic cache needs **two sidecars**: the embedder container
(`EMBEDDER_URL`) and Redis with the **RediSearch** module (`REDIS_URL`). Both are
wired automatically in `docker-compose.yml`; on Azure they're separate Container
Apps (see [deploy/azure.md](deploy/azure.md)). It is **fail-open** — with either
unset it no-ops, which is why the Cloudflare path (no Redis) leaves these empty.

- `CACHE_MODE=observe` (default): shadow only — compares what the cache would
  answer to the real verdict and Slack-alerts via `CACHE_SHADOW_SLACK_WEBHOOK_URL`.
  Never affects `/scan`.
- `CACHE_MODE=decide`: serves cached `is_valid=true` hits and skips the cascade;
  cached blocks / misses always fall through to the real scan. Recommended
  rollout: run `observe` first, watch the `hit_mismatch` rate, then flip.
- `CACHE_MODE=off`: cache disabled entirely.
