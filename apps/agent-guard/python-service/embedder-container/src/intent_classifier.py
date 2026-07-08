"""Per-agent instruction-intent classifier (multi-class LogisticRegression on
MiniLM embeddings).

Lives in the embedder container because that is where numpy / scikit-learn (the
sentence-transformers stack) already are; the Pyodide worker can't host them.

One multi-class model per agent: each class is a fine-grained intent the agent's
offline LLM-labeling service has assigned (e.g. "flight_booking",
"resource_delete_order"), plus two reject classes — "__other__" (a
recognizable-but-unmodeled ask, long-tail good intents folded in rather than
discarded) and "__background__" (data/context that leaked past the upstream
instruction/data segmenter). There is no "malicious" class here: this
classifier only ever sees examples derived from GOOD (is_valid=True) verdicts;
telling a genuinely novel malicious ask apart from a benign one remains the
LLM cascade's job.

predict() returns, per vector: {intent, confidence, margin, centroid_similarity,
risk_category}, or None when the agent has no usable model yet (cold start) —
the caller then ESCALATEs to the cascade, so the system is safe before any
model exists. confidence/margin come from the calibrated classifier;
centroid_similarity is an independent corroborating signal (cosine similarity
to the predicted class's mean training vector) that catches a confidently-wrong
prediction the classifier itself is extrapolating into.

Models are swapped atomically (a single dict reassignment under the GIL), so a
concurrent /classify always sees a fully-built model, never a half-trained one.
"""

import logging
import os
import threading
from typing import Any, Dict, List, Optional, Tuple

import numpy as np
from sklearn.calibration import CalibratedClassifierCV
from sklearn.linear_model import LogisticRegression

logger = logging.getLogger(__name__)

# Minimum samples in the smallest class before we train at all. Below this a
# discriminative model is meaningless → stay None and the caller ESCALATEs.
_MIN_PER_CLASS = 2
# Calibration needs ≥ cv samples per class; cap cv at 3 (the user-provided shape).
_MAX_CV = 3


def _debug_enabled() -> bool:
    return os.getenv("INTENT_CLASSIFY_DEBUG", "").strip().lower() in ("1", "true", "yes", "on")

_models: Dict[str, "_AgentModel"] = {}
_lock = threading.Lock()


class _AgentModel:
    """A fitted multi-class classifier plus per-class centroids and risk map."""

    __slots__ = ("clf", "centroids", "class_risk", "n_samples")

    def __init__(self, clf, centroids: Dict[str, np.ndarray],
                class_risk: Dict[str, str], n_samples: int):
        self.clf = clf
        self.centroids = centroids
        self.class_risk = class_risk
        self.n_samples = n_samples

    def _cosine_to_centroid(self, x: np.ndarray, cls: str) -> float:
        c = self.centroids.get(cls)
        if c is None:
            return 0.0
        xn = float(np.linalg.norm(x))
        if xn == 0.0:
            return 0.0
        return float(np.dot(x, c) / xn)  # c is already unit-norm

    def predict_intent(self, X: np.ndarray, agent_host: str = "") -> List[Dict[str, Any]]:
        proba = self.clf.predict_proba(X)
        classes = self.clf.classes_
        debug = _debug_enabled()
        out: List[Dict[str, Any]] = []
        for i in range(X.shape[0]):
            row = proba[i]
            order = np.argsort(row)[::-1]
            top1_idx = int(order[0])
            top2_idx = int(order[1]) if len(order) > 1 else top1_idx
            top1_class = str(classes[top1_idx])
            result = {
                "intent": top1_class,
                "confidence": float(row[top1_idx]),
                "margin": float(row[top1_idx] - row[top2_idx]),
                "centroid_similarity": self._cosine_to_centroid(X[i], top1_class),
                "risk_category": self.class_risk.get(top1_class, "unknown"),
            }
            if debug:
                top_k = [(str(classes[j]), round(float(row[j]), 4)) for j in order[:5]]
                logger.info(f"[intent_clf] agent={agent_host!r} unit={i} n_classes={len(classes)} "
                           f"top5={top_k} centroid_sim={result['centroid_similarity']:.4f}")
            out.append(result)
        return out


