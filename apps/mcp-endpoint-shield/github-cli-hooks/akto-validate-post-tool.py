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

# SSL Configuration
SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"


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
        }


def setup_logging(log_dir: str):
    os.makedirs(log_dir, exist_ok=True)
    logger = logging.getLogger(__name__)
    logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    if not logger.handlers:
        file_handler = logging.FileHandler(os.path.join(log_dir, "validate-post-tool.log"))
        file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
        logger.addHandler(file_handler)
        console_handler = logging.StreamHandler(sys.stderr)
        console_handler.setLevel(logging.ERROR)
        logger.addHandler(console_handler)
    return logger


def create_ssl_context():
    return ssl._create_unverified_context()


def build_http_proxy_url(cfg: dict, *, response_guardrails: bool = False, ingest_data: bool = True) -> str:
    """Build Akto HTTP proxy URL with query parameters."""
    params = []
    if response_guardrails:
        params.append("response_guardrails=true")
    params.append(f"akto_connector={cfg['connector']}")
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
    result_text: str,
    status_code: str,
    result_type: str,
    cfg: dict
) -> Dict[str, Any]:
    """Build request payload for Akto data ingestion."""
    tags = {"gen-ai": "Gen AI", "tool-use": "Tool Execution"}
    if MODE == "atlas":
        tags["ai-agent"] = cfg["ai_agent_tag"]
        tags["source"] = CONTEXT_SOURCE

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = cfg["api_url"].replace("https://", "").replace("http://", "")
    hook_header = cfg["hook_header"]

    request_headers = json.dumps({
        "host": host,
        hook_header: "PostToolUse",
        "content-type": "application/json"
    })

    response_headers = json.dumps({
        hook_header: "PostToolUse",
        "content-type": "application/json"
    })

    request_payload = json.dumps({
        "body": json.dumps({"toolName": tool_name, "toolArgs": tool_args})
    })

    # VSCode response payload omits resultType; github-cli includes it
    if cfg["is_vscode"]:
        response_payload = json.dumps({
            "body": json.dumps({"result": result_text})
        })
    else:
        response_payload = json.dumps({
            "body": json.dumps({"resultType": result_type, "result": result_text})
        })

    return {
        "path": f"/copilot/tool/{tool_name}",
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
        "contextSource": "ENDPOINT"
    }


def _guardrails_behaviour_value(behaviour: Any) -> str:
    return str(behaviour or "").strip().lower()


def _is_warn_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "warn"


def _is_alert_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "alert"


def call_guardrails(
    tool_name: str,
    tool_args: str,
    result_text: str,
    cfg: dict,
    logger,
) -> Tuple[bool, str, str]:
    if not tool_args or not result_text:
        return True, "", ""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing request (fail-open)")
        return True, "", ""

    logger.info(f"Validating tool result against guardrails: {tool_name}")
    try:
        request_body = build_akto_request(tool_name, tool_args, result_text, "200", "unknown", cfg)
        result = post_to_akto(
            build_http_proxy_url(cfg, response_guardrails=True, ingest_data=False),
            request_body,
            logger,
        )
        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")
        behaviour = guardrails_result.get("behaviour", "") or guardrails_result.get("Behaviour", "")

        if allowed:
            logger.info(f"Tool result ALLOWED for {tool_name}")
        else:
            logger.warning(f"Tool result DENIED for {tool_name}: {reason}")

        return allowed, reason, behaviour
    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, "", ""


def posttool_fingerprint(tool_name: str, tool_args: str, result_text: str) -> str:
    canonical = json.dumps(
        {"t": tool_name, "a": tool_args, "r": result_text},
        sort_keys=True,
        ensure_ascii=False,
    )
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
    return False, reason


