# Akto Guardrails Embedder

A small FastAPI service that produces sentence embeddings for the
guardrails-service semantic cache.

The model (`sentence-transformers/all-MiniLM-L6-v2`, 384-dim, L2-normalized)
lives here instead of inside the Go `guardrails-service` binary. guardrails-service
keeps the cache logic (Redis vector store, policy-rule hashing, comparison and
Slack alerting) and calls this service over HTTP whenever it needs a vector.

```
guardrails-service (Go)  ──HTTP POST /embed──▶  embedder-container (FastAPI + sentence-transformers)
        │
        └──▶ Redis vector index (KNN / store)
```

## Why a container (and not the Pyodide `worker-py`)

`worker-py` runs on Cloudflare's Pyodide runtime, which can't load
`onnxruntime` / `sentence-transformers` / `torch` or a ~90 MB model. Heavy
native Python ML in this repo runs in plain Docker containers — same pattern as
`anonymizer-container` (Presidio).

## Endpoints

| Method | Path           | Body                       | Response                                  |
|--------|----------------|----------------------------|-------------------------------------------|
| GET    | `/health`      | —                          | `{"ok": true, "model": ..., "dim": 384}`  |
| POST   | `/embed`       | `{"text": "..."}`          | `{"vector": [...384 floats], "dim": 384}` |
| POST   | `/embed/batch` | `{"texts": ["...", ...]}`  | `{"vectors": [[...], ...], "dim": 384}`   |

## Run

```bash
# Local (after `pip install -r src/requirements.txt`)
cd src && uvicorn embedder_service:app --host 0.0.0.0 --port 8094

# Docker
docker build -t akto-guardrails-embedder .
docker run -p 8094:8094 akto-guardrails-embedder
```

## Config

| Env var       | Default                                          | Purpose                  |
|---------------|--------------------------------------------------|--------------------------|
| `EMBED_MODEL` | `sentence-transformers/all-MiniLM-L6-v2`         | Model to load and serve  |
| `PORT`        | `8094`                                           | Listen port              |

guardrails-service points at this service via `CACHE_EMBEDDER_URL`
(e.g. `http://embedder:8094`).
