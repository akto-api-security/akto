#!/usr/bin/env python3
"""
Cursor Chat Before Hook - Prompt Validation via Akto HTTP Proxy API
Validates user prompts before submission using Akto guardrails.
Triggered by beforeSubmitPrompt hook.
"""
import hashlib
import json
import os
import sys
from typing import Any, List, Set, Tuple

from akto_ingestion_utility import AKTO_SYNC_MODE, MODE, send_ingestion_data, setup_logger

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.cursor/akto/chat-logs"))
WARN_STATE_PATH = os.path.join(LOG_DIR, "akto_chat_prompt_warn_pending.json")

logger = setup_logger("akto-validate-chat-prompt.log")


def _guardrails_behaviour_value(behaviour: Any) -> str:
    return str(behaviour or "").strip().lower()


def _is_warn_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "warn"


def _is_alert_behaviour(behaviour: Any) -> bool:
    return _guardrails_behaviour_value(behaviour) == "alert"


def prompt_fingerprint(prompt: str, attachments: List[Any]) -> str:
    canonical = json.dumps({"p": prompt, "a": attachments}, sort_keys=True, ensure_ascii=False)
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
    os.makedirs(LOG_DIR, exist_ok=True)
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
            "Alert behaviour: allowing prompt despite violation (server-side alert only)"
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


def main():
    logger.info(f"=== Chat Prompt Hook execution started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        print(json.dumps({"continue": True}))
        sys.exit(0)

    prompt = input_data.get("prompt", "")
    attachments = input_data.get("attachments", [])

    if not prompt.strip():
        logger.warning("Empty prompt received, allowing")
        print(json.dumps({"continue": True}))
        sys.exit(0)

    result = send_ingestion_data(
        hook_name="beforeSubmitPrompt",
        request_payload=prompt,
        response_payload={},
        guardrails=AKTO_SYNC_MODE,
        logger=logger,
    )

    gr_allowed = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Allowed", True)
    gr_reason = (result or {}).get("data", {}).get("guardrailsResult", {}).get("Reason", "")
    behaviour = (result or {}).get("data", {}).get("guardrailsResult", {}).get("behaviour", "") or \
                (result or {}).get("data", {}).get("guardrailsResult", {}).get("Behaviour", "")

    fingerprint = prompt_fingerprint(prompt, attachments)
    allowed, _ = apply_warn_resubmit_flow(gr_allowed, gr_reason, behaviour, fingerprint)

    # Return response
    response = {"continue": allowed}
    if not allowed:
        if _is_warn_behaviour(behaviour):
            response["user_message"] = (
                "Warning!!, prompt blocked, please review it. Send again to bypass. "
                f"Reason for blocking: {gr_reason}"
            )
        else:
            response["user_message"] = f"Prompt blocked: {gr_reason}"

    logger.info(f"Hook response: {response}")
    print(json.dumps(response))
    sys.exit(0)

if __name__ == "__main__":
    main()
