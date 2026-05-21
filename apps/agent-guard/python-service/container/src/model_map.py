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

# Shared across all requests so we don't pay thread-creation cost per /scan.
# 32 workers ≈ 10 concurrent scans against the default 3-provider modelMap.
_EXECUTOR = concurrent.futures.ThreadPoolExecutor(
    max_workers=32,
    thread_name_prefix="modelmap",
)

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

        t1_futures, t2_futures, arb_futures = self._submit_tiers(tier1, tier2, arbiters)
        winner = self._cascade(t1_futures, t2_futures, arbiters, arb_futures)
        return self._shape_result(winner)

    def _submit_tiers(
        self,
        tier1: List[ScannerEntry],
        tier2: List[ScannerEntry],
        arbiters: List[ScannerEntry],
    ) -> Tuple[
        Dict[concurrent.futures.Future, Dict[str, Any]],
        Dict[concurrent.futures.Future, Dict[str, Any]],
        Optional[Dict[concurrent.futures.Future, Dict[str, Any]]],
    ]:
        """Fire tier1 + tier2 on the shared executor. When parallelExecution=true,
        arbiters are submitted alongside so the cascade can consult them
        without paying the serial-escalation cost. Unused futures finish in
        the background and their results are discarded."""
        eager = bool(self.config.get("parallelExecution", False)) and bool(arbiters)

        t1_futures = self._submit(_EXECUTOR, tier1)
        t2_futures = self._submit(_EXECUTOR, tier2)
        arb_futures = self._submit(_EXECUTOR, arbiters) if eager else None

        if eager:
            logger.info(f"[ModelMap] parallelExecution=true → {_ROLE_ARBITER} fired alongside tier1/tier2")
        return t1_futures, t2_futures, arb_futures

    def _cascade(
        self,
        t1_futures: Dict[concurrent.futures.Future, Dict[str, Any]],
        t2_futures: Dict[concurrent.futures.Future, Dict[str, Any]],
        arbiters: List[ScannerEntry],
        arb_futures: Optional[Dict[concurrent.futures.Future, Dict[str, Any]]],
    ) -> Dict[str, Any]:
        """Walk the verdict cascade and return the winning result dict."""
        t1 = self._collect_majority(t1_futures, label=_ROLE_TIER1)

        if t1["majority"] != _SAFE:
            logger.info(f"[ModelMap] {_ROLE_TIER1} majority={t1['majority']} → escalating to {_ROLE_ARBITER}, skipping {_ROLE_TIER2}")
            return self._run_arbiters(arbiters, pre_submitted=arb_futures)

        logger.info(f"[ModelMap] {_ROLE_TIER1} majority=SAFE → waiting for {_ROLE_TIER2}")
        t2 = self._collect_majority(t2_futures, label=_ROLE_TIER2)

        if t2["majority"] == _UNSAFE:
            logger.info(f"[ModelMap] {_ROLE_TIER2} majority=UNSAFE → escalating to {_ROLE_ARBITER}")
            return self._run_arbiters(arbiters, pre_submitted=arb_futures)

        logger.info(f"[ModelMap] {_ROLE_TIER2} majority={t2['majority']} → returning SAFE")
        winner = max(t2["completed"] or t1["completed"], key=lambda r: r["risk_score"])
        winner["is_valid"] = True
        return winner

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

        # One slot per modelConfigs entry, keyed by stem; missing → {completed: false}.
        completed_by_stem = self._index_completed_by_stem()
        for entry in self.config.get("modelConfigs", []):
            stem = self._stem(entry.get("provider", ""))
            if stem:
                details.setdefault(stem, self._summarize(completed_by_stem.get(stem)))

        return {
            "is_valid": winner.get("is_valid", True),
            "risk_score": float(winner.get("risk_score", 0.0)),
            "details": details,
        }

    def _index_completed_by_stem(self) -> Dict[str, Dict[str, Any]]:
        """Index completed results by short provider stem (gemma_vertexai → gemma)."""
        out: Dict[str, Dict[str, Any]] = {}
        for r in self.completed_all:
            stem = self._stem((r.get("details") or {}).get("llm_provider", ""))
            if stem:
                out[stem] = r
        return out

    @staticmethod
    def _summarize(r: Optional[Dict[str, Any]]) -> Dict[str, Any]:
        """Per-model entry for the response; None means the model never ran."""
        if r is None:
            return {"completed": False}
        return {
            "completed": True,
            "is_valid": r.get("is_valid"),
            "risk_score": r.get("risk_score"),
            "decision_confidence": r.get("decision_confidence"),
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

    def _run_arbiters(
        self,
        arbiters: List[ScannerEntry],
        pre_submitted: Optional[Dict[concurrent.futures.Future, Dict[str, Any]]] = None,
    ) -> Dict[str, Any]:
        """Consult FINAL_ARBITER models. Reuses `pre_submitted` futures when
        parallelExecution fired them upfront; otherwise submits them now."""
        if not arbiters:
            logger.error(f"[ModelMap] No {_ROLE_ARBITER} configured for scanner={self.scanner_name}")
            return self._error_result("no arbiter configured")

        future_map = pre_submitted if pre_submitted is not None else self._submit_arbiters_lazily(arbiters)

        completed: List[Dict[str, Any]] = []
        completed_entries: List[Dict[str, Any]] = []
        for future in concurrent.futures.as_completed(future_map):
            entry = future_map[future]
            timeout_s = (entry.get("timeoutMs") or _DEFAULT_ARBITER_TIMEOUT_MS) / 1000.0
            try:
                completed.append(future.result(timeout=timeout_s))
                completed_entries.append(entry)
            except Exception as exc:
                logger.error(f"[ModelMap] {_ROLE_ARBITER} '{entry.get('provider')}' failed: {exc}")

        self.completed_all.extend(completed)
        if not completed:
            return self._error_result("all arbiters failed")

        # Any arbiter that classifies unsafe blocks; else allow on highest-risk result.
        unsafe = [r for r, e in zip(completed, completed_entries) if _classify(r, e)]
        winner = max(unsafe or completed, key=lambda r: r["risk_score"])
        winner["is_valid"] = not unsafe
        return winner

    def _submit_arbiters_lazily(
        self, arbiters: List[ScannerEntry]
    ) -> Dict[concurrent.futures.Future, Dict[str, Any]]:
        return self._submit(_EXECUTOR, arbiters)

    @staticmethod
    def _error_result(error: str) -> Dict[str, Any]:
        """Fail-closed stub used when arbitration cannot produce a verdict."""
        return {"is_valid": False, "risk_score": 1.0, "details": {"error": error}}
