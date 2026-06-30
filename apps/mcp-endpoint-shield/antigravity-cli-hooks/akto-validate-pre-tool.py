#!/usr/bin/env python3

import hashlib
import json
import logging
import os
import re
import ssl
import sys
import time
import urllib.request
from urllib.parse import quote
from typing import Any, Dict, Optional, Set, Tuple, Union

from akto_machine_id import get_machine_id, get_username

from akto_ingestion_utility import installer_headers, resolve_session_info

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.gemini/antigravity/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_pretool_warn_pending.json")
STEP_CACHE_PATH = os.path.join(LOG_DIR, "akto_step_cache.json")

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-pre-tool.log"))
    file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    logger.addHandler(console_handler)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_INGEST_NON_MCP_TOOLS = os.getenv("AKTO_INGEST_NON_MCP_TOOLS", "false").lower() == "true"
AKTO_CONNECTOR = "antigravity_cli"
AKTO_CONNECTOR_VALUE = os.getenv("AKTO_CONNECTOR_VALUE", "antigravitycli")
AKTO_API_TOKEN = os.getenv("AKTO_API_TOKEN", "")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
MCP_INGEST_PATH = os.getenv("MCP_INGEST_PATH", "/mcp")
NON_MCP_TOOL_PATH_PREFIX = os.getenv("NON_MCP_TOOL_PATH_PREFIX", "/tool")

SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"
DEVICE_ID = os.getenv("DEVICE_ID") or get_machine_id()

ANTIGRAVITY_API_URL = os.getenv("ANTIGRAVITY_API_URL", "https://generativelanguage.googleapis.com")
if MODE == "atlas":
    ANTIGRAVITY_API_URL = (
        f"https://{DEVICE_ID}.ai-agent.{AKTO_CONNECTOR_VALUE}" if DEVICE_ID else ANTIGRAVITY_API_URL
    )
    logger.info(f"MODE: {MODE}, Device ID: {DEVICE_ID}, ANTIGRAVITY_API_URL: {ANTIGRAVITY_API_URL}")
else:
    logger.info(f"MODE: {MODE}, ANTIGRAVITY_API_URL: {ANTIGRAVITY_API_URL}")


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


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

    headers = {"Content-Type": "application/json"}
    if AKTO_API_TOKEN:
        headers["Authorization"] = AKTO_API_TOKEN
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
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


def normalize_tool_name_for_url_path(tool_name: str) -> str:
    s = (tool_name or "unknown").strip()
    s = re.sub(r"[^a-zA-Z0-9._~-]+", "-", s)
    s = re.sub(r"-+", "-", s).strip("-")
    if not s:
        s = "unknown"
    return quote(s, safe=".-_~")


def non_mcp_ingest_path(tool_name: str) -> str:
    fixed = (os.getenv("NON_MCP_INGEST_PATH") or "").strip()
    if fixed:
        return fixed if fixed.startswith("/") else "/" + fixed
    prefix = (NON_MCP_TOOL_PATH_PREFIX or "/tool").strip()
    if not prefix.startswith("/"):
        prefix = "/" + prefix
    prefix = prefix.rstrip("/")
    if not prefix:
        prefix = "/tool"
    return f"{prefix}/{normalize_tool_name_for_url_path(tool_name)}"


def parse_antigravity_tool(tool_name: str, tool_args: Any) -> Tuple[bool, str, str, Any]:
    """
    Detect Antigravity CLI MCP calls.

    Antigravity CLI routes all MCP calls through a single dispatcher:
      toolCall.name = "call_mcp_tool"
      toolCall.args = {"ServerName": "...", "ToolName": "...", "Arguments": {...}}

    Returns (is_mcp, server_name, mcp_tool_name, mcp_arguments).
    """
    logger.info(f"Raw toolCall.name received: {tool_name!r}")
    if tool_name == "call_mcp_tool" and isinstance(tool_args, dict):
        server = str(tool_args.get("ServerName") or "")
        mcp_tool = str(tool_args.get("ToolName") or "")
        mcp_args = tool_args.get("Arguments") or {}
        return True, server, mcp_tool, mcp_args
    return False, "", "", tool_args


