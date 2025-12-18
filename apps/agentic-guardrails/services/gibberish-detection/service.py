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

from llm_guard import input_scanners

logging.basicConfig(
    level=logging.INFO,
    format='%(levelname)s - %(message)s'
)
logging.getLogger("llm_guard").setLevel(logging.ERROR)
logging.getLogger("presidio").setLevel(logging.ERROR)
logging.getLogger("transformers").setLevel(logging.ERROR)
logger = logging.getLogger(__name__)
logger.setLevel(logging.INFO)

app = FastAPI(title="Gibberish Detection Service", version="1.0.0")
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

SCANNERS = {
    "Gibberish": input_scanners.Gibberish,
}

def get_scanner(scanner_name: str, config: Dict[str, Any]):
    config_str = str(sorted(config.items())) if config else ""
    cache_key = f"{scanner_name}:{config_str}"

    if cache_key in scanner_cache:
        return scanner_cache[cache_key]

    scanner_class = SCANNERS.get(scanner_name)
    if not scanner_class:
        raise ValueError(f"Scanner {scanner_name} not found")

    try:
        config = config or {}

        # Apply optimized configurations for Gibberish detection
        if scanner_name == "Gibberish":
            # Use optimal threshold for gibberish detection
            # Higher threshold means more strict detection (less false positives)
            if "threshold" not in config:
                config["threshold"] = 0.7
            # Use FULL match type to scan entire input
            if "match_type" not in config:
                config["match_type"] = "full"
            # Note: Library defaults to TangoBeeAkto/gibberish-detector model
            # Enable ONNX for 5-10x faster performance
            if "use_onnx" not in config:
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
    return {"status": "healthy", "service": "gibberish-detection"}

@app.get("/scanners")
async def list_scanners():
    return {"scanners": list(SCANNERS.keys())}

@app.post("/scan", response_model=ScanResponse)
async def scan_text(request: ScanRequest):
    start_time = time.time()

    try:
        logger.info(f"Starting scan: scanner={request.scanner_name}, text_length={len(request.text)}")

        scanner = get_scanner(request.scanner_name, request.config)

        scan_start = time.time()
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

if __name__ == "__main__":
    import uvicorn
    import os
    port = int(os.getenv("PORT", 8096))
    uvicorn.run(app, host="0.0.0.0", port=port, log_level="warning")