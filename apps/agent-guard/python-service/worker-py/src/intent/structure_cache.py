"""In-process, per-pod cache for each agent's learned structure profile
(intent/corpus.py's build_structure_profile() output).

No Redis: like intent_classifier's per-pod model swap (embedder container)
and intent/trainer.py's per-pod training buffers, this trades cross-pod
sharing for simplicity — agent-guard's caching layer moved to
guardrails-service, and a profile lost on pod restart just means the agent
is cold until the next warmup(), the same fail-open semantics used
everywhere else in this module family.

Owned by neither prefilter.py (reader) nor corpus.py (writer) to avoid a
circular import between them.
"""

import time
from typing import Any, Dict, Optional, Tuple

_TTL_SECONDS = 24 * 3600
_profiles: Dict[str, Tuple[float, Dict[str, Any]]] = {}  # agent_host -> (expires_at, profile)


def set(agent_host: str, profile: Dict[str, Any], ttl_seconds: int = _TTL_SECONDS) -> None:
    if not agent_host:
        return
    _profiles[agent_host] = (time.time() + ttl_seconds, profile)


def get(agent_host: str) -> Optional[Dict[str, Any]]:
    if not agent_host:
        return None
    entry = _profiles.get(agent_host)
    if entry is None:
        return None
    expires_at, profile = entry
    if time.time() >= expires_at:
        _profiles.pop(agent_host, None)
        return None
    return profile
