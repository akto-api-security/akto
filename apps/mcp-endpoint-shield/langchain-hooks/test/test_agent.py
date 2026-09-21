#!/usr/bin/env python3
"""
Interactive LangChain agent for testing AktoGuardrailsMiddleware against a
real, running Akto guardrails backend (default: http://127.0.0.1:7072) —
including the "warn" interrupt-and-resume flow.

Type real messages and see what your actual configured guardrail policies
decide. On a "warn" verdict, akto_middleware.resolve_interrupts()
asks you "Proceed anyway? [y/N]" and resumes with Command(resume=True/False)
based on your answer.

Usage:
    export AKTO_DATA_INGESTION_URL=http://localhost:7072   # or wherever it runs
    python3 test_agent.py

Requires: pip install langchain langgraph
"""

import os
import sys
import uuid

os.environ.setdefault("AKTO_DATA_INGESTION_URL", "http://127.0.0.1:7072")
os.environ.setdefault("AKTO_SYNC_MODE", "true")
os.environ.setdefault("LOG_LEVEL", "WARNING")

sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import logging
logging.basicConfig(level=logging.WARNING, format="%(levelname)s %(name)s: %(message)s")

from langchain.agents import create_agent
from langchain_core.language_models.fake_chat_models import FakeListChatModel
from langgraph.checkpoint.memory import InMemorySaver

from akto_middleware import AktoGuardrailsMiddleware, resolve_interrupts  # noqa: E402


def build_agent():
    # The guardrails verdict comes from your real backend, not from the model —
    # a canned model is enough here since we're testing the middleware, not chat quality.
    model = FakeListChatModel(responses=[f"(model reply #{i})" for i in range(1, 1000)])
    return create_agent(
        model=model,
        tools=[],
        middleware=[AktoGuardrailsMiddleware()],
        checkpointer=InMemorySaver(),
    )


def send(agent, thread_id: str, text: str) -> str:
    config = {"configurable": {"thread_id": thread_id}}
    try:
        result = agent.invoke({"messages": [{"role": "user", "content": text}]}, config=config)
        result = resolve_interrupts(agent, result, config)
    except ValueError as e:
        return f"[BLOCKED] {e}"

    return result["messages"][-1].content


def main():
    agent = build_agent()
    thread_id = str(uuid.uuid4())
    print(f"Talking to guardrails at {os.environ['AKTO_DATA_INGESTION_URL']}")
    print(f"thread_id={thread_id}. Ctrl-D to quit.")
    while True:
        try:
            text = input("you> ")
        except EOFError:
            break
        if not text.strip():
            continue
        print(send(agent, thread_id, text))


if __name__ == "__main__":
    main()
