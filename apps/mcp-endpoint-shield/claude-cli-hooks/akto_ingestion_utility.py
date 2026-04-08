"""
Common utilities for Akto Claude CLI hooks.
Shared config, HTTP, ingestion payload building, and transcript reading.
"""
import json
import logging
import os
import ssl
import time
import urllib.request
from typing import Any, Dict, Optional, Union

from akto_machine_id import get_machine_id, get_username

# ── Config ────────────────────────────────────────────────────────────────────

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "claude_code_cli")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

if MODE == "atlas":
    _device_id = os.getenv("DEVICE_ID") or get_machine_id()
    CLAUDE_API_URL = f"https://{_device_id}.ai-agent.claudecli" if _device_id else "https://api.anthropic.com"
else:
    CLAUDE_API_URL = os.getenv("CLAUDE_API_URL", "https://api.anthropic.com")

# ── Logging helpers ───────────────────────────────────────────────────────────

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.claude/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"


def setup_logger(log_filename: str) -> logging.Logger:
    """Return a logger that writes to LOG_DIR/<log_filename> and errors to stderr."""
    os.makedirs(LOG_DIR, exist_ok=True)
    logger = logging.getLogger(log_filename)
    if logger.handlers:
        return logger  # already configured (e.g. re-import in tests)
    logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

    fh = logging.FileHandler(os.path.join(LOG_DIR, log_filename))
    fh.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    fh.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(fh)

    ch = logging.StreamHandler()
    ch.setLevel(logging.ERROR)
    logger.addHandler(ch)

    return logger


# ── HTTP ──────────────────────────────────────────────────────────────────────

def create_ssl_context() -> ssl.SSLContext:
    return ssl._create_unverified_context()


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(
    url: str,
    payload: Dict[str, Any],
    logger: logging.Logger,
) -> Union[Dict[str, Any], str]:
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers={"Content-Type": "application/json"},
        method="POST",
    )
    start_time = time.time()
    try:
        with urllib.request.urlopen(request, context=create_ssl_context(), timeout=AKTO_TIMEOUT) as response:
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


# ── Ingestion payload ─────────────────────────────────────────────────────────

def build_ingestion_payload(
    *,
    hook_name: str,
    request_payload: Any,
    response_payload: Any,
    extra_headers: Optional[Dict[str, str]] = None,
    tags: Optional[Dict[str, str]] = None,
    status_code: str = "200",
) -> Dict[str, Any]:
    """
    Build the base HTTP-proxy ingestion payload.

    Args:
        hook_name:        Value placed in x-claude-hook headers (e.g. "SubagentStop").
        request_payload:  Arbitrary dict/value serialised as requestPayload body.
        response_payload: Arbitrary dict/value serialised as responsePayload body.
        extra_headers:    Additional response headers merged alongside x-claude-hook.
        tags:             Extra key/value pairs merged into the tag/metadata dicts.
        status_code:      HTTP status code string (default "200").
    """
    base_tags: Dict[str, str] = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        base_tags["ai-agent"] = "claudecli"
        base_tags["source"] = CONTEXT_SOURCE
    if tags:
        base_tags.update(tags)

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = CLAUDE_API_URL.replace("https://", "").replace("http://", "")

    req_headers = {"host": host, "x-claude-hook": hook_name, "content-type": "application/json"}
    resp_headers: Dict[str, str] = {"x-claude-hook": hook_name, "content-type": "application/json"}
    if extra_headers:
        resp_headers.update(extra_headers)

    return {
        "path": f"/v1/hooks/{hook_name}",
        "requestHeaders": json.dumps(req_headers),
        "responseHeaders": json.dumps(resp_headers),
        "method": "POST",
        "requestPayload": json.dumps({"body": request_payload}),
        "responsePayload": json.dumps({"body": response_payload}),
        "ip": get_username(),
        "destIp": "127.0.0.1",
        "time": str(int(time.time() * 1000)),
        "statusCode": status_code,
        "type": "HTTP/1.1",
        "status": status_code,
        "akto_account_id": "1000000",
        "akto_vxlan_id": device_id,
        "is_pending": "false",
        "source": "MIRRORING",
        "direction": None,
        "process_id": None,
        "socket_id": None,
        "daemonset_id": None,
        "enabled_graph": None,
        "tag": json.dumps(base_tags),
        "metadata": json.dumps(base_tags),
        "contextSource": CONTEXT_SOURCE,
    }


def send_ingestion_data(
    *,
    hook_name: str,
    request_payload: Any,
    response_payload: Any,
    tags: Optional[Dict[str, str]] = None,
    extra_headers: Optional[Dict[str, str]] = None,
    status_code: str = "200",
    guardrails: bool = False,
    logger: logging.Logger,
) -> None:
    """Post ingestion data to the Akto HTTP proxy."""
    if not AKTO_DATA_INGESTION_URL:
        logger.info("AKTO_DATA_INGESTION_URL not set, skipping ingestion")
        return

    logger.info(f"Guardrails enabled? -> {guardrails}")

    payload = build_ingestion_payload(
        hook_name=hook_name,
        request_payload=request_payload,
        response_payload=response_payload,
        extra_headers=extra_headers,
        tags=tags,
        status_code=status_code,
    )
    logger.info(f">>>>>>>>>>>>>>>>>Ingestion payload: {json.dumps(payload)}")
    try:
        post_payload_json(
            build_http_proxy_url(guardrails=guardrails, ingest_data=True),
            payload,
            logger,
        )
        logger.info(f"Ingestion successful for hook: {hook_name}")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


# ── Transcript reading ────────────────────────────────────────────────────────

def _extract_text_from_entry(entry: Dict[str, Any]) -> str:
    content = entry.get("message", {}).get("content", "")
    if isinstance(content, str):
        return content.strip()
    if isinstance(content, list):
        parts = []
        for block in content:
            if isinstance(block, dict) and block.get("type") == "text":
                text = block.get("text", "")
                if isinstance(text, str) and text:
                    parts.append(text)
        return "".join(parts).strip()
    return ""


def get_last_user_prompt(transcript_path: str, logger: logging.Logger) -> str:
    """Return the last user message text from a JSONL transcript file."""
    if not transcript_path or not os.path.exists(transcript_path):
        return ""
    try:
        last_user = ""
        with open(transcript_path, "r") as f:
            for line in f:
                try:
                    entry = json.loads(line)
                except json.JSONDecodeError:
                    continue
                if entry.get("type") == "user":
                    text = _extract_text_from_entry(entry)
                    if text:
                        last_user = text
        return last_user
    except Exception as e:
        logger.error(f"Error reading transcript: {e}")
        return ""
