#!/usr/bin/env python3
import json
import os
import sys
import urllib.request

GUARDRAILS_URL = os.getenv("AKTO_GUARDRAILS_URL", "http://localhost:80")
TIMEOUT = float(os.getenv("AKTO_GUARDRAILS_TIMEOUT", "5"))
CLAUDE_API_URL = os.getenv("CLAUDE_API_URL", "https://api.anthropic.com")
AKTO_CONNECTOR = "lightllm"


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


def call_guardrails(query: str) -> tuple:
    if not query.strip():
        return True, ""

    try:
        request_body = build_validation_request(query)

        headers = {"Content-Type": "application/json"}

        req = urllib.request.Request(
            f"{GUARDRAILS_URL}/api/http-proxy?guardrails=true&akto_connector={AKTO_CONNECTOR}",
            data=json.dumps(request_body).encode("utf-8"),
            headers=headers,
            method="POST",
        )

        with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
            result = json.loads(response.read().decode("utf-8"))
            data = result.get("data", {})
            guardrails_result = data.get("guardrailsResult", {})
            allowed = guardrails_result.get("Allowed", True)
            reason = guardrails_result.get("Reason", "")

            return allowed, reason

    except Exception as e:
        print(f"Guardrails error: {e}", file=sys.stderr)
        return True, ""


def main():
    try:
        input_data = json.load(sys.stdin)
    except json.JSONDecodeError as e:
        print(f"Invalid JSON input: {e}", file=sys.stderr)
        sys.exit(1)

    prompt = input_data.get("prompt", "")

    if not prompt.strip():
        sys.exit(0)

    allowed, reason = call_guardrails(prompt)

    if not allowed:
        output = {
            "decision": "block",
            "reason": f"Blocked by Akto Guardrails: {reason or 'Security policy violation detected'}"
        }
        print(json.dumps(output))

    sys.exit(0)


main()