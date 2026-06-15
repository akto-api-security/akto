#!/usr/bin/env python3
"""
Akto Guardrails core business logic for Claude Agent SDK hooks.

This module consolidates the shared logic from all four hook scripts into a
single async-capable module. It is used exclusively by hooks.py.

Environment Variables:
    AKTO_DATA_INGESTION_URL  : Base URL for Akto's data ingestion service (required).
    AKTO_SYNC_MODE           : "true" (default) to block in PreToolUse/UserPromptSubmit;
                               "false" to ingest-only (observational).
    AKTO_TIMEOUT             : HTTP request timeout in seconds (default: 5).
    AKTO_CONNECTOR           : Connector name sent to Akto (default: "claude_agent_sdk").
    AKTO_API_TOKEN               : Authorization header value sent to AKTO_DATA_INGESTION_URL.
    AGENT_ID                 : Identifies this agent server instance; used as
                               akto_vxlan_id in payloads. Set to a stable identifier
                               for your deployment (e.g. pod name, service name).
    AKTO_HOST                : Hostname written into request headers for Akto's
                               HTTP proxy (default: "api.anthropic.com").
    MODE                     : "argus" (default) or "atlas".
    LOG_DIR                  : Log file directory (default: ~/.claude/akto/logs).
    LOG_LEVEL                : Logging level (default: INFO).
    LOG_PAYLOADS             : "true" to log full request/response bodies (default: false).
    SSL_CERT_PATH            : Path to custom CA bundle (optional).
"""

import asyncio
import json
import logging
import os
import re
import ssl
import time
import urllib.request
from urllib.parse import quote
from typing import Any, Dict, Tuple, Union

# ---------------------------------------------------------------------------
# Configuration
# ---------------------------------------------------------------------------

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.claude/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

_file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-agent-sdk.log"))
_file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
_file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
logger.addHandler(_file_handler)

MODE = os.getenv("MODE", "argus").lower()
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "claude_agent_sdk")
AKTO_CONNECTOR_VALUE = os.getenv("AKTO_CONNECTOR_VALUE", "claude_agent_sdk")
AKTO_HOST = os.getenv("AKTO_HOST", "api.anthropic.com")

# MCP tool calls are mirrored on MCP_INGEST_PATH as JSON-RPC so Akto's runtime classifies
# them as MCP; built-in / non-MCP tools are mirrored under NON_MCP_TOOL_PATH_PREFIX/<tool-name>.
MCP_INGEST_PATH = os.getenv("MCP_INGEST_PATH", "/mcp")
NON_MCP_TOOL_PATH_PREFIX = os.getenv("NON_MCP_TOOL_PATH_PREFIX", "/tool")

# Read at call time so values set after import (e.g. via load_dotenv) are picked up
def _data_ingestion_url() -> str:
    return os.getenv("AKTO_DATA_INGESTION_URL", "")

def _akto_token() -> str:
    return os.getenv("AKTO_API_TOKEN", "")

# contextSource is always AGENTIC for server-side agent deployments
CONTEXT_SOURCE = "AGENTIC"

DEFAULT_CLIENT_IP = "0.0.0.0"

logger.info(
    f"Akto Agent SDK hooks initialised | mode={MODE} sync={AKTO_SYNC_MODE} "
    f"connector={AKTO_CONNECTOR}"
)

# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def build_http_proxy_url(
    *, guardrails: bool, ingest_data: bool, response_guardrails: bool = False
) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    if response_guardrails:
        params.append("response_guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{_data_ingestion_url()}/api/http-proxy?{'&'.join(params)}"


def _create_ssl_context():
    return ssl._create_unverified_context()


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

    headers = {"Content-Type": "application/json"}
    token = _akto_token()
    if token:
        headers["Authorization"] = token
    request = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )

    start_time = time.time()
    try:
        ssl_context = _create_ssl_context()
        with urllib.request.urlopen(request, context=ssl_context, timeout=AKTO_TIMEOUT) as response:
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


