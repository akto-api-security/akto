"""Tier-based multi-model cascade (async port of container/src/model_map.py).

    FAST_THREAT_FILTER       (tier 1)  ─┐ fire in parallel
    FAST_FALLBACK_SAFE_FILTER (tier 2) ─┘
    tier1 majority UNSAFE        → FINAL_ARBITER decides
    tier1 majority SAFE          → wait for tier2:
                                     UNSAFE       → FINAL_ARBITER decides
                                     SAFE/INCONC. → ALLOW (best tier2 result)
    tier1 all INCONCLUSIVE       → FINAL_ARBITER decides

Verdict logic is identical to the container; only the executor changed
(asyncio tasks + wait_for instead of a ThreadPoolExecutor).
"""

import asyncio
import logging
import time
from typing import Any, Dict, List, Optional, Tuple

from providers import build_provider_from_config

logger = logging.getLogger(__name__)

_DEFAULT_SAFE_THRESHOLD = 0.8

_ROLE_TIER1 = "FAST_THREAT_FILTER"
_ROLE_TIER2 = "FAST_FALLBACK_SAFE_FILTER"
_ROLE_ARBITER = "FINAL_ARBITER"

_SAFE = "SAFE"
_UNSAFE = "UNSAFE"
_INCONCLUSIVE = "INCONCLUSIVE"

_DEFAULT_TIER_TIMEOUT_MS = 5000
_DEFAULT_ARBITER_TIMEOUT_MS = 10000

ScannerEntry = Tuple[Any, Dict[str, Any]]


def _classify(result: Dict[str, Any], entry: Dict[str, Any]) -> bool:
    """True if this result counts as unsafe."""
    if not result.get("is_valid", True):
        return True
    confidence = float(result.get("decision_confidence", 0.0))
    safe_threshold = float(entry.get("safeDecisionThreshold") or _DEFAULT_SAFE_THRESHOLD)
    return confidence < safe_threshold


def _fire_store(store_fn: Optional[Any], completed: List[Dict[str, Any]], scanner_name: str) -> None:
    if store_fn is None:
        return
    try:
        store_fn(completed, scanner_name)
    except Exception as exc:
        logger.warning(f"[ModelMap] store_fn failed for scanner={scanner_name}: {exc}")


