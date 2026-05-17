import logging
import os
import threading
import time
from typing import Dict, Any, List, Optional
from fastapi import FastAPI, HTTPException
import httpx
from pydantic import BaseModel

# Fix for optimum 2.0+ and transformers 4.57+ compatibility
try:
    from optimum.onnxruntime import (
        ORTModelForSequenceClassification,
        ORTModelForTokenClassification,
        ORTModelForQuestionAnswering,
        ORTModelForFeatureExtraction,
    )
    
    # Add can_generate method to ONNX models if missing
    def _can_generate(self):
        """ONNX classification models cannot generate sequences."""
        return False
    
    for model_class in [ORTModelForSequenceClassification, ORTModelForTokenClassification, 
                        ORTModelForQuestionAnswering, ORTModelForFeatureExtraction]:
        if not hasattr(model_class, 'can_generate'):
            model_class.can_generate = _can_generate
except:
    pass

from llm_guard import input_scanners, output_scanners
from intent_analyzer import IntentAnalysisScanner
from llm_scanner import (
    init_llm_scanner,
    init_cascade_scanners,
    is_truthy,
    CASCADE_SUPPORTED_SCANNERS,
    LLM_SUPPORTED_SCANNERS,
    scan_with_cascade,
    scan_with_model_map,
)

logging.basicConfig(
    level=logging.INFO,
    format='%(levelname)s - %(message)s'
)
logging.getLogger("llm_guard").setLevel(logging.ERROR)
logging.getLogger("presidio").setLevel(logging.ERROR)
logging.getLogger("transformers").setLevel(logging.ERROR)
logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

app = FastAPI(title="Agent Guard Scanner Service", version="1.0.0")
scanner_cache = {}

_DB_ABSTRACTOR_URL = (os.getenv("DATABASE_ABSTRACTOR_SERVICE_URL") or "").rstrip("/")


def store_model_results(
    all_results: List[Dict[str, Any]],
    scanner_name: str,
) -> None:
    """
    Sync: POST all model outputs to DB abstractor. Called from a daemon thread
    spawned inside scan_with_model_map — never blocks the /scan response. Never raises.
    """
    if not _DB_ABSTRACTOR_URL:
        logger.debug("[ModelMap] DATABASE_ABSTRACTOR_SERVICE_URL not set; skipping DB store")
        return
    payload = {
        "scannerName": scanner_name,
        "modelResults": all_results,
    }
    try:
        resp = httpx.post(
            f"{_DB_ABSTRACTOR_URL}/api/storeGuardrailModelResults",
            json=payload,
            timeout=10.0,
        )
        if resp.status_code >= 400:
            logger.warning(
                f"[ModelMap] DB store returned status {resp.status_code} for scanner={scanner_name}"
            )
    except Exception as exc:
        logger.warning(f"[ModelMap] DB store failed for scanner={scanner_name}: {exc}")

# LLM scanner — initialized once from env vars. None if no provider configured.
_llm_scanner = init_llm_scanner()
_force_llm = is_truthy(os.getenv("FORCE_LLM_MODE"))
if _force_llm:
    logger.info("[Service] FORCE_LLM_MODE=true — PromptInjection/BanTopics will use LLM path")

# Cascade scanner (Qwen + Gemma + Haiku) — initialized once when CASCADE_MODE_ENABLED is truthy.
_cascade_enabled = is_truthy(os.getenv("CASCADE_MODE_ENABLED"))
_cascade = init_cascade_scanners() if _cascade_enabled else None
if _cascade_enabled and _cascade is None:
    logger.warning("[Service] CASCADE_MODE_ENABLED=true but cascade providers failed to initialize; falling back to existing paths")
elif _cascade is not None:
    logger.info("[Service] Cascade enabled — PromptInjection/BanTopics will use Qwen+Gemma+Haiku")

