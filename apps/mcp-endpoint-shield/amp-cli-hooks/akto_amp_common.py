#!/usr/bin/env python3
"""
Shared runtime for the Akto Guardrails Amp plugin validators.

The Amp plugin (index.ts) writes one JSON object to a validator's stdin and reads
the decision back off the last stdout line:

    {"decision": "block", "reason": "..."}          -> hard block
    {"decision": "allow", "updatedInput": {...}}    -> allow with rewritten tool args
    <no output>                                     -> allow

Amp names MCP tools `mcp__<server>__<tool>` (same as Claude Code), so MCP and
built-in tool calls are told apart from the tool name alone. MCP traffic is
mirrored to /mcp as JSON-RPC `tools/call` (what Akto's MCP classifier expects);
built-in tools are mirrored to /tool/<tool-name> without a top-level jsonrpc key.
"""

import hashlib
import json
import logging
import os
import re
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, Optional, Set, Tuple, Union
from urllib.parse import quote

# Default the connector before importing the shared utility: it reads AKTO_CONNECTOR
# at import time to pick the session field map, log dir, and header names.
os.environ.setdefault("AKTO_CONNECTOR", "amp")

from akto_machine_id import get_machine_id, get_username

try:
    from akto_heartbeat import send_heartbeat
except ImportError:  # akto_heartbeat.py not copied alongside the plugin
    def send_heartbeat(log_dir: str, logger=None) -> None:
        return

try:
    from akto_ingestion_utility import installer_headers, resolve_session_info
except ImportError:  # shared/akto_ingestion_utility.py not copied alongside the plugin
    def installer_headers(session_info, input_data=None) -> Dict[str, str]:
        return {}

    def resolve_session_info(input_data, logger, *, is_prompt_hook: bool = False) -> Dict[str, Any]:
        return {}

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_API_TOKEN = os.getenv("AKTO_API_TOKEN", "")
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "amp")
AKTO_CONNECTOR_VALUE = os.getenv("AKTO_CONNECTOR_VALUE", "amp")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
# Non-MCP blocked-request ingestion is off by default, matching claude-cli-hooks.
AKTO_INGEST_NON_MCP_TOOLS = os.getenv("AKTO_INGEST_NON_MCP_TOOLS", "false").lower() == "true"

# Mirrored paths: /mcp matches JsonRpcUtils.isMcpPath; non-MCP uses /<prefix>/<tool>.
MCP_INGEST_PATH = os.getenv("MCP_INGEST_PATH", "/mcp")
NON_MCP_TOOL_PATH_PREFIX = os.getenv("NON_MCP_TOOL_PATH_PREFIX", "/tool")

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.config/amp/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

DEVICE_ID = os.getenv("DEVICE_ID") or get_machine_id()

HOOK_HEADER = f"x-{AKTO_CONNECTOR_VALUE}-hook"

if MODE == "atlas":
    AMP_API_URL = (
        f"https://{DEVICE_ID}.ai-agent.{AKTO_CONNECTOR_VALUE}"
        if DEVICE_ID
        else os.getenv("AMP_API_URL", "https://ampcode.com")
    )
else:
    AMP_API_URL = os.getenv("AMP_API_URL", "https://ampcode.com")


def setup_logger(log_filename: str) -> logging.Logger:
    """Logger writing to LOG_DIR/<log_filename>, with errors mirrored to stderr."""
    os.makedirs(LOG_DIR, exist_ok=True)

    logger = logging.getLogger(log_filename)
    logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    if logger.handlers:
        return logger

    file_handler = logging.FileHandler(os.path.join(LOG_DIR, log_filename))
    file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    logger.addHandler(console_handler)
    return logger


def create_ssl_context() -> ssl.SSLContext:
    return ssl._create_unverified_context()


def build_http_proxy_url(*, guardrails: bool = False, ingest_data: bool = False) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(
    url: str, payload: Dict[str, Any], logger: logging.Logger
) -> Union[Dict[str, Any], str]:
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

    headers = {"Content-Type": "application/json"}
    if AKTO_API_TOKEN:
        headers["Authorization"] = AKTO_API_TOKEN
    request = urllib.request.Request(
        url, data=json.dumps(payload).encode("utf-8"), headers=headers, method="POST"
    )

    start_time = time.time()
    try:
        with urllib.request.urlopen(
            request, context=create_ssl_context(), timeout=AKTO_TIMEOUT
        ) as response:
            duration_ms = int((time.time() - start_time) * 1000)
            raw = response.read().decode("utf-8")
            logger.info(
                f"API RESPONSE: Status {response.getcode()}, Duration: {duration_ms}ms, "
                f"Size: {len(raw)} bytes"
            )
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


