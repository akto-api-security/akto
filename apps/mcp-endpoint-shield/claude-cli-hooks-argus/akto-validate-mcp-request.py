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
from typing import Any, Dict, Set, Tuple, Union

from akto_helpers import get_device_ip

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.claude/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-mcp-request.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
logger.addHandler(file_handler)

console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)
logger.addHandler(console_handler)

AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_TOKEN = os.getenv("AKTO_TOKEN", "")
AKTO_HOST = os.getenv("AKTO_HOST", "https://api.anthropic.com")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
# Non-MCP blocked-request ingestion is off by default; set AKTO_INGEST_NON_MCP_TOOLS=true to send it.
AKTO_INGEST_NON_MCP_TOOLS = os.getenv("AKTO_INGEST_NON_MCP_TOOLS", "false").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "claude_code_cli")
AKTO_CONNECTOR_VALUE = os.getenv("AKTO_CONNECTOR_VALUE", "claudecli")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "AGENTIC")
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_pretool_warn_pending.json")
# Mirrored path: /mcp matches JsonRpcUtils.isMcpPath; non-MCP uses /{prefix}/{normalized-tool-name}
MCP_INGEST_PATH = os.getenv("MCP_INGEST_PATH", "/mcp")
NON_MCP_TOOL_PATH_PREFIX = os.getenv("NON_MCP_TOOL_PATH_PREFIX", "/tool")

DEVICE_IP = get_device_ip()
HOST_HEADER = AKTO_HOST.replace("https://", "").replace("http://", "")

logger.info(f"AKTO_HOST: {AKTO_HOST}, DEVICE_IP: {DEVICE_IP}")


def create_ssl_context():
    return ssl._create_unverified_context()


def build_http_proxy_url(
    *,
    guardrails: bool = False,
    response_guardrails: bool = False,
    ingest_data: bool = False,
) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    if response_guardrails:
        params.append("response_guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)}")

    headers = {"Content-Type": "application/json"}
    if AKTO_TOKEN:
        headers["authorization"] = AKTO_TOKEN
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
            status_code = response.getcode()
            raw = response.read().decode("utf-8")
            logger.info(f"API RESPONSE: Status {status_code}, Duration: {duration_ms}ms, Size: {len(raw)} bytes")

            if LOG_PAYLOADS:
                logger.debug(f"Response body: {raw}")

            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                return raw
    except Exception as e:
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(f"API CALL FAILED after {duration_ms}ms: {e}")
        raise


# Ref: https://code.claude.com/docs/en/hooks#match-mcp-tools
# Ingested traffic is classified as MCP when the mirrored request is JSON-RPC with an MCP method
# (see com.akto.mcp.McpRequestResponseUtils#isMcpRequest). Non-MCP tools must omit top-level jsonrpc.


def normalize_tool_name_for_url_path(tool_name: str) -> str:
    """RFC 3986 path segment: unreserved + hyphen; collapse repeats."""
    s = (tool_name or "unknown").strip()
    s = re.sub(r"[^a-zA-Z0-9._~-]+", "-", s)
    s = re.sub(r"-+", "-", s).strip("-")
    if not s:
        s = "unknown"
    return quote(s, safe=".-_~")


def non_mcp_ingest_path(tool_name: str) -> str:
    """Non-MCP mirrored path: NON_MCP_INGEST_PATH if set, else NON_MCP_TOOL_PATH_PREFIX + normalized tool name."""
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