# ── Slack per-scan alerts (fire-and-forget) ──────────────────────────────────
# When SLACK_WEBHOOK_URL is set, every /scan result is posted to Slack from a
# daemon thread. Never blocks the request path; never raises.
# Slack truncates a message's `text` at ~40,000 chars and rejects oversized
# payloads. Default the prompt cap just under that and keep a small reserve for
# the header / model lines; the whole message is also hard-clamped below so a
# huge prompt can never push the payload past Slack's limit.
_SLACK_MSG_HARD_LIMIT = 39000
_SLACK_WEBHOOK_URL = (os.getenv("SLACK_WEBHOOK_URL") or "").strip()
_SLACK_PROMPT_MAX = int(os.getenv("SLACK_ALERT_PROMPT_MAXLEN", "38000"))
if _SLACK_WEBHOOK_URL:
    logger.info("[SlackAlert] enabled — every scan will be posted to Slack (fire-and-forget)")
else:
    logger.info("[SlackAlert] SLACK_WEBHOOK_URL not set — Slack alerts disabled")


def _model_lines(details: Dict[str, Any], is_valid: bool, risk_score: float) -> str:
    """Render the per-model breakdown for whichever path handled the scan."""
    def voter(name: str, v: Optional[Dict[str, Any]]) -> str:
        if not v or not v.get("completed"):
            return f"  • {name}: _no result_"
        verdict = "safe" if v.get("is_valid") else "unsafe"
        rs = v.get("risk_score")
        dc = v.get("decision_confidence")
        sv = v.get("safe_vote")
        extra = f" safe_vote={sv}" if sv is not None else ""
        return f"  • {name}: {verdict} (risk={rs}, conf={dc}){extra}"

    # Cascade path — qwen/gemma/haiku.
    if "qwen" in details or "gemma" in details:
        lines = [voter("Qwen", details.get("qwen")), voter("Gemma", details.get("gemma"))]
        if details.get("haiku_consulted"):
            lines.append(f"  • Haiku: consulted → {'safe' if is_valid else 'unsafe'} (risk={risk_score})")
        else:
            lines.append("  • Haiku: not consulted (fast-pass)")
        return "\n".join(lines)

    # Single-LLM path (Toxicity / Gibberish / forced LLM).
    provider = details.get("llm_provider")
    if provider:
        return f"  • {provider}: {'safe' if is_valid else 'unsafe'} (risk={risk_score})"

    # Deterministic / local-ML path (BanSubstrings, Secrets, TokenLimit, …).
    return "  • no LLM — deterministic/ML scanner"


def _build_slack_payload(
    scanner_name: str, text: str, is_valid: bool, risk_score: float,
    details: Dict[str, Any], e2e_ms: float,
) -> Dict[str, Any]:
    decision = details.get("cascade_decision")
    fast_pass = decision == "fast_pass_allow"
    result_icon = ":white_check_mark:" if is_valid else ":no_entry:"
    result_word = "ALLOWED" if is_valid else "BLOCKED"
    prompt = text if len(text) <= _SLACK_PROMPT_MAX else text[:_SLACK_PROMPT_MAX] + " …[truncated]"

    decision_str = decision or "n/a"
    err = details.get("error")

    msg = (
        f":shield: *Agent-Guard* `{scanner_name}` — {result_icon} *{result_word}*\n"
        f"*Fast-pass:* {'yes' if fast_pass else 'no'}   "
        f"*Decision:* `{decision_str}`   "
        f"*E2E latency:* {e2e_ms:.0f} ms\n"
        f"*Models:*\n{_model_lines(details, is_valid, risk_score)}\n"
    )
    if err:
        msg += f"*Error:* `{err}`\n"
    msg += f"*Prompt:*\n```{prompt}```"

    # Final safety clamp: Slack rejects/truncates messages over ~40k chars.
    # Keep the closing fence so the code block still renders if we clipped here.
    if len(msg) > _SLACK_MSG_HARD_LIMIT:
        msg = msg[: _SLACK_MSG_HARD_LIMIT - 16] + " …[clipped]```"
    return {"text": msg}


def _post_slack(payload: Dict[str, Any]) -> None:
    """Runs in a daemon thread. Posts to Slack; swallows every error."""
    try:
        resp = httpx.post(_SLACK_WEBHOOK_URL, json=payload, timeout=5.0)
        if resp.status_code >= 400:
            logger.warning(f"[SlackAlert] webhook returned {resp.status_code}: {resp.text[:200]}")
    except Exception as exc:
        logger.warning(f"[SlackAlert] post failed: {exc}")


