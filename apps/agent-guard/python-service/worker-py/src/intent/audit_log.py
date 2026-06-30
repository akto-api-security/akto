"""Append-only JSONL audit log for intent prefilter decisions.

Writes one JSON line per intent decision so operators can review prompts,
nearest-neighbour matches, classifier scores, and decision paths without
parsing terminal logs.

Enabled when INTENT_LOG_FILE env var points to a writable path.
Fail-open: any I/O error is warned and discarded — never blocks traffic.

Each line is a JSON object:
{
  "ts":             ISO-8601 timestamp,
  "path":           "intent_allow" | "intent_block" | "intent_allow_shadow" |
                    "intent_block_shadow" | "intent_escalate",
  "agent":          agent_host string,
  "scanner":        scanner name,
  "prompt":         original prompt text (capped at 500 chars),
  "norm_text":      stripped/normalised text (omitted when identical to prompt),
  "p_clf":          per-agent classifier p(malicious), or null if cold-start,
  "reason":         human-readable decision reason,
  "task":           task intent label ("benign" | "malicious" | "uncertain" | ...),
  "risk":           risk signal string ("read,pii" | "none" | ...),
  "scope_distance": distance to nearest allow-example used for ALLOW, or null,
  "neighbor": {
    "distance":     cosine distance to nearest cached example,
    "is_valid":     whether that cached example was safe (true) or blocked (false),
    "reason":       LLM reason stored with that cached example,
    "task_intent":  task label of the cached example ("benign" | "system_override" | ...),
    "scope_bucket": "allow" or "blocked"
  } | null           (null when no KNN neighbour exists)
}
"""

import json
import logging
import os
from datetime import datetime, timezone
from typing import Any, Dict, Optional

logger = logging.getLogger(__name__)

_fh = None
_fh_path: Optional[str] = None


def _get_fh():
    global _fh, _fh_path
    path = os.environ.get("INTENT_LOG_FILE", "").strip()
    if not path:
        return None
    if _fh is not None and _fh_path == path:
        return _fh
    # Path changed or first open.
    if _fh is not None:
        try:
            _fh.close()
        except Exception:
            pass
    try:
        _fh = open(path, "a", buffering=1)  # line-buffered: flush on every newline
        _fh_path = path
        return _fh
    except Exception as exc:
        logger.warning(f"[intent_audit] cannot open log file {path!r}: {exc}")
        _fh = None
        _fh_path = None
        return None


def append(
    path: str,
    *,
    agent: str,
    scanner: str,
    prompt: str,
    norm_text: str,
    p_clf: Optional[float],
    reason: str,
    task: str,
    risk: str,
    scope_distance: Optional[float],
    neighbor: Optional[Dict[str, Any]],
) -> None:
    """Write one audit entry. No-op when INTENT_LOG_FILE is unset."""
    fh = _get_fh()
    if fh is None:
        return
    entry: Dict[str, Any] = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "path": path,
        "agent": agent or "",
        "scanner": scanner or "",
        "prompt": (prompt or "")[:500],
        "p_clf": round(p_clf, 4) if p_clf is not None else None,
        "reason": reason or "",
        "task": task or "",
        "risk": risk or "",
        "scope_distance": round(scope_distance, 4) if scope_distance is not None else None,
        "neighbor": None,
    }
    # Only include norm_text when it differs from the raw prompt (i.e. JSON was stripped).
    stripped = (norm_text or "")[:500]
    if stripped and stripped != entry["prompt"]:
        entry["norm_text"] = stripped

    if neighbor is not None:
        entry["neighbor"] = {
            "distance":    round(float(neighbor.get("distance", 1.0)), 4),
            "is_valid":    bool(neighbor.get("is_valid", True)),
            "reason":      (neighbor.get("reason") or "")[:200],
            "task_intent": neighbor.get("task_intent") or "",
            "scope_bucket": neighbor.get("scope_bucket") or "",
        }

    try:
        fh.write(json.dumps(entry, ensure_ascii=False) + "\n")
    except Exception as exc:
        logger.warning(f"[intent_audit] write failed: {exc}")
