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
| `AGENT_GUARD_EXECUTOR_TAG` | yes (legacy ONNX only) | no |
| `FORCE_LLM_MODE` / `SCANNER_LLM_PROVIDER` | legacy ONNX only | no |
| `VERTEX_AI_*` | legacy ONNX (generic Vertex provider) | rarely |
| `ANONYMIZER_URL` | set in compose | leave empty |
| Vertex / Slack / model map keys | yes | yes |
| `*_FOUNDRY_*` (Azure AI Foundry providers) | yes | yes |

> The per-scanner semantic cache has moved out of agent-guard to
> guardrails-service (which now owns the Redis vector store + embedder in front of
> agent-guard's `/scan`). agent-guard no longer reads `CACHE_*`, `EMBEDDER_URL`,
> or `REDIS_URL`.
