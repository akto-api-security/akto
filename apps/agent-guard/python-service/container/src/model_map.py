"""Tier-based multi-model orchestration for LLM scanners.

The pipeline runs two cheap tiers in parallel, then escalates to expensive
arbiters only when the cheap tiers disagree or call unsafe:

    FAST_THREAT_FILTER       (tier 1)  ─┐
                                         ├─ both fire in parallel
    FAST_FALLBACK_SAFE_FILTER (tier 2)  ─┘

    tier1 majority UNSAFE        → FINAL_ARBITER decides
    tier1 majority SAFE          → wait for tier2:
                                     UNSAFE       → FINAL_ARBITER decides
                                     SAFE/INCONC. → ALLOW (best tier2 result)
    tier1 all INCONCLUSIVE       → FINAL_ARBITER decides
"""

import concurrent.futures
import logging
import time
from typing import Any, Dict, List, Optional, Tuple

from providers import build_provider_from_config

logger = logging.getLogger(__name__)

_DEFAULT_SAFE_THRESHOLD = 0.8

# modelRole values
_ROLE_TIER1 = "FAST_THREAT_FILTER"
_ROLE_TIER2 = "FAST_FALLBACK_SAFE_FILTER"
_ROLE_ARBITER = "FINAL_ARBITER"

# Verdict labels
_SAFE = "SAFE"
_UNSAFE = "UNSAFE"
_INCONCLUSIVE = "INCONCLUSIVE"

# Per-tier default timeouts (ms) used when an entry doesn't set timeoutMs.
_DEFAULT_TIER_TIMEOUT_MS = 5000
_DEFAULT_ARBITER_TIMEOUT_MS = 10000

# (scanner, modelMap entry) pair used through the pipeline.
ScannerEntry = Tuple[Any, Dict[str, Any]]


def _classify(result: Dict[str, Any], entry: Dict[str, Any]) -> bool:
    """Return True if this scan result should count as unsafe, else False.
    """
    if not result.get("is_valid", True):
        return True
    confidence = float(result.get("decision_confidence", 0.0))
    safe_threshold = float(entry.get("safeDecisionThreshold") or _DEFAULT_SAFE_THRESHOLD)
    return confidence < safe_threshold


def _fire_store(store_fn: Optional[Any], completed: List[Dict[str, Any]], scanner_name: str) -> None:
    """Invoke the optional persistence callback; never let its failure affect the scan result."""
    if store_fn is None:
        return
    try:
        store_fn(completed, scanner_name)
    except Exception as exc:
        logger.warning(f"[ModelMap] store_fn failed for scanner={scanner_name}: {exc}")