class ModelMapScanner:
    def __init__(self, scanner_name, scanner_type, text, config, store_fn=None):
        self.scanner_name = scanner_name
        self.scanner_type = scanner_type
        self.text = text
        self.config = config
        self.store_fn = store_fn
        self.completed_all: List[Dict[str, Any]] = []

    # ── Public entry point ──────────────────────────────────────────────────

    async def run(self) -> Dict[str, Any]:
        model_map: list = self.config.get("modelConfigs", [])
        if not model_map:
            raise ValueError("ModelMapScanner: empty modelConfigs")

        from llm_scanner import LLMScanner

        scanners: List[ScannerEntry] = []
        for entry in model_map:
            provider = build_provider_from_config(entry)
            if provider is not None:
                scanners.append((LLMScanner(provider), entry))
        if not scanners:
            raise ValueError("ModelMapScanner: no usable providers in modelConfigs")

        start = time.time()
        result = await self._run_pipeline(scanners)
        result["execution_time_ms"] = round((time.time() - start) * 1000, 2)
        _fire_store(self.store_fn, self.completed_all, self.scanner_name)
        logger.info(
            f"[ModelMap] {self.scanner_name} isValid={result['is_valid']} "
            f"risk={result['risk_score']:.2f} ms={result['execution_time_ms']:.0f}"
        )
        return result

    # ── Pipeline core ───────────────────────────────────────────────────────

    async def _run_pipeline(self, scanners: List[ScannerEntry]) -> Dict[str, Any]:
        tier1 = [se for se in scanners if se[1].get("modelRole") == _ROLE_TIER1]
        tier2 = [se for se in scanners if se[1].get("modelRole") == _ROLE_TIER2]
        arbiters = [se for se in scanners if se[1].get("modelRole") == _ROLE_ARBITER]

        t1 = self._submit(tier1, _DEFAULT_TIER_TIMEOUT_MS)
        t2 = self._submit(tier2, _DEFAULT_TIER_TIMEOUT_MS)
        eager = bool(self.config.get("parallelExecution", False)) and bool(arbiters)
        arb = self._submit(arbiters, _DEFAULT_ARBITER_TIMEOUT_MS) if eager else None
        if eager:
            logger.info(f"[ModelMap] parallelExecution=true → {_ROLE_ARBITER} fired alongside tiers")

        winner = await self._cascade(t1, t2, arbiters, arb)
        return self._shape_result(winner)

    async def _cascade(self, t1, t2, arbiters, arb) -> Dict[str, Any]:
        res1 = await self._collect_majority(t1, _ROLE_TIER1)
        if res1["majority"] != _SAFE:
            logger.info(f"[ModelMap] {_ROLE_TIER1}={res1['majority']} → arbiter, skip {_ROLE_TIER2}")
            self._cancel(t2)
            return await self._run_arbiters(arbiters, arb)

        res2 = await self._collect_majority(t2, _ROLE_TIER2)
        if res2["majority"] == _UNSAFE:
            logger.info(f"[ModelMap] {_ROLE_TIER2}=UNSAFE → arbiter")
            return await self._run_arbiters(arbiters, arb)

        logger.info(f"[ModelMap] {_ROLE_TIER2}={res2['majority']} → SAFE")
        self._cancel(arb)  # eager arbiters spawned but not needed
        winner = max(res2["completed"] or res1["completed"], key=lambda r: r["risk_score"])
        winner["is_valid"] = True
        return winner

    # ── Task helpers ──────────────────────────────────────────────────────────

    def _submit(self, pairs: List[ScannerEntry], default_timeout_ms: int) -> Dict[asyncio.Task, Dict[str, Any]]:
        out: Dict[asyncio.Task, Dict[str, Any]] = {}
        for scanner, entry in pairs:
            timeout = (entry.get("timeoutMs") or default_timeout_ms) / 1000.0
            coro = scanner.scan(self.scanner_name, self.scanner_type, self.text, self.config)
            out[asyncio.ensure_future(asyncio.wait_for(coro, timeout))] = entry
        return out

    @staticmethod
    def _cancel(task_map: Optional[Dict[asyncio.Task, Dict[str, Any]]]) -> None:
        if not task_map:
            return
        for task in task_map:
            if not task.done():
                task.cancel()

    @staticmethod
    async def _await_map(task_map):
        tasks = list(task_map.keys())
        entries = [task_map[t] for t in tasks]
        results = await asyncio.gather(*tasks, return_exceptions=True)
        return list(zip(results, entries))

    async def _collect_majority(self, task_map, label: str) -> Dict[str, Any]:
        if not task_map:
            return {"majority": _SAFE, "completed": []}
        completed: List[Dict[str, Any]] = []
        safe = unsafe = 0
        for result, entry in await self._await_map(task_map):
            if isinstance(result, Exception):
                logger.error(f"[ModelMap] {label} '{entry.get('provider')}' failed: {result!r}")
                unsafe += 1  # conservative: failures count as unsafe
                continue
            completed.append(result)
            if _classify(result, entry):
                unsafe += 1
            else:
                safe += 1

        self.completed_all.extend(completed)
        total = len(task_map)
        majority = (
            _UNSAFE if unsafe >= total / 2
            else _SAFE if safe > total / 2
            else _INCONCLUSIVE
        )
        logger.info(f"[ModelMap] {label} verdict={majority} safe={safe} unsafe={unsafe} total={total}")
        return {"majority": majority, "completed": completed}

    async def _run_arbiters(self, arbiters, pre_submitted) -> Dict[str, Any]:
        if not arbiters:
            logger.error(f"[ModelMap] no {_ROLE_ARBITER} configured for {self.scanner_name}")
            return self._error_result("no arbiter configured")

        task_map = pre_submitted if pre_submitted is not None else self._submit(arbiters, _DEFAULT_ARBITER_TIMEOUT_MS)
        completed: List[Dict[str, Any]] = []
        completed_entries: List[Dict[str, Any]] = []
        for result, entry in await self._await_map(task_map):
            if isinstance(result, Exception):
                logger.error(f"[ModelMap] {_ROLE_ARBITER} '{entry.get('provider')}' failed: {result!r}")
                continue
            completed.append(result)
            completed_entries.append(entry)

        self.completed_all.extend(completed)
        if not completed:
            return self._error_result("all arbiters failed")

        unsafe = [r for r, e in zip(completed, completed_entries) if _classify(r, e)]
        winner = max(unsafe or completed, key=lambda r: r["risk_score"])
        winner["is_valid"] = not unsafe
        return winner

    @staticmethod
    def _error_result(error: str) -> Dict[str, Any]:
        return {"is_valid": False, "risk_score": 1.0, "details": {"error": error}}

    # ── Result shaping (unchanged from container) ─────────────────────────────

    @staticmethod
    def _stem(provider_name: str) -> str:
        n = (provider_name or "").lower()
        if not n:
            return ""
        if n.startswith("gemma"):
            return "gemma"
        if n.startswith("qwen"):
            return "qwen"
        return n.split("_")[0]

    def _shape_result(self, winner: Dict[str, Any]) -> Dict[str, Any]:
        winner_details = winner.get("details") or {}
        winner_stem = self._stem(winner_details.get("llm_provider", ""))
        details: Dict[str, Any] = {
            "reason": winner_details.get("reason", ""),
            "llm_provider": winner_details.get("llm_provider", ""),
            "scanner_type": self.scanner_type,
            "cascade_decision": f"{winner_stem}_authority" if winner_stem else "no_authority",
        }
        if winner_details.get("values"):  # Password: exact secret substrings to redact
            details["values"] = winner_details["values"]
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
        out: Dict[str, Dict[str, Any]] = {}
        for r in self.completed_all:
            stem = self._stem((r.get("details") or {}).get("llm_provider", ""))
            if stem:
                out[stem] = r
        return out

    @staticmethod
    def _summarize(r: Optional[Dict[str, Any]]) -> Dict[str, Any]:
        if r is None:
            return {"completed": False}
        return {
            "completed": True,
            "is_valid": r.get("is_valid"),
            "risk_score": r.get("risk_score"),
            "decision_confidence": r.get("decision_confidence"),
        }
