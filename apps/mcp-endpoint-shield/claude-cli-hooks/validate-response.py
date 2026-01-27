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


def post_json(url: str, payload: Dict[str, Any]) -> Union[Dict[str, Any], str]:
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


def build_ingestion_payload(user_prompt: str, response_text: str) -> Dict[str, Any]:
    return {
        "url": CLAUDE_API_URL,
        "path": "/v1/messages",
        "request": {
            "method": "POST",
            "headers": {
                "content-type": "application/json"
            },
            "body": {
                "messages": [{"role": "user", "content": user_prompt}]
            },
            "queryParams": {},
            "metadata": {
                "tag": {
                    "gen-ai": "Gen AI"
                }
            }
        },
        "response": {
            "body": {
                "choices": [{"message": {"content": response_text}}]
            },
            "headers": {
                "content-type": "application/json"
            },
            "statusCode": 200,
            "status": "OK"
        }
    }


def get_last_interaction(transcript_path: str) -> tuple[str, str]:
    if not os.path.exists(transcript_path):
        return "", ""

    user_prompt, assistant_response = "", ""
    
    try:
        with open(transcript_path, 'r') as f:
            for line in f:
                try:
                    entry = json.loads(line)
                    entry_type = entry.get('type')
                    if entry_type not in ('user', 'assistant'):
                        continue
                    
                    content = entry.get('message', {}).get('content', '')
                    text = content if isinstance(content, str) else "".join(
                        b.get('text', '') for b in content if b.get('type') == 'text'
                    )
                    
                    if entry_type == 'user':
                        user_prompt = text
                    else:
                        assistant_response = text
                        
                except json.JSONDecodeError:
                    continue
                    
        return user_prompt, assistant_response
    except Exception as e:
        logger.error(f"Error reading transcript: {e}")
        return "", ""


def send_ingestion_data(user_prompt: str, response_text: str):
    if not user_prompt.strip() or not response_text.strip():
        return

    try:
        request_body = build_ingestion_payload(user_prompt, response_text)
        post_json(
            build_http_proxy_url(
                guardrails=not AKTO_SYNC_MODE,
                ingest_data=True,
            ),
            request_body,
        )
        logger.info("Data ingestion successful")

    except Exception as e:
        logger.error(f"Ingestion error: {e}")


def main():
    try:
        input_data = json.load(sys.stdin)
        transcript_path = input_data.get("transcript_path")
        
        if not transcript_path:
            sys.exit(0)

        transcript_path = os.path.expanduser(transcript_path)
        
        user_prompt, response_text = get_last_interaction(transcript_path)
        
        if not user_prompt or not response_text:
            sys.exit(0)

        send_ingestion_data(user_prompt, response_text)

    except Exception as e:
        logger.error(f"Main error: {e}")
        sys.exit(0)

    sys.exit(0)


if __name__ == "__main__":
    main()