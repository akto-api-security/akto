"""Thin async client for the embedder container's per-agent intent classifier.

The worker already embeds text for the semantic cache (cache._embed). To avoid a
second embed on the miss path, we send those same vectors here and the embedder
runs only the cheap per-agent LogisticRegression on them.

Fail-open: any error (URL unset, network, no model for the agent) returns a list
of None — the decision layer then relies on the regex signal + cache alone.
"""

import logging
from typing import List, Optional

import http_client
from settings import settings

logger = logging.getLogger(__name__)

_CLASSIFY_TIMEOUT_S = 5.0


async def classify_vectors(agent_host: str, vectors: List[List[float]]) -> List[Optional[float]]:
    """Return per-vector p_malicious from the agent's classifier, or [None,...].

    vectors are the 384-dim embeddings the cache already computed, so no
    re-embedding happens here.
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
        out: List[Optional[float]] = []
        for i in range(n):
            item = results[i] if i < len(results) else None
            if isinstance(item, dict) and item.get("p_malicious") is not None:
                out.append(float(item["p_malicious"]))
            else:
                out.append(None)
        return out
    except Exception as exc:
        logger.warning(f"[intent] classify failed: {exc}")
        return [None] * n
