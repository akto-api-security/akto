#!/usr/bin/env python3
# currently only Bash tool is supported by Codex CLI

import hashlib
import json
import logging
import os
import re
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, Set, Tuple, Union
from urllib.parse import quote

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "common"))
from akto_machine_id import get_machine_id, get_username
from akto_skill_blocked import is_skill_blocked

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.codex/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-pre-tool.log"))
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
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "codex_cli")
AKTO_TOKEN = os.getenv("AKTO_TOKEN", "")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
# Built-in (e.g. Bash) tool traffic: mirror non-MCP path; blocked-request ingestion off by default (claude-cli-hooks).
AKTO_INGEST_NON_MCP_TOOLS = os.getenv("AKTO_INGEST_NON_MCP_TOOLS", "false").lower() == "true"
NON_MCP_TOOL_PATH_PREFIX = os.getenv("NON_MCP_TOOL_PATH_PREFIX", "/tool")
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_pretool_warn_pending.json")

SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"

# Auto-detect Codex API host and path from the same env vars Codex CLI uses
def _detect_codex_api():
    base_url = os.getenv("OPENAI_BASE_URL")
    if base_url:
        return base_url.rstrip("/"), "/v1/responses"
    if os.getenv("OPENAI_API_KEY"):
        return "https://api.openai.com", "/v1/responses"
    return "https://chatgpt.com", "/backend-api/codex/responses"

_detected_host, CODEX_API_PATH = _detect_codex_api()
if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    CODEX_API_HOST = f"https://{device_id}.ai-agent.codexcli" if device_id else _detected_host
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, CODEX_API_HOST: {CODEX_API_HOST}, CODEX_API_PATH: {CODEX_API_PATH}")
else:
    CODEX_API_HOST = _detected_host
    logger.info(f"MODE: {MODE}, CODEX_API_HOST: {CODEX_API_HOST}, CODEX_API_PATH: {CODEX_API_PATH}")


def create_ssl_context():
    return ssl._create_unverified_context()


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
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

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
                logger.debug(f"Response body: {raw[:1000]}...")

            try:
                return json.loads(raw)
            except json.JSONDecodeError:
                return raw
    except Exception as e:
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(f"API CALL FAILED after {duration_ms}ms: {e}")
        raise


def build_validation_request(
    tool_name: str, tool_input: Any, session_info: dict = None
) -> Dict[str, Any]:
    tags = {"gen-ai": "Gen AI", "tool_name": tool_name}
    if MODE == "atlas":
        tags["ai-agent"] = "codexcli"
        tags["source"] = CONTEXT_SOURCE

    host = CODEX_API_HOST.replace("https://", "").replace("http://", "")

    req_headers: Dict[str, str] = {
        "host": host,
        "x-codex-hook": "PreToolUse",
        "content-type": "application/json",
    }
    if session_info:
        for key, value in session_info.items():
            if value is not None:
                req_headers[f"x-akto-installer-{key}"] = str(value)

    request_headers = json.dumps(req_headers)
    response_headers = json.dumps({"x-codex-hook": "PreToolUse"})
    request_payload = json.dumps({"body": tool_input, "toolName": tool_name})
    response_payload = json.dumps({})

    return {
        "path": non_mcp_ingest_path(tool_name),
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


def call_guardrails(
    tool_name: str, tool_input: Any, session_info: dict = None
) -> Tuple[bool, str, str]:
    if not tool_input:
        return True, "", ""

    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing request (fail-open)")
        return True, "", ""

    logger.info(f"Validating tool request for: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input payload: {json.dumps(tool_input)[:500]}...")

    try:
        request_body = build_validation_request(tool_name, tool_input, session_info)
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=True),
            request_body,
        )

        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")
        behaviour = guardrails_result.get("behaviour", "") or guardrails_result.get(
            "Behaviour", ""
        )

        if allowed:
            logger.info(f"Request ALLOWED for {tool_name}")
        else:
            logger.warning(f"Request DENIED for {tool_name}: {reason}")

        return allowed, reason, behaviour
    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, "", ""


