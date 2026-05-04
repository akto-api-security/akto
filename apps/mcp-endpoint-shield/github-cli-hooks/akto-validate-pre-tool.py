#!/usr/bin/env python3

import hashlib
import json
import logging
import os
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, Set, Tuple, Union
from akto_machine_id import get_machine_id, get_username
from akto_heartbeat import send_heartbeat

# Configuration
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_TOKEN = os.getenv("AKTO_TOKEN", "")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
MODE = os.getenv("MODE", "argus").lower()
# /mcp matches Akto's JsonRpcUtils.isMcpPath; non-MCP keeps the legacy /copilot/tool/{name} path.
MCP_INGEST_PATH = os.getenv("MCP_INGEST_PATH", "/mcp")

# SSL Configuration
SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"


def parse_github_tool(tool_name: str) -> Tuple[bool, str, str]:
    """GitHub Copilot CLI / VS Code MCP tool naming is `mcp_<server>_<tool>`
    (per VS Code MCP docs; CLI inherits the convention). Returns (is_mcp, server, mcp_tool).
    Note: server and tool names containing underscores will be split at the first
    underscore after the `mcp_` prefix — this is a limitation of the naming convention itself.
    Override via env var if a more precise mapping is needed."""
    if not tool_name.startswith("mcp_"):
        return False, "", ""
    rest = tool_name[len("mcp_"):]
    if "_" not in rest:
        return False, "", ""
    server, _, mcp_tool = rest.partition("_")
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
    return json.dumps(
        {
            "jsonrpc": "2.0",
            "method": "tools/call",
            "params": {"name": mcp_tool_name, "arguments": _tool_arguments_for_jsonrpc(tool_input)},
            "id": request_id,
        }
    )


def build_tools_call_result_jsonrpc(tool_response: Any, request_id: int = 1) -> str:
    if isinstance(tool_response, dict):
        result_body: Any = tool_response
    else:
        result_body = {"output": tool_response}
    return json.dumps({"jsonrpc": "2.0", "id": request_id, "result": result_body})


def mcp_mirror_host(device_id: str, ai_agent_tag: str, mcp_server_name: str) -> str:
    return f"{device_id}.{ai_agent_tag}.{mcp_server_name}"


def detect_connector(input_data: dict) -> str:
    """Detect connector from hook payload. hookEventName is present in all VSCode payloads."""
    if "hookEventName" in input_data:
        return "vscode"
    return os.getenv("AKTO_CONNECTOR", "vscode")


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
            "log_dir_default": "~/akto/.github/akto/copilot/logs",
            "blocked_exit_code": 2,
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
            "blocked_exit_code": 0,
        }


def setup_logging(log_dir: str):
    os.makedirs(log_dir, exist_ok=True)
    logger = logging.getLogger(__name__)
    logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    if not logger.handlers:
        file_handler = logging.FileHandler(os.path.join(log_dir, "validate-pre-tool.log"))
        file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
        logger.addHandler(file_handler)
        console_handler = logging.StreamHandler(sys.stderr)
        console_handler.setLevel(logging.ERROR)
        logger.addHandler(console_handler)
    return logger


def create_ssl_context():
    return ssl._create_unverified_context()


def build_http_proxy_url(cfg: dict, *, guardrails: bool = False, response_guardrails: bool = False, ingest_data: bool = False) -> str:
    """Build Akto HTTP proxy URL with query parameters."""
    params = [f"akto_connector={cfg['connector']}"]
    if guardrails:
        params.append("guardrails=true")
    if response_guardrails:
        params.append("response_guardrails=true")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_to_akto(url: str, payload: Dict[str, Any], logger) -> Union[Dict[str, Any], str]:
    """Send JSON payload to Akto API."""
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Payload: {json.dumps(payload, default=str)[:1000]}...")

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
            raw = response.read().decode("utf-8")
            logger.info(f"Response: {response.getcode()} in {duration_ms}ms")
            if LOG_PAYLOADS:
                logger.debug(f"Response body: {raw[:1000]}...")
            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                return raw
    except Exception as e:
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(f"API call failed after {duration_ms}ms: {e}")
        raise


