#!/usr/bin/env python3

import hashlib
import json
import logging
import os
import re
import ssl
import sys
import tempfile
import time
import urllib.request
from typing import Any, Dict, Optional, Set, Tuple, Union
from akto_machine_id import get_machine_id, get_username

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.gemini/akto/chat-logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_response_warn_pending.json")

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-response.log"))
    file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    file_formatter = logging.Formatter("%(asctime)s - %(levelname)s - %(message)s")
    file_handler.setFormatter(file_formatter)
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    logger.addHandler(console_handler)

AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
GEMINI_API_URL = os.getenv("GEMINI_API_URL", "https://generativelanguage.googleapis.com")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
MODE = os.getenv("MODE", "argus").lower()
AKTO_CONNECTOR = "gemini_cli"

# SSL Configuration
SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"

if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    GEMINI_API_URL = f"https://{device_id}.ai-agent.gemini" if device_id else GEMINI_API_URL
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, GEMINI_API_URL: {GEMINI_API_URL}")
else:
    logger.info(f"MODE: {MODE}, GEMINI_API_URL: {GEMINI_API_URL}")

AKTO_CHUNKS_DIR = os.path.join(tempfile.gettempdir(), "akto_gemini_cli_chunks")


def create_ssl_context():
    return ssl._create_unverified_context()


def uuid_to_ipv6_simple(uuid_str):
    hex_str = uuid_str.replace("-", "").lower()
    return ":".join(hex_str[i:i+4] for i in range(0, 32, 4))


def build_http_proxy_url(*, guardrails: bool = False, response_guardrails: bool = False, ingest_data: bool) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    if response_guardrails:
        params.append("response_guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_to_akto(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload, default=str)[:1000]}...")

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


def extract_session_metadata(input_data: dict) -> Dict[str, Any]:
    metadata = {}
    for key in ("session_id", "transcript_path", "cwd", "hook_event_name", "timestamp"):
        value = input_data.get(key)
        if value:
            metadata["hook_timestamp" if key == "timestamp" else key] = value
    return metadata


def get_chunks_filepath(session_id: str) -> str:
    return os.path.join(AKTO_CHUNKS_DIR, f"{session_id}.txt")


THINKING_BLOCK_PATTERN = re.compile(
    r"^\*\*[^*]+\*\*\s*\n\n[\s\S]*?\n\n\n+",
    re.MULTILINE,
)


def strip_thinking_blocks(text: str) -> str:
    if not text or not text.strip():
        return text
    stripped = text
    while True:
        next_stripped = THINKING_BLOCK_PATTERN.sub("", stripped)
        if next_stripped == stripped:
            break
        stripped = next_stripped
    return stripped.strip()


def extract_chunk_text(llm_response: Dict[str, Any]) -> str:
    candidates = llm_response.get("candidates") or []
    if not candidates:
        return ""
    parts = (candidates[0].get("content") or {}).get("parts") or []
    texts = []
    for part in parts:
        if isinstance(part, str):
            texts.append(part)
        elif isinstance(part, dict) and not part.get("thought", False):
            text = part.get("text", "")
            if text:
                texts.append(text)
    return "".join(texts)


def append_chunk(session_id: str, chunk_text: str) -> None:
    os.makedirs(AKTO_CHUNKS_DIR, exist_ok=True)
    with open(get_chunks_filepath(session_id), "a", encoding="utf-8") as f:
        f.write(chunk_text)


def read_accumulated_text_and_clear_file(session_id: str) -> str:
    filepath = get_chunks_filepath(session_id)
    try:
        with open(filepath, "r", encoding="utf-8") as f:
            text = f.read()
    except FileNotFoundError:
        text = ""
    try:
        os.remove(filepath)
    except OSError:
        pass
    return text


