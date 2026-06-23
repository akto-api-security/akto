"""
Common utilities for Akto AI agent hooks (Claude CLI, Cursor, and others).
Shared config, HTTP, ingestion payload building, transcript reading, and hook runners.
"""
import json
import logging
import os
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, List, Optional, Tuple, Union

try:
    from akto_machine_id import get_machine_id, get_username
except ImportError:
    # Some connectors (e.g. argus) never use the machine id (vxlan stays 0) and may not
    # ship akto_machine_id.py. Degrade gracefully rather than crash the hook at import.
    def get_machine_id() -> str:
        return ""

    def get_username() -> str:
        return os.getenv("USER") or os.getenv("USERNAME") or "unknown"

# ── Config ────────────────────────────────────────────────────────────────────

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_API_TOKEN = os.getenv("AKTO_API_TOKEN", "")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

# Map AKTO_CONNECTOR to a short tag name used in headers, ai-agent tag, and atlas URL suffix.
# Add new connectors here without touching anything else.
_CONNECTOR_TAG: Dict[str, str] = {
    "claude_code_cli": "claudecli",
    "cursor": "cursor",
    "vscode": "vscode",
    "gemini_cli": "geminicli",
    "github": "github",
    "codex_cli": "codexcli",
    "antigravity_cli": "antigravitycli",
}
TAG_NAME = _CONNECTOR_TAG.get(AKTO_CONNECTOR, AKTO_CONNECTOR)

_HOOK_HEADER = f"x-{TAG_NAME}-hook"

if MODE == "atlas":
    _device_id = os.getenv("DEVICE_ID") or get_machine_id()
    AI_AGENT_API_URL = f"https://{_device_id}.ai-agent.{TAG_NAME}" if _device_id else os.getenv("AKTO_API_URL", "")
else:
    AI_AGENT_API_URL = os.getenv("AKTO_API_URL", "")

# ── Logging helpers ───────────────────────────────────────────────────────────

_CONNECTOR_LOG_DIR: Dict[str, str] = {
    "claude_code_cli":  "~/.claude/akto/logs",
    "claude_agent_sdk": "~/.claude/akto/logs",
    "cursor":           "~/.cursor/akto/chat-logs",
    "gemini_cli":       "~/.gemini/akto/chat-logs",
    "codex_cli":        "~/.codex/akto/logs",
    "github":           "~/akto/.github/akto/vscode/logs",
    "opencode":         "~/.config/opencode/akto/logs",
    "antigravity_cli":  "~/.gemini/antigravity/akto/logs",
}
_default_log_dir = _CONNECTOR_LOG_DIR.get(AKTO_CONNECTOR, f"~/akto/{AKTO_CONNECTOR}-hooks/logs")
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", _default_log_dir))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"


def setup_logger(log_filename: str) -> logging.Logger:
    """Return a logger that writes to LOG_DIR/<log_filename> and errors to stderr."""
    os.makedirs(LOG_DIR, exist_ok=True)
    logger = logging.getLogger(log_filename)
    if logger.handlers:
        return logger
    logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

    fh = logging.FileHandler(os.path.join(LOG_DIR, log_filename))
    fh.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    fh.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(fh)

    ch = logging.StreamHandler()
    ch.setLevel(logging.ERROR)
    logger.addHandler(ch)

    return logger


# ── HTTP ──────────────────────────────────────────────────────────────────────

def create_ssl_context() -> ssl.SSLContext:
    return ssl._create_unverified_context()


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool, client_hook: str = "") -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    if client_hook:
        params.append(f"client_hook={client_hook}")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(
    url: str,
    payload: Dict[str, Any],
    logger: logging.Logger,
) -> Union[Dict[str, Any], str]:
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


# ── Session state (cross-event correlation) ────────────────────────────────────
#
# Downstream observability stitches the agent lifecycle (prompt → tools → response)
# into spans/traces, which needs the same session / conversation / message ids on
# every mirrored event as x-akto-installer-* headers. Each agent exposes those ids
# differently (verified from each agent's hook docs):
#   - cursor: conversation_id + generation_id on EVERY hook (pass-through).
#   - claude/github/gemini: only session_id on hooks, NO message id anywhere; the
#     message id is established by the prompt hook and floated to later events via
#     the session-state file.

SESSION_STATE_PATH = os.path.join(LOG_DIR, "akto_session_state.json")

