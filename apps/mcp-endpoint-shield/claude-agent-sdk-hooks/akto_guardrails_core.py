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
    AKTO_TOKEN               : Authorization header value sent to AKTO_DATA_INGESTION_URL.
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
import hashlib
import json
import logging
import os
import ssl
import time
import urllib.request
from typing import Any, Dict, Set, Tuple, Union

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
AGENT_ID = os.getenv("AGENT_ID", "")
AKTO_HOST = os.getenv("AKTO_HOST", "api.anthropic.com")

# Read at call time so values set after import (e.g. via load_dotenv) are picked up
def _data_ingestion_url() -> str:
    return os.getenv("AKTO_DATA_INGESTION_URL", "")

def _akto_token() -> str:
    return os.getenv("AKTO_TOKEN", "")

# contextSource is always AGENTIC for server-side agent deployments
CONTEXT_SOURCE = "AGENTIC"

DEFAULT_CLIENT_IP = "0.0.0.0"

WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_prompt_warn_pending.json")

logger.info(
    f"Akto Agent SDK hooks initialised | mode={MODE} sync={AKTO_SYNC_MODE} "
    f"connector={AKTO_CONNECTOR} agent_id={AGENT_ID or '(not set)'}"
)

# ---------------------------------------------------------------------------
# HTTP helpers
# ---------------------------------------------------------------------------

def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
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
        headers["authorization"] = token
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
        "akto_vxlan_id": AGENT_ID,
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
# MCP server name extraction
# ---------------------------------------------------------------------------

def extract_mcp_server_name(tool_name: str) -> str:
    if not tool_name.startswith("mcp__"):
        return "claude-built-in"
    parts = tool_name.split("__")
    if len(parts) >= 3 and parts[1]:
        return parts[1]
    return "claude-built-in"

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


def build_mcp_request_payload(
    tool_name: str, tool_input: Any, mcp_server_name: str, client_ip: str
) -> Dict[str, Any]:
    tags = {"gen-ai": "Gen AI", "agent-name": "claude-agent"}
    request_headers = json.dumps({
        "host": AKTO_HOST,
        "x-claude-hook": "PreToolUse",
        "content-type": "application/json",
    })
    response_headers = json.dumps({"x-claude-hook": "PreToolUse"})
    request_payload = json.dumps({"body": tool_input, "toolName": tool_name})
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


def build_mcp_response_payload(
    tool_name: str, tool_input: Any, tool_response: Any, mcp_server_name: str, client_ip: str
) -> Dict[str, Any]:
    tags = {
        "gen-ai": "Gen AI",
        "tool-use": "Tool Execution",
        "agent-name": "claude-agent",
    }
    request_headers = json.dumps({
        "host": AKTO_HOST,
        "x-claude-hook": "PostToolUse",
        "content-type": "application/json",
    })
    response_headers = json.dumps({
        "x-claude-hook": "PostToolUse",
        "content-type": "application/json",
    })
    request_payload = json.dumps({"body": {"toolName": tool_name, "toolArgs": tool_input}})
    response_payload = json.dumps({"body": {"result": tool_response}})
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


async def call_guardrails_mcp_async(
    tool_name: str, tool_input: Any, mcp_server_name: str, client_ip: str
) -> Tuple[bool, str]:
    """Validate an MCP tool call. Returns (allowed, reason)."""
    if not tool_input:
        return True, ""
    if not _data_ingestion_url():
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing MCP request (fail-open)")
        return True, ""

    logger.info(f"Validating MCP request for tool: {tool_name} (server: {mcp_server_name})")
    try:
        payload = build_mcp_request_payload(tool_name, tool_input, mcp_server_name, client_ip)
        result = await post_payload_json_async(
            build_http_proxy_url(guardrails=True, ingest_data=False), payload
        )
        data = result.get("data", {}) if isinstance(result, dict) else {}
        gr = data.get("guardrailsResult", {}) or {}
        allowed = gr.get("Allowed", True)
        reason = gr.get("Reason", "")
        if allowed:
            logger.info(f"MCP request ALLOWED for {tool_name}")
        else:
            logger.warning(f"MCP request DENIED for {tool_name}: {reason}")
        return allowed, reason
    except Exception as e:
        logger.error(f"Guardrails MCP validation error (fail-open): {e}")
        return True, ""

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
    tool_name: str, tool_input: Any, mcp_server_name: str, reason: str, client_ip: str
) -> None:
    if not _data_ingestion_url() or not AKTO_SYNC_MODE:
        return
    try:
        payload = build_mcp_request_payload(tool_name, tool_input, mcp_server_name, client_ip)
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


async def send_mcp_response_ingestion_async(
    tool_name: str, tool_input: Any, tool_response: Any, mcp_server_name: str, client_ip: str
) -> None:
    if not _data_ingestion_url():
        return
    if not tool_input or not tool_response:
        return
    logger.info(f"Ingesting MCP response for tool: {tool_name} (server: {mcp_server_name})")
    try:
        payload = build_mcp_response_payload(tool_name, tool_input, tool_response, mcp_server_name, client_ip)
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
            build_http_proxy_url(guardrails=not AKTO_SYNC_MODE, ingest_data=True), payload
        )
        logger.info("Conversation ingestion successful")
    except Exception as e:
        logger.error(f"Conversation ingestion error: {e}")

# ---------------------------------------------------------------------------
# Warn-flow helpers (UserPromptSubmit)
# ---------------------------------------------------------------------------

def _guardrails_behaviour_value(behaviour: Any) -> str:
    return str(behaviour or "").strip().lower()


def _is_warn_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "warn"


def _is_alert_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "alert"


def prompt_fingerprint(prompt: str) -> str:
    canonical = json.dumps({"p": prompt, "a": []}, sort_keys=True, ensure_ascii=False)
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
    gr_allowed: bool, reason: str, behaviour: str, fingerprint: str
) -> Tuple[bool, str]:
    if gr_allowed:
        return True, ""

    if _is_alert_behaviour(behaviour):
        logger.info("Alert behaviour: allowing prompt despite violation (server-side alert only)")
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
