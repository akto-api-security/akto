#!/usr/bin/env python3

import json
import logging
import os
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, Union
from akto_machine_id import get_machine_id

# Configuration
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
MODE = os.getenv("MODE", "argus").lower()

# SSL Configuration
SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"


def detect_connector(input_data: dict) -> str:
    """Detect connector from hook payload. hookEventName is present in all VSCode payloads."""
    if "hookEventName" in input_data:
        return "vscode"
    return os.getenv("AKTO_CONNECTOR", "github_copilot_cli")


def get_connector_config(connector: str) -> dict:
    """Return connector-specific config values."""
    if connector == "vscode":
        api_url = os.getenv("VSCODE_API_URL", "https://vscode.dev")
        return {
            "connector": connector,
            "is_vscode": True,
            "api_url": api_url,
            "ai_agent_tag": "vscode",
            "hook_header": "x-vscode-hook",
            "atlas_domain": "ai-agent.vscode",
            "log_dir_default": "~/akto/.github/akto/vscode/logs",
            "blocked_exit_code": 2
        }
    else:
        api_url = os.getenv("GITHUB_COPILOT_API_URL", "https://api.github.com")
        return {
            "connector": connector,
            "is_vscode": False,
            "api_url": api_url,
            "ai_agent_tag": "copilotcli",
            "hook_header": "x-copilot-hook",
            "atlas_domain": "ai-agent.copilot",
            "log_dir_default": "~/akto/.github/akto/copilot/logs",
            "blocked_exit_code": 0,  # github-cli cannot block prompts
        }


def setup_logging(log_dir: str):
    os.makedirs(log_dir, exist_ok=True)
    logger = logging.getLogger(__name__)
    logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    if not logger.handlers:
        file_handler = logging.FileHandler(os.path.join(log_dir, "validate-prompt.log"))
        file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
        logger.addHandler(file_handler)
        console_handler = logging.StreamHandler(sys.stderr)
        console_handler.setLevel(logging.ERROR)
        logger.addHandler(console_handler)
    return logger


def create_ssl_context():
    return ssl._create_unverified_context()


def build_http_proxy_url(cfg: dict, guardrails: bool, ingest_data: bool) -> str:
    """Build Akto HTTP proxy URL with query parameters."""
    params = [f"akto_connector={cfg['connector']}"]
    if guardrails:
        params.append("guardrails=true")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_to_akto(url: str, payload: Dict[str, Any], logger) -> Union[Dict[str, Any], str]:
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
        ssl_context = create_ssl_context()
        with urllib.request.urlopen(request, context=ssl_context, timeout=AKTO_TIMEOUT) as response:
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


def uuid_to_ipv6_simple(uuid_str):
    hex_str = uuid_str.replace("-", "").lower()
    return ":".join(hex_str[i:i+4] for i in range(0, 32, 4))


def build_akto_request(prompt: str, cwd: str, timestamp: int, cfg: dict) -> Dict[str, Any]:
    """Build request payload for Akto guardrails validation."""
    tags = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        tags["ai-agent"] = cfg["ai_agent_tag"]
        tags["source"] = CONTEXT_SOURCE

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = cfg["api_url"].replace("https://", "").replace("http://", "")
    hook_header = cfg["hook_header"]
    hook_value = "UserPromptSubmitted"

    request_headers = json.dumps({
        "host": host,
        hook_header: hook_value,
        "content-type": "application/json"
    })

    response_headers = json.dumps({
        hook_header: hook_value
    })

    request_payload = json.dumps({
        "body": prompt.strip()
    })

    response_payload = json.dumps({})

    return {
        "path": "/copilot/chat",
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": uuid_to_ipv6_simple(device_id),
        "destIp": "127.0.0.1",
        "time": str(timestamp),
        "statusCode": "200",
        "type": "HTTP/1.1",
        "status": "200",
        "akto_account_id": "1000000",
        "akto_vxlan_id": device_id,
        "is_pending": "false",
        "source": "MIRRORING",
        "direction": None,
        "process_id": None,
        "socket_id": None,
        "daemonset_id": None,
        "enabled_graph": None,
        "tag": json.dumps(tags),
        "metadata": json.dumps(tags),
        "contextSource": "ENDPOINT"
    }


