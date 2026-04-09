#!/usr/bin/env python3
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

from akto_validate_file_common import call_validate_file

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.claude/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()

os.makedirs(LOG_DIR, exist_ok=True)

logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-file.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_handler.setFormatter(logging.Formatter("%(asctime)s - %(levelname)s - %(message)s"))
logger.addHandler(file_handler)

console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)
logger.addHandler(console_handler)

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
VALIDATE_FILES = os.getenv("VALIDATE_FILES", "true").lower() == "true"

# Tools that read files and have a file_path in tool_input
FILE_READ_TOOLS = {"Read", "Glob"}


def allow_output() -> str:
    return json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PermissionRequest",
            "decision": {"behavior": "allow"}
        }
    })


def deny_output(message: str) -> str:
    return json.dumps({
        "hookSpecificOutput": {
            "hookEventName": "PermissionRequest",
            "decision": {"behavior": "deny", "message": message}
        }
    })


def main():
    logger.info(f"=== PermissionRequest file hook started, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        print(allow_output())
        sys.exit(0)

    tool_name = str(input_data.get("tool_name") or "")
    tool_input = input_data.get("tool_input") or {}

    if tool_name not in FILE_READ_TOOLS:
        logger.info(f"Tool {tool_name!r} is not a file-read tool, allowing")
        print(allow_output())
        sys.exit(0)

    if not VALIDATE_FILES or not AKTO_SYNC_MODE or not AKTO_DATA_INGESTION_URL:
        logger.info("File validation disabled or no ingestion URL, allowing")
        print(allow_output())
        sys.exit(0)

    file_path = os.path.expanduser(tool_input.get("file_path", "").strip())
    if not file_path or not os.path.isfile(file_path):
        logger.info(f"No valid file at path {file_path!r}, allowing")
        print(allow_output())
        sys.exit(0)

    logger.info(f"Validating file: {file_path}")
    allowed, reason = call_validate_file(
        file_path,
        logger,
        akto_data_ingestion_url=AKTO_DATA_INGESTION_URL,
        context_source=CONTEXT_SOURCE,
    )

    if not allowed:
        message = f"Blocked by Akto Guardrails: {reason}" if reason else "Blocked by Akto Guardrails"
        logger.warning(f"DENYING file read: {file_path} — {reason}")
        print(deny_output(message))
        sys.exit(0)

    logger.info(f"File allowed: {file_path}")
    print(allow_output())
    sys.exit(0)


if __name__ == "__main__":
    main()