class ModelMapScanner:
    """One-shot orchestrator. Construct with the request, then call run()."""

    def __init__(
        self,
        scanner_name: str,
        scanner_type: str,
        text: str,
        config: Dict[str, Any],
        store_fn: Optional[Any] = None,
    ):
        self.scanner_name = scanner_name
        self.scanner_type = scanner_type
        self.text = text
        self.config = config
        self.store_fn = store_fn
        self.completed_all: List[Dict[str, Any]] = []

    # ── Public entry point ──────────────────────────────────────────────────

    def run(self) -> Dict[str, Any]:
        model_map: list = self.config.get("modelConfigs", [])
        if not model_map:
            raise ValueError("ModelMapScanner: empty modelConfigs")

        # Imported lazily to avoid a circular import (llm_scanner imports us).
        from llm_scanner import LLMScanner

        scanners: List[ScannerEntry] = []
        for entry in model_map:
            provider = build_provider_from_config(entry)
            if provider is not None:
                scanners.append((LLMScanner(provider), entry))
        if not scanners:
            raise ValueError("ModelMapScanner: no usable providers in modelConfigs")

        start = time.time()
        result = self._run_pipeline(scanners)
        result["execution_time_ms"] = round((time.time() - start) * 1000, 2)

        _fire_store(self.store_fn, self.completed_all, self.scanner_name)

        logger.info(
            f"[ModelMap] returning scanner={self.scanner_name} "
            f"isValid={result['is_valid']} risk={result['risk_score']:.2f} "
            f"elapsed_ms={result['execution_time_ms']:.0f}"
        )
        return result

    # ── Pipeline core ───────────────────────────────────────────────────────

    def _run_pipeline(self, scanners: List[ScannerEntry]) -> Dict[str, Any]:
        tier1 = [se for se in scanners if se[1].get("modelRole") == _ROLE_TIER1]
        tier2 = [se for se in scanners if se[1].get("modelRole") == _ROLE_TIER2]
        arbiters = [se for se in scanners if se[1].get("modelRole") == _ROLE_ARBITER]

        # Fire tier1 + tier2 together. Tier2 futures may end up unused, but they
        # finish in the background — no need to cancel them.
        executor = concurrent.futures.ThreadPoolExecutor(
            max_workers=max(1, len(tier1) + len(tier2)),
            thread_name_prefix="modelmap",
        )
        t1_futures = self._submit(executor, tier1)
        t2_futures = self._submit(executor, tier2)
        executor.shutdown(wait=False)

        t1_verdict = self._collect_majority(t1_futures, label=_ROLE_TIER1)

        if t1_verdict["majority"] == _UNSAFE or t1_verdict["majority"] == _INCONCLUSIVE:
            logger.info(f"[ModelMap] {_ROLE_TIER1} majority=UNSAFE → escalating, skipping {_ROLE_TIER2}")
            winner = self._run_arbiters(arbiters)
        else:
            # tier1 majority SAFE → consult tier2
            logger.info(f"[ModelMap] {_ROLE_TIER1} majority=SAFE → waiting for {_ROLE_TIER2}")
            t2_verdict = self._collect_majority(t2_futures, label=_ROLE_TIER2)

            if t2_verdict["majority"] == _UNSAFE:
                logger.info(f"[ModelMap] {_ROLE_TIER2} majority=UNSAFE → escalating to {_ROLE_ARBITER}")
                winner = self._run_arbiters(arbiters)
            else:
                # tier2 SAFE or INCONCLUSIVE → allow without invoking arbiter
                logger.info(f"[ModelMap] {_ROLE_TIER2} majority={t2_verdict['majority']} → returning SAFE")
                source = t2_verdict["completed"] or t1_verdict["completed"]
                best_safe = max(source, key=lambda r: r["risk_score"])
                best_safe["is_valid"] = True
                winner = best_safe

        return self._shape_result(winner)

    # ── Result shaping ──────────────────────────────────────────────────────

    @staticmethod
    def _stem(provider_name: str) -> str:
        """Short stem for grouping per-model details: 'gemma_vertexai' → 'gemma'."""
        n = (provider_name or "").lower()
        if not n:
            return ""
        if n.startswith("gemma"):
            return "gemma"
        if n.startswith("qwen"):
            return "qwen"
        return n.split("_")[0]

    def _shape_result(self, winner: Dict[str, Any]) -> Dict[str, Any]:
        """Aggregate completed_all + winner into the response shape callers expect."""
        winner_details = winner.get("details") or {}
        winner_stem = self._stem(winner_details.get("llm_provider", ""))

        details: Dict[str, Any] = {
            "reason": winner_details.get("reason", ""),
            "llm_provider": winner_details.get("llm_provider", ""),
            "scanner_type": self.scanner_type,
            "cascade_decision": f"{winner_stem}_authority" if winner_stem else "no_authority",
        }

        # Per-model summaries: every modelConfigs entry gets a slot; missing → completed=False.
        completed_by_stem: Dict[str, Dict[str, Any]] = {}
        for r in self.completed_all:
            stem = self._stem((r.get("details") or {}).get("llm_provider", ""))
            if stem:
                completed_by_stem[stem] = r

        for entry in self.config.get("modelConfigs", []):
            stem = self._stem(entry.get("provider", ""))
            if not stem:
                continue
            r = completed_by_stem.get(stem)
            if r is not None:
                details[stem] = {
                    "completed": True,
                    "is_valid": r.get("is_valid"),
                    "risk_score": r.get("risk_score"),
                    "decision_confidence": r.get("decision_confidence"),
                }
            else:
                details.setdefault(stem, {"completed": False})

        return {
            "is_valid": winner.get("is_valid", True),
            "risk_score": float(winner.get("risk_score", 0.0)),
            "details": details,
        }

    # ── Helpers ─────────────────────────────────────────────────────────────

    def _submit(
        self,
        executor: concurrent.futures.ThreadPoolExecutor,
        pairs: List[ScannerEntry],
    ) -> Dict[concurrent.futures.Future, Dict[str, Any]]:
        return {
            executor.submit(scanner.scan, self.scanner_name, self.scanner_type, self.text, self.config): entry
            for scanner, entry in pairs
        }

    def _collect_majority(
        self,
        future_map: Dict[concurrent.futures.Future, Dict[str, Any]],
        label: str,
    ) -> Dict[str, Any]:
        completed: List[Dict[str, Any]] = []
        safe = unsafe = 0

        for future in concurrent.futures.as_completed(future_map):
            entry = future_map[future]
            timeout_s = (entry.get("timeoutMs") or _DEFAULT_TIER_TIMEOUT_MS) / 1000.0
            try:
                result = future.result(timeout=timeout_s)
                completed.append(result)
                decision = _classify(result, entry)
                if decision is True:
                    unsafe += 1
                elif decision is False:
                    safe += 1
            except Exception as exc:
                logger.error(f"[ModelMap] {label} '{entry.get('provider')}' failed: {exc}")
                unsafe += 1  # conservative: failures count as unsafe

        self.completed_all.extend(completed)

        total = len(future_map)
        majority = (
            _UNSAFE if unsafe >= total / 2
            else _SAFE if safe > total / 2
            else _INCONCLUSIVE
        )
        logger.info(
            f"[ModelMap] {label} verdict={majority} safe={safe} unsafe={unsafe} "
            f"total={total} scanner={self.scanner_name}"
        )
        return {"majority": majority, "completed": completed}

    def _run_arbiters(self, arbiters: List[ScannerEntry]) -> Dict[str, Any]:
        if not arbiters:
            logger.error(f"[ModelMap] No {_ROLE_ARBITER} configured for scanner={self.scanner_name}")
            return {
                "is_valid": False,
                "risk_score": 1.0,
                "details": {"error": "no arbiter configured"},
            }

        executor = concurrent.futures.ThreadPoolExecutor(
            max_workers=len(arbiters),
            thread_name_prefix="modelmap-arbiter",
        )
        future_map = self._submit(executor, arbiters)
        executor.shutdown(wait=False)

        completed: List[Dict[str, Any]] = []
        completed_entries: List[Dict[str, Any]] = []
        for future in concurrent.futures.as_completed(future_map):
            entry = future_map[future]
            timeout_s = (entry.get("timeoutMs") or _DEFAULT_ARBITER_TIMEOUT_MS) / 1000.0
            try:
                result = future.result(timeout=timeout_s)
                completed.append(result)
                completed_entries.append(entry)
            except Exception as exc:
                logger.error(f"[ModelMap] {_ROLE_ARBITER} '{entry.get('provider')}' failed: {exc}")

        self.completed_all.extend(completed)

        if not completed:
            return {
                "is_valid": False,
                "risk_score": 1.0,
                "details": {"error": "all arbiters failed"},
            }

        unsafe_results = [
            r for r, e in zip(completed, completed_entries) if _classify(r, e) is True
        ]
        if unsafe_results:
            winner = max(unsafe_results, key=lambda r: r["risk_score"])
            winner["is_valid"] = False
        else:
            winner = max(completed, key=lambda r: r["risk_score"])
            winner["is_valid"] = True
        return winner