def validate_prompt(prompt: str, cwd: str, timestamp: int, cfg: dict, logger) -> tuple[bool, str]:
    """Validate prompt against Akto guardrails. Returns (allowed, reason)."""
    if not prompt.strip():
        return True, ""

    logger.info("Validating prompt against guardrails")
    logger.info(f"Prompt preview: {prompt[:100]}...")

    try:
        request_body = build_akto_request(prompt, cwd, timestamp, cfg)
        result = post_to_akto(
            build_http_proxy_url(cfg, guardrails=True, ingest_data=False),
            request_body,
            logger
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


def ingest_request(prompt: str, cwd: str, timestamp: int, reason: str, blocked: bool, cfg: dict, logger):
    """Ingest request data to Akto for analytics."""
    if not AKTO_DATA_INGESTION_URL:
        return

    logger.info(f"Ingesting {'blocked' if blocked else 'allowed'} request")
    try:
        request_body = build_akto_request(prompt, cwd, timestamp, cfg)
        request_body["responsePayload"] = json.dumps({})
        if blocked:
            request_body["responsePayload"] = json.dumps({
                "body": json.dumps({"x-blocked-by": "Akto Proxy", "reason": reason})
            })
            request_body["statusCode"] = "403"
            request_body["status"] = "403"

        post_to_akto(
            build_http_proxy_url(cfg, guardrails=False, ingest_data=True),
            request_body,
            logger
        )
        logger.info(f"{'Blocked' if blocked else 'Allowed'} request ingested successfully")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logging.basicConfig()
        logging.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    # Auto-detect connector from hookEventName (present in all VSCode payloads)
    connector = detect_connector(input_data)
    cfg = get_connector_config(connector)

    # Update API URL for atlas mode now that we have the connector config
    if MODE == "atlas":
        device_id = os.getenv("DEVICE_ID") or get_machine_id()
        if device_id:
            cfg["api_url"] = f"https://{device_id}.{cfg['atlas_domain']}"

    log_dir = os.path.expanduser(os.getenv("LOG_DIR", cfg["log_dir_default"]))
    logger = setup_logging(log_dir)

    logger.info(f"=== User Prompt Submitted Hook - Connector: {connector}, Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    if LOG_PAYLOADS:
        logger.debug(f"Input: {json.dumps(input_data)}")

    logger.info(f"MODE: {MODE}, API_URL: {cfg['api_url']}")

    prompt = input_data.get("prompt", "")
    cwd = input_data.get("cwd", "")
    timestamp = input_data.get("timestamp", int(time.time() * 1000))

    logger.info(f"Prompt length: {len(prompt)} chars, CWD: {cwd}")

    if not prompt.strip():
        logger.info("Empty prompt, skipping validation")
        sys.exit(0)

    if AKTO_SYNC_MODE and AKTO_DATA_INGESTION_URL:
        allowed, reason = validate_prompt(prompt, cwd, timestamp, cfg, logger)

        if not allowed:
            logger.warning(f"Prompt blocked: {reason}")
            ingest_request(prompt, cwd, timestamp, reason, blocked=True, cfg=cfg, logger=logger)
            sys.stderr.write(f"⚠️  Akto Guardrails flagged prompt: {reason}\n")
            sys.stderr.flush()
            sys.exit(cfg["blocked_exit_code"])
        else:
            ingest_request(prompt, cwd, timestamp, reason, blocked=False, cfg=cfg, logger=logger)

    logger.info("Hook completed")
    sys.exit(0)


if __name__ == "__main__":
    main()
