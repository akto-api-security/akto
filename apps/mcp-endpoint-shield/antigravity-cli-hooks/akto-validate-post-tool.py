#!/usr/bin/env python3

import json
import logging
import os
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, Optional, Union

from akto_machine_id import get_machine_id, get_username

from akto_ingestion_utility import installer_headers, resolve_session_info

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.gemini/antigravity/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"
STEP_CACHE_PATH = os.path.join(LOG_DIR, "akto_step_cache.json")

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-post-tool.log"))
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
DEVICE_ID = os.getenv("DEVICE_ID") or get_machine_id()

ANTIGRAVITY_API_URL = os.getenv("ANTIGRAVITY_API_URL", "https://generativelanguage.googleapis.com")
if MODE == "atlas":
    ANTIGRAVITY_API_URL = (
        f"https://{DEVICE_ID}.ai-agent.{AKTO_CONNECTOR_VALUE}" if DEVICE_ID else ANTIGRAVITY_API_URL
    )

logger.info(f"MODE: {MODE}, ANTIGRAVITY_API_URL: {ANTIGRAVITY_API_URL}")


def create_ssl_context() -> ssl.SSLContext:
    return ssl._create_unverified_context()


def build_http_proxy_url(*, ingest_data: bool = False) -> str:
    params = [f"akto_connector={AKTO_CONNECTOR}"]
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


