"""Per-agent multi-class classifier training buffer (worker side).

Unlike the old binary malicious/benign classifier, this is fed exclusively by
intent/corpus.py's warmup()/refresh — never directly from a live cascade
verdict, because a live verdict only carries a coarse is_valid/risk_score, not
the fine-grained instruction intent (that's assigned later, offline, by the
LLM-labeling service). load_examples() *replaces* the agent's buffer with
whatever corpus.load() just pulled from Mongo (the full current labeled set
for that agent), rather than accumulating incrementally — so a label the
offline service revises is picked up cleanly on the next refresh instead of
lingering alongside a stale duplicate.

In-memory + per-pod (matches the classifier's atomic per-pod model swap in
embedder-container): simple, no Redis scan, fail-open.
"""

import logging
from typing import Any, Dict, List, Tuple

import http_client
from settings import settings

logger = logging.getLogger(__name__)

_TRAIN_TIMEOUT_S = 30.0

# agent_host -> (vectors, labels)
_buffers: Dict[str, Tuple[List[List[float]], List[str]]] = {}
# agent_host -> {intent -> risk_category}
_risk_maps: Dict[str, Dict[str, str]] = {}


def load_examples(agent_host: str, examples: List[Dict[str, Any]]) -> None:
    """Replace agent_host's training buffer with a freshly-loaded example set.

    Each example is {"vector", "task_intent", "risk_category", ...} (see
    intent/corpus.py's load()). Examples with is_valid=False are skipped —
    this classifier only ever learns fine-grained intents from GOOD verdicts;
    telling a genuinely novel malicious ask apart from benign remains the LLM
    cascade's job.
    """
    vectors: List[List[float]] = []
    labels: List[str] = []
    risk_map: Dict[str, str] = {}
    for ex in examples:
        if not ex.get("is_valid", True):
            continue
        vectors.append(ex["vector"])
        intent = ex["task_intent"]
        labels.append(intent)
        risk = ex.get("risk_category", "unknown")
        if risk != "unknown" or intent not in risk_map:
            risk_map[intent] = risk
    _buffers[agent_host] = (vectors, labels)
    _risk_maps[agent_host] = risk_map


async def train_now(agent_host: str) -> None:
    """POST the agent's currently-buffered examples to the embedder /train.
    Fail-open — logs and returns on any error, never raises to the caller."""
    buf = _buffers.get(agent_host)
    if not buf or not buf[0]:
        return
    base = (settings.EMBEDDER_URL or "").strip().rstrip("/")
    if not base:
        return
    vectors, labels = buf
    risk_categories = _risk_maps.get(agent_host, {})
    try:
        client = http_client.get_client()
        resp = await client.post(
            f"{base}/train",
            json={"agent_host": agent_host, "vectors": vectors, "labels": labels,
                  "risk_categories": risk_categories},
            headers={"Accept-Encoding": "identity"},
            timeout=_TRAIN_TIMEOUT_S,
        )
        if resp.status_code >= 400:
            logger.warning(f"[intent] train returned {resp.status_code} for agent={agent_host}")
        else:
            logger.info(f"[intent] retrained agent={agent_host} examples={len(labels)} -> {resp.json()}")
    except Exception as exc:
        logger.warning(f"[intent] train failed for agent={agent_host}: {exc}")
