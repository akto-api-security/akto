#!/usr/bin/env python3
"""
Example: using AktoGuardrailsMiddleware from a web app instead of a CLI.

Why this looks different from test_agent.py: a CLI can just block on input()
while waiting for a decision. An HTTP request can't — the human answering
"proceed anyway?" might be a completely separate browser request that arrives
seconds or minutes later. So there's no single blocking ask_human() callback
here. Instead, the flow is split across two endpoints:

  POST /chat         -> sends a message. If guardrails pause it, returns
                        {"status": "needs_approval", "thread_id": ..., "reason": ...}
                        instead of blocking.
  POST /chat/resume  -> the frontend calls this once the human answers, with
                        {"thread_id": ..., "decision": true/false}.

Both endpoints share the same "check the result, don't block" logic, so it's
factored into _agent_result_to_response() below.

Run:
    pip install flask langchain langgraph
    export AKTO_DATA_INGESTION_URL=http://localhost:8080
    python3 flask_app.py
Then:
    curl -s localhost:5000/chat -X POST -H 'Content-Type: application/json' \
        -d '{"thread_id": "t1", "text": "hello, my email is nayan@gmail.com"}'
    # if that returns status=needs_approval:
    curl -s localhost:5000/chat/resume -X POST -H 'Content-Type: application/json' \
        -d '{"thread_id": "t1", "decision": true}'
"""

import os
import sys

os.environ.setdefault("AKTO_DATA_INGESTION_URL", "http://127.0.0.1:8080")

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", ".."))

from flask import Flask, request, jsonify
from langchain.agents import create_agent
from langchain_core.language_models.fake_chat_models import FakeListChatModel
from langgraph.checkpoint.memory import InMemorySaver
from langgraph.types import Command

from akto_middleware import AktoGuardrailsMiddleware, interrupt_payload

app = Flask(__name__)

# One shared agent + checkpointer for the process. In a real app this would be
# a real model, and the checkpointer would likely be a durable one (Postgres,
# Redis, etc.) rather than in-memory, so a pause survives a server restart.
model = FakeListChatModel(responses=[f"(model reply #{i})" for i in range(1, 1000)])
agent = create_agent(
    model=model,
    tools=[],
    middleware=[AktoGuardrailsMiddleware()],
    checkpointer=InMemorySaver(),
)


def _agent_result_to_response(result: dict) -> dict:
    """Shared by /chat and /chat/resume: turn a raw invoke() result into an
    HTTP-friendly response instead of blocking to resolve it ourselves."""
    payload = interrupt_payload(result)
    if payload is not None:
        return {
            "status": "needs_approval",
            "phase": payload["phase"],
            "behaviour": payload["behaviour"],
            "reason": payload["reason"],
        }
    return {"status": "ok", "reply": result["messages"][-1].content}


@app.post("/chat")
def chat():
    body = request.get_json()
    # thread_id identifies "this conversation" to the checkpointer. This demo
    # trusts whatever the caller sends; a real app might instead generate one
    # here and hand it back for the caller to reuse on /chat/resume. Either
    # way, the same thread_id must show up on the matching /chat/resume call
    # below, or there's nothing paused to resume.
    thread_id = body["thread_id"]
    config = {"configurable": {"thread_id": thread_id}}
    try:
        result = agent.invoke({"messages": [{"role": "user", "content": body["text"]}]}, config=config)
    except ValueError as e:
        return jsonify({"status": "blocked", "reason": str(e)})
    return jsonify(_agent_result_to_response(result))


@app.post("/chat/resume")
def resume():
    body = request.get_json()
    # Must match the thread_id from the /chat call this is resuming — see the comment there.
    thread_id = body["thread_id"]
    config = {"configurable": {"thread_id": thread_id}}
    try:
        result = agent.invoke(Command(resume=bool(body["decision"])), config=config)
    except ValueError as e:
        return jsonify({"status": "blocked", "reason": str(e)})
    return jsonify(_agent_result_to_response(result))


if __name__ == "__main__":
    app.run(port=5000)
