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

## Enrichment model (`ENRICHMENT_MODEL_CONFIG_JSON`)

A separate model for the post-verdict enrichment calls, so they don't load the
core guardrails model. Same JSON shape as `DEFAULT_MODEL_CONFIG_JSON`; only its
`FINAL_ARBITER` (and optional `FINAL_ARBITER_BACKUP`) entries are used.

| Route | Unset (default) | Set |
|-------|-----------------|-----|
| `POST /guardrails/llm` (blocking LLM rules, PII) | core model | core model |
| `POST /guardrails/llm/session` (session risk summary) | core model | core model |
| `POST /guardrails/llm/async` (evidence line, remediation, custom-rule reason) | core model | enrichment model |
| `POST /scan` with `enrichment: "reason"` | policy `modelConfigs`, else core model | enrichment model |

Unset, unparseable, or without `modelConfigs` → behaves exactly as before (a
warning is logged for the last two). Set but its provider can't be built (e.g.
missing key) → enrichment calls fail (503 / empty reason) rather than silently
falling back onto the core model. Logs carry `source=enrichment|default` on
`[GuardrailsLLM]` lines.

## Per-pod model config (`PER_POD_MODEL_CONFIGS_JSON`)

Lets each pod run **PromptInjection** and **Toxicity** on its own model config (one config
per pod drives both scanners), e.g. to pin pod N to GPU endpoint N. Kubernetes only: run agent-guard as a **StatefulSet** and expose the
pod name, so pods are `agent-guard-0`, `agent-guard-1`, … and each knows its index:

```yaml
env:
- name: POD_NAME
  valueFrom: { fieldRef: { fieldPath: metadata.name } }
- name: PER_POD_MODEL_CONFIGS_JSON   # entry N is used by the pod ending in -N
  value: '[{"modelConfigs": [...]}, {"modelConfigs": [...]}]'
```

Each entry has the same shape as `DEFAULT_MODEL_CONFIG_JSON`; set a per-entry `baseUrl` /
`model` to point at that pod's endpoint. Precedence for those two scanners (verdict and
`enrichment: "reason"`): request/policy `modelConfigs` → this pod's entry →
`DEFAULT_MODEL_CONFIG_JSON` (`ENRICHMENT_MODEL_CONFIG_JSON` still wins for the reason call).
Other scanners always use `DEFAULT_MODEL_CONFIG_JSON`.

At boot each pod logs `[constants] pod <name> uses PER_POD_MODEL_CONFIGS_JSON[N]: ...`. If
the pod has no index (Deployment, no `POD_NAME`), the index is past the end of the list, its
entry has no `modelConfigs`, or the JSON is invalid, an error is logged and the pod uses
`DEFAULT_MODEL_CONFIG_JSON` — never another pod's entry. Keep `replicas` ≤ the number of entries (and no HPA).

## Docker-only vs Cloudflare-only keys

| Variable | Docker | Cloudflare |
|----------|--------|------------|
| `AGENT_GUARD_WORKER_TAG` | yes (current stack) | no |
| `AGENT_GUARD_ANONYMIZER_TAG` | yes (current stack) | no |
| `AGENT_GUARD_EXECUTOR_TAG` | yes (legacy ONNX only) | no |
| `FORCE_LLM_MODE` / `SCANNER_LLM_PROVIDER` | legacy ONNX only | no |
| `SCANNER_RESPONSE_FORMAT` | yes | yes |
| `LLM_VALIDATOR_RESPONSE_FORMAT` | gateway-side (akto-gateway), not agent-guard | no |
| `VERTEX_AI_*` | legacy ONNX (generic Vertex provider) | rarely |
| `ANONYMIZER_URL` | set in compose | leave empty |
| Vertex / Slack / model map keys | yes | yes |
| `*_FOUNDRY_*` (Azure AI Foundry providers) | yes | yes |
| `BEDROCK_*` (AWS Bedrock provider) | yes | yes |
| `REACHBACK_*` (reach-back gateway `reachback` provider) | yes | no |

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
| `BEDROCK_CREDENTIALS_REFRESH_MARGIN_SEC` | no | pod IAM-role mode only: refresh credentials this many seconds before they expire (default `1800` = 30 min; capped at half their lifetime) |
| *(none)* | third auth mode | **IAM role on EKS/ECS**: with no key set, the pod's role is used via EKS Pod Identity, IRSA or an ECS task role — detected from the `AWS_*` vars EKS injects, refreshed automatically |

Auth precedence: `BEDROCK_API_KEY` → `BEDROCK_ACCESS_KEY_ID`/`BEDROCK_SECRET_ACCESS_KEY` → pod IAM role.
On EKS, leave all three credential vars unset and attach the role to the pod's
service account (Pod Identity association or IRSA `eks.amazonaws.com/role-arn`
annotation). The pod then needs egress to `bedrock-runtime.<region>` and, for
IRSA, `sts.<region>` (VPC interface endpoints keep both private), plus
`169.254.170.23:80` for the Pod Identity agent.

- IAM needs `bedrock:InvokeModel` on the model (or inference-profile) ARN, and
  model access must be enabled for that model in the Bedrock console.
