"""Append-only JSONL audit log for intent prefilter decisions.

Writes one JSON line per intent decision so operators can review prompts,
extracted instruction units, per-unit classifier scores, and decision paths
without parsing terminal logs.

Enabled when INTENT_LOG_FILE env var points to a writable path.
Fail-open: any I/O error is warned and discarded — never blocks traffic.

Each line is a JSON object:
{
  "ts":                    ISO-8601 timestamp,
  "path":                  "intent_allow" | "intent_allow_shadow" | "intent_escalate",
  "agent":                 agent_host string,
  "scanner":                scanner name,
  "prompt":                original prompt text (capped at 300 words),
  "reason":                human-readable decision reason (from intent/decision.py),
  "extraction_method":     "structured" | "deterministic" | "heuristic" | "error",
  "extraction_confidence": how confidently intent/segmenter.py separated
                            instruction from data for this request,
  "risk_category":         highest risk category across matched units, or null,
  "total_ms":              decide_fast's total wall time for this request, or
                            null when no timer was passed,
  "stage_ms":              {"segment": ..., "embed_units": ..., "classify": ...}
                            — see scan_diag.StageTimer; only the stages actually
                            run for this request are present (a cold agent skips
                            embed_units/classify entirely). {} when no timer was
                            passed,
  "units": [
    {
      "text":               extracted instruction unit (capped at 200 chars),
      "intent":              matched intent, or null if unmatched,
      "confidence":          calibrated classifier confidence, or null,
      "margin":              top1/top2 probability margin, or null,
      "centroid_similarity": cosine similarity to the matched class centroid, or null,
      "risk_category":       this unit's risk category, or null
    }, ...
  ]
}

See scripts/analyze_intent_audit.py (repo root ../../../../scripts) for latency
percentile / hit-rate analysis over this file.
"""

import json
import logging
import os
from datetime import datetime, timezone
from typing import Any, Dict, List, Optional

logger = logging.getLogger(__name__)

_fh = None
_fh_path: Optional[str] = None

_MAX_PROMPT_WORDS = 300


def _trim_to_words(text: str, max_words: int = _MAX_PROMPT_WORDS) -> str:
    return " ".join((text or "").split()[:max_words])


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
    reason: str,
    extraction_method: str,
    extraction_confidence: Optional[float],
    risk_category: Optional[str],
    units: List[Dict[str, Any]],
    unit_texts: Optional[List[str]] = None,
    timer: Optional[Any] = None,
) -> None:
    """Write one audit entry. No-op when INTENT_LOG_FILE is unset.

    `units` is intent/prefilter.py's per-unit classify result list (one dict
    or None per extracted unit); `unit_texts` is the parallel list of the
    units' raw text, joined in here purely for a readable audit line.
    `timer` is the scan_diag.StageTimer decide_fast ran under (duck-typed, not
    imported, to avoid coupling this module to scan_diag) — its total_ms()/
    stages give per-request latency and the segment/embed_units/classify
    breakdown; omitted (None) fields are written as null/{} rather than 0, so
    "no timer" and "all stages measured 0ms" stay distinguishable.
    """
    fh = _get_fh()
    if fh is None:
        return
    texts = unit_texts or []
    entry: Dict[str, Any] = {
        "ts": datetime.now(timezone.utc).isoformat(),
        "path": path,
        "agent": agent or "",
        "scanner": scanner or "",
        "prompt": _trim_to_words(prompt),
        "reason": reason or "",
        "extraction_method": extraction_method or "",
        "extraction_confidence": round(extraction_confidence, 4) if extraction_confidence is not None else None,
        "risk_category": risk_category,
        "total_ms": round(timer.total_ms(), 2) if timer is not None else None,
        "stage_ms": {k: round(v, 2) for k, v in timer.stages.items()} if timer is not None else {},
        "units": [
            {
                "text": (texts[i] if i < len(texts) else "")[:200],
                "intent": (u or {}).get("intent"),
                "confidence": round(u["confidence"], 4) if u and u.get("confidence") is not None else None,
                "margin": round(u["margin"], 4) if u and u.get("margin") is not None else None,
                "centroid_similarity": round(u["centroid_similarity"], 4) if u and u.get("centroid_similarity") is not None else None,
                "risk_category": (u or {}).get("risk_category"),
            }
            for i, u in enumerate(units)
        ],
    }
    try:
        fh.write(json.dumps(entry, ensure_ascii=False) + "\n")
    except Exception as exc:
        logger.warning(f"[intent_audit] write failed: {exc}")