def _tool_arguments_for_jsonrpc(tool_input: Any) -> Dict[str, Any]:
    if isinstance(tool_input, dict):
        return tool_input
    if tool_input is None:
        return {}
    return {"input": tool_input}


def build_tools_call_jsonrpc(mcp_tool_name: str, tool_input: Any, request_id: int = 1) -> str:
    return json.dumps({
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {"name": mcp_tool_name, "arguments": _tool_arguments_for_jsonrpc(tool_input)},
        "id": request_id,
    })


def mcp_mirror_host(mcp_server_name: str) -> str:
    return f"{DEVICE_ID}.{AKTO_CONNECTOR_VALUE}.{mcp_server_name}"


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


def build_validation_request(
    tool_name: str,
    tool_input: Any,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    mcp_arguments: Any = None,
    session_info: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    tags = build_hook_tags(is_mcp=is_mcp)

    if is_mcp:
        host = mcp_mirror_host(mcp_server_name)
    else:
        host = ANTIGRAVITY_API_URL.replace("https://", "").replace("http://", "")

    req_hdr: Dict[str, str] = {
        "host": host,
        "x-antigravitycli-hook": "PreToolUse",
        "content-type": "application/json",
    }
    if is_mcp and mcp_server_name:
        req_hdr["x-mcp-server"] = mcp_server_name
    if session_info:
        req_hdr.update(installer_headers(session_info))

    request_headers = json.dumps(req_hdr)
    response_headers = json.dumps({"x-antigravitycli-hook": "PreToolUse"})
    if is_mcp:
        request_payload = build_tools_call_jsonrpc(
            mcp_tool_name, mcp_arguments if mcp_arguments is not None else tool_input
        )
    else:
        request_payload = json.dumps({"body": tool_input, "toolName": tool_name})
    response_payload = json.dumps({})

    path = MCP_INGEST_PATH if is_mcp else non_mcp_ingest_path(tool_name)
    return {
        "path": path,
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": get_username(),
        "destIp": "127.0.0.1",
        "time": str(int(time.time() * 1000)),
        "statusCode": "200",
        "type": "HTTP/1.1",
        "status": "200",
        "akto_account_id": "1000000",
        "akto_vxlan_id": 0,
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


# ---------- Warn / alert behaviour ----------

def _guardrails_behaviour_value(behaviour: Any) -> str:
    return str(behaviour or "").strip().lower()


def _is_warn_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "warn"


def _is_alert_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "alert"


def call_guardrails(
    tool_name: str,
    tool_input: Any,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    mcp_arguments: Any = None,
    session_info: Optional[Dict[str, Any]] = None,
) -> Tuple[bool, str, str]:
    if not tool_input:
        return True, "", ""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing request (fail-open)")
        return True, "", ""

    if is_mcp:
        logger.info(f"Validating MCP tools/call: {mcp_tool_name} (server={mcp_server_name})")
    else:
        logger.info(f"Validating built-in Antigravity tool: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {json.dumps(tool_input)[:500]}...")

    try:
        request_body = build_validation_request(
            tool_name, tool_input,
            is_mcp=is_mcp, mcp_server_name=mcp_server_name, mcp_tool_name=mcp_tool_name,
            mcp_arguments=mcp_arguments, session_info=session_info,
        )
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=True),
            request_body,
        )
        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")
        behaviour = guardrails_result.get("behaviour", "") or guardrails_result.get("Behaviour", "")
        if allowed:
            logger.info(f"Request ALLOWED for {tool_name}")
        else:
            logger.warning(f"Request DENIED for {tool_name}: {reason}")
        return allowed, reason, behaviour
    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, "", ""