# ── Tool naming ───────────────────────────────────────────────────────────────

def parse_amp_tool(tool_name: str) -> Tuple[bool, str, str]:
    """
    Parse an Amp tool name into (is_mcp, server_name, mcp_tool_name).
    MCP tools follow mcp__<server>__<tool>; the tool segment may contain '__'.
    """
    if not tool_name.startswith("mcp__"):
        return False, "", ""
    parts = tool_name.split("__")
    if len(parts) < 3:
        return False, "", ""
    server = parts[1]
    mcp_tool = "__".join(parts[2:])
    if not server or not mcp_tool:
        return False, "", ""
    return True, server, mcp_tool


def normalize_tool_name_for_url_path(tool_name: str) -> str:
    """RFC 3986 path segment: unreserved + hyphen; collapse repeats."""
    s = (tool_name or "unknown").strip()
    s = re.sub(r"[^a-zA-Z0-9._~-]+", "-", s)
    s = re.sub(r"-+", "-", s).strip("-")
    if not s:
        s = "unknown"
    return quote(s, safe=".-_~")


def non_mcp_ingest_path(tool_name: str) -> str:
    """NON_MCP_INGEST_PATH if set, else NON_MCP_TOOL_PATH_PREFIX + normalized tool name."""
    fixed = (os.getenv("NON_MCP_INGEST_PATH") or "").strip()
    if fixed:
        return fixed if fixed.startswith("/") else "/" + fixed
    prefix = (NON_MCP_TOOL_PATH_PREFIX or "/tool").strip()
    if not prefix.startswith("/"):
        prefix = "/" + prefix
    prefix = prefix.rstrip("/") or "/tool"
    return f"{prefix}/{normalize_tool_name_for_url_path(tool_name)}"


def _tool_arguments_for_jsonrpc(tool_input: Any) -> Dict[str, Any]:
    if isinstance(tool_input, dict):
        return tool_input
    if tool_input is None:
        return {}
    return {"input": tool_input}


def build_tools_call_jsonrpc(mcp_tool_name: str, tool_input: Any, request_id: int = 1) -> str:
    """JSON-RPC body aligned with MCP tools/call (https://modelcontextprotocol.io)."""
    return json.dumps(
        {
            "jsonrpc": "2.0",
            "method": "tools/call",
            "params": {
                "name": mcp_tool_name,
                "arguments": _tool_arguments_for_jsonrpc(tool_input),
            },
            "id": request_id,
        }
    )


def build_tools_call_result_jsonrpc(tool_response: Any, request_id: int = 1) -> str:
    """JSON-RPC response body for an MCP tools/call result."""
    if isinstance(tool_response, str):
        result: Any = {"content": [{"type": "text", "text": tool_response}]}
    elif isinstance(tool_response, dict):
        result = tool_response
    else:
        result = {"content": [{"type": "text", "text": json.dumps(tool_response)}]}
    return json.dumps({"jsonrpc": "2.0", "id": request_id, "result": result})


def mcp_mirror_host(mcp_server_name: str) -> str:
    return f"{DEVICE_ID}.{AKTO_CONNECTOR_VALUE}.{mcp_server_name}"


def api_host() -> str:
    return AMP_API_URL.replace("https://", "").replace("http://", "")


def build_hook_tags(*, is_mcp: bool) -> Dict[str, str]:
    tags: Dict[str, str] = {}
    if is_mcp:
        tags["mcp-server"] = "MCP Server"
        tags["mcp-client"] = AKTO_CONNECTOR_VALUE
    else:
        tags["gen-ai"] = "Gen AI"
        tags["ai-agent"] = AKTO_CONNECTOR_VALUE
    if MODE == "atlas":
        tags["source"] = CONTEXT_SOURCE
    return tags


# ── Mirrored payloads ─────────────────────────────────────────────────────────

