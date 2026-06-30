"""Per-agent task-intent classifier (LogisticRegression on MiniLM embeddings).

Lives in the embedder container because that is where numpy / scikit-learn (the
sentence-transformers stack) already are; the Pyodide worker can't host them.

One binary model per agent: label 1 = malicious task, 0 = benign. Trained from
the agent's learned examples (vectors + verdict-derived labels) that the worker
POSTs to /train. predict() returns p_malicious per vector, or None when the
agent has no usable model yet (cold start) — the worker then falls back to its
regex signal + semantic cache, so the system works before any model exists.

Models are swapped atomically (a single dict reassignment under the GIL), so a
concurrent /classify always sees a fully-built model, never a half-trained one.
"""

import logging
import threading
from typing import Dict, List, Optional

import numpy as np
from sklearn.calibration import CalibratedClassifierCV
from sklearn.linear_model import LogisticRegression

logger = logging.getLogger(__name__)

# Minimum samples in the smaller class before we train at all. Below this a
# discriminative model is meaningless → stay None and let the worker use regex.
_MIN_PER_CLASS = 2
# Calibration needs ≥ cv samples per class; cap cv at 3 (the user-provided shape).
_MAX_CV = 3

_models: Dict[str, "_AgentModel"] = {}
_lock = threading.Lock()


class _AgentModel:
    """A fitted classifier plus the column index of the malicious (label=1) class."""

    __slots__ = ("clf", "pos_index", "n_samples")

    def __init__(self, clf, pos_index: int, n_samples: int):
        self.clf = clf
        self.pos_index = pos_index
        self.n_samples = n_samples

    def predict_malicious(self, X: np.ndarray) -> List[float]:
        proba = self.clf.predict_proba(X)
        return [float(row[self.pos_index]) for row in proba]


def _fit(X: np.ndarray, y: np.ndarray):
    """Fit a calibrated LogReg when there are enough per-class samples, else a
    plain LogReg. Returns (clf, pos_index) or (None, -1) when untrainable."""
    classes, counts = np.unique(y, return_counts=True)
    if len(classes) < 2:
        return None, -1  # one-class agent → not discriminative
    min_count = int(counts.min())
    if min_count < _MIN_PER_CLASS:
        return None, -1
    base = LogisticRegression(max_iter=1000, class_weight="balanced")
    if min_count >= 2:
        cv = min(_MAX_CV, min_count)
        clf = CalibratedClassifierCV(base, cv=cv)
    else:  # unreachable given the guard above, kept for clarity
        clf = base
    clf.fit(X, y)
    pos_index = int(np.where(clf.classes_ == 1)[0][0])
    return clf, pos_index


def train(agent_host: str, vectors: List[List[float]], labels: List[int]) -> Dict[str, object]:
    """Train (or retrain) the agent's model from examples. Returns a status dict.

    labels: 1 = malicious, 0 = benign. Atomic swap on success; on too-few-samples
    the previous model (if any) is left untouched and trained=False is returned.
    """
    if not vectors or len(vectors) != len(labels):
        return {"trained": False, "reason": "empty_or_mismatched"}
    X = np.asarray(vectors, dtype=np.float32)
    y = np.asarray(labels, dtype=np.int64)
    clf, pos_index = _fit(X, y)
    if clf is None:
        return {"trained": False, "reason": "insufficient_per_class_samples",
                "n_samples": int(len(y))}
    model = _AgentModel(clf, pos_index, int(len(y)))
    with _lock:
        _models[agent_host] = model  # atomic swap
    logger.info(f"[intent_clf] trained agent={agent_host} n={len(y)} classes={np.unique(y).tolist()}")
    return {"trained": True, "n_samples": int(len(y))}


def predict(agent_host: str, vectors: List[List[float]]) -> List[Optional[float]]:
    """Return p_malicious per vector, or None per vector when no model exists."""
    model = _models.get(agent_host)
    if model is None or not vectors:
        return [None] * len(vectors)
    try:
        X = np.asarray(vectors, dtype=np.float32)
        return list(model.predict_malicious(X))  # type: ignore[arg-type]
    except Exception as exc:
        logger.warning(f"[intent_clf] predict failed agent={agent_host}: {exc}")
        return [None] * len(vectors)


def stats() -> Dict[str, int]:
    return {"agents_with_models": len(_models)}
