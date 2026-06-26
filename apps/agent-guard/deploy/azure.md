# Agent Guard on Azure

See [README.md](../README.md), [ENV.md](../ENV.md). No Cloudflare DNS cutover — new installs get two Azure worker URLs manually.

## Architecture

| Container App | Image | Ingress | Scale |
|---------------|-------|---------|-------|
| `agent-guard-anonymizer` | `akto-agent-guard-anonymizer:<tag>` | Internal `:8093` | min 1, max 5, HTTP ×50 |
| `agent-guard-embedder` | `akto-agent-guard-embedder:<tag>` | Internal `:8094` | min 1, max 5, HTTP ×50 |
| `agent-guard-redis` | `redis/redis-stack-server:<tag>` | Internal `:6379` (TCP) | min 1, max 1 |
| `agent-guard-executor-v2` | `akto-agent-guard-worker:<tag>` | External `:8090` | min 1, max 20, HTTP ×100 |
| `agent-guard-executor` | same worker image | External `:8090` | same |

- **Environment:** External, **public network access ON** (create with first Container App).
- **Two workers** = same image, different config (`DEFAULT_MODEL_CONFIG_JSON` on executor only).
- **Scale:** min replicas = floor always running; max = burst cap; HTTP ×N = concurrent requests per replica before adding another.
- **Embedder:** keep **min 1** (a cold start reloads the ~90 MB model); needs ~1–2 GB RAM + ≥1 vCPU (torch). Internal ingress only — the workers reach it via `EMBEDDER_URL`.
- **Redis:** must run **redis-stack-server** (bundles the RediSearch module the vector cache needs — plain Redis has no `FT.*`). **min 1, max 1** (stateful, single writer). Internal **TCP** ingress (not HTTP). Add a volume if you want the cache to survive restarts; it's only a cache, so ephemeral is acceptable. Alternative: Azure Cache for Redis **Enterprise** (managed, has the search module) instead of this Container App — then skip the app and point `REDIS_URL` at it.

Deploy order: **anonymizer → embedder → redis → executor-v2 → executor**. The embedder + Redis only need to exist before a worker runs with `CACHE_MODE` enabled; the cache is fail-open, so workers start fine even if they're not ready yet.

## Initial setup (Portal)

1. **Container Apps** → **Create** → new environment, public access **On**, Log Analytics attached.
2. Create the **5 apps** (table above). Anonymizer, embedder, redis: ingress **Limited to environment** (redis = **TCP** target port 6379). Workers: **Accepting traffic from anywhere**.
3. **Key Vault** → secrets for SA keys; **access policies** for your user (Get, List, Set) + `container-apps-identity` (Get, List).
4. Per worker: **Identity** → user-assigned `container-apps-identity` → **Application** → **Secrets** (Key Vault ref) → **Containers** → env **Reference a secret**.
5. Plain env on workers: `ANONYMIZER_URL=https://<anonymizer-internal-fqdn>`, `EMBEDDER_URL=https://<embedder-internal-fqdn>`, `REDIS_URL=redis://<redis-internal-fqdn>:6379` (or the Azure Redis Enterprise connection string as a secret), `CACHE_MODE` (`observe` to start, `decide` to serve), `CACHE_DISTANCE_THRESHOLD`, `CACHE_TTL_SECONDS`, project/endpoint IDs, `DEFAULT_MODEL_CONFIG_JSON` on executor only.
6. Optional: **Custom domains** on each worker (CNAME → `*.azurecontainerapps.io`, managed cert).

### Secrets layout

