#!/usr/bin/env python3

import hashlib
import json
import logging
import os
import ssl
import sys
import time
import urllib.request
from typing import Any, Dict, Optional, Set, Tuple, Union

from akto_machine_id import get_machine_id, get_username
from akto_ingestion_utility import installer_headers, resolve_session_info

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.gemini/antigravity/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_preinvocation_warn_pending.json")
BLOCK_PENDING_PATH = os.path.join(LOG_DIR, "akto_preinvocation_block_pending.json")

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

if not logger.handlers:
    file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-pre-invocation.log"))
    file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
    file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
    logger.addHandler(file_handler)

    console_handler = logging.StreamHandler(sys.stderr)
    console_handler.setLevel(logging.ERROR)
    logger.addHandler(console_handler)

AKTO_DATA_INGESTION_URL = (os.getenv("AKTO_DATA_INGESTION_URL") or "").rstrip("/")
AKTO_API_TOKEN = os.getenv("AKTO_API_TOKEN", "")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
ANTIGRAVITY_API_URL = os.getenv("ANTIGRAVITY_API_URL", "https://generativelanguage.googleapis.com")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
MODE = os.getenv("MODE", "argus").lower()
AKTO_CONNECTOR = "antigravity_cli"
AKTO_CONNECTOR_VALUE = os.getenv("AKTO_CONNECTOR_VALUE", "antigravitycli")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
DEVICE_ID = os.getenv("DEVICE_ID") or get_machine_id()

if MODE == "atlas":
    ANTIGRAVITY_API_URL = (
        f"https://{DEVICE_ID}.ai-agent.{AKTO_CONNECTOR_VALUE}" if DEVICE_ID else ANTIGRAVITY_API_URL
    )
    logger.info(f"MODE: {MODE}, Device ID: {DEVICE_ID}, ANTIGRAVITY_API_URL: {ANTIGRAVITY_API_URL}")
else:
    logger.info(f"MODE: {MODE}, ANTIGRAVITY_API_URL: {ANTIGRAVITY_API_URL}")


def create_ssl_context() -> ssl.SSLContext:
    return ssl._create_unverified_context()


def build_http_proxy_url(*, guardrails: bool = False, ingest_data: bool = False) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_to_akto(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload, default=str)[:1000]}...")

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


def read_transcript(transcript_path: str) -> list:
    if not transcript_path:
        return []
    path = os.path.expanduser(transcript_path)
    if not os.path.exists(path):
        logger.warning(f"Transcript not found: {path}")
        return []
    entries = []
    try:
        with open(path, "r", encoding="utf-8", errors="replace") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                try:
                    entries.append(json.loads(line))
                except json.JSONDecodeError:
                    pass
        logger.debug(f"Read {len(entries)} transcript entries from {path}")
    except OSError as e:
        logger.error(f"Error reading transcript: {e}")
    return entries


def _extract_user_request_tag(text: str) -> str:
    """Strip <USER_REQUEST>...</USER_REQUEST> wrapper from Antigravity transcript entries."""
    import re
    m = re.search(r"<USER_REQUEST>(.*?)</USER_REQUEST>", text, re.DOTALL)
    return m.group(1).strip() if m else text.strip()


def _is_user_entry(entry: dict) -> bool:
    # Antigravity: source=USER_EXPLICIT, type=USER_INPUT
    if entry.get("source") == "USER_EXPLICIT":
        return True
    # Generic role-based fallback (Claude, Gemini, etc.)
    role = (entry.get("role") or "").lower()
    return role in ("user", "human")


def extract_text_from_entry(entry: dict) -> str:
    content = entry.get("content") or entry.get("text") or entry.get("message") or ""
    if isinstance(content, str):
        raw = content
    elif isinstance(content, list):
        texts = []
        for part in content:
            if isinstance(part, str):
                texts.append(part)
            elif isinstance(part, dict):
                texts.append(part.get("text") or part.get("content") or "")
        raw = "".join(t for t in texts if t)
    elif isinstance(content, dict):
        raw = content.get("text") or content.get("content") or ""
    else:
        return ""
    # Strip Antigravity XML wrapper if present
    if "<USER_REQUEST>" in raw:
        return _extract_user_request_tag(raw)
    return raw.strip()