def read_step_cache(conversation_id: str, step_idx: int) -> Optional[Dict[str, Any]]:
    cache_key = f"{conversation_id}_{step_idx}"
    if not os.path.exists(STEP_CACHE_PATH):
        return None
    try:
        with open(STEP_CACHE_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        entry = data.get(cache_key)
        logger.debug(f"Step cache hit: {cache_key}" if entry else f"Step cache miss: {cache_key}")
        return entry
    except (json.JSONDecodeError, OSError) as e:
        logger.warning(f"Could not read step cache: {e}")
        return None


def mcp_mirror_host(mcp_server_name: str) -> str:
    return f"{DEVICE_ID}.{AKTO_CONNECTOR_VALUE}.{mcp_server_name}"


def build_ingest_payload(
    tool_name: str,
    tool_args: Any,
    tool_output: str,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    mcp_arguments: Any = None,
    step_idx: int,
    error: str,
    session_info: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    tags: Dict[str, str] = {}
    if is_mcp:
        tags["mcp-server"] = "MCP Server"
        tags["mcp-client"] = AKTO_CONNECTOR_VALUE
    else:
        tags["gen-ai"] = "Gen AI"
        tags["ai-agent"] = AKTO_CONNECTOR_VALUE
    if MODE == "atlas":
        tags["source"] = CONTEXT_SOURCE

    host = mcp_mirror_host(mcp_server_name) if is_mcp else ANTIGRAVITY_API_URL.replace("https://", "").replace("http://", "")

    req_hdr: Dict[str, str] = {
        "host": host,
        "x-antigravitycli-hook": "PostToolUse",
        "content-type": "application/json",
    }
    if is_mcp and mcp_server_name:
        req_hdr["x-mcp-server"] = mcp_server_name
    if session_info:
        req_hdr.update(installer_headers(session_info))

    if is_mcp:
        args_for_jsonrpc = mcp_arguments if mcp_arguments is not None else tool_args
        request_payload = json.dumps({
            "jsonrpc": "2.0",
            "method": "tools/call",
            "params": {
                "name": mcp_tool_name,
                "arguments": args_for_jsonrpc if isinstance(args_for_jsonrpc, dict) else {"input": args_for_jsonrpc},
            },
            "id": step_idx,
        })
    else:
        request_payload = json.dumps({"body": tool_args, "toolName": tool_name})

    resp_body: Dict[str, Any] = {"output": tool_output}
    if error:
        resp_body["error"] = error
    response_payload = json.dumps(resp_body)

    status_code = "500" if error else "200"

    return {
        "path": "/mcp" if is_mcp else f"/tool/{tool_name}",
        "requestHeaders": json.dumps(req_hdr),
        "responseHeaders": json.dumps({"x-antigravitycli-hook": "PostToolUse"}),
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


def ingest_tool_result(
    tool_name: str,
    tool_args: Any,
    tool_output: str,
    *,
    is_mcp: bool,
    mcp_server_name: str,
    mcp_tool_name: str,
    mcp_arguments: Any = None,
    step_idx: int,
    error: str,
    session_info: Optional[Dict[str, Any]] = None,
) -> None:
    if not AKTO_DATA_INGESTION_URL:
        logger.info("AKTO_DATA_INGESTION_URL not set, skipping ingestion")
        return
    if not is_mcp and not AKTO_INGEST_NON_MCP_TOOLS:
        logger.info("Skipping non-MCP tool ingestion (set AKTO_INGEST_NON_MCP_TOOLS=true)")
        return

    logger.info(f"Ingesting PostToolUse result: tool={tool_name!r}, stepIdx={step_idx}, error={error!r}")
    try:
        payload = build_ingest_payload(
            tool_name, tool_args, tool_output,
            is_mcp=is_mcp, mcp_server_name=mcp_server_name, mcp_tool_name=mcp_tool_name,
            mcp_arguments=mcp_arguments, step_idx=step_idx, error=error, session_info=session_info,
        )
        post_payload_json(build_http_proxy_url(ingest_data=True), payload)
        logger.info("PostToolUse ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def ingest_minimal_payload(
    step_idx: int,
    error: str,
    conversation_id: str,
    session_info: Optional[Dict[str, Any]] = None,
) -> None:
    if not AKTO_DATA_INGESTION_URL:
        return
    logger.info(f"Ingesting minimal PostToolUse (cache miss): stepIdx={step_idx}")
    try:
        req_hdr: Dict[str, str] = {
            "host": ANTIGRAVITY_API_URL.replace("https://", "").replace("http://", ""),
            "x-antigravitycli-hook": "PostToolUse",
            "content-type": "application/json",
        }
        if session_info:
            req_hdr.update(installer_headers(session_info))

        resp_body: Dict[str, Any] = {"stepIdx": step_idx}
        if error:
            resp_body["error"] = error
        payload = {
            "path": "/tool/unknown",
            "requestHeaders": json.dumps(req_hdr),
            "responseHeaders": json.dumps({"x-antigravitycli-hook": "PostToolUse"}),
            "method": "POST",
            "requestPayload": json.dumps({"stepIdx": step_idx, "conversationId": conversation_id}),
            "responsePayload": json.dumps(resp_body),
            "ip": get_username(),
            "destIp": "127.0.0.1",
            "time": str(int(time.time() * 1000)),
            "statusCode": "500" if error else "200",
            "type": "HTTP/1.1",
            "status": "500" if error else "200",
            "akto_account_id": "1000000",
            "akto_vxlan_id": 0,
            "is_pending": "false",
            "source": "MIRRORING",
            "direction": None,
            "process_id": None,
            "socket_id": None,
            "daemonset_id": None,
            "enabled_graph": None,
            "tag": json.dumps({"gen-ai": "Gen AI"}),
            "metadata": json.dumps({"cache_miss": True, "stepIdx": step_idx}),
            "contextSource": CONTEXT_SOURCE,
        }
        post_payload_json(build_http_proxy_url(ingest_data=True), payload)
        logger.info("Minimal ingestion successful")
    except Exception as e:
        logger.error(f"Minimal ingestion error: {e}")


def main():
    if hasattr(sys.stdin, "reconfigure"):
        sys.stdin.reconfigure(encoding="utf-8")
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")

    logger.info(f"=== PostToolUse Hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.stdout.write("{}")
        sys.exit(0)

    logger.info(f"Raw PostToolUse input: {json.dumps(input_data)}")

    step_idx = int(input_data.get("stepIdx") or 0)
    error = str(input_data.get("error") or "")
    conversation_id = str(input_data.get("conversationId") or "")
    transcript_path = str(input_data.get("transcriptPath") or "")

    logger.info(f"PostToolUse: stepIdx={step_idx}, conversationId={conversation_id!r}, error={error!r}")

    input_data["hook_event_name"] = "PostToolUse"
    session_info: Optional[Dict[str, Any]] = None
    try:
        session_info = resolve_session_info(input_data, logger, is_prompt_hook=False)
    except Exception as e:
        logger.warning(f"resolve_session_info failed: {e}")

    cached = read_step_cache(conversation_id, step_idx)
    if not cached:
        logger.warning(f"Step cache miss for {conversation_id}_{step_idx} — ingesting minimal payload")
        ingest_minimal_payload(step_idx, error, conversation_id, session_info)
        sys.stdout.write("{}")
        sys.exit(0)

    tool_name = str(cached.get("tool_name") or "unknown")
    tool_args = cached.get("tool_args") or {}
    is_mcp = bool(cached.get("is_mcp"))
    mcp_server_name = str(cached.get("mcp_server_name") or "")
    mcp_tool_name = str(cached.get("mcp_tool_name") or "")
    mcp_arguments = cached.get("mcp_arguments") if cached.get("mcp_arguments") is not None else tool_args

    # tool output is available via transcript if needed; for ingestion pass stepIdx as marker
    tool_output = json.dumps({"stepIdx": step_idx, "error": error}) if error else json.dumps({"stepIdx": step_idx})

    ingest_tool_result(
        tool_name, tool_args, tool_output,
        is_mcp=is_mcp, mcp_server_name=mcp_server_name, mcp_tool_name=mcp_tool_name,
        mcp_arguments=mcp_arguments, step_idx=step_idx, error=error, session_info=session_info,
    )

    sys.stdout.write("{}")
    sys.exit(0)


if __name__ == "__main__":
    main()
