#!/usr/bin/env python3
import json
import os
import sys
import urllib.request
import urllib.error

GUARDRAILS_URL = os.getenv("AKTO_GUARDRAILS_URL", "http://localhost:80")
if GUARDRAILS_URL and not GUARDRAILS_URL.lower().startswith(("http://", "https://")):
    print("AKTO_GUARDRAILS_URL uses an unsafe scheme; ignoring", file=sys.stderr)
    GUARDRAILS_URL = ""
AUTH_TOKEN = os.getenv("DATABASE_ABSTRACTOR_SERVICE_TOKEN", "")
TIMEOUT = float(os.getenv("AKTO_GUARDRAILS_TIMEOUT", "5"))
FAIL_OPEN = os.getenv("AKTO_GUARDRAILS_FAIL_OPEN", "true").lower() == "true"


def call_guardrails(query: str) -> tuple:
    if not GUARDRAILS_URL or not query.strip():
        return True, ""
    
    try:
        payload = {"query": query.strip(), "model": "claude-code"}
        request_body = {"payload": json.dumps(payload), "call_type": "completion"}
        
        headers = {"Content-Type": "application/json"}
        if AUTH_TOKEN:
            headers["authorization"] = AUTH_TOKEN
        
        req = urllib.request.Request(
            f"{GUARDRAILS_URL}/api/validate/request",
            data=json.dumps(request_body).encode("utf-8"),
            headers=headers,
            method="POST",
        )
        
        with urllib.request.urlopen(req, timeout=TIMEOUT) as response:
            result = json.loads(response.read().decode("utf-8"))
            return result.get("Allowed", result.get("allowed", True)), result.get("Reason", result.get("reason", ""))
            
    except Exception as e:
        print(f"Guardrails error: {e}", file=sys.stderr)
        return FAIL_OPEN, str(e)


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