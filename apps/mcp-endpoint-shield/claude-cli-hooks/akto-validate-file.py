#!/usr/bin/env python3
import json
import logging
import os
import ssl
import sys
import time
import urllib.request
from typing import Dict, Tuple

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
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")
VALIDATE_FILES = os.getenv("VALIDATE_FILES", "true").lower() == "true"
FILE_MAX_BYTES = int(os.getenv("FILE_MAX_BYTES", str(5 * 1024 * 1024)))  # 5MB default

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


def encode_multipart(fields: Dict[str, str], file_path: str) -> Tuple[bytes, str]:
    """Build multipart/form-data body by reading directly from file_path."""
    boundary = "----AktoValidateBoundary" + str(int(time.time() * 1000))
    filename = os.path.basename(file_path)

    parts = b""
    for key, value in fields.items():
        parts += (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="{key}"\r\n\r\n'
            f"{value}\r\n"
        ).encode("utf-8")

    parts += (
        f"--{boundary}\r\n"
        f'Content-Disposition: form-data; name="file"; filename="{filename}"\r\n'
        f"Content-Type: application/octet-stream\r\n\r\n"
    ).encode("utf-8")

    with open(file_path, "rb") as f:
        parts += f.read()

    parts += f"\r\n--{boundary}--\r\n".encode("utf-8")
    return parts, f"multipart/form-data; boundary={boundary}"


def call_validate_file(file_path: str) -> Tuple[bool, str]:
    """Call POST /api/validate/file with the file at file_path. Returns (allowed, reason)."""
    filename = os.path.basename(file_path)

    try:
        file_size = os.path.getsize(file_path)
    except OSError as e:
        logger.error(f"Cannot stat file {file_path}: {e}")
        return True, ""

    if file_size > FILE_MAX_BYTES:
        logger.warning(f"File {filename} ({file_size} bytes) exceeds FILE_MAX_BYTES, skipping validation (fail-open)")
        return True, ""

    url = f"{AKTO_DATA_INGESTION_URL}/api/validate/file"
    logger.info(f"API CALL: POST {url} (file: {filename}, size: {file_size} bytes)")

    try:
        body, content_type = encode_multipart({"contextSource": CONTEXT_SOURCE}, file_path)
    except OSError as e:
        logger.error(f"Cannot read file {file_path}: {e}")
        return True, ""

    request = urllib.request.Request(
        url,
        data=body,
        headers={"Content-Type": content_type},
        method="POST",
    )

    start_time = time.time()
    try:
        ssl_context = ssl._create_unverified_context()
        with urllib.request.urlopen(request, context=ssl_context, timeout=AKTO_TIMEOUT) as response:
            duration_ms = int((time.time() - start_time) * 1000)
            raw = response.read().decode("utf-8")
            logger.info(f"API RESPONSE: Status {response.getcode()}, Duration: {duration_ms}ms")

            try:
                result = json.loads(raw)
            except json.JSONDecodeError:
                logger.warning(f"Non-JSON response from validate/file, allowing (fail-open): {raw[:200]}")
                return True, ""

            allowed = result.get("allowed", True)
            reason = result.get("reason", "")

            if allowed:
                logger.info(f"File ALLOWED by guardrails: {filename}")
            else:
                logger.warning(f"File DENIED by guardrails: {filename} — {reason}")

            return allowed, reason

    except urllib.error.HTTPError as e:
        duration_ms = int((time.time() - start_time) * 1000)
        if 400 <= e.code < 500:
            # Unsupported file type or bad request — fail open
            logger.info(f"Validate/file returned {e.code} for {filename}, allowing (fail-open)")
            return True, ""
        logger.error(f"API CALL FAILED after {duration_ms}ms: HTTP {e.code}")
        return True, ""
    except Exception as e:
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(f"API CALL FAILED after {duration_ms}ms: {e}")
        return True, ""


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
    allowed, reason = call_validate_file(file_path)

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