def parse_claude_tool(tool_name: str) -> Tuple[bool, str, str]:
    """
    Parse Claude tool_name into (is_mcp, server_name, mcp_tool_name).
    MCP tools follow mcp__<server>__<tool> (tool segment may contain underscores).
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


def _tool_arguments_for_jsonrpc(tool_input: Any) -> Dict[str, Any]:
    if isinstance(tool_input, dict):
        return tool_input
    if tool_input is None:
        return {}
    return {"input": tool_input}


def build_tools_call_jsonrpc(mcp_tool_name: str, tool_input: Any, request_id: int = 1) -> str:
    """JSON-RPC body aligned with MCP tools/call (https://modelcontextprotocol.io)."""
    return json.dumps({
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {"name": mcp_tool_name, "arguments": _tool_arguments_for_jsonrpc(tool_input)},
        "id": request_id,
    })


def build_hook_tags(*, is_mcp: bool) -> Dict[str, str]:
    if is_mcp:
        tags = {"mcp-server": "MCP Server", "mcp-client": AKTO_CONNECTOR_VALUE}
    else:
        tags = {"gen-ai": "Gen AI", "ai-agent": AKTO_CONNECTOR_VALUE}
    tags["source"] = CONTEXT_SOURCE
    return tags


def build_validation_request(
    tool_name: str,
    tool_input: Any,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    session_info: dict = None,
) -> Dict[str, Any]:
    tags = build_hook_tags(is_mcp=is_mcp)

    req_hdr: Dict[str, str] = {
        "host": HOST_HEADER,
        "x-claude-hook": "PreToolUse",
        "content-type": "application/json",
    }
    if is_mcp and mcp_server_name:
        req_hdr["x-mcp-server"] = mcp_server_name
    if session_info:
        for key, value in session_info.items():
            if value is not None:
                req_hdr[f"x-akto-installer-{key}"] = str(value)

    if is_mcp:
        request_payload = build_tools_call_jsonrpc(mcp_tool_name, tool_input)
    else:
        request_payload = json.dumps({"body": tool_input, "toolName": tool_name})

    path = MCP_INGEST_PATH if is_mcp else non_mcp_ingest_path(tool_name)
    return {
        "path": path,
        "requestHeaders": json.dumps(req_hdr),
        "responseHeaders": json.dumps({"x-claude-hook": "PreToolUse"}),
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": json.dumps({}),
        "ip": DEVICE_IP,
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
    session_info: dict = None,
) -> Tuple[bool, str, str, bool, str]:
    if not tool_input:
        return True, "", "", False, ""

    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing request (fail-open)")
        return True, "", "", False, ""

    if is_mcp:
        logger.info(f"Validating MCP tools/call for {mcp_tool_name} (server={mcp_server_name}, claudeTool={tool_name})")
    else:
        logger.info(f"Validating built-in / non-MCP tool request: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input payload: {json.dumps(tool_input)}")

    try:
        request_body = build_validation_request(
            tool_name,
            tool_input,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            session_info=session_info,
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
        modified = guardrails_result.get("Modified", False)
        modified_payload = guardrails_result.get("ModifiedPayload", "")

        if allowed:
            logger.info(f"Request ALLOWED for {tool_name}")
        else:
            logger.warning(f"Request DENIED for {tool_name}: {reason}")

        return allowed, reason, behaviour, modified, modified_payload
    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, "", "", False, ""


def pretool_fingerprint(tool_name: str, tool_input: Any) -> str:
    canonical = json.dumps(
        {"t": tool_name, "i": tool_input}, sort_keys=True, ensure_ascii=False
    )
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


def extract_tool_input_from_modified_payload(
    modified_payload: Any,
    *,
    is_mcp: bool,
    fallback: Any,
) -> Any:
    """
    Akto returns the mirrored HTTP body in ModifiedPayload.
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


def pretool_allow_output(*, updated_input: Any = None, reason: str = "") -> Dict[str, Any]:
    """PreToolUse allow decision; optional updatedInput replaces entire tool arguments."""
    out: Dict[str, Any] = {
        "hookSpecificOutput": {
            "hookEventName": "PreToolUse",
            "permissionDecision": "allow",
            "permissionDecisionReason": reason or "Tool request allowed",
        }
    }
    if updated_input is not None:
        out["hookSpecificOutput"]["updatedInput"] = updated_input
    return out


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
    return False, reason


def ingest_blocked_request(
    tool_name: str,
    tool_input: Any,
    reason: str,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    session_info: dict = None,
):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    if not is_mcp and not AKTO_INGEST_NON_MCP_TOOLS:
        logger.info("Skipping non-MCP blocked-request ingestion (set AKTO_INGEST_NON_MCP_TOOLS=true to re-enable)")
        return

    logger.info("Ingesting blocked request data")
    try:
        request_body = build_validation_request(
            tool_name,
            tool_input,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            session_info=session_info,
        )
        request_body["responseHeaders"] = json.dumps({
            "x-claude-hook": "PreToolUse",
            "x-blocked-by": "Akto Proxy",
            "content-type": "application/json",
        })
        request_body["responsePayload"] = json.dumps(
            {"body": {"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"}}
        )
        request_body["statusCode"] = "403"
        request_body["status"] = "403"
        post_payload_json(
            build_http_proxy_url(guardrails=False, ingest_data=True),
            request_body,
        )
        logger.info("Blocked request ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info(f"=== Hook execution started - Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(0)

    session_info = {}
    for field in (
        "session_id",
        "transcript_path",
        "cwd",
        "permission_mode",
        "hook_event_name",
        "tool_use_id",
    ):
        value = input_data.get(field)
        if value is not None:
            session_info[field] = value

    tool_name = str(input_data.get("tool_name") or "")
    tool_input = input_data.get("tool_input") or {}
    is_mcp, mcp_server_name, mcp_tool_name = parse_claude_tool(tool_name)

    modified = False
    modified_payload = ""

    if is_mcp:
        logger.info(f"Processing MCP tool request: {tool_name} (server={mcp_server_name}, mcpTool={mcp_tool_name})")
    else:
        logger.info(f"Processing non-MCP tool request (gen-ai only): {tool_name}")

    if AKTO_SYNC_MODE:
        gr_allowed, gr_reason, behaviour, modified, modified_payload = call_guardrails(
            tool_name,
            tool_input,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
            session_info=session_info,
        )
        fingerprint = pretool_fingerprint(tool_name, tool_input)
        allowed, _ = apply_warn_resubmit_flow(gr_allowed, gr_reason, behaviour, fingerprint)

        if not allowed:
            if _is_warn_behaviour(behaviour):
                block_reason = (
                    "Warning!!, tool request blocked, please review it. Send again to bypass. "
                    f"Reason for blocking: {gr_reason}"
                )
            else:
                block_reason = f"Tool request blocked: {gr_reason}"

            output = {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": block_reason,
                }
            }
            logger.warning(f"BLOCKING tool request - Tool: {tool_name}, Reason: {gr_reason}")
            print(json.dumps(output))
            ingest_blocked_request(
                tool_name,
                tool_input,
                gr_reason,
                is_mcp=is_mcp,
                mcp_server_name=mcp_server_name,
                mcp_tool_name=mcp_tool_name,
                session_info=session_info,
            )
            sys.exit(0)

    if modified and modified_payload:
        new_input = extract_tool_input_from_modified_payload(
            modified_payload,
            is_mcp=is_mcp,
            fallback=tool_input,
        )
        if new_input is not tool_input:
            logger.info(f"Applying guardrail-modified tool_input for {tool_name}")
        print(
            json.dumps(
                pretool_allow_output(
                    updated_input=new_input,
                    reason="Tool request allowed (Akto guardrails)",
                )
            )
        )
        sys.exit(0)

    logger.info(f"Tool request allowed for {tool_name}")
    sys.exit(0)


if __name__ == "__main__":
    main()