def build_mirror_payload(
    *,
    path: str,
    hook_name: str,
    request_payload: str,
    response_payload: str,
    tags: Dict[str, str],
    host: str,
    extra_request_headers: Optional[Dict[str, str]] = None,
    session_info: Optional[Dict[str, Any]] = None,
    status_code: str = "200",
) -> Dict[str, Any]:
    req_hdr: Dict[str, str] = {
        "host": host,
        HOOK_HEADER: hook_name,
        "content-type": "application/json",
    }
    if extra_request_headers:
        req_hdr.update(extra_request_headers)
    if session_info:
        req_hdr.update(installer_headers(session_info))

    return {
        "path": path,
        "requestHeaders": json.dumps(req_hdr),
        "responseHeaders": json.dumps({HOOK_HEADER: hook_name, "content-type": "application/json"}),
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": get_username(),
        "destIp": "127.0.0.1",
        "time": str(int(time.time() * 1000)),
        "statusCode": status_code,
        "type": "HTTP/1.1",
        "status": status_code,
        "akto_account_id": "1000000",
        "akto_vxlan_id": DEVICE_ID,
        "is_pending": "false",
        "source": "MIRRORING",
        "direction": None,
        "process_id": None,
        "socket_id": None,
        "daemonset_id": None,
        "enabled_graph": None,
        "tag": json.dumps(tags),
        "metadata": json.dumps(tags),
        "contextSource": CONTEXT_SOURCE,
    }


def mark_blocked(payload: Dict[str, Any], reason: str, *, is_mcp: bool) -> Dict[str, Any]:
    """Turn a mirrored request into its blocked (403) counterpart for the audit trail."""
    payload["responseHeaders"] = json.dumps(
        {
            HOOK_HEADER: json.loads(payload["responseHeaders"]).get(HOOK_HEADER, ""),
            "x-blocked-by": "Akto Proxy",
            "content-type": "application/json",
        }
    )
    if is_mcp:
        payload["responsePayload"] = json.dumps(
            {
                "jsonrpc": "2.0",
                "error": {"code": -32000, "message": f"Blocked: {reason or 'Policy violation'}"},
            }
        )
    else:
        payload["responsePayload"] = json.dumps(
            {"body": {"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"}}
        )
    payload["statusCode"] = "403"
    payload["status"] = "403"
    return payload


# ── Guardrails ────────────────────────────────────────────────────────────────

class GuardrailsVerdict:
    def __init__(
        self,
        allowed: bool = True,
        reason: str = "",
        behaviour: str = "",
        modified: bool = False,
        modified_payload: Any = "",
    ):
        self.allowed = allowed
        self.reason = reason
        self.behaviour = behaviour
        self.modified = modified
        self.modified_payload = modified_payload


def parse_guardrails_verdict(result: Any) -> GuardrailsVerdict:
    data = result.get("data", {}) if isinstance(result, dict) else {}
    gr = data.get("guardrailsResult", {}) if isinstance(data, dict) else {}
    return GuardrailsVerdict(
        allowed=gr.get("Allowed", True),
        reason=gr.get("Reason", ""),
        behaviour=gr.get("behaviour", "") or gr.get("Behaviour", ""),
        modified=gr.get("Modified", False),
        modified_payload=gr.get("ModifiedPayload", ""),
    )


def call_guardrails(
    payload: Dict[str, Any], logger: logging.Logger, *, guardrails: bool = True
) -> GuardrailsVerdict:
    """Post to Akto with guardrails on. Fails OPEN on any transport error."""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing (fail-open)")
        return GuardrailsVerdict()
    try:
        result = post_payload_json(
            build_http_proxy_url(guardrails=guardrails, ingest_data=True), payload, logger
        )
        verdict = parse_guardrails_verdict(result)
        logger.info("Guardrails verdict: %s", "ALLOWED" if verdict.allowed else f"DENIED ({verdict.reason})")
        return verdict
    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return GuardrailsVerdict()


