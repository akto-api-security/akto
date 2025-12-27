import logging
import time
from typing import Dict, Any
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

# Fix for optimum 2.0+ and transformers 4.57+ compatibility
try:
    from optimum.onnxruntime import (
        ORTModelForSequenceClassification,
        ORTModelForTokenClassification,
        ORTModelForQuestionAnswering,
        ORTModelForFeatureExtraction,
    )

    def _can_generate(self):
        """ONNX classification models cannot generate sequences."""
        return False

    for model_class in [ORTModelForSequenceClassification, ORTModelForTokenClassification,
                        ORTModelForQuestionAnswering, ORTModelForFeatureExtraction]:
        if not hasattr(model_class, 'can_generate'):
            model_class.can_generate = _can_generate
except:
    pass

from llm_guard import output_scanners

logging.basicConfig(
    level=logging.INFO,
    format='%(levelname)s - %(message)s'
)
logging.getLogger("llm_guard").setLevel(logging.ERROR)
logging.getLogger("presidio").setLevel(logging.ERROR)
logging.getLogger("transformers").setLevel(logging.ERROR)
logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

app = FastAPI(title="Output Quality & Safety Service", version="1.0.0")
scanner_cache = {}

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

OUTPUT_SCANNERS = {
    "Relevance": output_scanners.Relevance,
    "NoRefusal": output_scanners.NoRefusal,
    "MaliciousURLs": output_scanners.MaliciousURLs,
    "Sensitive": output_scanners.Sensitive,
}

def get_scanner(scanner_name: str, config: Dict[str, Any]):
    config_str = str(sorted(config.items())) if config else ""
    cache_key = f"{scanner_name}:{config_str}"

    if cache_key in scanner_cache:
        return scanner_cache[cache_key]

    scanner_class = OUTPUT_SCANNERS.get(scanner_name)
    if not scanner_class:
        raise ValueError(f"Scanner {scanner_name} not found")

    try:
        config = config or {}

        # Enable ONNX for all scanners in this service
        onnx_scanners = ["Relevance", "NoRefusal", "MaliciousURLs", "Sensitive"]
        if scanner_name in onnx_scanners and "use_onnx" not in config:
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
    return {"status": "healthy", "service": "output-quality-safety"}

@app.get("/scanners")
async def list_scanners():
    return {"scanners": list(OUTPUT_SCANNERS.keys())}

@app.post("/scan", response_model=ScanResponse)
async def scan_text(request: ScanRequest):
    start_time = time.time()

    try:
        logger.info(f"Starting scan: scanner={request.scanner_name}, text_length={len(request.text)}")

        scanner = get_scanner(request.scanner_name, request.config)

        scan_start = time.time()
        # Output scanners require both prompt and output
        prompt = request.config.get("prompt", "")
        sanitized_output, is_valid, risk_score = scanner.scan(prompt, request.text)
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

if __name__ == "__main__":
    import uvicorn
    import os
    port = int(os.getenv("PORT", 8095))
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="warning")