# Per-agent field map, keyed by AKTO_CONNECTOR.
#   session_id_field    -> input key for the session            (-> akto_session_id)
#   conversation_field  -> input key for conversation/thread     (-> akto_conversation_id)
#   message_id_field    -> input key carrying a msg id on the hook itself, else None
#   message_id_strategy -> how the prompt hook derives the message id:
#                          "passthrough"     (id is on every hook),
#                          "transcript_uuid" (uuid of latest transcript entry),
#                          "turn_counter"    (synthesize session_id:turn_seq)
#   state_key           -> which id field keys the state-file row
#   extra_fields        -> other session fields to also forward (raw)
_SESSION_FIELD_MAP: Dict[str, Dict[str, Any]] = {
    "cursor": {
        "session_id_field": None,
        "conversation_field": "conversation_id",
        "message_id_field": "generation_id",
        "message_id_strategy": "passthrough",
        "state_key": "conversation_id",
        "extra_fields": ("model", "transcript_path", "user_email",
                         "cursor_version", "hook_event_name"),
    },
    "claude_code_cli": {
        "session_id_field": "session_id",
        "conversation_field": None,
        "message_id_field": None,
        "message_id_strategy": "transcript_uuid",
        "state_key": "session_id",
        "extra_fields": ("transcript_path", "cwd", "permission_mode", "hook_event_name"),
    },
    "gemini_cli": {
        "session_id_field": "session_id",
        "conversation_field": None,
        "message_id_field": None,
        "message_id_strategy": "turn_counter",
        "state_key": "session_id",
        "extra_fields": ("transcript_path", "cwd", "hook_event_name"),
    },
    "github": {
        "session_id_field": "session_id",
        "conversation_field": None,
        "message_id_field": None,
        "message_id_strategy": "turn_counter",
        "state_key": "session_id",
        "extra_fields": ("cwd", "hook_event_name"),
    },
    # NOTE: codex_cli not yet verified against docs; uses the default map until confirmed.
    "antigravity_cli": {
        # conversationId is Antigravity's session key (camelCase field name in hook input).
        "session_id_field":    "conversationId",
        "conversation_field":  None,
        "message_id_field":    None,
        "message_id_strategy": "turn_counter",
        "state_key":           "conversationId",
        # workspacePaths is an array; hooks extract the first element themselves before
        # passing to the utility — not listed here so extract_session_info skips it.
        "extra_fields":        ("transcriptPath", "hook_event_name"),
    },
}
_DEFAULT_FIELD_MAP: Dict[str, Any] = {
    "session_id_field": "session_id",
    "conversation_field": "conversation_id",
    "message_id_field": "generation_id",
    "message_id_strategy": "turn_counter",
    "state_key": "session_id",
    "extra_fields": ("model", "transcript_path", "user_email", "cwd",
                     "permission_mode", "hook_event_name"),
}

# Field written onto the row to carry the current message turn across events.
_CURRENT_MESSAGE_ID = "current_message_id"
_TURN_SEQ = "turn_seq"


def _field_map() -> Dict[str, Any]:
    return _SESSION_FIELD_MAP.get(AKTO_CONNECTOR, _DEFAULT_FIELD_MAP)


def _id_fields(fm: Dict[str, Any]) -> List[str]:
    """Ordered, de-duplicated list of the id/extra input keys this agent forwards."""
    fields: List[str] = []
    for key in (fm["session_id_field"], fm["conversation_field"], fm["message_id_field"]):
        if key and key not in fields:
            fields.append(key)
    for key in fm["extra_fields"]:
        if key not in fields:
            fields.append(key)
    return fields


def extract_session_info(input_data: Dict[str, Any]) -> Dict[str, Any]:
    """Pull the present (non-None) id/extra fields from a hook's stdin input, using
    this agent's field map. Keys are the agent's RAW field names."""
    fm = _field_map()
    info: Dict[str, Any] = {}
    for key in _id_fields(fm):
        value = input_data.get(key)
        if value is not None:
            info[key] = value
    return info


def _state_key(input_data: Dict[str, Any], session_info: Dict[str, Any]) -> str:
    fm = _field_map()
    key_field = fm["state_key"]
    value = input_data.get(key_field) or session_info.get(key_field)
    if value:
        return str(value)
    for fallback in (fm["session_id_field"], fm["conversation_field"]):
        if fallback and session_info.get(fallback):
            return str(session_info[fallback])
    return "_latest"


