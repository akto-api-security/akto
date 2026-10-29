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
| `SCANNER_RESPONSE_FORMAT` | yes | yes |
| `LLM_VALIDATOR_RESPONSE_FORMAT` | gateway-side (akto-gateway), not agent-guard | no |
| `VERTEX_AI_*` | legacy ONNX (generic Vertex provider) | rarely |
| `ANONYMIZER_URL` | set in compose | leave empty |
| Vertex / Slack / model map keys | yes | yes |
| `*_FOUNDRY_*` (Azure AI Foundry providers) | yes | yes |
| `BEDROCK_*` (AWS Bedrock provider) | yes | yes |
| `REACHBACK_*` (Morgan Stanley reach-back demo API) | yes | no |

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

## Morgan Stanley reach-back gateway (`POST /reachback/test`)

Demo endpoint that sends one chat completion to a model behind Morgan
Stanley's reach-back gateway. Not wired into `modelConfigs` or the cascade.
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
| `REACHBACK_TENANT` | e.g. `morganstanley.onmicrosoft.com` |
| `REACHBACK_CLIENT_ID` | Azure AD app (SPN) client id |
| `REACHBACK_SCOPE` | e.g. `api://<gateway-app>/.default` |
| `REACHBACK_THUMBPRINT` | SHA-1 thumbprint of the SPN's `public_key.cer`, hex (colons allowed) |
| `REACHBACK_SPN_KEY_FILE` | SPN private key, PEM (PKCS#1 or PKCS#8, unencrypted), e.g. under `/etc/reachback/spn/`; re-read on each token fetch |
| `REACHBACK_MTLS_CERT_FILE` | mTLS client cert, e.g. `/etc/reachback/mtls/tls.crt` (cert-manager Secret) |
| `REACHBACK_MTLS_KEY_FILE` | mTLS client key, e.g. `/etc/reachback/mtls/tls.key` |
| `REACHBACK_CA_FILE` | CA bundle for the gateway's server cert, e.g. `/etc/reachback/ca/ca.pem` |

All are required; with any missing the endpoint returns 503 naming them. The
mTLS files are loaded once per process, so a renewed cert needs a pod restart.

```bash
curl -s localhost:8090/reachback/test -H 'Content-Type: application/json' \
  -d '{"prompt": "Say hello", "model": "gpt-6-luna"}'
# 200 {"content": "...", "model": "gpt-6-luna", "token_ms": 412, "call_ms": 1830}
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
