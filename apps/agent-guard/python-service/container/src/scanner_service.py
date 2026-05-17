import logging
import os
import time
from typing import Dict, Any, List
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
            and request.scanner_name in LLM_SUPPORTED_SCANNERS
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