def get_last_user_prompt(transcript_path: str) -> str:
    entries = read_transcript(transcript_path)
    if LOG_PAYLOADS and entries:
        logger.debug(f"First transcript entry keys: {list(entries[0].keys())}")
    for entry in reversed(entries):
        if _is_user_entry(entry):
            text = extract_text_from_entry(entry)
            if text:
                return text
    return ""


def build_guardrails_request(
    prompt: str,
    session_info: Optional[Dict[str, Any]] = None,
) -> Dict[str, Any]:
    tags: Dict[str, str] = {"gen-ai": "Gen AI", "ai-agent": AKTO_CONNECTOR_VALUE}
    if MODE == "atlas":
        tags["source"] = CONTEXT_SOURCE

    host = ANTIGRAVITY_API_URL.replace("https://", "").replace("http://", "")
    req_hdr: Dict[str, str] = {
        "host": host,
        "x-antigravitycli-hook": "PreInvocation",
        "content-type": "application/json",
    }
    if session_info:
        req_hdr.update(installer_headers(session_info))

    return {
        "path": "/antigravity/chat",
        "requestHeaders": json.dumps(req_hdr),
        "responseHeaders": json.dumps({"x-antigravitycli-hook": "PreInvocation"}),
        "method": "POST",
        "requestPayload": json.dumps({"body": prompt.strip()}),
        "responsePayload": json.dumps({}),
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
    prompt: str,
    session_info: Optional[Dict[str, Any]] = None,
) -> Tuple[bool, str, str]:
    if not prompt.strip():
        return True, "", ""
    if not AKTO_DATA_INGESTION_URL:
        logger.warning("AKTO_DATA_INGESTION_URL not set, allowing request (fail-open)")
        return True, "", ""

    logger.info(f"Calling guardrails on user prompt (len={len(prompt)})")
    try:
        request_body = build_guardrails_request(prompt, session_info)
        result = post_to_akto(build_http_proxy_url(guardrails=True, ingest_data=False), request_body)
        data = result.get("data", {}) if isinstance(result, dict) else {}
        gr = data.get("guardrailsResult", {})
        allowed = gr.get("Allowed", True)
        reason = gr.get("Reason", "")
        behaviour = gr.get("behaviour", "") or gr.get("Behaviour", "")
        if allowed:
            logger.info("Prompt ALLOWED by guardrails")
        else:
            logger.warning(f"Prompt DENIED by guardrails: {reason}")
        return allowed, reason, behaviour
    except Exception as e:
        logger.error(f"Guardrails call error: {e}")
        return True, "", ""


def ingest_blocked_request(
    prompt: str,
    reason: str,
    session_info: Optional[Dict[str, Any]] = None,
) -> None:
    if not AKTO_DATA_INGESTION_URL:
        return
    logger.info("Ingesting blocked prompt request")
    try:
        request_body = build_guardrails_request(prompt, session_info)
        request_body["responseHeaders"] = json.dumps({
            "x-antigravitycli-hook": "PreInvocation",
            "x-blocked-by": "Akto Proxy",
            "content-type": "application/json",
        })
        request_body["responsePayload"] = json.dumps(
            {"body": {"x-blocked-by": "Akto Proxy", "reason": reason or "Policy violation"}}
        )
        request_body["statusCode"] = "403"
        request_body["status"] = "403"
        post_to_akto(build_http_proxy_url(guardrails=False, ingest_data=True), request_body)
        logger.info("Blocked prompt ingestion successful")
    except Exception as e:
        logger.error(f"Blocked prompt ingestion error: {e}")


def _guardrails_behaviour_value(behaviour: Any) -> str:
    return str(behaviour or "").strip().lower()


def _is_warn_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "warn"


def _is_alert_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "alert"


def prompt_fingerprint(prompt: str) -> str:
    canonical = json.dumps({"p": prompt}, sort_keys=True, ensure_ascii=False)
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
    tmp = WARN_STATE_PATH + ".tmp"
    try:
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump({"warn_pending": sorted(hashes)}, f, indent=0)
            f.write("\n")
        os.replace(tmp, WARN_STATE_PATH)
    except OSError as e:
        logger.error(f"Could not persist warn-pending map: {e}")
        if os.path.exists(tmp):
            try:
                os.remove(tmp)
            except OSError:
                pass


