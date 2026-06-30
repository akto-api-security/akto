"""Akto Agent Guard semantic-cache embedder service.

A tiny FastAPI service that turns text into sentence embeddings using
sentence-transformers/all-MiniLM-L6-v2 (384-dim, L2-normalized).

It exists so the worker (Pyodide on Cloudflare, or the FastAPI container) does
not have to host an ONNX/torch runtime and a model. The worker owns the cache
logic (Redis vector store, rule hashing, comparison/alerting) and calls
POST /embed here whenever it needs a vector.
"""

import os
from typing import List, Optional

from fastapi import FastAPI
from pydantic import BaseModel
from sentence_transformers import SentenceTransformer

import intent_classifier

MODEL_NAME = os.getenv("EMBED_MODEL", "sentence-transformers/all-MiniLM-L6-v2")
EMBEDDING_DIM = 384  # all-MiniLM-L6-v2

app = FastAPI(title="Akto Agent Guard Embedder", version="1.0.0")

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


class ClassifyRequest(BaseModel):
    agent_host: str = ""
    vectors: List[List[float]]


class ClassifyResult(BaseModel):
    p_malicious: Optional[float]


class ClassifyResponse(BaseModel):
    results: List[ClassifyResult]


class TrainRequest(BaseModel):
    agent_host: str
    vectors: List[List[float]]
    labels: List[int]  # 1 = malicious, 0 = benign


def _encode(texts: List[str]) -> List[List[float]]:
    # normalize_embeddings=True yields unit-norm vectors, which is what lets the
    # cache's COSINE distance behave as a dot product.
    arr = model.encode(texts, normalize_embeddings=True)
    return arr.tolist()


@app.get("/health")
def health():
    return {"ok": True, "model": MODEL_NAME, "dim": EMBEDDING_DIM,
            **intent_classifier.stats()}


@app.post("/embed", response_model=EmbedResponse)
def embed(req: EmbedRequest):
    vector = _encode([req.text])[0]
    return EmbedResponse(vector=vector, dim=len(vector))


@app.post("/embed/batch", response_model=EmbedBatchResponse)
def embed_batch(req: EmbedBatchRequest):
    vectors = _encode(req.texts) if req.texts else []
    return EmbedBatchResponse(vectors=vectors, dim=EMBEDDING_DIM)


@app.post("/classify", response_model=ClassifyResponse)
def classify(req: ClassifyRequest):
    """Run the agent's task-intent classifier on already-computed embeddings.

    Returns p_malicious per vector (None when the agent has no model yet — the
    worker then falls back to its regex signal + semantic cache).
    """
    probs = intent_classifier.predict(req.agent_host, req.vectors)
    return ClassifyResponse(results=[ClassifyResult(p_malicious=p) for p in probs])


@app.post("/train")
def train(req: TrainRequest):
    """(Re)train an agent's classifier from learned examples (vectors + labels)."""
    return intent_classifier.train(req.agent_host, req.vectors, req.labels)
