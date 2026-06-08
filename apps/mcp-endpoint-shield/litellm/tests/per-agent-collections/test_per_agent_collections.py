#!/usr/bin/env python3
"""
Test per-agent collection creation in Akto via LiteLLM proxy.

Tests:
  metadata           — client sends agent_name via OpenAI SDK
  metadata_sdk       — client sends agent_name via LiteLLM SDK
  key_alias          — admin creates key with alias (OpenAI SDK)
  key_alias_sdk      — admin creates key with alias (LiteLLM SDK)
  team_alias         — admin creates team with alias (OpenAI SDK)
  team_alias_sdk     — admin creates team with alias (LiteLLM SDK)

Usage:
    pip install -r requirements.txt
    python test_per_agent_collections.py metadata
    python test_per_agent_collections.py all

Environment variables:
    LITELLM_URL        - LiteLLM proxy URL (default: http://localhost:4000)
    LITELLM_MASTER_KEY - Master key for admin operations (default: sk-1234)
"""
import os
import sys
import time
import requests
from openai import OpenAI
import litellm

LITELLM_URL = os.getenv("LITELLM_URL", "http://localhost:4000")
LITELLM_MASTER_KEY = os.getenv("LITELLM_MASTER_KEY", "sk-1234")
MODEL = "gemini/gemini-3.1-flash-lite-preview"
MESSAGES = [
    {"role": "system", "content": "You are a helpful assistant."},
    {"role": "user", "content": "Say hello world!"},
]


def call_openai(api_key, extra_body=None):
    client = OpenAI(base_url=LITELLM_URL, api_key=api_key)
    return client.chat.completions.create(
        model=MODEL, messages=MESSAGES, **({"extra_body": extra_body} if extra_body else {}),
    )


def call_litellm_sdk(api_key, extra_body=None):
    return litellm.completion(
        model=f"litellm_proxy/{MODEL}", messages=MESSAGES,
        api_base=LITELLM_URL, api_key=api_key,
        **({"extra_body": extra_body} if extra_body else {}),
    )


def create_key(alias):
    resp = requests.post(
        f"{LITELLM_URL}/key/generate",
        headers={"Authorization": f"Bearer {LITELLM_MASTER_KEY}"},
        json={"key_alias": alias, "models": [MODEL]},
    )
    resp.raise_for_status()
    return resp.json()["key"]


def create_team_key(team_alias):
    resp = requests.post(
        f"{LITELLM_URL}/team/new",
        headers={"Authorization": f"Bearer {LITELLM_MASTER_KEY}"},
        json={"team_alias": team_alias, "models": [MODEL]},
    )
    resp.raise_for_status()
    team_id = resp.json()["team_id"]
    print(f"Created team: {team_id}")

    resp = requests.post(
        f"{LITELLM_URL}/key/generate",
        headers={"Authorization": f"Bearer {LITELLM_MASTER_KEY}"},
        json={"team_id": team_id, "models": [MODEL]},
    )
    resp.raise_for_status()
    return resp.json()["key"]


def run_test(name, call_fn, api_key, expected_collection, extra_body=None):
    print(f"\n--- {name} ---")
    response = call_fn(api_key, extra_body)
    print(f"Response: {response.choices[0].message.content}")
    print(f"Expected Akto collection: '{expected_collection}'")


def test_metadata(agent_name="hello-world-agent"):
    run_test("metadata (OpenAI SDK)", call_openai, LITELLM_MASTER_KEY,
             agent_name, extra_body={"metadata": {"agent_name": agent_name}})


def test_metadata_sdk(agent_name="hello-world-agent-sdk"):
    run_test("metadata (LiteLLM SDK)", call_litellm_sdk, LITELLM_MASTER_KEY,
             agent_name, extra_body={"metadata": {"agent_name": agent_name}})


def _unique(prefix):
    return f"{prefix}-{int(time.time())}"


def test_key_alias():
    alias = _unique("agent-openai")
    key = create_key(alias)
    print(f"Created key: {key[:12]}...")
    run_test(f"key_alias={alias} (OpenAI SDK)", call_openai, key, alias)


def test_key_alias_sdk():
    alias = _unique("agent-sdk")
    key = create_key(alias)
    print(f"Created key: {key[:12]}...")
    run_test(f"key_alias={alias} (LiteLLM SDK)", call_litellm_sdk, key, alias)


def test_team_alias():
    alias = _unique("team-openai")
    key = create_team_key(alias)
    print(f"Created key: {key[:12]}...")
    run_test(f"team_alias={alias} (OpenAI SDK)", call_openai, key, alias)


def test_team_alias_sdk():
    alias = _unique("team-sdk")
    key = create_team_key(alias)
    print(f"Created key: {key[:12]}...")
    run_test(f"team_alias={alias} (LiteLLM SDK)", call_litellm_sdk, key, alias)


TESTS = {
    "metadata": test_metadata,
    "metadata_sdk": test_metadata_sdk,
    "key_alias": test_key_alias,
    "key_alias_sdk": test_key_alias_sdk,
    "team_alias": test_team_alias,
    "team_alias_sdk": test_team_alias_sdk,
}

if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "metadata"

    if mode == "all":
        for test_fn in TESTS.values():
            test_fn()
    elif mode in TESTS:
        TESTS[mode]()
    else:
        print(f"Unknown mode: {mode}")
        print(f"Usage: python test_per_agent_collections.py [{' | '.join(TESTS.keys())} | all]")
        sys.exit(1)
