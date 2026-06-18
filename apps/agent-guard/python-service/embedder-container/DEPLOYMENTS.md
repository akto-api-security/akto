# Deployments — semantic-cache infrastructure

The per-scanner semantic cache in `worker-py` (shadow mode, `src/cache_shadow.py`)
has two external dependencies. This doc covers provisioning **both**:

- **Vectorize index** — the vector store the worker queries and upserts into.
  Required before the worker's first deploy (the `VECTORIZE` binding is
  unconditional in `wrangler*.jsonc`).
- **Embedder service** — this FastAPI container; the worker calls it to turn text
  into 384-dim vectors.

Provisioning these only *enables* the cache to run. Turning shadow mode on per
worker (the `CACHE_*` env vars) lives in
[worker-py/DEPLOYMENTS.md → Semantic cache](../worker-py/DEPLOYMENTS.md#semantic-cache-shadow-mode).

## Prerequisites

- `wrangler` authenticated for the `billing-53a` Cloudflare account
  (`wrangler login` or `CLOUDFLARE_API_TOKEN`).
- Docker, to build the embedder image.

## 1. Vectorize index (one-time, per Cloudflare account)

The `VECTORIZE` binding in `worker-py/wrangler*.jsonc` points at an index named
`guardrails-shadow-cache`, so the index **must exist before the worker's first
deploy** — `pywrangler deploy` fails on a binding to a missing index. Create it
and its metadata index once:

```bash
npx wrangler vectorize create guardrails-shadow-cache --dimensions 384 --metric cosine
npx wrangler vectorize create-metadata-index guardrails-shadow-cache --property-name scanner_key --type string
```

- Dimensions (384) must match the embedder model (`all-MiniLM-L6-v2`).
- The `scanner_key` metadata index is **required** — the per-scanner `$eq` filter
  returns nothing without it.
- The binding uses `"remote": true` (Vectorize has no local emulator), so even
  `wrangler dev` talks to this real index.

Verify:

```bash
npx wrangler vectorize list
npx wrangler vectorize info guardrails-shadow-cache
```

## 2. Embedder service

The worker embeds text by calling `POST {EMBEDDER_URL}/embed`, so the embedder
must be deployed at a URL reachable **from Cloudflare's edge**. It's a FastAPI
service serving `all-MiniLM-L6-v2` on port 8094, with the model pre-baked into the
image (so the first request isn't blocked on a HuggingFace download). The model
can't run in the Pyodide worker, which is why it lives in a container.

### Local (`wrangler dev` only)

```bash
docker build -t akto-guardrails-embedder .
docker run --rm -p 8094:8094 akto-guardrails-embedder
curl -s localhost:8094/health      # {"ok": true, "model": ..., "dim": 384}
# then in worker-py/.dev.vars: EMBEDDER_URL=http://localhost:8094
```

`http://localhost:8094` is reachable only under local `wrangler dev`, never from a
deployed worker.

### Option A: Cloudflare Container (the deployed path)

The embedder is hosted by the existing **`../anonymizer-worker`**, which owns
*both* containers (anonymizer + embedder) and routes `/embed*` to this one — see
its `wrangler.jsonc` (two `containers` entries + two Durable Object classes) and
`src/index.ts` (path routing). Deploy that one worker:

```bash
cd ../anonymizer-worker && npm install && npx wrangler deploy
```

`wrangler deploy` builds and pushes both container images; the two containers
start/sleep/scale independently (separate Durable Object classes).

worker-py reaches the embedder over the **`ANONYMIZER_WORKER` service binding**
(`cache_shadow._embed`), not a public URL — a deployed Worker can't fetch another
Worker via its `workers.dev` URL. So there's nothing else to configure for prod:
no `EMBEDDER_URL` needed. `EMBEDDER_URL` is only the local-dev fallback (see Local
above).

### Option B: any container host

Build, push to a registry (Akto images live under `public.ecr.aws/aktosecurity/…`),
and run it somewhere that exposes a public HTTPS endpoint (ECS / Cloud Run / Fly /
a VM):

```bash
docker build -t <registry>/akto-guardrails-embedder:latest .
docker push <registry>/akto-guardrails-embedder:latest
# run it on the host, then: EMBEDDER_URL=https://<public-embedder-host>
```

## Config

| Env var       | Default                                    | Purpose                 |
|---------------|--------------------------------------------|-------------------------|
| `EMBED_MODEL` | `sentence-transformers/all-MiniLM-L6-v2`   | Model to load and serve |
| `PORT`        | `8094`                                      | Listen port             |

> Changing `EMBED_MODEL` changes the vector dimension. If you do, recreate the
> Vectorize index with matching `--dimensions`.

## Next step

With the index and embedder in place, enable shadow mode per worker — see
[worker-py/DEPLOYMENTS.md → Semantic cache](../worker-py/DEPLOYMENTS.md#semantic-cache-shadow-mode).