def ingest_blocked_request(
    tool_name: str,
    tool_args: str,
    result_text: str,
    reason: str,
    cfg: dict,
    logger,
):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    logger.info("Ingesting blocked tool result data")
    try:
        request_body = build_akto_request(tool_name, tool_args, result_text, "403", "denied", cfg)
        request_body["responseHeaders"] = json.dumps({
            cfg["hook_header"]: "PostToolUse",
            "x-blocked-by": "Akto Proxy",
            "content-type": "application/json",
        })
        request_body["responsePayload"] = json.dumps({
            "body": json.dumps({
                "x-blocked-by": "Akto Proxy",
                "reason": reason or "Policy violation",
            })
        })
        request_body["statusCode"] = "403"
        request_body["status"] = "403"
        post_to_akto(build_http_proxy_url(cfg, ingest_data=True), request_body, logger)
        logger.info("Blocked tool result ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def ingest_tool_result(
    tool_name: str,
    tool_args: str,
    result_text: str,
    status_code: str,
    result_type: str,
    cfg: dict,
    logger,
):
    """Ingest tool execution result to Akto for analytics."""
    if not AKTO_DATA_INGESTION_URL:
        logger.info("Skipping ingestion - no Akto URL configured")
        return

    logger.info(f"Ingesting tool result: {tool_name}")

    try:
        request_body = build_akto_request(tool_name, tool_args, result_text, status_code, result_type, cfg)
        post_to_akto(build_http_proxy_url(cfg, response_guardrails=not AKTO_SYNC_MODE, ingest_data=True), request_body, logger)
        logger.info("Tool result ingested successfully")
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
    send_heartbeat(log_dir, logger)
    warn_state_path = os.path.join(log_dir, "akto_posttool_warn_pending.json")

    logger.info(f"=== Post-Tool Use Hook - Connector: {connector}, Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    if LOG_PAYLOADS:
        logger.debug(f"Input: {json.dumps(input_data)}")

    logger.info(f"MODE: {MODE}, API_URL: {cfg['api_url']}")

    # Parse input — key names and result format differ between connectors
    if cfg["is_vscode"]:
        tool_name = input_data.get("tool_name", "unknown")
        tool_input = input_data.get("tool_input", {})
        tool_args = json.dumps(tool_input) if isinstance(tool_input, dict) else str(tool_input)
        tool_response = input_data.get("tool_response", "")
        result_text = tool_response if isinstance(tool_response, str) else str(tool_response)
        result_type = "unknown"
        status_code = "200"
    else:
        tool_name = input_data.get("toolName") or input_data.get("tool_name", "unknown")
        tool_args = input_data.get("toolArgs") or json.dumps(input_data.get("tool_input", {}))
        tool_result = input_data.get("toolResult", {})
        result_text = tool_result.get("textResultForLlm", "")
        result_type = tool_result.get("resultType", "unknown")
        status_code = {"failure": "500", "denied": "403"}.get(result_type, "200")

    logger.info(f"Tool: {tool_name}")
    if LOG_PAYLOADS:
        logger.debug(f"Tool args: {tool_args}")
        logger.debug(f"Result: {result_text[:500]}")
    else:
        logger.info(f"Result preview: {result_text[:100]}...")

    if AKTO_SYNC_MODE:
        gr_allowed, gr_reason, behaviour = call_guardrails(tool_name, tool_args, result_text, cfg, logger)
        fingerprint = posttool_fingerprint(tool_name, tool_args, result_text)
        allowed, _ = apply_warn_resubmit_flow(gr_allowed, gr_reason, behaviour, fingerprint, warn_state_path, logger)

        if not allowed:
            if _is_warn_behaviour(behaviour):
                block_reason = (
                    "Warning!!, tool result blocked, please review it. Send again to bypass. "
                    f"Reason for blocking: {gr_reason}"
                )
            else:
                block_reason = f"Tool result blocked: {gr_reason}"

            output = {
                "decision": "block",
                "reason": block_reason,
                "hookSpecificOutput": {
                    "hookEventName": "PostToolUse",
                    "additionalContext": gr_reason or "Policy violation",
                },
            }
            logger.warning(f"BLOCKING tool result - Tool: {tool_name}, Reason: {gr_reason}")
            print(json.dumps(output))
            ingest_blocked_request(tool_name, tool_args, result_text, gr_reason, cfg, logger)
            sys.exit(0)

    ingest_tool_result(tool_name, tool_args, result_text, status_code, result_type, cfg, logger)

    logger.info("Hook completed")
    sys.exit(0)


if __name__ == "__main__":
    main()
