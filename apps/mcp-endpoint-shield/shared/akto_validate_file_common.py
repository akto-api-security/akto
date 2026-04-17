"""
Shared Akto /api/validate/file client for Claude PermissionRequest and Cursor preToolUse hooks.
"""
import json
import logging
import os
import ssl
import time
import urllib.error
import urllib.request
from typing import Dict, Tuple

AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
FILE_MAX_BYTES = int(os.getenv("FILE_MAX_BYTES", str(5 * 1024 * 1024)))


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


def call_validate_file(
    file_path: str,
    logger: logging.Logger,
    *,
    akto_data_ingestion_url: str,
    context_source: str,
) -> Tuple[bool, str]:
    """Call POST /api/validate/file with the file at file_path. Returns (allowed, reason)."""
    filename = os.path.basename(file_path)

    try:
        file_size = os.path.getsize(file_path)
    except OSError as e:
        logger.error(f"Cannot stat file {file_path}: {e}")
        return True, ""

    if file_size > FILE_MAX_BYTES:
        logger.warning(
            f"File {filename} ({file_size} bytes) exceeds FILE_MAX_BYTES, skipping validation (fail-open)"
        )
        return True, ""

    url = f"{akto_data_ingestion_url.rstrip('/')}/api/validate/file"
    logger.info(f"API CALL: POST {url} (file: {filename}, size: {file_size} bytes)")

    try:
        body, content_type = encode_multipart({"contextSource": context_source}, file_path)
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
            logger.info(f"Validate/file returned {e.code} for {filename}, allowing (fail-open)")
            return True, ""
        logger.error(f"API CALL FAILED after {duration_ms}ms: HTTP {e.code}")
        return True, ""
    except Exception as e:
        duration_ms = int((time.time() - start_time) * 1000)
        logger.error(f"API CALL FAILED after {duration_ms}ms: {e}")
        return True, ""
