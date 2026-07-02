# Agent Guard on Azure

See [README.md](../README.md), [ENV.md](../ENV.md). No Cloudflare DNS cutover — new installs get two Azure worker URLs manually.

## Architecture

| Container App | Image | Ingress | Scale |
|---------------|-------|---------|-------|
| `agent-guard-anonymizer` | `akto-agent-guard-anonymizer:<tag>` | Internal `:8093` | min 1, max 5, HTTP ×50 |
| `agent-guard-executor-v2` | `akto-agent-guard-worker:<tag>` | External `:8090` | min 1, max 20, HTTP ×100 |
| `agent-guard-executor` | same worker image | External `:8090` | same |

- **Environment:** External, **public network access ON** (create with first Container App).
- **Two workers** = same image, different config (`DEFAULT_MODEL_CONFIG_JSON` on executor only).
- **Scale:** min replicas = floor always running; max = burst cap; HTTP ×N = concurrent requests per replica before adding another.

> The semantic cache (embedder + Redis) has moved to guardrails-service, which
> now caches in front of agent-guard's `/scan`. agent-guard no longer deploys an
> embedder or Redis, and workers no longer read `CACHE_*` / `EMBEDDER_URL` / `REDIS_URL`.

Deploy order: **anonymizer → executor-v2 → executor**.

## Initial setup (Portal)

1. **Container Apps** → **Create** → new environment, public access **On**, Log Analytics attached.
2. Create the **3 apps** (table above). Anonymizer: ingress **Limited to environment**. Workers: **Accepting traffic from anywhere**.
3. **Key Vault** → secrets for SA keys; **access policies** for your user (Get, List, Set) + `container-apps-identity` (Get, List).
4. Per worker: **Identity** → user-assigned `container-apps-identity` → **Application** → **Secrets** (Key Vault ref) → **Containers** → env **Reference a secret**.
5. Plain env on workers: `ANONYMIZER_URL=https://<anonymizer-internal-fqdn>`, project/endpoint IDs, `DEFAULT_MODEL_CONFIG_JSON` on executor only.
6. Optional: **Custom domains** on each worker (CNAME → `*.azurecontainerapps.io`, managed cert).

### Secrets layout

| Key Vault → app secret → env ref | Plain env |
|----------------------------------|-----------|
| `QWEN3GUARD_SA_KEY_JSON`, `GEMMA_VERTEX_SA_KEY_JSON`, `ANTHROPIC_API_KEY` | `QWEN3GUARD_PROJECT`, `QWEN3GUARD_LOCATION`, `QWEN3GUARD_ENDPOINT_ID`, `GEMMA_VERTEX_*`, `ANTHROPIC_MODEL`, `ANONYMIZER_URL`, `DEFAULT_MODEL_CONFIG_JSON` (executor), `SLACK_WEBHOOK_URL` |

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
