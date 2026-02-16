#!/usr/bin/env python3

import json
import logging
import os
import sys
import time
import urllib.request
from typing import Any, Dict, Union

# Configuration
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "./.github/akto/copilot/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
GITHUB_COPILOT_API_URL = os.getenv("GITHUB_COPILOT_API_URL", "https://api.github.com")
AKTO_CONNECTOR = "github_copilot_cli"

# Setup logging
os.makedirs(LOG_DIR, exist_ok=True)
logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "prompt-hook.log"))
    file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(file_handler)
    
    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    logger.addHandler(console_handler)


def build_http_proxy_url(guardrails: bool, ingest_data: bool) -> str:
    """Build Akto HTTP proxy URL with query parameters."""
    params = [f"akto_connector={AKTO_CONNECTOR}"]
    if guardrails:
        params.append("guardrails=true")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_to_akto(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    """Send JSON payload to Akto API."""
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Payload: {json.dumps(payload, default=str)[:1000]}...")
    
    headers = {"Content-Type": "application/json"}
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    
    start_time = time.time()
    try:
        with urllib.request.urlopen(request, timeout=AKTO_TIMEOUT) as response:
            duration_ms = int((time.time() - start_time) * 1000)
            raw = response.read().decode("utf-8")
            logger.info(f"Response: {response.getcode()} in {duration_ms}ms")
            
            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                return raw
    except Exception as e:
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(f"API call failed after {duration_ms}ms: {e}")
        raise


def build_akto_request(prompt: str, cwd: str, timestamp: int) -> Dict[str, Any]:
    """Build request payload for Akto guardrails validation."""
    return {
        "url": GITHUB_COPILOT_API_URL,
        "path": "/copilot/chat",
        "request": {
            "method": "POST",
            "headers": {"content-type": "application/json"},
            "body": {"query": prompt.strip()},
            "queryParams": {},
            "metadata": {
                "tag": {"gen-ai": "Gen AI"},
                "cwd": cwd,
                "timestamp": timestamp
            }
        },
        "response": None
    }


def validate_prompt(prompt: str, cwd: str, timestamp: int) -> tuple[bool, str]:
    """Validate prompt against Akto guardrails. Returns (allowed, reason)."""
    if not prompt.strip():
        return True, ""
    
    logger.info("Validating prompt against guardrails")
    logger.info(f"Prompt preview: {prompt[:100]}...")
    
    try:
        request_body = build_akto_request(prompt, cwd, timestamp)
        result = post_to_akto(
            build_http_proxy_url(guardrails=True, ingest_data=False),
            request_body
        )
        
        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")
        
        if allowed:
            logger.info("✓ Prompt ALLOWED by guardrails")
        else:
            logger.warning(f"✗ Prompt DENIED by guardrails: {reason}")
        
        return allowed, reason
        
    except Exception as e:
        logger.error(f"Guardrails validation error: {e}", exc_info=True)
        return True, ""  # Allow on error


def ingest_blocked_request(prompt: str, cwd: str, timestamp: int, reason: str):
    """Ingest blocked request data to Akto for analytics."""
    if not AKTO_DATA_INGESTION_URL:
        return
    
    logger.info("Ingesting blocked request")
    try:
        request_body = build_akto_request(prompt, cwd, timestamp)
        request_body["response"] = {
            "body": {"x-blocked-by": "Akto Guardrails", "reason": reason},
            "headers": {"content-type": "application/json"},
            "statusCode": 403,
            "status": "forbidden"
        }
        
        post_to_akto(
            build_http_proxy_url(guardrails=False, ingest_data=True),
            request_body
        )
        logger.info("Blocked request ingested successfully")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info("=== User Prompt Submitted Hook ===")
    
    try:
        input_data = json.load(sys.stdin)
        if LOG_PAYLOADS:
            logger.debug(f"Input: {json.dumps(input_data)}")
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)
    
    prompt = input_data.get("prompt", "")
    cwd = input_data.get("cwd", "")
    timestamp = input_data.get("timestamp", int(time.time() * 1000))
    
    logger.info(f"Prompt length: {len(prompt)} chars, CWD: {cwd}")
    
    if not prompt.strip():
        logger.info("Empty prompt, skipping validation")
        sys.exit(0)
    
    if AKTO_SYNC_MODE and AKTO_DATA_INGESTION_URL:
        allowed, reason = validate_prompt(prompt, cwd, timestamp)
        
        if not allowed:
            logger.warning(f"Prompt blocked: {reason}")
            # Ingest blocked request
            ingest_blocked_request(prompt, cwd, timestamp, reason)
            # Warn user (but cannot block - GitHub limitation)
            sys.stderr.write(f"Akto Guardrails flagged prompt: {reason}\n")
            sys.stderr.flush()
    
    logger.info("Hook completed")
    sys.exit(0)


if __name__ == "__main__":
    main()
