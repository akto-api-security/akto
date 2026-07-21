"""Per-customer intent-classifier service.

Deployed one instance per tenant (colocated with or reachable from that
customer's mcp-endpoint-shield), so /classify and /train never see another
customer's traffic — statefulness (the in-process `_models` registry in
intent_classifier.py) is safe here in a way it wouldn't be on the shared,
multi-tenant embedder-container.

Contract:
  POST /train    {agent_host, vectors, labels, risk_categories} -> fits and
                  installs the model locally (L1), and returns a pickled,
                  base64-encoded blob of it so the caller (Go) can persist it
                  to Redis (L2) for other replicas of this same service.
  POST /classify {agent_host, vectors, model_cache_key} -> on an L1 miss,
                  fetches model_cache_key from Redis (L2) and installs it
                  before predicting. Never errors on a cold agent — returns an
                  all-None result, matching today's fail-open-to-ESCALATE
                  contract.
"""

import base64
import logging
from typing import Dict, List, Optional

from fastapi import FastAPI
from pydantic import BaseModel

import intent_classifier
import redis_client

logger = logging.getLogger(__name__)

app = FastAPI(title="Akto Agent Guard Intent Classifier", version="1.0.0")


class ClassifyRequest(BaseModel):
    agent_host: str
    vectors: List[List[float]]
    # Redis key mcp-endpoint-shield wrote the trained model artifact under
    # (see mcp/intentguard/store.go) — used only on an L1 (`_models`) miss.
    model_cache_key: str = ""


class ClassifyResult(BaseModel):
    intent: Optional[str] = None
    confidence: Optional[float] = None
    margin: Optional[float] = None
    centroid_similarity: Optional[float] = None
    risk_category: Optional[str] = None


class ClassifyResponse(BaseModel):
    results: List[ClassifyResult]


class TrainRequest(BaseModel):
    agent_host: str
    vectors: List[List[float]]
    labels: List[str]  # fine-grained intent per vector, may include "__other__"/"__background__"
    risk_categories: Dict[str, str] = {}  # intent -> delete|edit|create|fetch_pii|fetch_generic


class TrainResponse(BaseModel):
    trained: bool
    reason: Optional[str] = None
    n_samples: int = 0
    classes: List[str] = []
    dropped_classes: List[str] = []
    # base64(pickle(model)) — present only when trained=True. The caller
    # persists this to Redis; this service never writes it there itself.
    model_blob: Optional[str] = None


@app.get("/health")
def health():
    return {"ok": True, **intent_classifier.stats()}


@app.post("/classify", response_model=ClassifyResponse)
def classify(req: ClassifyRequest):
    if not intent_classifier.has_model(req.agent_host) and req.model_cache_key:
        blob = redis_client.get(req.model_cache_key)
        if blob is not None:
            intent_classifier.load_model(req.agent_host, blob)

    results = intent_classifier.predict(req.agent_host, req.vectors)
    return ClassifyResponse(results=[
        ClassifyResult(**r) if r is not None else ClassifyResult() for r in results
    ])


@app.post("/train", response_model=TrainResponse)
def train(req: TrainRequest):
    result = intent_classifier.train(req.agent_host, req.vectors, req.labels, req.risk_categories)
    model_blob = None
    if result.get("trained"):
        blob = intent_classifier.serialize_model(req.agent_host)
        if blob is not None:
            model_blob = base64.b64encode(blob).decode("ascii")
    return TrainResponse(**result, model_blob=model_blob)