def build_akto_request(
    user_prompt: str,
    full_response_text: str,
    model: Optional[str] = None,
    session_metadata: Optional[Dict[str, Any]] = None,
    usage_metadata: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    tags = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        tags["ai-agent"] = "geminicli"
        tags["source"] = "ENDPOINT"

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = GEMINI_API_URL.replace("https://", "").replace("http://", "")

    request_headers = json.dumps({
        "host": host,
        "x-gemini-hook": "AfterModel",
        "content-type": "application/json"
    })

    response_headers = json.dumps({
        "x-gemini-hook": "AfterModel",
        "content-type": "application/json"
    })

    request_payload = json.dumps({
        "body": user_prompt
    })

    response_body: Dict[str, Any] = {"result": full_response_text}
    if usage_metadata:
        response_body["usageMetadata"] = usage_metadata

    response_payload = json.dumps({
        "body": json.dumps(response_body)
    })

    metadata: Dict[str, Any] = {"model": model or ""}
    if MODE == "atlas":
        metadata["machine_id"] = device_id
        metadata["log_storage"] = {"type": "local_file", "path": LOG_DIR}
    if session_metadata:
        metadata["gemini_cli_session"] = session_metadata

    return {
        "path": "/gemini/chat",
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
        "akto_vxlan_id": device_id,
        "is_pending": "false",
        "source": "MIRRORING",
        "direction": None,
        "process_id": None,
        "socket_id": None,
        "daemonset_id": None,
        "enabled_graph": None,
        "tag": json.dumps(tags),
        "metadata": json.dumps(metadata),
        "contextSource": "ENDPOINT"
    }


# ---------- Warn / alert behaviour (lifted from claude-cli-hooks) ----------

def _guardrails_behaviour_value(behaviour: Any) -> str:
    return str(behaviour or "").strip().lower()


def _is_warn_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "warn"


def _is_alert_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "alert"


def response_fingerprint(user_prompt: str, full_response_text: str) -> str:
    canonical = json.dumps({"p": user_prompt, "r": full_response_text}, sort_keys=True, ensure_ascii=False)
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


def call_response_guardrails(
    user_prompt: str,
    full_response_text: str,
    model: Optional[str] = None,
    session_metadata: Optional[Dict[str, Any]] = None,
    usage_metadata: Optional[Dict[str, Any]] = None,
) -> Tuple[bool, str, str]:
    """Synchronous response-side guardrails check. Returns (allowed, reason, behaviour)."""
    if not user_prompt.strip() or not full_response_text.strip():
        return True, "", ""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing response (fail-open)")
        return True, "", ""

    logger.info("Validating response against guardrails (response_guardrails=true)")
    try:
        request_body = build_akto_request(
            user_prompt, full_response_text, model=model,
            session_metadata=session_metadata, usage_metadata=usage_metadata,
        )
        result = post_to_akto(
            build_http_proxy_url(guardrails=False, response_guardrails=True, ingest_data=False),
            request_body,
        )
        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")
        behaviour = guardrails_result.get("behaviour", "") or guardrails_result.get("Behaviour", "")
        if allowed:
            logger.info("Response ALLOWED by guardrails")
        else:
            logger.warning(f"Response DENIED by guardrails: {reason} (behaviour={behaviour!r})")
        return allowed, reason, behaviour
    except Exception as e:
        logger.error(f"Response guardrails error: {e}", exc_info=True)
        return True, "", ""


def send_ingestion_data(
    user_prompt: str,
    full_response_text: str,
    model: Optional[str] = None,
    streaming: Optional[bool] = None,
    session_metadata: Optional[Dict[str, Any]] = None,
    usage_metadata: Optional[Dict[str, Any]] = None,
):
    if not user_prompt.strip():
        return

    logger.info(f"Ingesting response data (prompt_len={len(user_prompt)}, response_len={len(full_response_text)})")
    if LOG_PAYLOADS:
        logger.debug(f"Prompt preview: {user_prompt[:500]}...")
        logger.debug(f"Response preview: {full_response_text[:500]}...")

    try:
        request_body = build_akto_request(
            user_prompt,
            full_response_text,
            model=model,
            session_metadata=session_metadata,
            usage_metadata=usage_metadata,
        )
        post_to_akto(
            build_http_proxy_url(
                response_guardrails=not AKTO_SYNC_MODE,
                ingest_data=True,
            ),
            request_body,
        )
        logger.info("Response ingestion successful")

    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def parse_user_prompt(messages: list) -> str:
    for message in reversed(messages):
        if message.get("role") == "user":
            content = message.get("content", "")
            if isinstance(content, str):
                return content
            return "".join(part.get("text", "") for part in content if isinstance(part, dict))
    return ""


def extract_from_after_model(input_data: dict) -> tuple[str, Dict[str, Any], Optional[str], Optional[bool], Dict[str, Any]]:
    llm_request = input_data.get("llm_request") or {}
    llm_response = input_data.get("llm_response") or {}
    prompt = parse_user_prompt(llm_request.get("messages") or [])

    streaming = (llm_request.get("config") or {}).get("streaming")
    session_metadata = extract_session_metadata(input_data)
    return prompt, llm_response, llm_request.get("model"), streaming, session_metadata


def main():
    logger.info(f"=== Response hook execution started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")
    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(2)

    hook_event = input_data.get("hook_event_name", "")

    if hook_event != "AfterModel":
        sys.stdout.write(json.dumps({}))
        sys.exit(0)

    session_id = input_data.get("session_id", "default")
    user_prompt, llm_response, model, streaming, session_metadata = extract_from_after_model(input_data)
    logger.info(f"AfterModel: model={model or 'unknown'}, streaming={streaming}")

    chunk_text = extract_chunk_text(llm_response)
    if chunk_text:
        append_chunk(session_id, chunk_text)
    elif LOG_PAYLOADS:
        logger.debug("No chunk text extracted for this event")

    candidates = llm_response.get("candidates") or []
    finish_reason = candidates[0].get("finishReason") if candidates else None

    if user_prompt and finish_reason:
        full_response_text = read_accumulated_text_and_clear_file(session_id)
        full_response_text = strip_thinking_blocks(full_response_text)
        usage_metadata = llm_response.get("usageMetadata")

        if AKTO_SYNC_MODE:
            gr_allowed, gr_reason, behaviour = call_response_guardrails(
                user_prompt, full_response_text,
                model=model, session_metadata=session_metadata, usage_metadata=usage_metadata,
            )
            fingerprint = response_fingerprint(user_prompt, full_response_text)
            allowed, _ = apply_warn_resubmit_flow(gr_allowed, gr_reason, behaviour, fingerprint)
            if not allowed:
                if _is_warn_behaviour(behaviour):
                    deny_reason = (
                        f"Warning!! Response blocked, please review it. "
                        f"Reason: {gr_reason or 'Policy violation'}"
                    )
                else:
                    deny_reason = f"Blocked by Akto Guardrails: {gr_reason or 'Policy violation'}"
                logger.warning(f"BLOCKING response - behaviour={behaviour!r}, Reason: {gr_reason}")
                # Gemini AfterModel docs: "Block Turn / Redact" — `decision: deny` blocks the turn.
                sys.stdout.write(json.dumps({"decision": "deny", "reason": deny_reason}))
                sys.exit(0)

        send_ingestion_data(
            user_prompt,
            full_response_text,
            model=model,
            streaming=streaming,
            session_metadata=session_metadata,
            usage_metadata=usage_metadata,
        )
    else:
        logger.info("No final response to ingest yet (waiting for finishReason/user_prompt)")

    sys.stdout.write(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