def _filter_min_class(X: np.ndarray, y: np.ndarray) -> Tuple[np.ndarray, np.ndarray, List[str]]:
    """Drop rows belonging to any class with fewer than _MIN_PER_CLASS samples,
    so one singleton/rare intent doesn't sink training for every other class
    the agent already has enough examples for. Returns (X, y, dropped_classes)
    — dropped classes just won't be predicted, so those requests ESCALATE
    (safe) instead of the agent staying cold entirely."""
    classes, counts = np.unique(y, return_counts=True)
    keep = set(classes[counts >= _MIN_PER_CLASS].tolist())
    dropped = sorted(str(c) for c in classes.tolist() if c not in keep)
    if not dropped:
        return X, y, []
    mask = np.isin(y, list(keep))
    return X[mask], y[mask], dropped


def _fit(X: np.ndarray, y: np.ndarray):
    """Fit a calibrated multi-class LogReg when there are enough per-class
    samples, else return None (untrainable — caller stays cold)."""
    classes, counts = np.unique(y, return_counts=True)
    if len(classes) < 2:
        return None  # one-class agent → not discriminative
    min_count = int(counts.min())
    if min_count < _MIN_PER_CLASS:
        return None
    base = LogisticRegression(max_iter=1000, class_weight="balanced")
    cv = min(_MAX_CV, min_count)
    clf = CalibratedClassifierCV(base, cv=cv)
    clf.fit(X, y)
    return clf


def _compute_centroids(X: np.ndarray, y: np.ndarray) -> Dict[str, np.ndarray]:
    """L2-renormalized mean training vector per class — used for the
    centroid_similarity corroborating signal at predict time."""
    centroids: Dict[str, np.ndarray] = {}
    for label in np.unique(y):
        rows = X[y == label]
        c = rows.mean(axis=0)
        norm = np.linalg.norm(c)
        centroids[str(label)] = (c / norm) if norm > 0 else c
    return centroids


def train(agent_host: str, vectors: List[List[float]], labels: List[str],
         risk_categories: Optional[Dict[str, str]] = None) -> Dict[str, object]:
    """Train (or retrain) the agent's multi-class model from examples.

    labels: one fine-grained intent string per vector (may include
    "__other__"/"__background__"). risk_categories maps intent -> risk
    category (delete/edit/create/fetch_pii/fetch_generic), static per-intent
    metadata carried on the model for predict() to return alongside a match.
    Atomic swap on success; on too-few-samples the previous model (if any) is
    left untouched and trained=False is returned.
    """
    if not vectors or len(vectors) != len(labels):
        return {"trained": False, "reason": "empty_or_mismatched"}
    X = np.asarray(vectors, dtype=np.float32)
    y = np.asarray(labels, dtype=object)
    X, y, dropped = _filter_min_class(X, y)
    if dropped:
        logger.info(f"[intent_clf] agent={agent_host} dropping sparse classes "
                   f"(< {_MIN_PER_CLASS} samples): {dropped}")
    clf = _fit(X, y)
    if clf is None:
        return {"trained": False, "reason": "insufficient_per_class_samples",
                "n_samples": int(len(y)), "dropped_classes": dropped}
    centroids = _compute_centroids(X, y)
    model = _AgentModel(clf, centroids, dict(risk_categories or {}), int(len(y)))
    with _lock:
        _models[agent_host] = model  # atomic swap
    classes = sorted(set(y.tolist()))
    logger.info(f"[intent_clf] trained agent={agent_host} n={len(y)} classes={classes}"
               + (f" dropped={dropped}" if dropped else ""))
    return {"trained": True, "n_samples": int(len(y)), "classes": classes, "dropped_classes": dropped}


def predict(agent_host: str, vectors: List[List[float]]) -> List[Optional[Dict[str, Any]]]:
    """Return {intent, confidence, margin, centroid_similarity, risk_category}
    per vector, or None per vector when no model exists (cold start)."""
    model = _models.get(agent_host)
    if model is None or not vectors:
        return [None] * len(vectors)
    try:
        X = np.asarray(vectors, dtype=np.float32)
        return model.predict_intent(X)
    except Exception as exc:
        logger.warning(f"[intent_clf] predict failed agent={agent_host}: {exc}")
        return [None] * len(vectors)


def stats() -> Dict[str, int]:
    return {"agents_with_models": len(_models)}