def fire_slack_alert(
    scanner_name: str, text: str, response: "ScanResponse", e2e_ms: float,
) -> None:
    """Fire-and-forget Slack alert for one completed scan. Builds the payload
    on the calling thread (cheap, dict access) but does the network POST in a
    daemon thread so the request path is never blocked. Never raises."""
    if not _SLACK_WEBHOOK_URL:
        return
    try:
        payload = _build_slack_payload(
            scanner_name, text, response.is_valid, response.risk_score,
            response.details or {}, e2e_ms,
        )
        threading.Thread(
            target=_post_slack, args=(payload,),
            daemon=True, name="slack-alert",
        ).start()
    except Exception as exc:
        # Building the payload should never fail, but alerting must never
        # affect the scan response.
        logger.warning(f"[SlackAlert] failed to enqueue alert: {exc}")


class ScanRequest(BaseModel):
    scanner_type: str
    scanner_name: str
    text: str
    config: Dict[str, Any] = {}

class ScanResponse(BaseModel):
    scanner_name: str
    is_valid: bool
    risk_score: float
    sanitized_text: str
    details: Dict[str, Any] = {}
    all_results: List[Dict[str, Any]] = []

PROMPT_SCANNERS = {
    "Anonymize": input_scanners.Anonymize,
    "BanCode": input_scanners.BanCode,
    "BanCompetitors": input_scanners.BanCompetitors,
    "BanSubstrings": input_scanners.BanSubstrings,
    "BanTopics": input_scanners.BanTopics,
    "Code": input_scanners.Code,
    "Gibberish": input_scanners.Gibberish,
    "IntentAnalysis": IntentAnalysisScanner,
    "Language": input_scanners.Language,
    "PromptInjection": input_scanners.PromptInjection,
    "Secrets": input_scanners.Secrets,
    "Sentiment": input_scanners.Sentiment,
    "TokenLimit": input_scanners.TokenLimit,
    "Toxicity": input_scanners.Toxicity,
}

OUTPUT_SCANNERS = {
    "BanCode": output_scanners.BanCode,
    "BanCompetitors": output_scanners.BanCompetitors,
    "BanSubstrings": output_scanners.BanSubstrings,
    "BanTopics": output_scanners.BanTopics,
    "Bias": output_scanners.Bias,
    "Code": output_scanners.Code,
    "Deanonymize": output_scanners.Deanonymize,
    "Language": output_scanners.Language,
    "MaliciousURLs": output_scanners.MaliciousURLs,
    "NoRefusal": output_scanners.NoRefusal,
    "Relevance": output_scanners.Relevance,
    "Sensitive": output_scanners.Sensitive,
    "Sentiment": output_scanners.Sentiment,
    "Toxicity": output_scanners.Toxicity,
}

def get_scanner(scanner_type: str, scanner_name: str, config: Dict[str, Any]):
    config_str = str(sorted(config.items())) if config else ""
    cache_key = f"{scanner_type}:{scanner_name}:{config_str}"
    
    if cache_key in scanner_cache:
        return scanner_cache[cache_key]
    
    if scanner_type == "prompt":
        scanner_class = PROMPT_SCANNERS.get(scanner_name)
    elif scanner_type == "output":
        scanner_class = OUTPUT_SCANNERS.get(scanner_name)
    else:
        raise ValueError(f"Invalid scanner type: {scanner_type}")
    
    if not scanner_class:
        raise ValueError(f"Scanner {scanner_name} not found")
    
    try:
        onnx_scanners = ["Toxicity", "PromptInjection", "Bias", "Relevance",
                        "NoRefusal", "MaliciousURLs", "Sensitive"]

        config = config or {}
        # Strip transport-only flag before passing to scanner constructor
        config.pop("use_llm", None)
        
        # Apply optimized configurations for best attack coverage
        if scanner_name == "PromptInjection":
            # Use lower threshold (0.5) for better coverage of sophisticated attacks
            # Testing showed 0.5 provides best detection without false negatives
            if "threshold" not in config:
                config["threshold"] = 0.75
            # Use FULL match type to scan entire input
            if "match_type" not in config:
                config["match_type"] = "full"
            # Note: Library defaults to TangoBeeAkto/deberta-prompt-injection model
            # Enable ONNX for 27x faster performance (60-120ms vs 1500ms)
            if "use_onnx" not in config:
                config["use_onnx"] = True
        elif scanner_name in onnx_scanners:
            config["use_onnx"] = True
        
        scanner = scanner_class(**config)
        scanner_cache[cache_key] = scanner
        
        logger.info(f"Initialized scanner: {scanner_name} with config: {config}")
        return scanner
    except Exception as e:
        logger.error(f"Scanner init failed: {scanner_name} - {str(e)}")
        raise