| Key Vault → app secret → env ref | Plain env |
|----------------------------------|-----------|
| `QWEN3GUARD_SA_KEY_JSON`, `GEMMA_VERTEX_SA_KEY_JSON`, `ANTHROPIC_API_KEY`, `CACHE_SHADOW_SLACK_WEBHOOK_URL`, `REDIS_URL` (if Azure Redis Enterprise w/ access key) | `QWEN3GUARD_PROJECT`, `QWEN3GUARD_LOCATION`, `QWEN3GUARD_ENDPOINT_ID`, `GEMMA_VERTEX_*`, `ANTHROPIC_MODEL`, `ANONYMIZER_URL`, `EMBEDDER_URL`, `REDIS_URL` (if internal redis-stack app, no password), `CACHE_MODE`, `CACHE_DISTANCE_THRESHOLD`, `CACHE_TTL_SECONDS`, `DEFAULT_MODEL_CONFIG_JSON` (executor), `SLACK_WEBHOOK_URL` |

Env dropdown empty? Add **Application → Secrets** first, then env **Reference a secret** (not Key Vault directly).

No `docker-compose.yml` upload in Portal — one Container App per service.

## Updates

Azure does **not** auto-pull when Docker Hub overwrites `:latest`. Each release needs a **new revision**.

### Manual / local

```bash
cd apps/agent-guard
RG=rg-agent-guard-prod ./deploy/azure-deploy.sh
```

Sets `DEPLOYED_AT` so Azure re-pulls `latest`. Override app names: `WORKER_APPS="app1 app2"`.

**Portal:** each app → **Edit and deploy** → bump plain env `DEPLOYED_AT` → **Create**. Do not touch secrets.

**Rollback:** **Revision management** → activate previous revision.

### CI (`prod.yml`)

Enable checkbox **Deploy Agent Guard to Azure** when running Production workflow (after **Agent Guard** build). Runs `deploy/azure-deploy.sh` after image push.

| GitHub secret | Required | Purpose |
|---------------|----------|---------|
| `AZURE_CREDENTIALS` | Yes | Service principal JSON for `azure/login` |
| `AGENT_GUARD_AZURE_RESOURCE_GROUP` | No | Defaults to `rg-agent-guard-prod` in script |
| `AGENT_GUARD_AZURE_WORKER_APPS` | No | Space-separated worker app names |
| `AGENT_GUARD_AZURE_ANONYMIZER_APP` | No | Defaults to `agent-guard-anonymizer` |
| `AGENT_GUARD_AZURE_IMAGE_TAG` | No | Defaults to `latest` |

#### One-time: Azure service principal for GitHub

```bash
RG=rg-agent-guard-prod
SP_NAME=github-agent-guard-deploy

az ad sp create-for-rbac \
  --name "$SP_NAME" \
  --role contributor \
  --scopes /subscriptions/<SUBSCRIPTION_ID>/resourceGroups/$RG \
  --sdk-auth
```

Copy the **entire JSON output** → GitHub repo **Settings** → **Secrets** → **New repository secret** → name `AZURE_CREDENTIALS`.

Grant only the agent-guard resource group (not whole subscription) unless your policy requires broader scope. Contributor on the RG allows `az containerapp update`.

Optional secrets for non-default app names:

```bash
# Example if your apps differ
AGENT_GUARD_AZURE_WORKER_APPS=agent-guard-worker-red agent-guard-executor
AGENT_GUARD_AZURE_RESOURCE_GROUP=rg-agent-guard-prod
```

## Monitoring

| Need | Portal |
|------|--------|
| Replica count | **Application** → **Revisions and replicas** or **Metrics** → `Replicas` |
| Live logs | **Monitoring** → **Log stream** |
| Hits / errors | **Metrics** → `Requests`, `Http5xx`, `RequestDuration` |
| Query history | Log Analytics → **Logs** → `ContainerAppConsoleLogs_CL` |

```bash
az containerapp logs show -n <app> -g <rg> --follow
az containerapp replica list -n <app> -g <rg> -o table
```

## Custom DNS

Per worker: **Custom domains** → add hostname → CNAME at DNS provider → managed certificate.

| DNS | App |
|-----|-----|
| DNS #1 (v2) | `agent-guard-executor-v2` |
| DNS #2 (prod cascade) | `agent-guard-executor` |

Set `AGENT_GUARD_ENGINE_URL` on new installs to the matching HTTPS URL.
