#!/usr/bin/env python3
"""
Cursor preToolUse hook — validates local file reads (agent Read / read_file / Glob / glob_file_search)
via Akto POST /api/validate/file, same behavior as Claude CLI akto-validate-file.py.

See: https://cursor.com/docs/hooks (preToolUse; analogous to beforeReadFile for LLM context).
"""
import json
import logging
import os
import sys

_SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
if _SCRIPT_DIR not in sys.path:
    sys.path.insert(0, _SCRIPT_DIR)

for _p in (_SCRIPT_DIR, os.path.normpath(os.path.join(_SCRIPT_DIR, "..", "shared"))):
    if os.path.isfile(os.path.join(_p, "akto_validate_file_common.py")):
        if _p not in sys.path:
            sys.path.insert(0, _p)
        break

from akto_cursor_file_read_tools import (
    cursor_file_guard_allow_response,
    cursor_file_guard_deny_response,
    file_path_from_tool_input,
    is_file_read_tool,
    parse_tool_input,
    resolve_read_hook_file_path,
)
from akto_machine_id import get_machine_id
from akto_validate_file_common import call_validate_file

LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.cursor/akto/file-read-logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

_file_handler = logging.FileHandler(os.path.join(LOG_DIR, "akto-validate-file-pretool.log"))
_file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
_file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
logger.addHandler(_file_handler)

_console = logging.StreamHandler(sys.stderr)
_console.setLevel(logging.ERROR)
logger.addHandler(_console)

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
VALIDATE_FILES = os.getenv("VALIDATE_FILES", "true").lower() == "true"

MODE = os.getenv("MODE", "argus").lower()
if MODE == "atlas":
    _device_id = os.getenv("DEVICE_ID") or get_machine_id()
    API_URL = f"https://{_device_id}.ai-agent.cursor" if _device_id else "https://api.anthropic.com"
else:
    API_URL = os.getenv("API_URL", "https://api.anthropic.com")


def main():
    logger.info(f"=== preToolUse file validation — Mode: {MODE}, Sync: {AKTO_SYNC_MODE}, API_URL: {API_URL} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        print(json.dumps(cursor_file_guard_allow_response()))
        sys.exit(0)

    tool_name = str(input_data.get("tool_name") or "")
    tool_input = parse_tool_input(input_data.get("tool_input"))

    if not is_file_read_tool(tool_name):
        logger.info(f"Tool {tool_name!r} is not a file-read tool, allowing")
        print(json.dumps(cursor_file_guard_allow_response()))
        sys.exit(0)

    if not VALIDATE_FILES or not AKTO_SYNC_MODE or not AKTO_DATA_INGESTION_URL:
        logger.info("File validation disabled or no ingestion URL, allowing")
        print(json.dumps(cursor_file_guard_allow_response()))
        sys.exit(0)

    file_path = os.path.expanduser(file_path_from_tool_input(tool_input))
    if file_path and not os.path.isabs(file_path) and not os.path.isfile(file_path):
        file_path = resolve_read_hook_file_path({
            "file_path": file_path,
            "workspace_roots": input_data.get("workspace_roots"),
        })
        file_path = os.path.expanduser(file_path)
    if not file_path or not os.path.isfile(file_path):
        logger.info(f"No valid file at path {file_path!r}, allowing")
        print(json.dumps(cursor_file_guard_allow_response()))
        sys.exit(0)

    logger.info(f"Validating file: {file_path}")
    allowed, reason = call_validate_file(
        file_path,
        logger,
        akto_data_ingestion_url=AKTO_DATA_INGESTION_URL,
        context_source=CONTEXT_SOURCE,
    )

    if not allowed:
        msg = f"Blocked by Akto Guardrails: {reason}" if reason else "Blocked by Akto Guardrails"
        logger.warning(f"DENYING file read: {file_path} — {reason}")
        print(json.dumps(cursor_file_guard_deny_response(msg)))
        sys.exit(0)

    logger.info(f"File allowed: {file_path}")
    print(json.dumps(cursor_file_guard_allow_response()))
    sys.exit(0)


if __name__ == "__main__":
    main()
