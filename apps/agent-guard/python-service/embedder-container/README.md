# Akto Agent Guard Embedder

A small FastAPI service that produces sentence embeddings for the per-scanner
semantic cache.

The model (`sentence-transformers/all-MiniLM-L6-v2`, 384-dim, L2-normalized)
lives here instead of inside the worker, which can't host torch/ONNX (Pyodide on
Cloudflare) or shouldn't carry a ~90 MB model (the FastAPI container). The worker
keeps the cache logic (Redis vector store, rule hashing, comparison, alerting)
and calls this service over HTTP whenever it needs a vector.

```
worker (cache.py)  ──HTTP POST /embed──▶  embedder-container (FastAPI + sentence-transformers)
   │
   └──▶ Redis vector index (RediSearch KNN / store)   # see cache_store.py
```

Same container pattern as `anonymizer-container` (Presidio): heavy native Python
ML runs in a plain Docker container, the model is baked into the image at build
time, and it's reached over an internal URL.

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
docker build -t akto-agent-guard-embedder .
docker run -p 8094:8094 akto-agent-guard-embedder
```

The worker points at this service via `EMBEDDER_URL` (e.g. `http://embedder:8094`
in docker-compose, the internal FQDN on Azure Container Apps).

## Config

| Env var       | Default                                          | Purpose                  |
|---------------|--------------------------------------------------|--------------------------|
| `EMBED_MODEL` | `sentence-transformers/all-MiniLM-L6-v2`         | Model to load and serve  |
| `PORT`        | `8094`                                           | Listen port              |

> Changing `EMBED_MODEL` changes the vector dimension (`EMBEDDING_DIM`). If you
> do, update `EMBEDDING_DIM` in `worker-py/src/cache_store.py` to match, and let
> the RediSearch index recreate with the new `DIM`.

## Sizing

Pulls in torch (CPU-only wheel — see Dockerfile), so the image is larger
(~1–2 GB) and wants ~1–2 GB RAM. Keep **min replicas = 1** wherever it runs so a
cold start doesn't reload the model on the critical path (same reasoning as the
anonymizer's long `start_period`).
