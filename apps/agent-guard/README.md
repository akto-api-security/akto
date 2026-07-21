# Agent Guard

LLM guardrail scanning: cascade models (Vertex or Azure AI Foundry) + local scanners + Presidio anonymization.

| Doc | Contents |
|-----|----------|
| [ENV.md](ENV.md) | Environment variables (one `.env` file) |
| [PRODUCTION.md](PRODUCTION.md) | **Legacy ONNX executor** (`:8092`, existing deployments) |
| [deploy/azure.md](deploy/azure.md) | Azure Container Apps (Portal + CLI setup) |

## Quick start (Docker)

```bash
cd apps/agent-guard
cp .env.example .env          # see ENV.md
docker compose up -d --build
make smoke                    # http://127.0.0.1:8090
```

Services: **worker** `:8090` (scan API), **anonymizer** `:8093` (internal, Presidio).

> **Legacy ONNX deployments** still on `aktosecurity/akto-agent-guard-executor` (`:8092`) → see [PRODUCTION.md](PRODUCTION.md).

## API

Base URL: `http://127.0.0.1:8090` (docker) or `https://<worker>.workers.dev` (Cloudflare).

```bash
# Health
curl -s localhost:8090/health

# Single scan
curl -s -X POST localhost:8090/scan \
  -H 'content-type: application/json' \
  -d '{"scanner_name":"BanSubstrings","text":"confidential","config":{"substrings":["confidential"]}}'

# Batch
curl -s -X POST localhost:8090/scan/batch \
  -H 'content-type: application/json' \
  -d '[{"scanner_name":"Secrets","text":"AKIAIOSFODNN7EXAMPLE"}]'

# Anonymize (via worker)
curl -s -X POST localhost:8090/scan \
  -H 'content-type: application/json' \
  -d '{"scanner_name":"Anonymize","text":"email john@example.com"}'
```

**Scanners:** `BanSubstrings`, `Secrets`, `TokenLimit` (local); `PromptInjection`, `BanTopics`, `Toxicity`, `Gibberish`, `BanCode` (cascade, needs Vertex or Azure Foundry creds); `Anonymize` (proxies to anonymizer).

**Cascade model providers** (used in `modelConfigs` / `DEFAULT_MODEL_CONFIG_JSON`): `qwen3guard`, `gemma_vertexai`, `vertexai` (GCP Vertex dedicated endpoints); `qwen3guard_foundry`, `gemma_foundry`, `azure_foundry` (Azure AI Foundry managed-compute endpoints, OpenAI-compatible); `anthropic`, `openai`, `openai_compatible`. Foundry needs `*_FOUNDRY_BASE_URL` + `*_FOUNDRY_API_KEY` per provider (see [ENV.md](ENV.md)).

**Anonymizer direct** (`:8093` if port exposed):

```bash
curl -s -X POST localhost:8093/anonymize \
  -H 'content-type: application/json' \
  -d '{"text":"john@example.com","language":"en"}'
```

Full smoke suite: `python-service/worker-py/scripts/smoke.sh <base-url>`

---

## Cloudflare Workers

Two deployments, same code (`python-service/worker-py`), different secrets/model map:

| | executor-v2 | executor |
|---|-------------|----------|
| Config | `wrangler.jsonc` | `wrangler-exec.jsonc` |
| Name | `akto-agent-guard-executor-v2` | `akto-agent-guard-executor` |

```bash
cd python-service/worker-py
cp ../../.env .dev.vars
./scripts/set-secrets.sh
uv run pywrangler deploy
./scripts/smoke.sh https://akto-agent-guard-executor-v2.billing-53a.workers.dev
```

For **executor** with a different `DEFAULT_MODEL_CONFIG_JSON`: use `.dev.vars.exec` and
`./scripts/set-secrets.sh .dev.vars.exec -c wrangler-exec.jsonc`.

Local dev: `uv run pywrangler dev` (or `-c wrangler-exec.jsonc`).

---

## Docker images (CI)

| Image | Module |
|-------|--------|
| `aktosecurity/akto-agent-guard-executor:<tag>` | legacy ONNX `python-service/container` |
| `aktosecurity/akto-agent-guard-worker:<tag>` | portable `python-service/worker-py` |
| `aktosecurity/akto-agent-guard-anonymizer:<tag>` | `python-service/anonymizer-container` |

Built by `staging.yml`, `prod.yml`, `akto-agent-guard.yml`. Pull via `AGENT_GUARD_WORKER_TAG` / `AGENT_GUARD_ANONYMIZER_TAG` in `.env`.

**Staging manual run:** Actions → Staging → choose build target (`agent-guard-worker`, `agent-guard-anonymizer`, etc.). PR review on `master` builds all.

Compose files: root `docker-compose.yml` (worker + anonymizer), `docker-compose.legacy.yml` (ONNX executor), per-service compose under `worker-py/`, `anonymizer-container/`, and `container/`.

---

## Tests

```bash
cd python-service/worker-py
./tests/run.sh                                    # offline (69 tests)
set -a; source ../../.env; set +a
AGW_LIVE=1 ./tests/run.sh tests/integration -v    # live Vertex (opt-in)
```

## Lint / typecheck / pre-commit

```bash
make setup                                        # one-time: syncs deps + installs the pre-commit hook
```

```bash
cd python-service/worker-py
uv run ruff check .                               # lint (make lint from apps/agent-guard/)
uv run ruff format .                              # format
uv run mypy src                                   # typecheck (make typecheck from apps/agent-guard/)
```

After `make setup`, touching any file under `python-service/worker-py/` runs ruff, mypy, and pytest on `git commit` — everything else is untouched.

---

## Portable vs Cloudflare

Scan logic lives in `scan_handler.py`; Cloudflare `entry.py` and FastAPI `app.py` are thin wrappers. Behavior is the same except:

- `/health` service name differs cosmetically
- Portable uses `ANONYMIZER_URL` (HTTP); Cloudflare uses `ANONYMIZER_WORKER` binding — do not set `ANONYMIZER_URL` on Workers

---

## Layout

```
apps/agent-guard/
  docker-compose.yml          # worker + anonymizer
  .env.example                # secrets template → ENV.md
  python-service/
    worker-py/                # scan API (CF Worker + FastAPI)
    anonymizer-container/     # Presidio
    container/                # legacy ONNX (see PRODUCTION.md)
    worker/                   # CF container wrapper for legacy ONNX
```
