"""Cloud-agnostic scan routing shared by the Cloudflare Worker and FastAPI app."""

import time
from typing import Any, Callable, Coroutine, Dict, Optional

import alerts
import cascade_backpressure
import cache
import scan_diag
from constants import (
    CASCADE_SCANNERS,
    GEMMA_ONLY_SCANNERS,
    LOCAL_SCANNERS,
    REMOTE_SCANNERS,
    SUPPORTED_SCANNERS,
    canonical_scanner,
    get_default_config,
    strip_qwen_tier,
)
from intent import audit_log as intent_audit
from intent import corpus as intent_corpus
from intent import decision as intent_decision
from intent import prefilter
from llm_scanner import scan_with_model_map
from remote_scanner import scan_anonymize
from scanners import scan_local
from settings import settings

ScheduleFn = Callable[[Coroutine[Any, Any, None]], None]


def _noop_schedule(_: Coroutine[Any, Any, None]) -> None:
    pass


def shape_response(
    scanner_name: str,
    is_valid: bool,
    risk_score: float,
    sanitized_text: str,
    details: Dict[str, Any],
) -> dict:
    return {
        "scanner_name": scanner_name,
        "is_valid": is_valid,
        "risk_score": risk_score,
        "sanitized_text": sanitized_text,
        "details": details,
    }


def scanners_metadata() -> dict:
    return {
        "supported": sorted(SUPPORTED_SCANNERS),
        "cascade": sorted(CASCADE_SCANNERS),
        "local": sorted(LOCAL_SCANNERS),
    }