- **Gemma 4** (`google.gemma-4-*`) is routed automatically: AWS serves it only
  on the `bedrock-mantle` endpoint (OpenAI-compatible chat completions at
  `https://bedrock-mantle.<region>.api.aws/openai/v1`), so the `bedrock`
  provider sends those models there and everything else to Converse. Same
  region and credentials, but IAM needs **`bedrock-mantle:CreateInference`** on
  the default project (`arn:aws:bedrock-mantle:<region>:<account>:project/default`),
  and the region must offer Gemma 4 (us-east-1, us-east-2, us-west-2,
  eu-central-1 at the time of writing — not ap-south-1). Use plain ids.
- A `modelConfigs` entry may set its own `model` and `baseUrl` (e.g. a VPC
  interface endpoint); credentials and region are env-only.
- Converse returns no logprobs, so Bedrock suits the LLM-judge roles (JSON
  verdict prompts), not the Qwen3Guard logprob-confidence path.

## Reach-back gateway (`reachback` provider, `POST /reachback/test`)

Models behind a client's reach-back gateway (OpenAI chat completions),
reachable two ways:

- **`modelConfigs`**: an entry with `"provider": "reachback"` and the gateway's
  model name in `model`, in any role:
  `{"provider": "reachback", "model": "gpt-6-luna", "modelRole": "FINAL_ARBITER", "timeoutMs": 30000}`.
  The entry must set `model`; the gateway URL and credentials come only from
  the `REACHBACK_*` env below. The provider turns reasoning off
  (`reasoning_effort: "none"`) and sends `temperature: 0`, like the other
  verdict providers, plus `max_completion_tokens` (256, or 4096 for the async
  `/guardrails/llm/async` calls). Models that cannot turn reasoning off
  (`gpt-6-astra`, `gpt-6.1-sol`) answer 400, so use luna, sol or terra. An entry with a missing setting is dropped from the cascade
  with a `[Providers] reachback: missing required vars` warning.
- **`POST /reachback/test`**: forwards an OpenAI chat-completions body,
  unchanged, and returns the gateway's JSON reply. Useful for trying models
  and parameters before putting them in `modelConfigs`.

Container runtime only (needs mounted files and client-certificate TLS).

Every call carries two credentials:

1. **Azure AD app token** — a client assertion signed with the SPN private key
   (`x5t` = the certificate thumbprint) is exchanged at
   `https://login.microsoftonline.com/{tenant}/oauth2/v2.0/token` for a
   token, cached until 5 min before expiry and sent as `Authorization: Bearer`.
   The pod needs egress to `login.microsoftonline.com`.
2. **mTLS** — the connection to the gateway presents the client cert/key and
   trusts only the CA bundle.

| Variable | Notes |
|----------|-------|
| `REACHBACK_BASE_URL` | e.g. `https://<gateway>/openai/v1`; a pasted `.../chat/completions` URL is trimmed |
| `REACHBACK_TENANT` | Azure AD tenant, e.g. `contoso.onmicrosoft.com` |
| `REACHBACK_CLIENT_ID` | Azure AD app (SPN) client id |
| `REACHBACK_SCOPE` | e.g. `api://<gateway-app>/.default` |
| `REACHBACK_THUMBPRINT` | SHA-1 thumbprint of the SPN's `public_key.cer`, hex (colons allowed) |
| `REACHBACK_SPN_KEY_FILE` | SPN private key, PEM (PKCS#1 or PKCS#8, unencrypted), e.g. under `/etc/reachback/spn/`; re-read on each token fetch |
| `REACHBACK_MTLS_CERT_FILE` | mTLS client cert, e.g. `/etc/reachback/mtls/tls.crt` (cert-manager Secret) |
| `REACHBACK_MTLS_KEY_FILE` | mTLS client key, e.g. `/etc/reachback/mtls/tls.key` |
| `REACHBACK_CA_FILE` | CA bundle for the gateway's server cert, e.g. `/etc/reachback/ca/ca.pem` |

All are required; with any missing the endpoint returns 503 naming them and
`reachback` entries are skipped. The
mTLS files are loaded once per process, so a renewed cert needs a pod restart.

```bash
curl -s localhost:8090/reachback/test -H 'Content-Type: application/json' \
  -d '{"model": "gpt-6-luna", "messages": [{"role": "user", "content": "Say hello"}], "max_completion_tokens": 64}'
# 200 {"response": {<gateway chat-completions JSON>}, "token_ms": 412, "call_ms": 1830}
# 502 {"stage": "token" | "connect" | "http", "status": 401, "error": "..."}
```

`stage` says where it failed: `token` = Azure AD rejected the assertion or
the key file is unreadable; `connect` = DNS/TCP/TLS handshake/timeout to the
gateway (wrong client cert or CA); `http` = the gateway answered with an
error status (401 is retried once with a fresh token first).

The endpoint has no auth of its own: anything that can reach the agent-guard
Service can call these models through it. Fine for the demo; gate or remove it
before production.

> The per-scanner semantic cache has moved out of agent-guard to
> guardrails-service (which now owns the Redis vector store + embedder in front of
> agent-guard's `/scan`). agent-guard no longer reads `CACHE_*`, `EMBEDDER_URL`,
> or `REDIS_URL`.