async def post_payload_json_async(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    """Non-blocking wrapper around post_payload_json using asyncio.to_thread."""
    return await asyncio.to_thread(post_payload_json, url, payload)


def _parse_guardrails_result(result: Any) -> Tuple[bool, str, str]:
    """Parse a guardrails API response. Returns (allowed, reason, behaviour)."""
    if not isinstance(result, dict):
        return True, "", ""
    data = result.get("data", {}) or {}
    gr = data.get("guardrailsResult", {}) or {}
    allowed = gr.get("Allowed", True)
    reason = gr.get("Reason", "")
    behaviour = gr.get("behaviour", "") or gr.get("Behaviour", "")
    return allowed, reason, behaviour

# ---------------------------------------------------------------------------
# Shared payload structure
# ---------------------------------------------------------------------------

def _base_payload(
    path: str,
    request_headers: str,
    response_headers: str,
    request_payload: str,
    response_payload: str,
    status_code: str,
    tags: dict,
    client_ip: str,
) -> Dict[str, Any]:
    return {
        "path": path,
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": client_ip or DEFAULT_CLIENT_IP,
        "destIp": "127.0.0.1",
        "time": str(int(time.time() * 1000)),
        "statusCode": status_code,
        "type": "HTTP/1.1",
        "status": status_code,
        "akto_account_id": "1000000",
        "akto_vxlan_id": "0",
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

# ---------------------------------------------------------------------------
# MCP tool classification
# ---------------------------------------------------------------------------
# Akto's runtime classifies traffic as MCP when the mirrored request is JSON-RPC with an
# MCP method on an MCP path (McpRequestResponseUtils.isMcpRequest / JsonRpcUtils.isMcpPath).
# Built-in / non-MCP tools must omit the JSON-RPC envelope and are mirrored as gen-ai HTTP.

def parse_claude_tool(tool_name: str) -> Tuple[bool, str, str]:
    """
    Parse a Claude tool name into (is_mcp, server_name, mcp_tool_name).
    MCP tools follow mcp__<server>__<tool> (the tool segment may contain underscores).
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
    """Mirrored path for a built-in / non-MCP tool: NON_MCP_TOOL_PATH_PREFIX + normalized name."""
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
    """JSON-RPC body aligned with the MCP tools/call method."""
    return json.dumps({
        "jsonrpc": "2.0",
        "method": "tools/call",
        "params": {"name": mcp_tool_name, "arguments": _tool_arguments_for_jsonrpc(tool_input)},
        "id": request_id,
    })


def build_tools_call_result_jsonrpc(tool_response: Any, request_id: int = 1) -> str:
    """JSON-RPC success body for the mirrored tool result (avoids MCP error-path handling)."""
    result_body = tool_response if isinstance(tool_response, dict) else {"output": tool_response}
    return json.dumps({"jsonrpc": "2.0", "id": request_id, "result": result_body})


def build_tool_hook_tags(is_mcp: bool) -> Dict[str, str]:
    if is_mcp:
        return {"mcp-server": "MCP Server", "mcp-client": AKTO_CONNECTOR_VALUE}
    return {"gen-ai": "Gen AI", "ai-agent": AKTO_CONNECTOR_VALUE}

# ---------------------------------------------------------------------------
# Payload builders
# ---------------------------------------------------------------------------

def build_prompt_validation_payload(query: str, client_ip: str) -> Dict[str, Any]:
    tags = {"gen-ai": "Gen AI", "agent-name": "claude-agent"}
    request_headers = json.dumps({
        "host": AKTO_HOST,
        "x-claude-hook": "UserPromptSubmit",
        "content-type": "application/json",
    })
    response_headers = json.dumps({"x-claude-hook": "UserPromptSubmit"})
    request_payload = json.dumps({"body": query.strip()})
    response_payload = json.dumps({})
    return _base_payload(
        "/v1/messages",
        request_headers,
        response_headers,
        request_payload,
        response_payload,
        "200",
        tags,
        client_ip,
    )


def build_mcp_request_payload(tool_name: str, tool_input: Any, client_ip: str) -> Dict[str, Any]:
    is_mcp, mcp_server_name, mcp_tool_name = parse_claude_tool(tool_name)
    tags = build_tool_hook_tags(is_mcp)
    request_headers = {
        "host": AKTO_HOST,
        "x-claude-hook": "PreToolUse",
        "content-type": "application/json",
    }
    if is_mcp and mcp_server_name:
        request_headers["x-mcp-server"] = mcp_server_name

    if is_mcp:
        request_payload = build_tools_call_jsonrpc(mcp_tool_name, tool_input)
        path = MCP_INGEST_PATH
    else:
        request_payload = json.dumps({"body": tool_input, "toolName": tool_name})
        path = non_mcp_ingest_path(tool_name)

    return _base_payload(
        path,
        json.dumps(request_headers),
        json.dumps({"x-claude-hook": "PreToolUse"}),
        request_payload,
        json.dumps({}),
        "200",
        tags,
        client_ip,
    )


def build_mcp_response_payload(
    tool_name: str, tool_input: Any, tool_response: Any, client_ip: str
) -> Dict[str, Any]:
    is_mcp, mcp_server_name, mcp_tool_name = parse_claude_tool(tool_name)
    tags = build_tool_hook_tags(is_mcp)
    request_headers = {
        "host": AKTO_HOST,
        "x-claude-hook": "PostToolUse",
        "content-type": "application/json",
    }
    if is_mcp and mcp_server_name:
        request_headers["x-mcp-server"] = mcp_server_name
    response_headers = {"x-claude-hook": "PostToolUse", "content-type": "application/json"}

    if is_mcp:
        request_payload = build_tools_call_jsonrpc(mcp_tool_name, tool_input)
        response_payload = build_tools_call_result_jsonrpc(tool_response)
        path = MCP_INGEST_PATH
    else:
        request_payload = json.dumps({"body": {"toolName": tool_name, "toolArgs": tool_input}})
        response_payload = json.dumps({"body": {"result": tool_response}})
        path = non_mcp_ingest_path(tool_name)

    return _base_payload(
        path,
        json.dumps(request_headers),
        json.dumps(response_headers),
        request_payload,
        response_payload,
        "200",
        tags,
        client_ip,
    )


def build_stop_ingestion_payload(user_prompt: str, response_text: str, client_ip: str) -> Dict[str, Any]:
    tags = {"gen-ai": "Gen AI", "agent-name": "claude-agent"}
    request_headers = json.dumps({
        "host": AKTO_HOST,
        "x-claude-hook": "Stop",
        "content-type": "application/json",
    })
    response_headers = json.dumps({
        "x-claude-hook": "Stop",
        "content-type": "application/json",
    })
    request_payload = json.dumps({"body": user_prompt})
    response_payload = json.dumps({"body": response_text})
    return _base_payload(
        "/v1/messages",
        request_headers,
        response_headers,
        request_payload,
        response_payload,
        "200",
        tags,
        client_ip,
    )

# ---------------------------------------------------------------------------
# Guardrails callers
# ---------------------------------------------------------------------------

async def call_guardrails_prompt_async(query: str, client_ip: str) -> Tuple[bool, str, str]:
    """Validate a user prompt. Returns (allowed, reason, behaviour)."""
    if not query.strip():
        return True, "", ""
    if not _data_ingestion_url():
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing prompt (fail-open)")
        return True, "", ""

    logger.info("Validating prompt against guardrails")
    try:
        payload = build_prompt_validation_payload(query, client_ip)
        result = await post_payload_json_async(
            build_http_proxy_url(guardrails=True, ingest_data=False), payload
        )
        allowed, reason, behaviour = _parse_guardrails_result(result)
        if allowed:
            logger.info("Prompt ALLOWED by guardrails")
        else:
            logger.warning(f"Prompt DENIED by guardrails: {reason}")
        return allowed, reason, behaviour
    except Exception as e:
        logger.error(f"Guardrails prompt validation error (fail-open): {e}")
        return True, "", ""


async def call_guardrails_response_async(
    user_prompt: str, response_text: str, client_ip: str
) -> Tuple[bool, str, str]:
    """Validate an agent response. Returns (allowed, reason, behaviour)."""
    if not response_text.strip():
        return True, "", ""
    if not _data_ingestion_url():
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing response (fail-open)")
        return True, "", ""

    logger.info("Validating response against guardrails")
    try:
        payload = build_stop_ingestion_payload(user_prompt, response_text, client_ip)
        result = await post_payload_json_async(
            build_http_proxy_url(guardrails=False, response_guardrails=True, ingest_data=False), payload
        )
        allowed, reason, behaviour = _parse_guardrails_result(result)
        if allowed:
            logger.info("Response ALLOWED by guardrails")
        else:
            logger.warning(f"Response DENIED by guardrails: {reason}")
        return allowed, reason, behaviour
    except Exception as e:
        logger.error(f"Guardrails response validation error (fail-open): {e}")
        return True, "", ""


async def call_guardrails_mcp_async(
    tool_name: str, tool_input: Any, client_ip: str
) -> Tuple[bool, str, str]:
    """Validate an MCP / built-in tool call. Returns (allowed, reason, behaviour)."""
    if not tool_input:
        return True, "", ""
    if not _data_ingestion_url():
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing MCP request (fail-open)")
        return True, "", ""

    logger.info(f"Validating tool call: {tool_name}")
    try:
        payload = build_mcp_request_payload(tool_name, tool_input, client_ip)
        result = await post_payload_json_async(
            build_http_proxy_url(guardrails=True, ingest_data=False), payload
        )
        allowed, reason, behaviour = _parse_guardrails_result(result)
        if allowed:
            logger.info(f"MCP request ALLOWED for {tool_name}")
        else:
            logger.warning(f"MCP request DENIED for {tool_name}: {reason}")
        return allowed, reason, behaviour
    except Exception as e:
        logger.error(f"Guardrails MCP validation error (fail-open): {e}")
        return True, "", ""

# ---------------------------------------------------------------------------
# Ingestion senders
# ---------------------------------------------------------------------------

async def ingest_blocked_prompt_async(user_prompt: str, reason: str, client_ip: str) -> None:
    if not _data_ingestion_url() or not AKTO_SYNC_MODE:
        return
    try:
        payload = build_prompt_validation_payload(user_prompt, client_ip)
        payload["responseHeaders"] = json.dumps({
            "x-claude-hook": "UserPromptSubmit",
            "x-blocked-by": "Akto Proxy",
            "content-type": "application/json",
        })
        payload["responsePayload"] = json.dumps({
            "body": json.dumps({"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"})
        })
        payload["statusCode"] = "403"
        payload["status"] = "403"
        await post_payload_json_async(
            build_http_proxy_url(guardrails=False, ingest_data=True), payload
        )
        logger.info("Blocked prompt ingestion successful")
    except Exception as e:
        logger.error(f"Blocked prompt ingestion error: {e}")


async def ingest_blocked_mcp_async(
    tool_name: str, tool_input: Any, reason: str, client_ip: str
) -> None:
    if not _data_ingestion_url() or not AKTO_SYNC_MODE:
        return
    try:
        payload = build_mcp_request_payload(tool_name, tool_input, client_ip)
        payload["responseHeaders"] = json.dumps({
            "x-claude-hook": "PreToolUse",
            "x-blocked-by": "Akto Proxy",
            "content-type": "application/json",
        })
        payload["responsePayload"] = json.dumps({
            "body": {"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"}
        })
        payload["statusCode"] = "403"
        payload["status"] = "403"
        await post_payload_json_async(
            build_http_proxy_url(guardrails=False, ingest_data=True), payload
        )
        logger.info("Blocked MCP request ingestion successful")
    except Exception as e:
        logger.error(f"Blocked MCP ingestion error: {e}")


async def ingest_blocked_response_async(
    user_prompt: str, response_text: str, reason: str, client_ip: str
) -> None:
    if not _data_ingestion_url() or not AKTO_SYNC_MODE:
        return
    try:
        payload = build_stop_ingestion_payload(user_prompt, response_text, client_ip)
        payload["responseHeaders"] = json.dumps({
            "x-claude-hook": "Stop",
            "x-blocked-by": "Akto Proxy",
            "content-type": "application/json",
        })
        payload["responsePayload"] = json.dumps({
            "body": json.dumps({"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"})
        })
        payload["statusCode"] = "403"
        payload["status"] = "403"
        await post_payload_json_async(
            build_http_proxy_url(guardrails=False, ingest_data=True), payload
        )
        logger.info("Blocked response ingestion successful")
    except Exception as e:
        logger.error(f"Blocked response ingestion error: {e}")


async def send_mcp_response_ingestion_async(
    tool_name: str, tool_input: Any, tool_response: Any, client_ip: str
) -> None:
    if not _data_ingestion_url():
        return
    if not tool_input or not tool_response:
        return
    logger.info(f"Ingesting tool response: {tool_name}")
    try:
        payload = build_mcp_response_payload(tool_name, tool_input, tool_response, client_ip)
        await post_payload_json_async(
            build_http_proxy_url(guardrails=not AKTO_SYNC_MODE, ingest_data=True), payload
        )
        logger.info("MCP response ingestion successful")
    except Exception as e:
        logger.error(f"MCP response ingestion error: {e}")


async def send_stop_ingestion_async(user_prompt: str, response_text: str, client_ip: str) -> None:
    if not _data_ingestion_url():
        return
    if not user_prompt.strip() or not response_text.strip():
        return
    logger.info("Ingesting conversation data (Stop hook)")
    try:
        payload = build_stop_ingestion_payload(user_prompt, response_text, client_ip)
        await post_payload_json_async(
            build_http_proxy_url(
                guardrails=False,
                response_guardrails=not AKTO_SYNC_MODE,
                ingest_data=True,
            ), payload
        )
        logger.info("Conversation ingestion successful")
    except Exception as e:
        logger.error(f"Conversation ingestion error: {e}")

# ---------------------------------------------------------------------------
# Guardrail behaviour resolution
# ---------------------------------------------------------------------------

def _guardrails_behaviour_value(behaviour: Any) -> str:
    return str(behaviour or "").strip().lower()


def _is_warn_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "warn"


def _is_alert_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "alert"


def resolve_guardrail_decision(
    gr_allowed: bool, reason: str, behaviour: str
) -> Tuple[bool, str]:
    """
    Resolve a guardrails verdict into an allow/deny decision.

    - allowed              -> allow
    - 'alert' or 'warn'    -> allow; the violation is recorded server-side during evaluation
    - anything else (block) -> deny

    The CLI's "warn = deny then allow on identical resubmit" gate does not apply in the
    Agent SDK: prompts are issued by the calling app and tool calls by the model, neither
    of which resubmits-to-bypass, and there is no human in the loop. So 'warn' is treated
    as 'alert' here — observational, not blocking.
    """
    if gr_allowed:
        return True, ""

    if _is_alert_behaviour(behaviour) or _is_warn_behaviour(behaviour):
        logger.info(
            f"{_guardrails_behaviour_value(behaviour)} behaviour: allowing despite violation "
            "(recorded server-side)"
        )
        return True, ""

    return False, reason

# ---------------------------------------------------------------------------
# Transcript reader (Stop hook)
# ---------------------------------------------------------------------------

def extract_text_from_entry(entry: Dict[str, Any]) -> str:
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


def get_last_user_prompt(transcript_path: str) -> str:
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
                    text = extract_text_from_entry(entry)
                    if text:
                        last_user = text
        return last_user
    except Exception as e:
        logger.error(f"Error reading transcript: {e}")
        return ""
