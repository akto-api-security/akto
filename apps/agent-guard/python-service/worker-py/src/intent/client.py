"""Thin async client for the embedder container's per-agent multi-class
instruction-intent classifier.

The worker embeds each extracted instruction unit in one batched /embed/batch
call (intent/prefilter.py), then sends all of a request's unit vectors here in
a single batched /classify call — never one HTTP round-trip per unit,
regardless of how many units a request has (typically 1-3), to stay inside
the fast-path latency budget.

Fail-open: any error (URL unset, network, no model for the agent) returns a
list of None — the decision layer then treats every unit as unmatched and
ESCALATEs to the LLM cascade.
"""

import logging
from typing import Any, Dict, List, Optional

import http_client
from settings import settings

logger = logging.getLogger(__name__)

_CLASSIFY_TIMEOUT_S = 5.0
_EMBED_TIMEOUT_S = 5.0


async def embed_units(texts: List[str]) -> List[Optional[List[float]]]:
    """Embed a request's extracted instruction units in one batched call.

    Returns one 384-dim vector per text, or None for a text whose embedding
    failed (fail-open — the caller treats a None vector as an unmatched unit,
    which ESCALATEs rather than raising).
    """
    n = len(texts)
    if n == 0:
        return []
    base = (settings.EMBEDDER_URL or "").strip().rstrip("/")
    if not base:
        return [None] * n
    try:
        client = http_client.get_client()
        resp = await client.post(
            f"{base}/embed/batch",
            json={"texts": texts},
            headers={"Accept-Encoding": "identity"},
            timeout=_EMBED_TIMEOUT_S,
        )
        if resp.status_code >= 400:
            logger.warning(f"[intent] embed_units returned {resp.status_code}")
            return [None] * n
        vectors = resp.json().get("vectors") or []
        out: List[Optional[List[float]]] = []
        for i in range(n):
            v = vectors[i] if i < len(vectors) else None
            out.append(v if isinstance(v, list) and v else None)
        return out
    except Exception as exc:
        logger.warning(f"[intent] embed_units failed: {exc}")
        return [None] * n


async def classify_intent(agent_host: str, vectors: List[List[float]]) -> List[Optional[Dict[str, Any]]]:
    """Return {intent, confidence, margin, centroid_similarity, risk_category}
    per vector, or None per vector (cold start / no model / error).

    vectors are the 384-dim embeddings of the extracted instruction units
    (intent/segmenter.py), one call for the whole request's units.
    """
    n = len(vectors)
    if n == 0:
        return []
    base = (settings.EMBEDDER_URL or "").strip().rstrip("/")
    if not base:
        return [None] * n
    try:
        client = http_client.get_client()
        resp = await client.post(
            f"{base}/classify",
            json={"agent_host": agent_host, "vectors": vectors},
            headers={"Accept-Encoding": "identity"},
            timeout=_CLASSIFY_TIMEOUT_S,
        )
        if resp.status_code >= 400:
            logger.warning(f"[intent] classifier returned {resp.status_code}")
            return [None] * n
        results = resp.json().get("results") or []
        out: List[Optional[Dict[str, Any]]] = []
        for i in range(n):
            item = results[i] if i < len(results) else None
            if isinstance(item, dict) and item.get("intent"):
                out.append(item)
            else:
                out.append(None)
        return out
    except Exception as exc:
        logger.warning(f"[intent] classify failed: {exc}")
        return [None] * n