def ingest(payload: Dict[str, Any], logger: logging.Logger) -> None:
    """Fire ingestion without guardrails. Never raises."""
    if not AKTO_DATA_INGESTION_URL:
        logger.info("AKTO_DATA_INGESTION_URL not set, skipping ingestion")
        return
    try:
        post_payload_json(build_http_proxy_url(guardrails=False, ingest_data=True), payload, logger)
        logger.info("Ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


# ── Warn / alert behaviour ────────────────────────────────────────────────────

def _behaviour(value: Any) -> str:
    return str(value or "").strip().lower()


def is_warn_behaviour(value: Any) -> bool:
    return _behaviour(value) == "warn"


def is_alert_behaviour(value: Any) -> bool:
    return _behaviour(value) == "alert"


def fingerprint(*parts: Any) -> str:
    canonical = json.dumps(parts, sort_keys=True, ensure_ascii=False, default=str)
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _warn_state_path(kind: str) -> str:
    return os.path.join(LOG_DIR, f"akto_{kind}_warn_pending.json")


def load_warn_pending(kind: str, logger: logging.Logger) -> Set[str]:
    path = _warn_state_path(kind)
    if not os.path.exists(path):
        return set()
    try:
        with open(path, encoding="utf-8") as f:
            return set(json.load(f).get("warn_pending", []))
    except (json.JSONDecodeError, OSError) as e:
        logger.warning(f"Could not read warn-pending map: {e}")
        return set()


def save_warn_pending(kind: str, hashes: Set[str], logger: logging.Logger) -> None:
    path = _warn_state_path(kind)
    tmp_path = path + ".tmp"
    try:
        os.makedirs(LOG_DIR, exist_ok=True)
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump({"warn_pending": sorted(hashes)}, f, indent=0)
            f.write("\n")
        os.replace(tmp_path, path)
    except OSError as e:
        logger.error(f"Could not persist warn-pending map: {e}")
        if os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
            except OSError:
                pass


def apply_warn_resubmit_flow(
    verdict: GuardrailsVerdict, kind: str, print_hash: str, logger: logging.Logger
) -> bool:
    """
    Resolve a denial into a final allow/block.
      alert -> allow (server-side alert only)
      warn  -> block the first time, allow the identical retry
      block -> block
    """
    if verdict.allowed:
        return True

    if is_alert_behaviour(verdict.behaviour):
        logger.info("Alert behaviour: allowing despite violation (server-side alert only)")
        return True

    if not is_warn_behaviour(verdict.behaviour):
        return False

    pending = load_warn_pending(kind, logger)
    if print_hash in pending:
        pending.discard(print_hash)
        save_warn_pending(kind, pending, logger)
        logger.info("Warn flow: allowing resubmit; removed fingerprint from map")
        return True

    pending.add(print_hash)
    save_warn_pending(kind, pending, logger)
    return False


def block_reason_text(verdict: GuardrailsVerdict, subject: str) -> str:
    if is_warn_behaviour(verdict.behaviour):
        return (
            f"Warning!!, {subject} blocked, please review it. Send again to bypass. "
            f"Reason for blocking: {verdict.reason}"
        )
    return f"{subject.capitalize()} blocked: {verdict.reason or 'Policy violation'}"


# ── Plugin I/O ────────────────────────────────────────────────────────────────

def heartbeat(logger: logging.Logger) -> None:
    """Register this device with Akto. Rate-limited to once per 30s inside the
    publisher, and it swallows its own errors — without a heartbeat record
    mini-runtime cannot resolve the device to a user and drops events before
    indexing, so traces and LLM observability stay empty."""
    send_heartbeat(LOG_DIR, logger)


def read_input(logger: logging.Logger) -> Dict[str, Any]:
    """Read the plugin's JSON event from stdin. Returns {} on malformed input."""
    try:
        data = json.load(sys.stdin)
        return data if isinstance(data, dict) else {}
    except (json.JSONDecodeError, ValueError) as e:
        logger.error(f"Invalid JSON input: {e}")
        return {}


def emit_block(reason: str, logger: logging.Logger) -> None:
    logger.warning(f"BLOCKING: {reason}")
    print(json.dumps({"decision": "block", "reason": reason}), flush=True)


def emit_allow(updated_input: Any = None) -> None:
    if updated_input is not None:
        print(json.dumps({"decision": "allow", "updatedInput": updated_input}), flush=True)


def extract_tool_input_from_modified_payload(
    modified_payload: Any, *, is_mcp: bool, fallback: Any, logger: logging.Logger
) -> Any:
    """
    Akto returns the rewritten mirrored body in ModifiedPayload.
    Non-MCP: {"body": <tool arguments>, "toolName": "..."}.
    MCP: JSON-RPC tools/call with params.arguments.
    """
    if modified_payload is None:
        return fallback
    if isinstance(modified_payload, str) and not modified_payload.strip():
        return fallback

    if isinstance(modified_payload, dict):
        parsed = modified_payload
    else:
        try:
            parsed = json.loads(modified_payload)
        except (json.JSONDecodeError, TypeError):
            logger.warning("ModifiedPayload is not valid JSON; keeping original tool_input")
            return fallback

    if not isinstance(parsed, dict):
        return fallback

    if is_mcp:
        params = parsed.get("params")
        if isinstance(params, dict) and isinstance(params.get("arguments"), dict):
            return params["arguments"]
        logger.warning("MCP ModifiedPayload missing params.arguments dict; keeping original tool_input")
        return fallback

    body = parsed.get("body")
    if isinstance(body, dict):
        return body
    logger.warning("Non-MCP ModifiedPayload missing body dict; keeping original tool_input")
    return fallback
