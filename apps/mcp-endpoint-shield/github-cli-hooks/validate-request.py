#!/usr/bin/env python3
"""
GitHub Copilot CLI Input Guardrails Hook
Validates user prompts before they are processed by Copilot CLI.
"""
import json
import logging
import os
import sys
import tempfile
import time
import urllib.request
from pathlib import Path
from typing import Any, Dict, Union

# Logging configuration
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/akto-main/akto/.github/akto-copilot/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    # File handler for persistent logs
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-request.log"))
    file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    file_formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
    file_handler.setFormatter(file_formatter)
    logger.addHandler(file_handler)
    
    # Console handler for errors only (to stderr)
    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    logger.addHandler(console_handler)

# Environment variables
AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
GITHUB_COPILOT_API_URL = os.getenv("GITHUB_COPILOT_API_URL", "https://api.github.com")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = "github_copilot_cli"


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    """Build the Akto HTTP proxy URL with appropriate query parameters."""
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    """Send a JSON payload to the specified URL and return the response."""
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload, default=str)[:1000]}...")
    
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
            status_code = response.getcode()
            raw = response.read().decode("utf-8")
            
            logger.info(f"API RESPONSE: Status {status_code}, Duration: {duration_ms}ms, Size: {len(raw)} bytes")
            if LOG_PAYLOADS:
                logger.debug(f"Response body: {raw[:1000]}...")
            
            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                return raw
    except Exception as e:
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(f"API CALL FAILED after {duration_ms}ms: {e}")
        raise


def build_validation_request(prompt: str) -> Dict[str, Any]:
    """Build the request body for guardrails validation."""
    return {
        "url": GITHUB_COPILOT_API_URL,
        "path": "/copilot/chat",
        "request": {
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "body": {
                "query": prompt.strip(),
            },
            "queryParams": {},
            "metadata": {
                "tag": {
                    "gen-ai": "Gen AI"
                }
            }
        },
        "response": None
    }


def call_guardrails(prompt: str) -> tuple[bool, str]:
    """
    Call Akto guardrails to validate the prompt.
    Returns: (allowed, reason)
    """
    if not prompt.strip():
        return True, ""

    logger.info("Validating prompt against guardrails")
    if LOG_PAYLOADS:
        logger.debug(f"Prompt: {prompt[:200]}...")
    else:
        logger.info(f"Prompt preview: {prompt[:100]}...")

    try:
        request_body = build_validation_request(prompt)
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=False),
            request_body,
        )

        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")

        if allowed:
            logger.info("Prompt ALLOWED by guardrails")
        else:
            logger.warning(f"Prompt DENIED by guardrails: {reason}")

        return allowed, reason

    except Exception as e:
        logger.error(f"Guardrails validation error: {e}", exc_info=True)
        # Default to allowing on error to avoid blocking legitimate usage
        return True, ""


def store_prompt(prompt: str, cwd: str):
    """Store the user prompt for later use by postToolUse hook."""
    try:
        temp_dir = Path(tempfile.gettempdir()) / "akto-copilot-hooks"
        temp_dir.mkdir(exist_ok=True)
        
        # Clean up old files (older than 1 hour)
        current_time = time.time()
        for old_file in temp_dir.glob("prompt_*.json"):
            if current_time - old_file.stat().st_mtime > 3600:
                old_file.unlink()
        
        # Store current prompt with timestamp
        prompt_file = temp_dir / f"prompt_{os.getpid()}_{int(time.time() * 1000)}.json"
        with open(prompt_file, 'w') as f:
            json.dump({
                "prompt": prompt,
                "cwd": cwd,
                "timestamp": int(time.time() * 1000)
            }, f)
        
        logger.info(f"Stored prompt in {prompt_file}")
    except Exception as e:
        logger.error(f"Failed to store prompt: {e}")


def ingest_blocked_request(prompt: str):
    """Ingest blocked request data for analytics."""
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    logger.info("Ingesting blocked request data")
    try:
        blocked_response_payload = {
            "body": {"x-blocked-by": "Akto Guardrails"},
            "headers": {"content-type": "application/json"},
            "statusCode": 403,
            "status": "forbidden"
        }

        request_body = build_validation_request(prompt)
        request_body["response"] = blocked_response_payload
        
        post_payload_json(
            build_http_proxy_url(guardrails=False, ingest_data=True),
            request_body,
        )
        logger.info("Blocked request ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def create_blocked_marker(reason: str):
    """Create a marker file so preToolUse hook can block tool execution."""
    try:
        temp_dir = Path(tempfile.gettempdir()) / "akto-copilot-hooks" / "blocked"
        temp_dir.mkdir(parents=True, exist_ok=True)
        
        # Create marker file with timestamp
        marker_file = temp_dir / f"blocked_{os.getpid()}_{int(time.time() * 1000)}.json"
        with open(marker_file, 'w') as f:
            json.dump({
                "reason": reason,
                "timestamp": int(time.time() * 1000)
            }, f)
        
        logger.info(f"Created blocked marker: {marker_file}")
    except Exception as e:
        logger.error(f"Failed to create blocked marker: {e}")


def main():
    """Main hook execution function."""
    logger.info(f"=== Hook execution started - Sync: {AKTO_SYNC_MODE} ===")
    try:
        # Read JSON input from stdin (GitHub Copilot hook format)
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)  # Exit gracefully to not break Copilot

    # Extract prompt from the input
    prompt = input_data.get("prompt", "")
    cwd = input_data.get("cwd", "")
    logger.info(f"Received prompt (length: {len(prompt)} chars)")

    if not prompt.strip():
        logger.info("Empty prompt, allowing")
        sys.exit(0)

    # Store prompt for postToolUse hook to access
    store_prompt(prompt, cwd)

    # Only enforce guardrails in sync mode
    if AKTO_SYNC_MODE and AKTO_DATA_INGESTION_URL:
        allowed, reason = call_guardrails(prompt)
        logger.info(f"Guardrails result: allowed={allowed}")
        
        if not allowed:
            logger.warning(f"BLOCKING prompt - Reason: {reason}")
            ingest_blocked_request(prompt)
            # Create marker file for preToolUse hook to detect and block tool execution
            create_blocked_marker(reason or "Security policy violation")
            # Write warning to stderr (visible to user)
            logger.info("Creating blocked marker for preToolUse hook")
            error_msg = f"Prompt flagged by Akto Guardrails: {reason or 'Security policy violation'}\n"
            error_msg += "Note: GitHub Copilot will generate a response, but tool executions will be blocked.\n"
            sys.stderr.write(error_msg)
            sys.stderr.flush()

    # Prompt logged (userPromptSubmitted hook cannot block - GitHub limitation)
    logger.info("Prompt logging complete - continuing to LLM")
    sys.exit(0)


if __name__ == "__main__":
    main()
