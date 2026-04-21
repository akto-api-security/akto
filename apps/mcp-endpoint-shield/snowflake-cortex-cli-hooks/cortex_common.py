#!/usr/bin/env python3
"""
Shared helpers for Akto Snowflake Cortex Code CLI hooks.
"""

import json
import logging
import os
import ssl
import sys
import tempfile
import time
import urllib.request
from typing import Any, Dict, Optional, Tuple, Union

from akto_machine_id import get_machine_id, get_username

DEFAULT_LOG_DIR = "~/.snowflake/cortex/akto/logs"
DEFAULT_CONNECTOR = "cortex_code_cli"
SYNTHETIC_ATLAS_SUFFIX = "ai-agent.cortex"
AI_AGENT_TAG = "cortexcli"
HOOK_HEADER = "x-cortex-hook"


def get_mode() -> str:
    return os.getenv("MODE", "argus").lower()


def get_akto_connector() -> str:
    return os.getenv("AKTO_CONNECTOR", DEFAULT_CONNECTOR)


def get_context_source() -> str:
    return os.getenv("CONTEXT_SOURCE", "ENDPOINT")


def get_log_dir() -> str:
    return os.path.expanduser(os.getenv("LOG_DIR", DEFAULT_LOG_DIR))


def get_effective_log_dir() -> str:
    """Preferred log directory, falling back to temp when the configured path cannot be created."""
    log_dir = get_log_dir()
    try:
        os.makedirs(log_dir, exist_ok=True)
        return log_dir
    except OSError:
        fallback = os.path.join(tempfile.gettempdir(), "akto-cortex-cli-hooks-logs")
        os.makedirs(fallback, exist_ok=True)
        return fallback


def get_akto_url() -> Optional[str]:
    v = (os.getenv("AKTO_DATA_INGESTION_URL") or "").strip().rstrip("/")
    if not v or "{{" in v:
        return None
    return v


def get_ingestion_authorization_value() -> Optional[str]:
    """Value for Authorization header on POSTs to ingestion; supports AKTO_API_TOKEN (Copilot-style) or AKTO_TOKEN (Claude-style)."""
    t = (os.getenv("AKTO_API_TOKEN") or os.getenv("AKTO_TOKEN") or "").strip()
    if not t or "{{" in t:
        return None
    return t


def get_akto_timeout() -> float:
    return float(os.getenv("AKTO_TIMEOUT", "5"))


def is_sync_mode() -> bool:
    return os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"


def get_synthetic_api_url() -> str:
    mode = get_mode()
    if mode == "atlas":
        device_id = os.getenv("DEVICE_ID") or get_machine_id()
        if device_id:
            return f"https://{device_id}.{SYNTHETIC_ATLAS_SUFFIX}"
    return os.getenv("CORTEX_SYNTHETIC_API_URL", "https://cortex.snowflakecomputing.com")


def synthetic_host() -> str:
    return get_synthetic_api_url().replace("https://", "").replace("http://", "")


def create_ssl_context():
    return ssl._create_unverified_context()


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool, response_guardrails: bool = False) -> str:
    base = get_akto_url()
    if not base:
        return ""
    params = [f"akto_connector={get_akto_connector()}"]
    if guardrails:
        params.append("guardrails=true")
    if response_guardrails:
        params.append("response_guardrails=true")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{base}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(url: str, payload: Dict[str, Any], logger: logging.Logger) -> Union[Dict[str, Any], str]:
    if not url:
        raise ValueError("empty proxy url")
    logger.info("API CALL: POST %s", url.split("?", 1)[0])
    headers: Dict[str, str] = {"Content-Type": "application/json"}
    auth = get_ingestion_authorization_value()
    if auth:
        headers["Authorization"] = auth
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )
    start = time.time()
    ssl_context = create_ssl_context()
    with urllib.request.urlopen(request, context=ssl_context, timeout=get_akto_timeout()) as response:
        raw = response.read().decode("utf-8")
        logger.info("API RESPONSE: %s in %sms", response.getcode(), int((time.time() - start) * 1000))
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return raw