def pretool_fingerprint(tool_name: str, tool_input: Any) -> str:
    canonical = json.dumps(
        {"t": tool_name, "i": tool_input},
        sort_keys=True,
        ensure_ascii=False,
    )
    return hashlib.sha256(canonical.encode("utf-8")).hexdigest()


def _guardrails_behaviour_value(behaviour: Any) -> str:
    return str(behaviour or "").strip().lower()


def _is_warn_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "warn"


def _is_alert_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "alert"


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
        logger.info(
            "Alert behaviour: allowing despite violation (server-side alert only)"
        )
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
    tool_name: str, tool_input: Any, reason: str, session_info: dict = None
):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    if not AKTO_INGEST_NON_MCP_TOOLS:
        logger.info(
            "Skipping non-MCP blocked-request ingestion (set AKTO_INGEST_NON_MCP_TOOLS=true to re-enable)"
        )
        return

    try:
        request_body = build_validation_request(tool_name, tool_input, session_info)
        request_body["responseHeaders"] = json.dumps(
            {
                "x-codex-hook": "PreToolUse",
                "x-blocked-by": "Akto Proxy",
                "content-type": "application/json",
            }
        )
        request_body["responsePayload"] = json.dumps(
            {"body": {"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"}}
        )
        request_body["statusCode"] = "403"
        request_body["status"] = "403"
        post_payload_json(
            build_http_proxy_url(
                guardrails=False, response_guardrails=False, ingest_data=True
            ),
            request_body,
        )
        logger.info("Blocked tool request ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info(f"=== PreToolUse hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

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
        "hook_event_name",
        "model",
        "turn_id",
        "tool_use_id",
    ):
        value = input_data.get(field)
        if value is not None:
            session_info[field] = value

    tool_name = str(input_data.get("tool_name") or "")
    tool_input = input_data.get("tool_input") or {}
    session_id = input_data.get("session_id", "")

    logger.info(f"Session: {session_id}, Processing tool request: {tool_name}")

    if is_skill_blocked(tool_name, logger):
        output = {
            "hookSpecificOutput": {
                "hookEventName": "PreToolUse",
                "permissionDecision": "deny",
                "permissionDecisionReason": f"Skill '{tool_name}' is blocked by your organization's policy",
            }
        }
        logger.warning(f"BLOCKING by org policy - Tool: {tool_name}")
        print(json.dumps(output))
        sys.exit(0)

    if AKTO_SYNC_MODE:
        gr_allowed, gr_reason, behaviour = call_guardrails(
            tool_name, tool_input, session_info
        )
        fingerprint = pretool_fingerprint(tool_name, tool_input)
        allowed, _ = apply_warn_resubmit_flow(
            gr_allowed, gr_reason, behaviour, fingerprint
        )

        if not allowed:
            if _is_warn_behaviour(behaviour):
                block_reason = (
                    "Warning!!, tool request blocked, please review it. Send again to bypass. "
                    f"Reason for blocking: {gr_reason}"
                )
            else:
                block_reason = gr_reason or "Policy violation"

            # PreToolUse: documented deny shape (hookSpecificOutput only; no continue/stopReason).
            output = {
                "hookSpecificOutput": {
                    "hookEventName": "PreToolUse",
                    "permissionDecision": "deny",
                    "permissionDecisionReason": block_reason,
                }
            }
            logger.warning(
                f"BLOCKING tool request - Tool: {tool_name}, Reason: {gr_reason}"
            )
            print(json.dumps(output))
            ingest_blocked_request(
                tool_name, tool_input, gr_reason or "Policy violation", session_info
            )
            sys.exit(0)

    logger.info(f"Tool request allowed for {tool_name}")
    sys.exit(0)


if __name__ == "__main__":
    main()