def load_session_state(key: str, logger: logging.Logger) -> Dict[str, Any]:
    if not os.path.exists(SESSION_STATE_PATH):
        return {}
    try:
        with open(SESSION_STATE_PATH, encoding="utf-8") as f:
            data = json.load(f)
        return data.get(key, {}) if isinstance(data, dict) else {}
    except (json.JSONDecodeError, OSError) as e:
        logger.warning(f"Could not read session state: {e}")
        return {}


def save_session_state(key: str, session_info: Dict[str, Any], logger: logging.Logger) -> None:
    """Upsert-merge session_info into the keyed row (atomic write)."""
    try:
        data: Dict[str, Any] = {}
        if os.path.exists(SESSION_STATE_PATH):
            with open(SESSION_STATE_PATH, encoding="utf-8") as f:
                loaded = json.load(f)
                if isinstance(loaded, dict):
                    data = loaded
        row = data.get(key, {}) if isinstance(data.get(key), dict) else {}
        row.update({k: v for k, v in session_info.items() if v is not None})
        data[key] = row
        os.makedirs(os.path.dirname(SESSION_STATE_PATH), exist_ok=True)
        tmp_path = SESSION_STATE_PATH + ".tmp"
        with open(tmp_path, "w", encoding="utf-8") as f:
            json.dump(data, f)
            f.write("\n")
        os.replace(tmp_path, SESSION_STATE_PATH)
    except (json.JSONDecodeError, OSError) as e:
        logger.error(f"Could not persist session state: {e}")


def get_last_entry_uuid(transcript_path: str, logger: logging.Logger) -> str:
    """Return the `uuid` of the latest entry in a JSONL transcript ('' if unavailable)."""
    if not transcript_path:
        return ""
    path = os.path.expanduser(transcript_path)
    if not os.path.exists(path):
        return ""
    try:
        with open(path, "r") as f:
            lines = f.readlines()
        for line in reversed(lines):
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            uuid_val = entry.get("uuid")
            if uuid_val:
                return str(uuid_val)
        return ""
    except OSError as e:
        logger.error(f"Error reading transcript for uuid: {e}")
        return ""


def open_message_turn(
    input_data: Dict[str, Any],
    session_info: Dict[str, Any],
    state_key: str,
    row: Dict[str, Any],
    logger: logging.Logger,
) -> Dict[str, Any]:
    """Prompt-hook only. Establish/rotate the message turn and return the fields to
    persist (current_message_id + turn_seq). Strategy chosen by the agent's field map."""
    fm = _field_map()
    strategy = fm["message_id_strategy"]

    if strategy == "passthrough":
        msg_field = fm["message_id_field"]
        message_id = input_data.get(msg_field) or session_info.get(msg_field)
        if message_id:
            return {_CURRENT_MESSAGE_ID: str(message_id)}

    if strategy == "transcript_uuid":
        message_id = get_last_entry_uuid(input_data.get("transcript_path", ""), logger)
        if message_id:
            return {_CURRENT_MESSAGE_ID: message_id}
        # fall through to turn_counter when no transcript uuid is available

    # turn_counter (also the fallback for the above): deterministic per-session sequence
    next_seq = int(row.get(_TURN_SEQ, 0)) + 1
    return {_TURN_SEQ: next_seq, _CURRENT_MESSAGE_ID: f"{state_key}:{next_seq}"}


def resolve_session_info(
    input_data: Dict[str, Any],
    logger: logging.Logger,
    *,
    is_prompt_hook: bool = False,
) -> Dict[str, Any]:
    """Extract this event's session info, persist it, and merge in stored row fields.

    Prompt hooks (is_prompt_hook=True) rotate the message turn; all other events read
    the current message id back from the row. Fail-open: any error returns just the
    fields present on this event."""
    try:
        session_info = extract_session_info(input_data)
        state_key = _state_key(input_data, session_info)
        row = load_session_state(state_key, logger)

        if is_prompt_hook:
            session_info.update(open_message_turn(input_data, session_info, state_key, row, logger))

        save_session_state(state_key, session_info, logger)

        # Backfill any id/extra fields and the current message id from the stored row.
        merged = dict(row)
        merged.update(session_info)
        if not merged.get(_CURRENT_MESSAGE_ID) and row.get(_CURRENT_MESSAGE_ID):
            merged[_CURRENT_MESSAGE_ID] = row[_CURRENT_MESSAGE_ID]
        return merged
    except Exception as e:
        logger.error(f"resolve_session_info error: {e}")
        return extract_session_info(input_data)