def build_akto_request(
    tool_name: str,
    tool_args: str,
    cwd: str,
    timestamp: int,
    cfg: dict,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
) -> Dict[str, Any]:
    """Build request payload for Akto guardrails validation.
    Wraps MCP tool calls in JSON-RPC 2.0 tools/call so Akto classifies them as MCP."""
    if is_mcp:
        tags = {"mcp-server": "MCP Server", "mcp-client": cfg["ai_agent_tag"]}
    else:
        tags = {"gen-ai": "Gen AI", "tool-use": "Tool Execution"}
        if MODE == "atlas":
            tags["ai-agent"] = cfg["ai_agent_tag"]
    if MODE == "atlas":
        tags["source"] = CONTEXT_SOURCE

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    if is_mcp:
        host = mcp_mirror_host(device_id, cfg["ai_agent_tag"], mcp_server_name)
    else:
        host = cfg["api_url"].replace("https://", "").replace("http://", "")
    hook_header = cfg["hook_header"]

    request_header_dict = {
        "host": host,
        hook_header: "PreToolUse",
        "content-type": "application/json",
    }
    if is_mcp and mcp_server_name:
        request_header_dict["x-mcp-server"] = mcp_server_name
    request_headers = json.dumps(request_header_dict)

    response_headers = json.dumps({
        hook_header: "PreToolUse"
    })

    if is_mcp:
        # Cursor-style: tool_args may already be a JSON-encoded string or a raw dict.
        try:
            parsed_input = json.loads(tool_args) if isinstance(tool_args, str) else tool_args
        except (json.JSONDecodeError, TypeError):
            parsed_input = {"raw": tool_args}
        request_payload = build_tools_call_jsonrpc(mcp_tool_name, parsed_input)
    else:
        request_payload = json.dumps({
            "body": json.dumps({"toolName": tool_name, "toolArgs": tool_args})
        })

    response_payload = json.dumps({})

    path = MCP_INGEST_PATH if is_mcp else f"/copilot/tool/{tool_name}"
    return {
        "path": path,
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": get_username(),
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


def _guardrails_behaviour_value(behaviour: Any) -> str:
    return str(behaviour or "").strip().lower()


def _is_warn_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "warn"


def _is_alert_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "alert"


def validate_tool_use(
    tool_name: str,
    tool_args: str,
    cwd: str,
    timestamp: int,
    cfg: dict,
    logger,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
) -> tuple[bool, str, str]:
    """Validate tool use against Akto guardrails. Returns (allowed, reason, behaviour)."""
    if is_mcp:
        logger.info(f"Validating MCP tools/call for {mcp_tool_name} (server={mcp_server_name}, githubTool={tool_name})")
    else:
        logger.info(f"Validating tool use: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool args: {tool_args}")
    else:
        logger.info(f"Tool args preview: {tool_args[:100]}...")

    try:
        request_body = build_akto_request(
            tool_name,
            tool_args,
            cwd,
            timestamp,
            cfg,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
        )
        result = post_to_akto(
            build_http_proxy_url(cfg, guardrails=True, ingest_data=False),
            request_body,
            logger
        )

        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")
        behaviour = guardrails_result.get("behaviour", "") or guardrails_result.get("Behaviour", "")
        logger.debug(f"Guardrails result — allowed={allowed}, behaviour={behaviour!r}, reason={reason!r}")

        if allowed:
            logger.info("✓ Tool use ALLOWED by guardrails")
        else:
            logger.warning(f"✗ Tool use DENIED by guardrails: {reason}")

        return allowed, reason, behaviour

    except Exception as e:
        logger.error(f"Guardrails validation error: {e}", exc_info=True)
        return True, "", ""  # Allow on error


def pretool_fingerprint(tool_name: str, tool_args: str) -> str:
    canonical = json.dumps({"t": tool_name, "a": tool_args}, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def load_warn_pending(warn_state_path: str, logger) -> Set[str]:
    if not os.path.exists(warn_state_path):
        return set()
    try:
        with open(warn_state_path, encoding="utf-8") as f:
            data = json.load(f)
        return set(data.get("warn_pending", []))
    except (json.JSONDecodeError, OSError) as e:
        logger.warning(f"Could not read warn-pending map: {e}")
        return set()


def save_warn_pending(warn_state_path: str, hashes: Set[str], logger) -> None:
    tmp_path = warn_state_path + ".tmp"
    try:
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump({"warn_pending": sorted(hashes)}, f, indent=0)
            f.write("\n")
        os.replace(tmp_path, warn_state_path)
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
    warn_state_path: str,
    logger,
) -> Tuple[bool, str]:
    if gr_allowed:
        return True, ""

    if _is_alert_behaviour(behaviour):
        logger.info("Alert behaviour: allowing despite violation (server-side alert only)")
        return True, ""

    if not _is_warn_behaviour(behaviour):
        return False, reason

    pending = load_warn_pending(warn_state_path, logger)
    if fingerprint in pending:
        pending.discard(fingerprint)
        save_warn_pending(warn_state_path, pending, logger)
        logger.info("Warn flow: allowing resubmit; removed fingerprint from map")
        return True, ""

    pending.add(fingerprint)
    save_warn_pending(warn_state_path, pending, logger)
    logger.info("Warn flow: first occurrence — blocked, resend same tool call to bypass")
    return False, reason


def ingest_blocked_tool_use(
    tool_name: str,
    tool_args: str,
    cwd: str,
    timestamp: int,
    reason: str,
    cfg: dict,
    logger,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
):
    """Ingest blocked tool use to Akto for analytics."""
    if not AKTO_DATA_INGESTION_URL:
        return

    logger.info("Ingesting blocked tool use")
    try:
        request_body = build_akto_request(
            tool_name,
            tool_args,
            cwd,
            timestamp,
            cfg,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
        )
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
        logger.info("Blocked tool use ingested successfully")
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
    warn_state_path = os.path.join(log_dir, "akto_pretool_warn_pending.json")
    send_heartbeat(log_dir, logger)

    logger.info(f"=== Pre-Tool Use Hook - Connector: {connector}, Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    if LOG_PAYLOADS:
        logger.debug(f"Input: {json.dumps(input_data)}")

    logger.info(f"MODE: {MODE}, API_URL: {cfg['api_url']}")

    # Parse input — key names differ between connectors
    if cfg["is_vscode"]:
        tool_name = input_data.get("tool_name", "unknown")
        tool_input = input_data.get("tool_input", {})
        tool_args = json.dumps(tool_input) if isinstance(tool_input, dict) else str(tool_input)
    else:
        tool_name = input_data.get("toolName") or input_data.get("tool_name", "unknown")
        tool_args = input_data.get("toolArgs") or json.dumps(input_data.get("tool_input", {}))

    cwd = input_data.get("cwd", "")
    timestamp = input_data.get("timestamp", int(time.time() * 1000))
    is_mcp, mcp_server_name, mcp_tool_name = parse_github_tool(tool_name)

    if is_mcp:
        logger.info(f"Tool: {tool_name} (MCP server={mcp_server_name}, mcpTool={mcp_tool_name}), CWD: {cwd}")
    else:
        logger.info(f"Tool: {tool_name}, CWD: {cwd}")

    if not AKTO_SYNC_MODE or not AKTO_DATA_INGESTION_URL:
        logger.info("Guardrails disabled (sync mode off or no URL)")
        sys.exit(0)

    gr_allowed, gr_reason, behaviour = validate_tool_use(
        tool_name,
        tool_args,
        cwd,
        timestamp,
        cfg,
        logger,
        is_mcp=is_mcp,
        mcp_server_name=mcp_server_name,
        mcp_tool_name=mcp_tool_name,
    )
    fingerprint = pretool_fingerprint(tool_name, tool_args)
    allowed, _ = apply_warn_resubmit_flow(gr_allowed, gr_reason, behaviour, fingerprint, warn_state_path, logger)

    if not allowed:
        if _is_warn_behaviour(behaviour):
            denial_reason = (
                f"Warning!! Tool use blocked, please review it. Send again to bypass. "
                f"Reason for blocking: {gr_reason}"
            )
        else:
            denial_reason = f"Blocked by Akto Guardrails: {gr_reason or 'Policy violation'}"

        logger.warning(f"BLOCKING tool use: {tool_name}, Reason: {denial_reason}")
        output = {
            "permissionDecision": "deny",
            "permissionDecisionReason": denial_reason,
            "hookSpecificOutput": {
                "permissionDecision": "deny",
                "permissionDecisionReason": denial_reason,
            },
        }
        sys.stdout.write(json.dumps(output))
        sys.stdout.flush()

        ingest_blocked_tool_use(
            tool_name,
            tool_args,
            cwd,
            timestamp,
            gr_reason,
            cfg,
            logger,
            is_mcp=is_mcp,
            mcp_server_name=mcp_server_name,
            mcp_tool_name=mcp_tool_name,
        )
        sys.exit(cfg["blocked_exit_code"])

    logger.info(f"Tool use PASSED guardrails for {tool_name}")
    sys.exit(0)


if __name__ == "__main__":
    main()