@app.get("/health")
async def health_check():
    return {"status": "healthy", "service": "agent-guard-scanner"}

@app.get("/scanners")
async def list_scanners():
    return {
        "prompt_scanners": list(PROMPT_SCANNERS.keys()),
        "output_scanners": list(OUTPUT_SCANNERS.keys()),
    }

@app.post("/scan", response_model=ScanResponse)
async def scan_text(request: ScanRequest):
    """Thin wrapper around the scan implementation. Adds a fire-and-forget
    Slack alert (when configured) for every completed scan, regardless of which
    path (cascade / single-LLM / modelMap / local-ML / deterministic) produced
    the result. HTTPException paths (validation/crash) are not alerted — those
    are logged as errors and represent prompts that were not evaluated."""
    started = time.time()
    response = await _scan_text_impl(request)
    e2e_ms = round((time.time() - started) * 1000, 1)
    fire_slack_alert(request.scanner_name, request.text, response, e2e_ms)
    return response


async def _scan_text_impl(request: ScanRequest):
    start_time = time.time()

    try:
        logger.info(f"Starting scan: scanner={request.scanner_name}, type={request.scanner_type}, text_length={len(request.text)}")

        # ── modelMap dispatch (multi-model parallel) ──────────────────
        if (
            request.config.get("modelMap")
            and request.scanner_name in LLM_SUPPORTED_SCANNERS
        ):
            try:
                store_fn = store_model_results if request.config.get("storeAllResults") else None
                result = scan_with_model_map(
                    request.scanner_name,
                    request.scanner_type,
                    request.text,
                    request.config,
                    store_fn=store_fn,
                )
                return ScanResponse(
                    scanner_name=request.scanner_name,
                    is_valid=result["is_valid"],
                    risk_score=result["risk_score"],
                    sanitized_text=request.text,
                    details=result.get("details", {}),
                    all_results=result.get("all_results", []),
                )
            except Exception as model_map_err:
                total_duration = (time.time() - start_time) * 1000
                logger.error(f"[ModelMap] scan_with_model_map failed: {model_map_err}")
                return ScanResponse(
                    scanner_name=request.scanner_name,
                    is_valid=True,
                    risk_score=0.0,
                    sanitized_text=request.text,
                    details={
                        "error": f"modelMap execution failed: {model_map_err}",
                        "execution_time_ms": round(total_duration, 2),
                    },
                )
        # ── End modelMap dispatch ─────────────────────────────────────

        # ── Cascade dispatch (Qwen + Gemma + Haiku) ──────────────────
        if (
            _cascade is not None
            and request.scanner_name in CASCADE_SUPPORTED_SCANNERS
        ):
            try:
                result = scan_with_cascade(
                    request.scanner_name,
                    request.scanner_type,
                    request.text,
                    request.config,
                    _cascade,
                )
                return ScanResponse(
                    scanner_name=request.scanner_name,
                    is_valid=result["is_valid"],
                    risk_score=result["risk_score"],
                    sanitized_text=request.text,
                    details=result.get("details", {}),
                )
            except Exception as cascade_err:
                total_duration = (time.time() - start_time) * 1000
                logger.error(f"[Cascade] scan_with_cascade failed: {cascade_err}")
                return ScanResponse(
                    scanner_name=request.scanner_name,
                    is_valid=True,
                    risk_score=0.0,
                    sanitized_text=request.text,
                    details={
                        "error": f"cascade execution failed: {cascade_err}",
                        "execution_time_ms": round(total_duration, 2),
                    },
                )
        # ── End cascade dispatch ──────────────────────────────────────

        # ── LLM dispatch ─────────────────────────────────────────────
        use_llm_requested = is_truthy(str(request.config.get("use_llm", "")))
        if ((_force_llm or use_llm_requested)
                and request.scanner_name in LLM_SUPPORTED_SCANNERS):
            if _llm_scanner is None:
                # No provider configured — fail open
                total_duration = (time.time() - start_time) * 1000
                return ScanResponse(
                    scanner_name=request.scanner_name,
                    is_valid=True,
                    risk_score=0.0,
                    sanitized_text=request.text,
                    details={
                        "error": "LLM mode requested but SCANNER_LLM_PROVIDER is not configured on the server",
                        "execution_time_ms": round(total_duration, 2),
                    },
                )
            try:
                result = _llm_scanner.scan(
                    request.scanner_name, request.scanner_type,
                    request.text, request.config,
                )
                return ScanResponse(
                    scanner_name=request.scanner_name,
                    is_valid=result["is_valid"],
                    risk_score=result["risk_score"],
                    sanitized_text=request.text,
                    details=result.get("details", {}),
                )
            except Exception as llm_err:
                # Provider failure — fail open
                total_duration = (time.time() - start_time) * 1000
                logger.error(f"[LLMScanner] Provider failed: {llm_err}")
                return ScanResponse(
                    scanner_name=request.scanner_name,
                    is_valid=True,
                    risk_score=0.0,
                    sanitized_text=request.text,
                    details={
                        "error": f"LLM provider failed: {llm_err}",
                        "execution_time_ms": round(total_duration, 2),
                    },
                )
        # ── End LLM dispatch ─────────────────────────────────────────

        scanner = get_scanner(request.scanner_type, request.scanner_name, request.config)

        scan_start = time.time()
        if request.scanner_type == "output":
            prompt = request.config.get("prompt", "")
            sanitized_output, is_valid, risk_score = scanner.scan(prompt, request.text)
        else:
            sanitized_output, is_valid, risk_score = scanner.scan(request.text)
        scan_duration = (time.time() - scan_start) * 1000

        total_duration = (time.time() - start_time) * 1000

        logger.info(f"Scan completed: scanner={request.scanner_name}, is_valid={is_valid}, risk_score={risk_score:.3f}, scan_time={scan_duration:.2f}ms, total_time={total_duration:.2f}ms")

        return ScanResponse(
            scanner_name=request.scanner_name,
            is_valid=is_valid,
            risk_score=risk_score,
            sanitized_text=sanitized_output,
            details={
                "original_length": len(request.text),
                "sanitized_length": len(sanitized_output),
                "scanner_type": request.scanner_type,
                "scan_time_ms": round(scan_duration, 2),
                "total_time_ms": round(total_duration, 2)
            }
        )
    except ValueError as e:
        total_duration = (time.time() - start_time) * 1000
        logger.error(f"Scan validation error: scanner={request.scanner_name}, error={str(e)}, time={total_duration:.2f}ms")
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        total_duration = (time.time() - start_time) * 1000
        logger.error(f"Scan failed: scanner={request.scanner_name}, error={str(e)}, time={total_duration:.2f}ms")
        raise HTTPException(status_code=500, detail=f"Scan failed: {str(e)}")

@app.post("/scan/batch", response_model=List[ScanResponse])
async def scan_batch(requests: List[ScanRequest]):
    results = []
    for req in requests:
        try:
            result = await scan_text(req)
            results.append(result)
        except HTTPException as e:
            results.append(ScanResponse(
                scanner_name=req.scanner_name,
                is_valid=False,
                risk_score=1.0,
                sanitized_text=req.text,
                details={"error": str(e.detail)}
            ))
    return results

if __name__ == "__main__":
    import uvicorn
    import os
    port = int(os.getenv("PORT", 8092))
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="warning")