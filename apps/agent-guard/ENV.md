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
| `REACHBACK_*` (reach-back gateway `reachback` provider) | yes | no |
| `OCR_MODEL_CONFIG_JSON` / `OCR_*` (image OCR) | yes | yes |

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

## Image OCR (`POST /ocr`)

Extracts the text in an image, so guardrails-service can hand images to
agent-guard and agent-guard picks the OCR provider. The image goes to the
providers in `OCR_MODEL_CONFIG_JSON`, in order: the first that answers wins,
and one that fails, times out or answers anything but HTTP 200 falls through
to the next.

**Providers are config, not code.** Each item in the list is a built-in
preset by name, or an `"http"` entry that describes one call. Adding a
provider is a config entry plus its key; no code change.

| Variable | Notes |
|----------|-------|
| `OCR_MODEL_CONFIG_JSON` | JSON list of preset names and entries, e.g. `["gemma", "azure_vision"]`. Unset → `["gemma"]`. Malformed → OCR is off (503), not a silent default. |
| `OCR_GEMMA_URL` / `OCR_GEMMA_KEY` / `OCR_GEMMA_MODEL` | `gemma` preset: an OpenAI-compatible chat endpoint (e.g. `http://<vllm>/v1`, without `/chat/completions`) that accepts image input, its Bearer key, and the served model name |
| `OCR_AZURE_URL` / `OCR_AZURE_KEY` | `azure_vision` preset: the Azure Computer Vision resource endpoint and key (OCR v3.2, the same call guardrails-service makes today) |
| any other `OCR_*` | whatever your own entries name, e.g. `OCR_GOOGLE_KEY` below |

Presets: `gemma` (OCR through a Gemma chat model; the endpoint must allow
image input, e.g. vLLM's `--limit-mm-per-prompt '{"image": 1}'`) and
`azure_vision`. Both are ordinary entries kept in `ocr.py`'s `PRESETS`.

An `"http"` entry:

```json
{
  "provider": "http",
  "name": "google_vision",
  "url": "https://vision.googleapis.com/v1/images:annotate",
  "headers": {"X-Goog-Api-Key": "${OCR_GOOGLE_KEY}"},
  "body": {"requests": [{"image": {"content": "${image_b64}"}, "features": [{"type": "TEXT_DETECTION"}]}]},
  "text": {"path": "responses[0].fullTextAnnotation.text"},
  "timeoutMs": 30000
}
```

| Field | Meaning |
|-------|---------|
| `url`, `headers`, `body` | The request. `body` is a JSON template, a text template, or `"${image_bytes}"` (the default: the raw image, sent as `application/octet-stream` unless `headers` says otherwise). |
| `text` | Where the text is in a JSON reply. `path`: `a.b`, `[0]` for an index, `[]` for every item of a list. `join` (default newline) for a list. `each`, a nested spec applied to every item first. For example, Azure's preset is `{"path": "regions[].lines[]", "join": "\n", "each": {"path": "words[].text", "join": " "}}`. Without `text`, the reply body is the text. |
| `timeoutMs` | Default 30000. A value that isn't a positive number fails only that entry. |

Placeholders: `${image_bytes}` (the raw image, whole body only),
`${image_b64}`, `${image_mime}` (e.g. `image/png`, from the image's first
bytes, for `data:${image_mime};base64,${image_b64}` URLs), and `${OCR_...}`
for any environment variable starting with `OCR_`. Only `OCR_` names are
readable, so an entry can't send agent-guard's model keys to the URL it names.
An entry naming an unset variable is skipped. Keys are redacted from logs.

Config can't express request signing (AWS Textract's SigV4), OAuth
token exchange (service accounts), or submit-then-poll APIs; those need code.

`image_b64` is standard base64: padded, with no line breaks and no `data:`
prefix (Go's `base64.StdEncoding`). A request whose `Content-Length` is over
the 4 MB image plus JSON framing gets a 413 before its body is read.

```bash
# Piped, not inline: an inline base64 image over ~96 KB exceeds the shell's per-argument limit.
{ printf '{"image_b64":"'; base64 -w0 screenshot.png; printf '"}'; } |
  curl -s localhost:8090/ocr -H 'Content-Type: application/json' -d @-
# 200 {"text": "line one\nline two", "provider": "gemma"}
# 400 body not a JSON object, or image_b64 missing/invalid · 413 image (or body) too large · 503 no provider configured
# 502 {"error": "every OCR provider failed", "failures": [{"provider": "gemma", "reason": "HTTP 400"}]}
```

Like `/scan`, the endpoint has no auth of its own: anything that can reach
agent-guard can run OCR on the configured key.

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