async def scan_payload(
    payload: dict,
    env=None,
    schedule_fn: Optional[ScheduleFn] = None,
) -> dict:
    """Run one scan and return the ScanResponse-shaped dict."""
    started = time.perf_counter()
    schedule = schedule_fn or _noop_schedule
    scanner_name = canonical_scanner(payload.get("scanner_name", ""))
    scanner_type = payload.get("scanner_type", "prompt")
    text = payload.get("text", "")
    config = payload.get("config") or {}
    agent_host = payload.get("agent_host") or (config.get("agent_host") if isinstance(config, dict) else "") or ""

    def _elapsed_ms() -> float:
        return (time.perf_counter() - started) * 1000.0

    if scanner_name not in SUPPORTED_SCANNERS:
        scan_diag.log_scan_outcome(
            "unsupported_scanner",
            scanner_name,
            _elapsed_ms(),
            always=True,
        )
        return shape_response(
            scanner_name, True, 0.0, text, {"error": f"unsupported scanner: {scanner_name}"}
        )

    if scanner_name in REMOTE_SCANNERS:
        if scanner_name == "Anonymize":
            r = await scan_anonymize(text, config, env)
            return shape_response(
                scanner_name, r["is_valid"], r["risk_score"], r["sanitized_text"], r["details"]
            )

    if scanner_name in CASCADE_SCANNERS:
        if not config.get("modelConfigs"):
            default_cfg = get_default_config(settings.DEFAULT_MODEL_CONFIG_JSON)
            config = {**default_cfg, **config, "modelConfigs": default_cfg["modelConfigs"]}
        if scanner_name in GEMMA_ONLY_SCANNERS:
            config = {**config, "modelConfigs": strip_qwen_tier(config.get("modelConfigs"))}
        store_fn = None
        if config.get("storeAllResults"):
            store_fn = lambda completed, name: schedule(alerts.store_results(completed, name))

        # Semantic verdict cache still keys off payload.normalize()'s canonical
        # NL string exactly as before this redesign — entirely independent of
        # the instruction/data segmentation the intent module now uses below.
        intent_on = prefilter.enabled()
        norm_text = ""
        cache_text = text
        timer = scan_diag.StageTimer() if intent_on else None
        if intent_on:
            with timer.span("strip"):
                norm_text, _ = prefilter.normalized_text(text)
            cache_text = norm_text or text

        # Semantic cache, decide mode: consult the cache BEFORE applying
        # backpressure, so a cached verdict (including a cached block) is still
        # served correctly while Vertex is slow — backpressure must only short-
        # circuit a genuine miss, never a known answer. A fresh within-threshold
        # hit short-circuits — safe verdicts on a fuzzy match, blocks only on a
        # (near-)exact repeat (see cache.try_serve); a miss/error falls through.
        # The served verdict (is_valid) is honoured so cached blocks still block.
        # `prep` is reused by observe() so a miss embeds only once. BLOCK is,
        # and remains, exclusively this cache path's job — the intent module
        # below never blocks, it only fast-ALLOWs or defers to the cascade.
        prep = None
        if cache.serving():
            if timer is not None:
                with timer.span("prepare"):
                    prep = await cache.prepare(scanner_name, scanner_type, cache_text, config, agent_host)
            else:
                prep = await cache.prepare(scanner_name, scanner_type, cache_text, config, agent_host)
            served = cache.try_serve(prep, scanner_name, scanner_type, cache_text)
            if served is not None:
                schedule(alerts.post_cache_shadow(served["alert"]))
                scan_diag.log_scan_outcome(
                    "cache_hit",
                    scanner_name,
                    _elapsed_ms(),
                    extra={"is_valid": served["is_valid"]},
                    always=True,
                )
                return shape_response(
                    scanner_name, served["is_valid"], served["risk_score"], text, served["details"]
                )

        # Intent fast-path: segment the user prompt into instruction units
        # (separate from any attached data), classify each with the per-agent
        # multi-class model, and decide. Runs independently of cache.serving()
        # — it does its own batched embed/classify calls now, no longer reuses
        # the cache's embedding. ESCALATE (the default) falls through to the
        # cascade and seeds the mission. Fast ALLOWs do NOT warm the cache —
        # only LLM verdicts become learned examples, to avoid self-reinforcement.
        fast = None
        if intent_on:
            fast = await prefilter.decide_fast(agent_host, text, timer=timer)
            decided = fast["decision"]
            log_extra = {
                "reason": fast["reason"],
                "risk_category": fast["intent"].get("risk_category"),
                "units": len(fast["unit_texts"]),
                "extraction_method": fast["extraction_method"],
                "extraction_confidence": fast["extraction_confidence"],
                "classifier_warm": fast["classifier_warm"],
            }
            _audit_kwargs = dict(
                agent=agent_host,
                scanner=scanner_name,
                prompt=text,
                reason=fast["reason"],
                extraction_method=fast["extraction_method"],
                extraction_confidence=fast["extraction_confidence"],
                risk_category=fast["intent"].get("risk_category"),
                units=fast["units"],
                unit_texts=fast["unit_texts"],
            )
            if decided == intent_decision.ALLOW:
                if prefilter.act():
                    scan_diag.log_intent_decision(
                        "allow", agent_host, scanner_name, decided,
                        timer, extra=log_extra, always=True,
                    )
                    intent_audit.append("intent_allow", **_audit_kwargs)
                    return shape_response(scanner_name, True, 0.0, text, {
                        "scanner_type": scanner_type,
                        "prefilter": "allow",
                        "reason": fast["reason"],
                        "task_intent": ",".join(fast["intent"].get("units") or []),
                        "risk_category": fast["intent"].get("risk_category"),
                        "extraction_method": fast["extraction_method"],
                    })
                # Shadow mode: log what would have been decided, then fall through.
                scan_diag.log_intent_decision(
                    "allow_shadow", agent_host, scanner_name, decided,
                    timer, extra=log_extra, always=True,
                )
                intent_audit.append("intent_allow_shadow", **_audit_kwargs)
            # ESCALATE (or shadow): log the intent timings/result, then fall through to the cascade.
            scan_diag.log_intent_decision(
                "escalate", agent_host, scanner_name, decided, timer, extra=log_extra,
            )
            if fast.get("classifier_warm") and any(u is None for u in (fast.get("units") or [None])):
                schedule(intent_corpus.warmup(agent_host))

        # Cache miss (or cache not serving): only now does backpressure fail-open
        # to avoid paying for the slow cascade while Vertex latency is elevated.
        if cascade_backpressure.should_skip_cascade():
            scan_diag.log_backpressure_skip(scanner_name)
            return shape_response(
                scanner_name,
                True,
                0.0,
                text,
                {**cascade_backpressure.cascade_skip_details(), "scanner_type": scanner_type},
            )
        scan_diag.log_backpressure_proceeding(scanner_name)
        try:
            result = await scan_with_model_map(
                scanner_name, scanner_type, text, config, store_fn=store_fn
            )
            elapsed = result.get("execution_time_ms")
            if isinstance(elapsed, (int, float)) and elapsed > 0:
                cascade_backpressure.record_cascade_latency(float(elapsed))
            schedule(alerts.post_slack(scanner_name, scanner_type, text, result))
            if intent_on:
                prefilter.enrich_result(result)
                is_valid_bool = bool(result.get("is_valid", True))
                # Only GOOD verdicts feed the corpus — analysis (offline) only
                # ever looks at stored GOOD prompts; a BLOCKED verdict's units
                # have nothing useful to teach the multi-class intent model.
                if is_valid_bool and fast is not None and fast.get("unit_texts"):
                    units_for_queue = [{"text": t} for t in fast["unit_texts"]]
                    if intent_corpus.queue(agent_host, units_for_queue):
                        schedule(intent_corpus.flush())
                    if intent_corpus.record_good(agent_host):
                        schedule(intent_corpus.warmup(agent_host))
            # Per-scanner semantic cache: observe + warm (shadow in observe mode;
            # cache-warm + miss-comparison in decide mode). Fire-and-forget.
            if cache.enabled():
                schedule(cache.observe(scanner_name, scanner_type, cache_text, config, result,
                                       prep=prep, agent_host=agent_host))
            scan_diag.log_scan_outcome(
                "cascade_run",
                scanner_name,
                _elapsed_ms(),
                extra={
                    "is_valid": result.get("is_valid"),
                    "cascade_ms": elapsed,
                    "intent": result.get("details", {}).get("task_intent") if intent_on else None,
                },
            )
            return shape_response(
                scanner_name,
                result["is_valid"],
                result["risk_score"],
                text,
                result.get("details", {}),
            )
        except Exception as exc:
            scan_diag.log_scan_outcome(
                "cascade_error",
                scanner_name,
                _elapsed_ms(),
                extra={"error": str(exc)},
                always=True,
            )
            return shape_response(
                scanner_name,
                True,
                0.0,
                text,
                {"scanner_type": scanner_type, "error": f"cascade failed: {exc}"},
            )

    if scanner_name in LOCAL_SCANNERS:
        try:
            r = scan_local(scanner_name, scanner_type, text, config)
            scan_diag.log_scan_outcome(
                "local_scan",
                scanner_name,
                _elapsed_ms(),
                extra={"is_valid": r["is_valid"]},
            )
            return shape_response(
                scanner_name, r["is_valid"], r["risk_score"], r["sanitized_text"], r["details"]
            )
        except ValueError:
            return shape_response(
                scanner_name,
                True,
                0.0,
                text,
                {
                    "scanner_type": scanner_type,
                    "status": "not_implemented",
                    "would_route_to": "local",
                },
            )

    return shape_response(scanner_name, True, 0.0, text, {"error": "unroutable"})
