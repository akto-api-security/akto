"""
Common utilities for Akto AI agent hooks (Claude CLI, Cursor, and others).
Shared config, HTTP, ingestion payload building, transcript reading, and hook runners.
"""
import json
import logging
import os
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, Optional, Union

from akto_machine_id import get_machine_id, get_username

# ── Config ────────────────────────────────────────────────────────────────────

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

# Map AKTO_CONNECTOR to a short tag name used in headers, ai-agent tag, and atlas URL suffix.
# Add new connectors here without touching anything else.
_CONNECTOR_TAG: Dict[str, str] = {
    "claude_code_cli": "claudecli",
    "cursor": "cursor",
    "vscode": "vscode",
    "gemini": "gemini",
    "github": "github",
}
TAG_NAME = _CONNECTOR_TAG.get(AKTO_CONNECTOR, AKTO_CONNECTOR)

_HOOK_HEADER = f"x-{TAG_NAME}-hook"

if MODE == "atlas":
    _device_id = os.getenv("DEVICE_ID") or get_machine_id()
    AI_AGENT_API_URL = f"https://{_device_id}.ai-agent.{TAG_NAME}" if _device_id else os.getenv("AKTO_API_URL", "")
else:
    AI_AGENT_API_URL = os.getenv("AKTO_API_URL", "")

# ── Logging helpers ───────────────────────────────────────────────────────────

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", f"~/akto/{AKTO_CONNECTOR}-hooks/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"


def setup_logger(log_filename: str) -> logging.Logger:
    """Return a logger that writes to LOG_DIR/<log_filename> and errors to stderr."""
    os.makedirs(LOG_DIR, exist_ok=True)
    logger = logging.getLogger(log_filename)
    if logger.handlers:
        return logger
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


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool, client_hook: str = "") -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    if client_hook:
        params.append(f"client_hook={client_hook}")
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
    base_tags: Dict[str, str] = {"gen-ai": "Gen AI", "hook": hook_name}
    if MODE == "atlas":
        base_tags["ai-agent"] = TAG_NAME
        base_tags["source"] = CONTEXT_SOURCE
    if tags:
        base_tags.update(tags)

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = AI_AGENT_API_URL.replace("https://", "").replace("http://", "")

    req_headers = {"host": host, _HOOK_HEADER: hook_name, "content-type": "application/json"}
    resp_headers: Dict[str, str] = {_HOOK_HEADER: hook_name, "content-type": "application/json"}
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
) -> Optional[Union[Dict[str, Any], str]]:
    """Post ingestion data to the Akto HTTP proxy. Returns the API response dict, or None on failure."""
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
        result = post_payload_json(
            build_http_proxy_url(guardrails=guardrails, ingest_data=True, client_hook=hook_name),
            payload,
            logger,
        )
        logger.info(f"Ingestion successful for hook: {hook_name}")
        return result
    except Exception as e:
        logger.error(f"Ingestion error: {e}")
        return None


# ── Hook runners ──────────────────────────────────────────────────────────────

def run_observability_hook(hook_name: str, log_file: str) -> None:
    """Run a fire-and-forget observability hook: ingest input_data and exit."""
    logger = setup_logger(log_file)
    logger.info(f"=== {hook_name} hook started ===")
    try:
        input_data = json.load(sys.stdin)
        logger.info(f"{hook_name} input:\n%s", json.dumps(input_data, indent=2))
        send_ingestion_data(
            hook_name=hook_name,
            request_payload=input_data,
            response_payload={},
            guardrails=AKTO_SYNC_MODE,
            logger=logger,
        )
        logger.info(f"=== {hook_name} hook completed ===")
    except Exception as e:
        logger.error(f"Main error: {e}")
    print(json.dumps({}))
    sys.exit(0)


def run_blocking_hook(hook_name: str, log_file: str) -> None:
    """Run a blocking hook: ingest input_data with guardrails and deny if not allowed."""
    logger = setup_logger(log_file)
    logger.info(f"=== {hook_name} hook started ===")
    try:
        input_data = json.load(sys.stdin)
        logger.info(f"{hook_name} input:\n%s", json.dumps(input_data, indent=2))
        result = send_ingestion_data(
            hook_name=hook_name,
            request_payload=input_data,
            response_payload={},
            guardrails=AKTO_SYNC_MODE,
            logger=logger,
        )
        allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
        if not allowed:
            reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
            logger.warning(f"BLOCKING {hook_name}: {reason}")
            print(json.dumps({
                "permission": "deny",
                "user_message": f"{hook_name} blocked by Akto: {reason}",
                "agent_message": f"Blocked by Akto Guardrails: {reason}",
            }))
            send_ingestion_data(
                hook_name=hook_name,
                request_payload=input_data,
                response_payload={"reason": reason, "blockedBy": "Akto Proxy"},
                guardrails=False,
                status_code="403",
                logger=logger,
            )
            logger.info(f"=== {hook_name} hook completed ===")
            sys.exit(0)
    except Exception as e:
        logger.error(f"Main error: {e}")
    print(json.dumps({"permission": "allow"}))
    sys.exit(0)


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
        with open(transcript_path, "r") as f:
            lines = f.readlines()
        for line in reversed(lines):
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            if entry.get("type") == "user":
                text = _extract_text_from_entry(entry)
                if text:
                    return text
        return ""
    except Exception as e:
        logger.error(f"Error reading transcript: {e}")
        return ""

def get_latest_message_for_cursor(transcript_path: str, role: str, logger: logging.Logger) -> str:
    """Return the latest message text for the given role from a cursor JSONL transcript file.

    Cursor transcript entries use {"role": "<role>", "message": {"content": ...}}
    Reads lines in reverse so the first match is the latest.
    """
    if not transcript_path or not os.path.exists(transcript_path):
        return ""
    try:
        with open(transcript_path, "r") as f:
            lines = f.readlines()
        for line in reversed(lines):
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            if entry.get("role", "") == role:
                text = _extract_text_from_entry(entry)
                if text:
                    return text
        return ""
    except Exception as e:
        logger.error(f"Error reading transcript: {e}")
        return ""


def read_file_content(file_path: str, logger: logging.Logger) -> str:
    """Read and return the full text content of a file. Returns empty string on failure."""
    if not file_path or not os.path.exists(file_path):
        return ""
    try:
        with open(os.path.expanduser(file_path), "r", encoding="utf-8", errors="replace") as f:
            return f.read()
    except Exception as e:
        logger.error(f"Error reading file {file_path}: {e}")
        return ""
