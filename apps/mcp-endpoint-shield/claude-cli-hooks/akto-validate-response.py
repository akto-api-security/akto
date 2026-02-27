#!/usr/bin/env python3
import json
import logging
import os
import ssl
import sys
import urllib.request
from typing import Any, Dict, Tuple, Union
import time


from akto_machine_id import get_machine_id

# Configure logging
LOG_DIR = os.path.expanduser(os.getenv("LOG_DIR", "~/.claude/akto/logs"))
LOG_LEVEL = os.getenv("LOG_LEVEL", "INFO").upper()
LOG_PAYLOADS = os.getenv("LOG_PAYLOADS", "false").lower() == "true"

# Create log directory if it doesn't exist
os.makedirs(LOG_DIR, exist_ok=True)

# Setup logging with both file and console handlers
logger = logging.getLogger(__name__)
logger.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))

# File handler
file_handler = logging.FileHandler(os.path.join(LOG_DIR, "validate-response.log"))
file_handler.setLevel(getattr(logging, LOG_LEVEL, logging.INFO))
file_formatter = logging.Formatter('%(asctime)s - %(levelname)s - %(message)s')
file_handler.setFormatter(file_formatter)
logger.addHandler(file_handler)

# Console handler
console_handler = logging.StreamHandler(sys.stderr)
console_handler.setLevel(logging.ERROR)  # Only show errors in console
logger.addHandler(console_handler)

MODE = os.getenv("MODE", "argus").lower()
AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = "claude_code_cli"
CONTEXT_SOURCE = os.getenv("CONTEXT_SOURCE", "ENDPOINT")

# SSL Configuration
SSL_CERT_PATH = os.getenv("SSL_CERT_PATH")
SSL_VERIFY = os.getenv("SSL_VERIFY", "true").lower() == "true"

# Configure CLAUDE_API_URL based on mode
if MODE == "atlas":
    device_id = os.getenv("DEVICE_ID") or get_machine_id()
    CLAUDE_API_URL = f"https://{device_id}.ai-agent.claudecli" if device_id else "https://api.anthropic.com"
    logger.info(f"MODE: {MODE}, Device ID: {device_id}, CLAUDE_API_URL: {CLAUDE_API_URL}")
else:
    CLAUDE_API_URL = os.getenv("CLAUDE_API_URL", "https://api.anthropic.com")
    logger.info(f"MODE: {MODE}, CLAUDE_API_URL: {CLAUDE_API_URL}")


def create_ssl_context():
    """
    Create SSL context with graceful fallback strategy.

    Attempts in order:
    1. Custom SSL_CERT_PATH if provided
    2. System default SSL context
    3. Python certifi bundle (if available)
    4. Unverified context (last resort)

    Returns:
        ssl.SSLContext or None
    """
    if not SSL_VERIFY:
        logger.warning("SSL verification disabled via SSL_VERIFY=false - INSECURE!")
        return ssl._create_unverified_context()

    # Try 1: Custom certificate path
    if SSL_CERT_PATH:
        try:
            context = ssl.create_default_context(cafile=SSL_CERT_PATH)
            logger.info(f"Using custom SSL certificate: {SSL_CERT_PATH}")
            return context
        except Exception as e:
            logger.warning(f"Failed to load custom SSL certificate from {SSL_CERT_PATH}: {e}")

    # Try 2: System default context
    try:
        context = ssl.create_default_context()
        logger.debug("Using system default SSL context")
        return context
    except Exception as e:
        logger.warning(f"Failed to create default SSL context: {e}")

    # Try 3: Python certifi bundle
    try:
        import certifi
        context = ssl.create_default_context(cafile=certifi.where())
        logger.info("Using Python certifi SSL bundle")
        return context
    except ImportError:
        logger.debug("certifi package not available")
    except Exception as e:
        logger.warning(f"Failed to create SSL context with certifi: {e}")

    # Try 4: Unverified context (last resort)
    logger.error("WARNING: All SSL verification methods failed! Falling back to UNVERIFIED context - INSECURE!")
    logger.error("This connection is vulnerable to Man-in-the-Middle attacks!")
    logger.error("Fix: Install proper certificates or set SSL_CERT_PATH environment variable")
    return ssl._create_unverified_context()


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    import time

    logger.info(f"API CALL: POST {url}")
    if LOG_PAYLOADS:
        logger.debug(f"Request payload: {json.dumps(payload)[:1000]}...")

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


def uuid_to_ipv6_simple(uuid_str):
    hex_str = uuid_str.replace("-", "").lower()
    return ":".join(hex_str[i:i+4] for i in range(0, 32, 4))


