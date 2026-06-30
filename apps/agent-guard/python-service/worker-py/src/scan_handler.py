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
from intent import prefilter, trainer
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
    # Agent identity (Host header, e.g. "dev.ai-agent.claude"). Scopes the cache
    # partition + learned mission per agent; "" keeps the global partition.
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

        # Intent prefilter: when enabled, strip the payload down to natural
        # language and cache/classify on THAT (cleaner key → higher hit-rate,
        # sharper intent). When disabled, the cache operates on the raw text
        # exactly as before.
        intent_on = prefilter.enabled()
        norm_text = ""
        cache_text = text
        chunk_count = 0
        timer = scan_diag.StageTimer() if intent_on else None
        if intent_on:
            with timer.span("strip"):
                norm_text, chunk_count = prefilter.normalized_text(text)
            cache_text = norm_text or text

        # Semantic cache, decide mode: consult the cache BEFORE applying
        # backpressure, so a cached verdict (including a cached block) is still
        # served correctly while Vertex is slow — backpressure must only short-
        # circuit a genuine miss, never a known answer. A fresh within-threshold
        # hit short-circuits — safe verdicts on a fuzzy match, blocks only on a
        # (near-)exact repeat (see cache.try_serve); a miss/error falls through.
        # The served verdict (is_valid) is honoured so cached blocks still block.
        # `prep` is reused by observe() so a miss embeds only once.
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

            # Intent fast-path on a cache miss: regex signal + per-agent
            # classifier (reusing prep's embedding) can ALLOW/BLOCK without the
            # LLM. ESCALATE (the default) falls through to the cascade and seeds
            # the mission. Fast decisions do NOT warm the cache — only LLM
            # verdicts become learned examples, to avoid self-reinforcement.
            if intent_on:
                fast = await prefilter.decide_fast(agent_host, norm_text, prep, timer=timer)
                decided = fast["decision"]
                log_extra = {
                    "reason": fast["reason"],
                    "task": fast["intent"]["task"],
                    "risk": fast["intent"]["risk"],
                    "p_clf": fast.get("p_malicious_clf"),
                    "chunks": chunk_count,
                }
                _neighbor = (prep or {}).get("cached")
                _audit_kwargs = dict(
                    agent=agent_host,
                    scanner=scanner_name,
                    prompt=text,
                    norm_text=norm_text,
                    p_clf=fast.get("p_malicious_clf"),
                    reason=fast["reason"],
                    task=fast["intent"]["task"],
                    risk=fast["intent"]["risk"],
                    scope_distance=fast["intent"].get("scope_distance"),
                    neighbor=_neighbor,
                )
                if decided in (intent_decision.ALLOW, intent_decision.BLOCK):
                    if prefilter.act():
                        is_valid = decided == intent_decision.ALLOW
                        scan_diag.log_intent_decision(
                            decided.lower(), agent_host, scanner_name, decided,
                            timer, extra=log_extra, always=True,
                        )
                        intent_audit.append(f"intent_{decided.lower()}", **_audit_kwargs)
                        return shape_response(scanner_name, is_valid, 0.0 if is_valid else 1.0, text, {
                            "scanner_type": scanner_type,
                            "prefilter": decided.lower(),
                            "reason": fast["reason"],
                            "task_intent": fast["intent"]["task"],
                            "risk_intent": fast["intent"]["risk"],
                            "scope_distance": fast["intent"].get("scope_distance"),
                        })
                    # Shadow mode: log what would have been decided, then fall through.
                    scan_diag.log_intent_decision(
                        f"{decided.lower()}_shadow", agent_host, scanner_name, decided,
                        timer, extra=log_extra, always=True,
                    )
                    intent_audit.append(f"intent_{decided.lower()}_shadow", **_audit_kwargs)
                # ESCALATE (or shadow): log the intent timings/result, then fall through to the cascade.
                scan_diag.log_intent_decision(
                    "escalate", agent_host, scanner_name, decided, timer, extra=log_extra,
                )
                if decided == intent_decision.ESCALATE:
                    intent_audit.append("intent_escalate", **_audit_kwargs)
                # Lazy corpus warm: on the first cold-start ESCALATE for this agent,
                # load its prior training examples from db-abstractor and re-fit the
                # per-agent LogReg. Fire-and-forget — current request already ESCALATEs;
                # subsequent requests on this pod benefit from the warmed classifier.
                if fast.get("p_malicious_clf") is None:
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
            # Intent learning: stamp the LLM verdict's intent triple onto the
            # result so observe() persists it as a learned mission example, and
            # feed the embedding+label to both the per-agent classifier trainer
            # and the durable corpus (database-abstractor → MongoDB).
            #
            # Learning loop closed:
            #   1. enrich_result()    – derives specific task_intent from the
            #                           LLM's categories/matchedTopic/reason
            #                           (not just binary is_valid).
            #   2. trainer.record()   – buffers (vec, label) in-process; fires
            #                           /train when threshold is crossed so the
            #                           per-agent LogReg improves this pod.
            #   3. corpus.queue()     – buffers (vec, triple) for a
            #                           batch flush to DATABASE_ABSTRACTOR_SERVICE_URL
            #                           so all pods share the training corpus and
            #                           examples survive pod restarts.
            #   4. cache.observe()    – writes the embedding+verdict to Redis KNN
            #                           so future requests hit the semantic cache
            #                           (sentence similarity fast-path).
            if intent_on:
                prefilter.enrich_result(result, norm_text or text)
                details = result.get("details", {})
                triple = {
                    "task_intent":  details.get("task_intent", ""),
                    "risk_intent":  details.get("risk_intent", ""),
                    "scope_bucket": details.get("scope_bucket", ""),
                }
                if prep is not None:
                    vec = prep.get("vec")
                    is_valid_bool = bool(result.get("is_valid", True))
                    # In-process trainer (per-pod LogReg retraining)
                    if trainer.record(agent_host, vec, is_valid_bool):
                        schedule(trainer.train_now(agent_host))
                    # Durable cross-pod corpus (batch → database-abstractor → MongoDB)
                    if intent_corpus.queue(agent_host, vec, is_valid_bool, triple):
                        schedule(intent_corpus.flush())
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
