#!/usr/bin/env python3
"""
Cursor postToolUse hook — ingests native file-read tool results to Akto (observational; cannot block).
Pairs with akto-validate-file-pretool.py for the same tool names.
"""
import json
import logging
import os
import sys
from typing import Any, Dict

from akto_cursor_file_read_tools import is_file_read_tool, parse_tool_input
from akto_cursor_mirror import send_mirror_ingestion
from akto_machine_id import get_machine_id

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.cursor/akto/file-read-logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

_file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-file-posttool.log"))
_file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
_file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
logger.addHandler(_file_handler)

_console = logging.StreamHandler(sys.stderr)
_console.setLevel(logging.ERROR)
logger.addHandler(_console)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = os.getenv("AKTO_CONNECTOR", "cursor")
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

if MODE == "atlas":
    _device_id = os.getenv("DEVICE_ID") or get_machine_id()
    API_URL = f"https://{_device_id}.ai-agent.cursor" if _device_id else "https://api.anthropic.com"
    logger.info(f"MODE: {MODE}, Device ID: {_device_id}, API_URL: {API_URL}")
else:
    API_URL = os.getenv("API_URL", "https://api.anthropic.com")
    logger.info(f"MODE: {MODE}, API_URL: {API_URL}")

def _result_text(input_data: Dict[str, Any]) -> str:
    for key in ("result_json", "tool_response", "tool_result", "output", "result"):
        v = input_data.get(key)
        if v is None:
            continue
        if isinstance(v, (dict, list)):
            return json.dumps(v)
        return str(v)
    return ""


def main():
    logger.info(f"=== postToolUse file ingestion — Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        print(json.dumps({}))
        sys.exit(0)

    tool_name = str(input_data.get("tool_name") or "")
    if not is_file_read_tool(tool_name):
        logger.info(f"Tool {tool_name!r} is not a file-read tool, skipping")
        print(json.dumps({}))
        sys.exit(0)

    tool_input = json.dumps(parse_tool_input(input_data.get("tool_input")))
    result_text = _result_text(input_data)

    if not tool_input.strip() or tool_input == "{}":
        logger.info("Empty tool input, skipping ingestion")
        print(json.dumps({}))
        sys.exit(0)

    if not result_text.strip():
        logger.info("Empty tool result, skipping ingestion")
        print(json.dumps({}))
        sys.exit(0)

    if not AKTO_DATA_INGESTION_URL:
        logger.info("No AKTO_DATA_INGESTION_URL, skipping")
        print(json.dumps({}))
        sys.exit(0)

    try:
        send_mirror_ingestion(
            x_cursor_hook="postToolUse",
            tool_input_str=tool_input,
            response_body_str=result_text,
            server_tag=f"cursor-file-tool:{tool_name}",
            mode=MODE,
            api_url=API_URL,
            context_source=CONTEXT_SOURCE,
            akto_connector=AKTO_CONNECTOR,
            akto_data_ingestion_url=AKTO_DATA_INGESTION_URL,
            akto_timeout=AKTO_TIMEOUT,
            akto_sync_mode=AKTO_SYNC_MODE,
            log_payloads=LOG_PAYLOADS,
            logger=logger,
        )
    except Exception as e:
        logger.error(f"Ingestion error: {e}")

    print(json.dumps({}))
    sys.exit(0)


if __name__ == "__main__":
    main()
