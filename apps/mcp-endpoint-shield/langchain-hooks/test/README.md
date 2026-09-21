# Testing AktoGuardrailsMiddleware

See the [main README](../README.md) for how the middleware itself works
(`block`/`alert`/`warn`/`approval`, `resolve_interrupts()`, `interrupt_payload()`).
This folder is just for exercising it.

## Setup

```bash
python3 -m venv venv && source venv/bin/activate
pip install langchain langgraph
```

## `test_agent.py` — CLI, against a real guardrails backend

```bash
export AKTO_DATA_INGESTION_URL=http://localhost:7072   # wherever your guardrails service runs
python3 test_agent.py
```

Drops you into an interactive chat against your real, configured guardrail
policies. On a `warn`/`approval` verdict it prints `Proceed anyway? [y/N]`
and resumes based on your answer (via `resolve_interrupts()` from
`akto_middleware.py`). `block` raises immediately with no prompt. `alert`
proceeds silently (logged server-side only).

## `examples/flask_app.py` — web app shape, two endpoints instead of a blocking prompt

```bash
pip install flask
export AKTO_DATA_INGESTION_URL=http://localhost:7072
python3 examples/flask_app.py
```

```bash
curl -s localhost:5000/chat -X POST -H 'Content-Type: application/json' \
    -d '{"thread_id": "t1", "text": "hello, my email is nayan@gmail.com"}'
# if that returns {"status": "needs_approval", ...}:
curl -s localhost:5000/chat/resume -X POST -H 'Content-Type: application/json' \
    -d '{"thread_id": "t1", "decision": true}'
```

Shows the pattern for an app that can't block a request waiting on a human
(the ask and the resume are two separate HTTP calls, using
`interrupt_payload()` instead of `resolve_interrupts()`'s blocking loop).
