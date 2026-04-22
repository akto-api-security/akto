#!/usr/bin/env python3
"""
Cursor Pre-Tool Hook - Tool Request Validation via Akto HTTP Proxy API
Validates all tool calls (Shell, Read, Write, MCP, Task, etc.) before execution.
Triggered by preToolUse hook. CAN block tool execution.
"""
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

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.cursor/akto/tool-logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-pre-tool.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_handler.setFormatter(logging.Formatter('%(asctime)s - %(levelname)s - %(message)s'))
logger.addHandler(file_handler)

console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)
logger.addHandler(console_handler)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = "cursor"
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_pretool_warn_pending.json")

if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    API_URL = f"https://{device_id}.ai-agent.cursor" if device_id else "https://api.anthropic.com"
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, API_URL: {API_URL}")
else:
    API_URL = os.getenv("API_URL", "https://api.anthropic.com")
    logger.info(f"MODE: {MODE}, API_URL: {API_URL}")


def create_ssl_context():
    return ssl._create_unverified_context()


def build_http_proxy_url(*, guardrails: bool = False, ingest_data: bool) -> str:
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
            logger.info(f"API RESPONSE: Status {response.getcode()}, Duration: {duration_ms}ms, Size: {len(raw)} bytes")
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


def build_ingestion_payload(tool_name: str, tool_input_str: str, status_code: str = "200") -> Dict[str, Any]:
    tags = {"gen-ai": "Gen AI", "tool-use": "Tool Execution"}
    if MODE == "atlas":
        tags["ai-agent"] = "cursor"
        tags["source"] = CONTEXT_SOURCE

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = API_URL.replace("https://", "").replace("http://", "")

    request_headers = json.dumps({
        "host": host,
        "x-cursor-hook": "preToolUse",
        "content-type": "application/json",
    })
    response_headers = json.dumps({
        "x-cursor-hook": "preToolUse",
        "content-type": "application/json",
    })
    request_payload = json.dumps({
        "body": json.dumps({"toolName": tool_name, "toolInput": tool_input_str})
    })
    response_payload = json.dumps({})

    return {
        "path": f"/cursor/tool/{tool_name}",
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
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
        "contextSource": CONTEXT_SOURCE,
    }


def _guardrails_behaviour_value(behaviour: Any) -> str:
    return str(behaviour or "").strip().lower()


def _is_warn_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "warn"


def _is_alert_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "alert"


def call_guardrails(tool_name: str, tool_input_str: str) -> Tuple[bool, str, str]:
    if not tool_input_str.strip() or tool_input_str == "{}":
        return True, "", ""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing (fail-open)")
        return True, "", ""

    logger.info(f"Validating tool request: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {tool_input_str[:500]}")

    try:
        request_body = build_ingestion_payload(tool_name, tool_input_str)
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=False),
            request_body,
        )
        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")
        behaviour = guardrails_result.get("behaviour", "") or guardrails_result.get("Behaviour", "")

        if allowed:
            logger.info(f"Tool ALLOWED: {tool_name}")
        else:
            logger.warning(f"Tool DENIED: {tool_name} - {reason}")

        return allowed, reason, behaviour

    except Exception as e:
        logger.error(f"Guardrails error: {e}")
        return True, "", ""


def tool_fingerprint(tool_name: str, tool_input_str: str) -> str:
    canonical = json.dumps({"t": tool_name, "i": tool_input_str}, sort_keys=True, ensure_ascii=False)
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
    return False, reason


def ingest_blocked_tool(tool_name: str, tool_input_str: str, reason: str):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return
    logger.info("Ingesting blocked tool request")
    try:
        request_body = build_ingestion_payload(tool_name, tool_input_str, status_code="403")
        request_body["responseHeaders"] = json.dumps({
            "x-cursor-hook": "preToolUse",
            "x-blocked-by": "Akto Proxy",
            "content-type": "application/json",
        })
        request_body["responsePayload"] = json.dumps({
            "body": json.dumps({"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"})
        })
        post_payload_json(build_http_proxy_url(ingest_data=True), request_body)
        logger.info("Blocked tool ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def ingest_allowed_tool(tool_name: str, tool_input_str: str):
    if not AKTO_DATA_INGESTION_URL:
        return
    logger.info(f"Ingesting allowed tool request: {tool_name}")
    try:
        request_body = build_ingestion_payload(tool_name, tool_input_str)
        post_payload_json(build_http_proxy_url(ingest_data=True), request_body)
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info(f"=== Pre-Tool Hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        print(json.dumps({"permission": "allow"}))
        sys.exit(0)

    tool_name = input_data.get("tool_name", "unknown")
    tool_input = input_data.get("tool_input", {})
    tool_input_str = json.dumps(tool_input) if isinstance(tool_input, dict) else str(tool_input)

    logger.info(f"Tool: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool input: {tool_input_str[:500]}")
    else:
        logger.info(f"Tool input preview: {tool_input_str[:100]}")

    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, skipping")
        print(json.dumps({"permission": "allow"}))
        sys.exit(0)

    if AKTO_SYNC_MODE:
        gr_allowed, gr_reason, behaviour = call_guardrails(tool_name, tool_input_str)
        fingerprint = tool_fingerprint(tool_name, tool_input_str)
        allowed, _ = apply_warn_resubmit_flow(gr_allowed, gr_reason, behaviour, fingerprint)

        if not allowed:
            if _is_warn_behaviour(behaviour):
                user_msg = (
                    f"Warning!!, tool blocked, please review it. Send again to bypass. "
                    f"Reason for blocking: {gr_reason}"
                )
                agent_msg = (
                    f"Warning: Tool '{tool_name}' was flagged. Resend to bypass. "
                    f"Reason: {gr_reason}"
                )
            else:
                user_msg = f"Tool '{tool_name}' blocked by Akto security policy: {gr_reason}"
                agent_msg = f"Blocked by Akto Guardrails: {gr_reason}"

            output = {
                "permission": "deny",
                "user_message": user_msg,
                "agent_message": agent_msg,
            }
            logger.warning(f"BLOCKING tool: {tool_name} - Reason: {gr_reason}")
            print(json.dumps(output))
            ingest_blocked_tool(tool_name, tool_input_str, gr_reason)
            sys.exit(0)

    ingest_allowed_tool(tool_name, tool_input_str)
    logger.info(f"Tool ALLOWED: {tool_name}")
    print(json.dumps({"permission": "allow"}))
    sys.exit(0)


if __name__ == "__main__":
    main()
