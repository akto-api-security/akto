#!/usr/bin/env python3
import json
import logging
import os
import sys
import urllib.request
from typing import Any, Dict, Tuple, Union

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

AKTO_DATA_INGESTION_URL = os.getenv("AKTO_DATA_INGESTION_URL")
AKTO_TIMEOUT = float(os.getenv("AKTO_TIMEOUT", "5"))
CLAUDE_API_URL = os.getenv("CLAUDE_API_URL", "https://api.anthropic.com")
AKTO_SYNC_MODE = os.getenv("AKTO_SYNC_MODE", "true").lower() == "true"
AKTO_CONNECTOR = "claude_code_cli"


def build_http_proxy_url(*, guardrails: bool, ingest_data: bool) -> str:
    params = []
    if guardrails:
        params.append("guardrails=true")
    params.append(f"akto_connector={AKTO_CONNECTOR}")
    if ingest_data:
        params.append("ingest_data=true")
    return f"{AKTO_DATA_INGESTION_URL}/api/http-proxy?{'&'.join(params)}"


def post_payload_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
    headers = {"Content-Type": "application/json"}
    req = urllib.request.Request(
        url,
        data=json.dumps(payload).encode("utf-8"),
        headers=headers,
        method="POST",
    )

    with urllib.request.urlopen(req, timeout=AKTO_TIMEOUT) as response:
        raw = response.read().decode("utf-8")
        try:
            return json.loads(raw)
        except json.JSONDecodeError:
            return raw


def build_validation_request(query: str) -> dict:
    """Build the request body for guardrails validation."""
    return {
        "url": CLAUDE_API_URL,
        "path": "/v1/messages",
        "request": {
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "body": {
                "query": query.strip(),
            },
            "queryParams": {},
            "metadata": {
                "tag": {
                    "gen-ai": "Gen AI"
                }
            }
        },
        "response": None
    }


def call_guardrails(query: str) -> Tuple[bool, str]:
    if not query.strip():
        return True, ""

    try:
        request_body = build_validation_request(query)
        result = post_payload_json(
            build_http_proxy_url(guardrails=True, ingest_data=False),
            request_body,
        )

        data = result.get("data", {}) if isinstance(result, dict) else {}
        guardrails_result = data.get("guardrailsResult", {})
        allowed = guardrails_result.get("Allowed", True)
        reason = guardrails_result.get("Reason", "")

        return allowed, reason

    except Exception as e:
        logger.error(f"Guardrails validation error: {e}")
        return True, ""


def ingest_blocked_request(user_prompt: str):
    if not AKTO_DATA_INGESTION_URL or not AKTO_SYNC_MODE:
        return

    try:
        blocked_response_payload = {
            "body": {"x-blocked-by": "Akto Proxy"},
            "headers": {"content-type": "application/json"},
            "statusCode": 403,
            "status": "forbidden"
        }

        request_body = build_validation_request(user_prompt)
        request_body["response"] = blocked_response_payload
        post_payload_json(
            build_http_proxy_url(guardrails=False, ingest_data=True),
            request_body,
        )
        logger.info("Data ingestion successful")
    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        logger.error(f"Invalid JSON input: {e}")
        sys.exit(1)

    prompt = input_data.get("prompt", "")

    if not prompt.strip():
        sys.exit(0)

    if AKTO_SYNC_MODE:
        allowed, reason = call_guardrails(prompt)
        if not allowed:
            output = {
                "decision": "block",
                "reason": f"Blocked by Akto Guardrails"
            }
            print(json.dumps(output))
            ingest_blocked_request(prompt)
            sys.exit(1)

    sys.exit(0)


if __name__ == "__main__":
    main()