def base_tags(extra: Optional[Dict[str, str]] = None) -> Dict[str, str]:
    tags: Dict[str, str] = {"gen-ai": "Gen AI"}
    if get_mode() == "atlas":
        tags["ai-agent"] = AI_AGENT_TAG
        tags["source"] = get_context_source()
    if extra:
        tags.update(extra)
    return tags


def build_proxy_payload(
    *,
    hook_event: str,
    path: str,
    request_payload_obj: Any,
    response_payload_obj: Any,
    method: str = "POST",
    status_code: str = "200",
    status: str = "200",
    extra_tags: Optional[Dict[str, str]] = None,
) -> Dict[str, Any]:
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = synthetic_host()
    tags = base_tags(extra_tags)
    request_headers = json.dumps(
        {
            "host": host,
            HOOK_HEADER: hook_event,
            "content-type": "application/json",
        }
    )
    response_headers = json.dumps({HOOK_HEADER: hook_event, "content-type": "application/json"})
    if isinstance(request_payload_obj, str):
        req_body = request_payload_obj
    else:
        req_body = json.dumps({"body": request_payload_obj})
    if isinstance(response_payload_obj, str):
        res_body = response_payload_obj
    else:
        res_body = json.dumps({"body": response_payload_obj})

    return {
        "path": path,
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": method,
        "requestPayload": req_body,
        "responsePayload": res_body,
        "ip": get_username(),
        "destIp": "127.0.0.1",
        "time": str(int(time.time() * 1000)),
        "statusCode": status_code,
        "type": "HTTP/1.1",
        "status": status,
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
        "contextSource": get_context_source(),
    }


def configure_logger(log_filename: str) -> logging.Logger:
    log_dir = get_effective_log_dir()
    level_name = os.getenv("LOG_LEVEL", "INFO").upper()
    level = getattr(logging, level_name, logging.INFO)
    logger = logging.getLogger(log_filename)
    logger.setLevel(level)
    logger.handlers.clear()
    fh = logging.FileHandler(os.path.join(log_dir, log_filename))
    fh.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    fh.setLevel(level)
    logger.addHandler(fh)
    ch = logging.StreamHandler(sys.stderr)
    ch.setLevel(logging.ERROR)
    logger.addHandler(ch)
    return logger


def parse_guardrails_allowed(result: Union[Dict[str, Any], str, None]) -> Tuple[bool, str, str]:
    if not isinstance(result, dict):
        return True, "", ""
    data = result.get("data")
    if not isinstance(data, dict):
        data = result
    if not isinstance(data, dict):
        return True, "", ""
    gr = data.get("guardrailsResult", {})
    if not isinstance(gr, dict):
        return True, "", ""
    allowed = gr.get("Allowed", True)
    if isinstance(allowed, str):
        allowed = allowed.lower() in ("true", "1", "yes")
    reason = str(gr.get("Reason", "") or "")
    behaviour = str(gr.get("behaviour", "") or gr.get("Behaviour", "") or "")
    return bool(allowed), reason, behaviour


def extract_user_prompt(data: Dict[str, Any]) -> str:
    for key in ("prompt", "user_prompt", "userPrompt", "text", "message", "content", "input"):
        v = data.get(key)
        if isinstance(v, str) and v.strip():
            return v
    for nested_key in ("submission", "input", "user_input", "userInput"):
        sub = data.get(nested_key)
        if isinstance(sub, dict):
            inner = extract_user_prompt(sub)
            if inner:
                return inner
        elif isinstance(sub, str) and sub.strip():
            return sub
    return ""


def extract_mcp_server_name(tool_name: str) -> str:
    if not tool_name.startswith("mcp__"):
        return "cortex-built-in"
    parts = tool_name.split("__")
    if len(parts) >= 3 and parts[1]:
        return parts[1]
    return "cortex-built-in"
