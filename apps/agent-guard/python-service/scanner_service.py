import logging
from typing import Dict, Any, List
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel
from llm_guard import input_scanners, output_scanners

logging.basicConfig(
    level=logging.ERROR,
    format='%(levelname)s - %(message)s'
)
logging.getLogger("llm_guard").setLevel(logging.ERROR)
logging.getLogger("presidio").setLevel(logging.ERROR)
logging.getLogger("transformers").setLevel(logging.ERROR)
logger = logging.getLogger(__name__)

app = FastAPI(title="Agent Guard Scanner Service", version="1.0.0")
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

PROMPT_SCANNERS = {
    "Anonymize": input_scanners.Anonymize,
    "BanCode": input_scanners.BanCode,
    "BanCompetitors": input_scanners.BanCompetitors,
    "BanSubstrings": input_scanners.BanSubstrings,
    "BanTopics": input_scanners.BanTopics,
    "Code": input_scanners.Code,
    "Gibberish": input_scanners.Gibberish,
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
        
        if scanner_name in onnx_scanners:
            config = config or {}
            config["use_onnx"] = True
        
        scanner = scanner_class(**config) if config else scanner_class()
        scanner_cache[cache_key] = scanner
        
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
    try:
        scanner = get_scanner(request.scanner_type, request.scanner_name, request.config)
        
        if request.scanner_type == "output":
            prompt = request.config.get("prompt", "")
            sanitized_output, is_valid, risk_score = scanner.scan(prompt, request.text)
        else:
            sanitized_output, is_valid, risk_score = scanner.scan(request.text)
        
        return ScanResponse(
            scanner_name=request.scanner_name,
            is_valid=is_valid,
            risk_score=risk_score,
            sanitized_text=sanitized_output,
            details={
                "original_length": len(request.text),
                "sanitized_length": len(sanitized_output),
                "scanner_type": request.scanner_type,
            }
        )
    except ValueError as e:
        raise HTTPException(status_code=400, detail=str(e))
    except Exception as e:
        logger.error(f"Scan failed: {str(e)}")
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
