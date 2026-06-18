"""Akto Guardrails semantic-cache embedder service.

A tiny FastAPI service that turns text into sentence embeddings using
sentence-transformers/all-MiniLM-L6-v2 (384-dim, L2-normalized).

It exists so the Go guardrails-service does not have to embed an ONNX runtime
and model into its binary. guardrails-service owns the cache logic (Redis vector
store, rule hashing, comparison/alerting) and calls POST /embed here whenever it
needs a vector.

Parity note: embeddings are byte-compatible with the previous in-Go hugot
pipeline — same model, mean pooling, unit-norm output — so the existing Redis
vector index and COSINE distance threshold keep working unchanged.
"""

import os
from typing import List

from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

MODEL_NAME = os.getenv("EMBED_MODEL", "sentence-transformers/all-MiniLM-L6-v2")
EMBEDDING_DIM = 384  # all-MiniLM-L6-v2

app = FastAPI(title="Akto Guardrails Embedder", version="1.0.0")

# Loaded once at process start. SentenceTransformer.encode is safe to call
# concurrently, so a single shared instance serves all requests.
model = SentenceTransformer(MODEL_NAME)


class EmbedRequest(BaseModel):
    text: str


class EmbedBatchRequest(BaseModel):
    texts: List[str]


class EmbedResponse(BaseModel):
    vector: List[float]
    dim: int


class EmbedBatchResponse(BaseModel):
    vectors: List[List[float]]
    dim: int


def _encode(texts: List[str]) -> List[List[float]]:
    # normalize_embeddings=True yields unit-norm vectors, which is what lets the
    # cache's COSINE distance behave as a dot product (parity with the old Go
    # path, which used hugot's WithNormalization()).
    arr = model.encode(texts, normalize_embeddings=True)
    return arr.tolist()


@app.get("/health")
def health():
    return {"ok": True, "model": MODEL_NAME, "dim": EMBEDDING_DIM}


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest):
    vector = _encode([req.text])[0]
    return EmbedResponse(vector=vector, dim=len(vector))


@app.post("/embed/batch", response_model=EmbedBatchResponse)
def embed_batch(req: EmbedBatchRequest):
    vectors = _encode(req.texts) if req.texts else []
    return EmbedBatchResponse(vectors=vectors, dim=EMBEDDING_DIM)