def pretool_fingerprint(tool_name: str, tool_input: Any) -> str:
    canonical = json.dumps({"t": tool_name, "i": tool_input}, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def load_warn_pending() -> Set[str]:
    if not os.path.exists(WARN_STATE_PATH):
        return set()
    try:
        with open(WARN_STATE_PATH, encoding="utf-8") as f:
            data = json.load(f)
        return set(data.get("warn_pending", []))
    except (json.JSONDecodeError, OSError) as e:
        logger.warning(f"Could not read warn-pending map: {e}")
        return set()


def save_warn_pending(hashes: Set[str]) -> None:
    tmp_path = WARN_STATE_PATH + ".tmp"
    try:
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump({"warn_pending": sorted(hashes)}, f, indent=0)
            f.write("\n")
        os.replace(tmp_path, WARN_STATE_PATH)
    except OSError as e:
        logger.error(f"Could not persist warn-pending map: {e}")
        if os.path.exists(tmp_path):
            try:
                os.remove(tmp_path)
            except OSError:
                pass


def apply_warn_resubmit_flow(
    gr_allowed: bool,
    reason: str,
    behaviour: str,
    fingerprint: str,
) -> Tuple[bool, str]:
    if gr_allowed:
        return True, ""
    if _is_alert_behaviour(behaviour):
        logger.info("Alert behaviour: allowing despite violation (server-side alert only)")
        return True, ""
    if not _is_warn_behaviour(behaviour):
        return False, reason
    pending = load_warn_pending()
    if fingerprint in pending:
        pending.discard(fingerprint)
        save_warn_pending(pending)
        logger.info("Warn flow: allowing resubmit; removed fingerprint from map")
        return True, ""
    pending.add(fingerprint)
    save_warn_pending(pending)
    logger.info("Warn flow: first occurrence — blocked, resend same tool call to bypass")
    return False, reason


def ingest_blocked_request(
    tool_name: str,
    tool_input: Any,
    reason: str,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    mcp_arguments: Any = None,
    session_info: Optional[Dict[str, Any]] = None,
) -> None:
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return
    if not is_mcp and not AKTO_INGEST_NON_MCP_TOOLS:
        logger.info("Skipping non-MCP blocked-request ingestion (set AKTO_INGEST_NON_MCP_TOOLS=true)")
        return

    logger.info("Ingesting blocked request data")
    try:
        request_body = build_validation_request(
            tool_name, tool_input,
            is_mcp=is_mcp, mcp_server_name=mcp_server_name, mcp_tool_name=mcp_tool_name,
            mcp_arguments=mcp_arguments, session_info=session_info,
        )
        request_body["responseHeaders"] = json.dumps({
            "x-antigravitycli-hook": "PreToolUse",
            "x-blocked-by": "Akto Proxy",
            "content-type": "application/json",
        })
        request_body["responsePayload"] = json.dumps(
            {"body": {"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"}}
        )
        request_body["statusCode"] = "403"
        request_body["status"] = "403"
        post_payload_json(build_http_proxy_url(guardrails=False, ingest_data=True), request_body)
        logger.info("Blocked request ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


# ---------- Step cache (PreToolUse writes; PostToolUse reads) ----------

def write_step_cache(
    conversation_id: str,
    step_idx: int,
    tool_name: str,
    tool_args: Any,
    *,
    is_mcp: bool = False,
    mcp_server_name: str = "",
    mcp_tool_name: str = "",
    mcp_arguments: Any = None,
) -> None:
    cache_key = f"{conversation_id}_{step_idx}"
    try:
        data: Dict[str, Any] = {}
        if os.path.exists(STEP_CACHE_PATH):
            try:
                with open(STEP_CACHE_PATH, "r", encoding="utf-8") as f:
                    data = json.load(f)
            except (json.JSONDecodeError, OSError):
                data = {}
        data[cache_key] = {
            "tool_name": tool_name,
            "tool_args": tool_args,
            "is_mcp": is_mcp,
            "mcp_server_name": mcp_server_name,
            "mcp_tool_name": mcp_tool_name,
            "mcp_arguments": mcp_arguments,
        }
        tmp = STEP_CACHE_PATH + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f)
            f.write("\n")
        os.replace(tmp, STEP_CACHE_PATH)
        logger.debug(f"Step cache written: {cache_key}")
    except OSError as e:
        logger.error(f"Could not write step cache: {e}")


def main():
    stdin_enc_before = getattr(sys.stdin, "encoding", "unknown")
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    logger.info(f"=== PreToolUse Hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")
    logger.info(f"Platform: {sys.platform}, Python: {sys.version.split()[0]}, stdin: {stdin_enc_before}")
    logger.info(f"AKTO_DATA_INGESTION_URL set: {bool(AKTO_DATA_INGESTION_URL)}, DEVICE_ID: {DEVICE_ID or '(auto)'}")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.stdout.write(json.dumps({}))
        sys.exit(0)

    # Log full toolCall for MCP format investigation
    tool_call = input_data.get("toolCall") or {}
    logger.info(f"Full toolCall received: {json.dumps(tool_call)}")

    tool_name = str(tool_call.get("name") or "")
    tool_args = tool_call.get("args") or {}
    step_idx = int(input_data.get("stepIdx") or 0)
    conversation_id = str(input_data.get("conversationId") or "")
    transcript_path = str(input_data.get("transcriptPath") or "")

    logger.info(f"PreToolUse: tool={tool_name!r}, stepIdx={step_idx}, conversationId={conversation_id!r}")

    # Inject hook_event_name so SESSION_FIELD_MAP extra_fields picks it up
    input_data["hook_event_name"] = "PreToolUse"
    # Normalize conversationId for session tracking (shared utility uses field name from map)
    session_info: Optional[Dict[str, Any]] = None
    try:
        session_info = resolve_session_info(input_data, logger, is_prompt_hook=False)
    except Exception as e:
        logger.warning(f"resolve_session_info failed: {e}")

    is_mcp, mcp_server_name, mcp_tool_name, mcp_arguments = parse_antigravity_tool(tool_name, tool_args)

    # Cache tool call details for PostToolUse (which doesn't receive toolCall in its input)
    write_step_cache(
        conversation_id, step_idx, tool_name, tool_args,
        is_mcp=is_mcp, mcp_server_name=mcp_server_name,
        mcp_tool_name=mcp_tool_name, mcp_arguments=mcp_arguments,
    )

    if is_mcp:
        logger.info(f"MCP tool: server={mcp_server_name!r}, tool={mcp_tool_name!r}")
    else:
        logger.info(f"Built-in Antigravity tool: {tool_name!r}")

    if AKTO_SYNC_MODE:
        gr_allowed, gr_reason, behaviour = call_guardrails(
            tool_name, tool_args,
            is_mcp=is_mcp, mcp_server_name=mcp_server_name, mcp_tool_name=mcp_tool_name,
            mcp_arguments=mcp_arguments, session_info=session_info,
        )
        fingerprint = pretool_fingerprint(tool_name, tool_args)
        allowed, _ = apply_warn_resubmit_flow(gr_allowed, gr_reason, behaviour, fingerprint)

        if not allowed:
            if _is_warn_behaviour(behaviour):
                block_reason = (
                    "Warning!! Tool request blocked, please review it. "
                    f"Send again to bypass. Reason: {gr_reason}"
                )
            else:
                block_reason = f"Tool request blocked by Akto: {gr_reason}"

            output = {"decision": "deny", "reason": block_reason}
            logger.warning(f"BLOCKING tool request - Tool: {tool_name!r}, Reason: {block_reason}")
            sys.stdout.write(json.dumps(output))
            ingest_blocked_request(
                tool_name, tool_args, gr_reason,
                is_mcp=is_mcp, mcp_server_name=mcp_server_name, mcp_tool_name=mcp_tool_name,
                mcp_arguments=mcp_arguments, session_info=session_info,
            )
            sys.exit(0)

    logger.info(f"Tool request allowed for {tool_name!r}")
    sys.stdout.write(json.dumps({"decision": "allow"}))
    sys.exit(0)


if __name__ == "__main__":
    main()