def installer_headers(
    session_info: Dict[str, Any],
    input_data: Optional[Dict[str, Any]] = None,
) -> Dict[str, str]:
    """Build x-akto-installer-* request headers from session info: the agent's RAW
    field names plus normalized akto_session_id / akto_conversation_id / akto_message_id."""
    headers: Dict[str, str] = {}

    def _put(name: str, value: Any) -> None:
        if value is None:
            return
        headers[f"x-akto-installer-{name}"] = (
            json.dumps(value) if isinstance(value, (dict, list)) else str(value)
        )

    for key, value in session_info.items():
        if key == _TURN_SEQ:
            continue
        _put(key, value)

    fm = _field_map()
    src = dict(session_info)
    if input_data:
        # input_data values win for the live ids on this event.
        for key in (fm["session_id_field"], fm["conversation_field"], fm["message_id_field"]):
            if key and input_data.get(key) is not None:
                src[key] = input_data[key]

    if fm["session_id_field"]:
        _put("akto_session_id", src.get(fm["session_id_field"]))
    if fm["conversation_field"]:
        _put("akto_conversation_id", src.get(fm["conversation_field"]))

    message_id = src.get(_CURRENT_MESSAGE_ID)
    if not message_id and fm["message_id_field"]:
        message_id = src.get(fm["message_id_field"])
    _put("akto_message_id", message_id)

    return headers


# ── Ingestion payload ─────────────────────────────────────────────────────────

def build_ingestion_payload(
    *,
    hook_name: str,
    request_payload: Any,
    response_payload: Any,
    extra_headers: Optional[Dict[str, str]] = None,
    session_info: Optional[Dict[str, Any]] = None,
    input_data: Optional[Dict[str, Any]] = None,
    tags: Optional[Dict[str, str]] = None,
    status_code: str = "200",
) -> Dict[str, Any]:
    base_tags: Dict[str, str] = {"gen-ai": "Gen AI", "hook": hook_name}
    if MODE == "atlas":
        base_tags["ai-agent"] = TAG_NAME
        base_tags["source"] = CONTEXT_SOURCE
    if tags:
        base_tags.update(tags)

    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    host = AI_AGENT_API_URL.replace("https://", "").replace("http://", "")

    req_headers = {"host": host, _HOOK_HEADER: hook_name, "content-type": "application/json"}
    if session_info:
        req_headers.update(installer_headers(session_info, input_data))
    resp_headers: Dict[str, str] = {_HOOK_HEADER: hook_name, "content-type": "application/json"}
    if extra_headers:
        resp_headers.update(extra_headers)

    return {
        "path": f"/v1/hooks/{hook_name}",
        "requestHeaders": json.dumps(req_headers),
        "responseHeaders": json.dumps(resp_headers),
        "method": "POST",
        "requestPayload": json.dumps({"body": request_payload}),
        "responsePayload": json.dumps({"body": response_payload}),
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
        "tag": json.dumps(base_tags),
        "metadata": json.dumps(base_tags),
        "contextSource": CONTEXT_SOURCE,
    }


def send_ingestion_data(
    *,
    hook_name: str,
    request_payload: Any,
    response_payload: Any,
    tags: Optional[Dict[str, str]] = None,
    extra_headers: Optional[Dict[str, str]] = None,
    session_info: Optional[Dict[str, Any]] = None,
    input_data: Optional[Dict[str, Any]] = None,
    status_code: str = "200",
    guardrails: bool = False,
    logger: logging.Logger,
) -> Optional[Union[Dict[str, Any], str]]:
    """Post ingestion data to the Akto HTTP proxy. Returns the API response dict, or None on failure."""
    if not AKTO_DATA_INGESTION_URL:
        logger.info("AKTO_DATA_INGESTION_URL not set, skipping ingestion")
        return

    logger.info(f"Guardrails enabled? -> {guardrails}")

    payload = build_ingestion_payload(
        hook_name=hook_name,
        request_payload=request_payload,
        response_payload=response_payload,
        extra_headers=extra_headers,
        session_info=session_info,
        input_data=input_data,
        tags=tags,
        status_code=status_code,
    )
    logger.debug(f">>>>>>>>>>>>>>>>>Ingestion payload: {json.dumps(payload)}")
    try:
        result = post_payload_json(
            build_http_proxy_url(guardrails=guardrails, ingest_data=True, client_hook=hook_name),
            payload,
            logger,
        )
        logger.info(f"Ingestion successful for hook: {hook_name}")
        return result
    except Exception as e:
        logger.error(f"Ingestion error: {e}")
        return None


