"""Per-agent classifier training loop (worker side).

On each ESCALATE→cascade verdict, the embedding + label (1=malicious, 0=benign)
are recorded here. When an agent crosses INTENT_RETRAIN_EVERY_N new examples we
fire a background POST to the embedder's /train so its per-agent model improves.

In-memory + per-pod (matches the cache's per-pod model): simple, no Redis scan,
fail-open. A durable cross-pod training corpus (DB-abstractor) is a later
enhancement; this already makes each pod learn over its own traffic.
"""

import logging
from typing import Dict, List, Optional, Tuple

import http_client
from settings import settings

logger = logging.getLogger(__name__)

_DEFAULT_RETRAIN_EVERY_N = 25
_MAX_BUFFER = 2000  # cap retained examples per agent (most recent win)
_TRAIN_TIMEOUT_S = 30.0

# agent_host -> (vectors, labels)
_buffers: Dict[str, Tuple[List[List[float]], List[int]]] = {}
_since_train: Dict[str, int] = {}


def _retrain_every_n() -> int:
    raw = str(getattr(settings, "INTENT_RETRAIN_EVERY_N", "")).strip()
    try:
        return int(raw) if raw else _DEFAULT_RETRAIN_EVERY_N
    except ValueError:
        return _DEFAULT_RETRAIN_EVERY_N


def record(agent_host: str, vector: Optional[List[float]], is_valid: bool) -> bool:
    """Append one learned example. Returns True when a retrain should be fired."""
    if not agent_host or vector is None:
        return False
    vecs, labels = _buffers.setdefault(agent_host, ([], []))
    vecs.append(vector)
    labels.append(0 if is_valid else 1)
    if len(vecs) > _MAX_BUFFER:  # keep most recent
        del vecs[0]
        del labels[0]
    _since_train[agent_host] = _since_train.get(agent_host, 0) + 1
    if _since_train[agent_host] >= _retrain_every_n():
        _since_train[agent_host] = 0
        return True
    return False


async def train_now(agent_host: str) -> None:
    """POST the agent's accumulated examples to the embedder /train. Fail-open."""
    buf = _buffers.get(agent_host)
    if not buf or not buf[0]:
        return
    base = (settings.EMBEDDER_URL or "").strip().rstrip("/")
    if not base:
        return
    vectors, labels = buf
    try:
        client = http_client.get_client()
        resp = await client.post(
            f"{base}/train",
            json={"agent_host": agent_host, "vectors": vectors, "labels": labels},
            headers={"Accept-Encoding": "identity"},
            timeout=_TRAIN_TIMEOUT_S,
        )
        if resp.status_code >= 400:
            logger.warning(f"[intent] train returned {resp.status_code} for agent={agent_host}")
        else:
            logger.info(f"[intent] retrained agent={agent_host} examples={len(labels)} -> {resp.json()}")
    except Exception as exc:
        logger.warning(f"[intent] train failed for agent={agent_host}: {exc}")