def apply_warn_resubmit(gr_allowed: bool, reason: str, behaviour: str, fingerprint: str) -> Tuple[bool, str]:
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
        logger.info("Warn flow: resubmit — allowing and clearing fingerprint")
        return True, ""
    pending.add(fingerprint)
    save_warn_pending(pending)
    logger.info("Warn flow: first occurrence — blocked, resend same prompt to bypass")
    return False, reason


def write_block_pending(conversation_id: str, fingerprint: str, reason: str) -> None:
    try:
        data: Dict[str, Any] = {}
        if os.path.exists(BLOCK_PENDING_PATH):
            try:
                with open(BLOCK_PENDING_PATH, "r", encoding="utf-8") as f:
                    data = json.load(f)
            except (json.JSONDecodeError, OSError):
                data = {}
        data[conversation_id] = {
            "fingerprint": fingerprint,
            "reason": reason,
            "ts": int(time.time()),
        }
        tmp = BLOCK_PENDING_PATH + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f)
            f.write("\n")
        os.replace(tmp, BLOCK_PENDING_PATH)
        logger.info(f"Block-pending state written for conversationId={conversation_id!r}")
    except OSError as e:
        logger.error(f"Could not write block-pending state: {e}")


def _clear_block_pending(conversation_id: str) -> None:
    """Remove block-pending entry for conversation_id (e.g. after warn/resubmit allow)."""
    if not os.path.exists(BLOCK_PENDING_PATH):
        return
    try:
        with open(BLOCK_PENDING_PATH, "r", encoding="utf-8") as f:
            data = json.load(f)
        if conversation_id not in data:
            return
        data.pop(conversation_id)
        tmp = BLOCK_PENDING_PATH + ".tmp"
        with open(tmp, "w", encoding="utf-8") as f:
            json.dump(data, f)
            f.write("\n")
        os.replace(tmp, BLOCK_PENDING_PATH)
        logger.info(f"Block-pending entry cleared for conversationId={conversation_id!r} (resubmit allowed)")
    except (json.JSONDecodeError, OSError) as e:
        logger.warning(f"Could not clear block-pending entry: {e}")


def main():
    logger.info(f"=== PreInvocation Hook started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        raw_input = sys.stdin.read()
    except Exception as e:
        logger.error(f"Error reading stdin: {e}")
        sys.stdout.write(json.dumps({}))
        sys.exit(0)

    logger.info(f"Raw stdin (first 500 chars): {raw_input[:500]}")

    try:
        input_data = json.loads(raw_input)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.stdout.write(json.dumps({}))
        sys.exit(0)

    conversation_id = str(input_data.get("conversationId") or "")
    invocation_num = int(input_data.get("invocationNum") or 0)
    transcript_path = str(input_data.get("transcriptPath") or "")

    logger.info(
        f"PreInvocation: conversationId={conversation_id!r}, "
        f"invocationNum={invocation_num}, "
        f"initialNumSteps={input_data.get('initialNumSteps')}"
    )

    input_data["hook_event_name"] = "PreInvocation"
    session_info: Optional[Dict[str, Any]] = None
    try:
        session_info = resolve_session_info(input_data, logger, is_prompt_hook=True)
    except Exception as e:
        logger.warning(f"resolve_session_info failed: {e}")

    prompt = get_last_user_prompt(transcript_path)
    if not prompt:
        logger.info("No user prompt found in transcript, skipping guardrails")
        sys.stdout.write(json.dumps({}))
        sys.exit(0)

    logger.info(f"Extracted prompt length: {len(prompt)} chars")

    if AKTO_SYNC_MODE:
        gr_allowed, gr_reason, behaviour = call_guardrails(prompt, session_info)
        fingerprint = prompt_fingerprint(prompt)
        allowed, _ = apply_warn_resubmit(gr_allowed, gr_reason, behaviour, fingerprint)

        if not allowed:
            logger.warning(f"Prompt BLOCKED — writing block-pending for conversationId={conversation_id!r}")
            write_block_pending(conversation_id, fingerprint, gr_reason)
            ingest_blocked_request(prompt, gr_reason, session_info)
        elif not gr_allowed:
            # Warn/alert resubmit was accepted — clear any stale block-pending entry so
            # PostInvocation does not terminate the now-allowed turn.
            _clear_block_pending(conversation_id)

    # PreInvocation cannot output a block decision — PostInvocation enforces via block-pending state file
    sys.stdout.write(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
