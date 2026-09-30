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
| `BEDROCK_*` (AWS Bedrock provider) | yes | yes |

## AWS Bedrock (`bedrock` provider)

One provider covers every Bedrock model through the model-agnostic Converse API
(`POST /model/{modelId}/converse`).

| Variable | Required | Notes |
|----------|----------|-------|
| `BEDROCK_REGION` | yes | e.g. `us-east-1`; also the SigV4 signing region |
| `BEDROCK_MODEL` | if an entry sets no `model` | foundation model id, cross-region inference profile (`us.anthropic...`) or ARN |
| `BEDROCK_API_KEY` | one auth mode | Bedrock API key, sent as Bearer; **takes precedence** over IAM keys |
| `BEDROCK_ACCESS_KEY_ID` / `BEDROCK_SECRET_ACCESS_KEY` | other auth mode | IAM user keys, SigV4-signed in pure Python (botocore can't load in the Worker) |
| `BEDROCK_SESSION_TOKEN` | no | only for temporary STS credentials |
| *(none)* | third auth mode | **IAM role on EKS/ECS**: with no key set, the pod's role is used via EKS Pod Identity, IRSA or an ECS task role — detected from the `AWS_*` vars EKS injects, refreshed automatically |

Auth precedence: `BEDROCK_API_KEY` → `BEDROCK_ACCESS_KEY_ID`/`BEDROCK_SECRET_ACCESS_KEY` → pod IAM role.
On EKS, leave all three credential vars unset and attach the role to the pod's
service account (Pod Identity association or IRSA `eks.amazonaws.com/role-arn`
annotation). The pod then needs egress to `bedrock-runtime.<region>` and, for
IRSA, `sts.<region>` (VPC interface endpoints keep both private), plus
`169.254.170.23:80` for the Pod Identity agent.

- IAM needs `bedrock:InvokeModel` on the model (or inference-profile) ARN, and
  model access must be enabled for that model in the Bedrock console.
- A `modelConfigs` entry may set its own `model` and `baseUrl` (e.g. a VPC
  interface endpoint); credentials and region are env-only.
- Converse returns no logprobs, so Bedrock suits the LLM-judge roles (JSON
  verdict prompts), not the Qwen3Guard logprob-confidence path.

> The per-scanner semantic cache has moved out of agent-guard to
> guardrails-service (which now owns the Redis vector store + embedder in front of
> agent-guard's `/scan`). agent-guard no longer reads `CACHE_*`, `EMBEDDER_URL`,
> or `REDIS_URL`.