# ── Hook runners ──────────────────────────────────────────────────────────────

def run_observability_hook(hook_name: str) -> None:
    """Run a fire-and-forget observability hook: ingest input_data and exit."""
    logger = setup_logger("hook-executions.log")
    logger.info(f"=== {hook_name} hook started ===")
    try:
        input_data = json.load(sys.stdin)
        logger.info(f"{hook_name} input:\n%s", json.dumps(input_data, indent=2))
        session_info = resolve_session_info(input_data, logger)
        send_ingestion_data(
            hook_name=hook_name,
            request_payload=input_data,
            response_payload={},
            session_info=session_info,
            input_data=input_data,
            guardrails=False,
            logger=logger,
        )
        logger.info(f"=== {hook_name} hook completed ===")
    except Exception as e:
        logger.error(f"Main error: {e}")
    print(json.dumps({}))
    sys.exit(0)


def run_blocking_hook(hook_name: str) -> None:
    """Run a blocking hook: ingest input_data with guardrails and deny if not allowed."""
    logger = setup_logger("hook-executions.log")
    logger.info(f"=== {hook_name} hook started ===")
    try:
        input_data = json.load(sys.stdin)
        logger.info(f"{hook_name} input:\n%s", json.dumps(input_data, indent=2))
        session_info = resolve_session_info(input_data, logger)
        result = send_ingestion_data(
            hook_name=hook_name,
            request_payload=input_data,
            response_payload={},
            session_info=session_info,
            input_data=input_data,
            guardrails=AKTO_SYNC_MODE,
            logger=logger,
        )
        allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
        if not allowed:
            reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "Policy violation")
            logger.warning(f"BLOCKING {hook_name}: {reason}")
            print(json.dumps({
                "permission": "deny",
                "user_message": f"{hook_name} blocked by Akto: {reason}",
                "agent_message": f"Blocked by Akto Guardrails: {reason}",
            }))
            send_ingestion_data(
                hook_name=hook_name,
                request_payload=input_data,
                response_payload={"reason": reason, "blockedBy": "Akto Proxy"},
                session_info=session_info,
                input_data=input_data,
                guardrails=False,
                status_code="403",
                logger=logger,
            )
            logger.info(f"=== {hook_name} hook completed ===")
            sys.exit(0)
    except Exception as e:
        logger.error(f"Main error: {e}")
    print(json.dumps({"permission": "allow"}))
    sys.exit(0)


# ── Transcript reading ────────────────────────────────────────────────────────

def _extract_text_from_entry(entry: Dict[str, Any]) -> str:
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


def get_last_user_prompt(transcript_path: str, logger: logging.Logger) -> str:
    """Return the last user message text from a JSONL transcript file."""
    if not transcript_path or not os.path.exists(transcript_path):
        return ""
    try:
        with open(transcript_path, "r") as f:
            lines = f.readlines()
        for line in reversed(lines):
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            if entry.get("type") == "user":
                text = _extract_text_from_entry(entry)
                if text:
                    return text
        return ""
    except Exception as e:
        logger.error(f"Error reading transcript: {e}")
        return ""

def get_latest_message_for_cursor(transcript_path: str, role: str, logger: logging.Logger) -> str:
    """Return the latest message text for the given role from a cursor JSONL transcript file.

    Cursor transcript entries use {"role": "<role>", "message": {"content": ...}}
    Reads lines in reverse so the first match is the latest.
    """
    if not transcript_path or not os.path.exists(transcript_path):
        return ""
    try:
        with open(transcript_path, "r") as f:
            lines = f.readlines()
        for line in reversed(lines):
            try:
                entry = json.loads(line)
            except json.JSONDecodeError:
                continue
            if entry.get("role", "") == role:
                text = _extract_text_from_entry(entry)
                if text:
                    return text
        return ""
    except Exception as e:
        logger.error(f"Error reading transcript: {e}")
        return ""


def read_file_content(file_path: str, logger: logging.Logger) -> str:
    """Read and return the full text content of a file. Returns empty string on failure."""
    if not file_path or not os.path.exists(file_path):
        return ""
    try:
        with open(os.path.expanduser(file_path), "r", encoding="utf-8", errors="replace") as f:
            return f.read()
    except Exception as e:
        logger.error(f"Error reading file {file_path}: {e}")
        return ""