def build_ingestion_payload(user_prompt: str, response_text: str) -> Dict[str, Any]:
    """Build the request body for data ingestion."""
    import time

    # Build tags based on mode
    tags = {"gen-ai": "Gen AI"}
    if MODE == "atlas":
        tags["ai-agent"] = "claudecli"
        tags["source"] = CONTEXT_SOURCE

    # Get device ID
    device_id = os.getenv("DEVICE_ID") or get_machine_id()

    # Build host from CLAUDE_API_URL
    host = CLAUDE_API_URL.replace("https://", "").replace("http://", "")

    # Build request headers as JSON string
    request_headers = json.dumps({
        "host": host,
        "x-claude-hook": "Stop",
        "content-type": "application/json"
    })

    # Build response headers as JSON string
    response_headers = json.dumps({
        "x-claude-hook": "Stop",
        "content-type": "application/json"
    })

    # Build request payload as JSON string
    request_payload = json.dumps({
        "body": user_prompt
    })

    # Build response payload as JSON string
    response_payload = json.dumps({
        "body": response_text
    })

    return {
        "path": "/v1/messages",
        "requestHeaders": request_headers,
        "responseHeaders": response_headers,
        "method": "POST",
        "requestPayload": request_payload,
        "responsePayload": response_payload,
        "ip": uuid_to_ipv6_simple(device_id),
        "destIp": "127.0.0.1",
        "time": str(int(time.time() * 1000)),
        "statusCode": "200",
        "type": None,
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
        "metadata": json.dumps(tags),
        "contextSource": CONTEXT_SOURCE
    }


def extract_text_from_entry(entry: Dict[str, Any]) -> str:
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


def get_last_interaction(transcript_path: str) -> tuple[str, str]:
    if not os.path.exists(transcript_path):
        return "", ""

    try:
        events = []
        with open(transcript_path, "r") as f:
            for line in f:
                try:
                    entry = json.loads(line)
                except json.JSONDecodeError:
                    continue

                entry_type = entry.get("type")
                if entry_type not in ("user", "assistant"):
                    continue

                text = extract_text_from_entry(entry)
                if not text:
                    continue
                events.append((entry_type, text))

        if not events:
            return "", ""

        last_assistant_idx = -1
        last_user_idx = -1
        for idx in range(len(events) - 1, -1, -1):
            if last_assistant_idx == -1 and events[idx][0] == "assistant":
                last_assistant_idx = idx
            if last_user_idx == -1 and events[idx][0] == "user":
                last_user_idx = idx
            if last_assistant_idx != -1 and last_user_idx != -1:
                break

        if last_assistant_idx == -1:
            return "", ""

        # If the latest user has no assistant yet, avoid mixing turns.
        if last_user_idx > last_assistant_idx:
            return "", ""

        for idx in range(last_assistant_idx - 1, -1, -1):
            if events[idx][0] == "user":
                return events[idx][1], events[last_assistant_idx][1]

        return "", ""
    except Exception as e:
        logger.error(f"Error reading transcript: {e}")
        return "", ""


def send_ingestion_data(user_prompt: str, response_text: str):
    if not AKTO_DATA_INGESTION_URL:
        logger.info("AKTO_DATA_INGESTION_URL not set, skipping ingestion")
        return

    if not user_prompt.strip() or not response_text.strip():
        return

    logger.info("Ingesting conversation data")
    if LOG_PAYLOADS:
        logger.debug(f"Prompt: {user_prompt[:200]}...")
        logger.debug(f"Response: {response_text[:200]}...")
    else:
        logger.info(f"Prompt preview: {user_prompt[:100]}...")
        logger.info(f"Response preview: {response_text[:100]}...")

    try:
        request_body = build_ingestion_payload(user_prompt, response_text)
        post_payload_json(
            build_http_proxy_url(
                guardrails=not AKTO_SYNC_MODE,
                ingest_data=True,
            ),
            request_body,
        )
        logger.info("Conversation ingestion successful")

    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    logger.info(f"=== Hook execution started - Mode: {MODE}, Sync: {AKTO_SYNC_MODE} ===")

    try:
        input_data = json.load(sys.stdin)
        transcript_path = input_data.get("transcript_path")

        if not transcript_path:
            logger.info("No transcript path provided")
            sys.exit(0)

        transcript_path = os.path.expanduser(transcript_path)
        logger.info(f"Reading transcript from: {transcript_path}")

        user_prompt, response_text = "", ""
        for attempt in range(3):
            user_prompt, response_text = get_last_interaction(transcript_path)
            if user_prompt and response_text:
                break
            if attempt < 2:
                # Wait briefly for the latest transcript line to be flushed.
                time.sleep(0.2)

        if not user_prompt or not response_text:
            logger.info("No complete interaction found in transcript")
            sys.exit(0)

        logger.info(f"Extracted interaction - Prompt: {len(user_prompt)} chars, Response: {len(response_text)} chars")
        send_ingestion_data(user_prompt, response_text)

    except Exception as e:
        logger.error(f"Main error: {e}")
        sys.exit(0)

    logger.info("Hook execution completed")
    sys.exit(0)


if __name__ == "__main__":
    main()
