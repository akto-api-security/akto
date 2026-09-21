# Akto Guardrails Middleware for LangChain

`akto_middleware.py` provides `AktoGuardrailsMiddleware`, a LangChain
`AgentMiddleware` that sends every model request/response through Akto's
guardrails service and enforces whatever your configured guardrail policies
decide.

## Install

```bash
pip install langchain langgraph httpx
```

## Configure

Set these environment variables (see `.env.example`):

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `AKTO_DATA_INGESTION_URL` | Yes | — | Akto service base URL |
| `AKTO_API_TOKEN` | No | — | Sent as `Authorization` header if set |
| `AKTO_SYNC_MODE` | No | `true` | `true` = block/warn on violation; `false` = log-only |
| `AKTO_TIMEOUT` | No | `5` | HTTP timeout (seconds) to the Akto service |
| `AKTO_INSTANCE_IP` | No | auto-detected | Source IP recorded in proxy payloads |
| `LOG_LEVEL` | No | `INFO` | Logging verbosity |
| `LOG_PAYLOADS` | No | `true` | Log full request/response payloads (privacy-sensitive) |

## What a guardrail policy's `behaviour` does

Every Akto guardrail policy has a `behaviour`, and it changes what the middleware does on a violation:

| `behaviour` | What happens | Client code needed |
|---|---|---|
| `block` | Raises `ValueError` immediately, before/after the model call. | `try/except ValueError` |
| `alert` | Proceeds silently — the violation is only logged server-side. | none |
| `warn` / `approval` | Pauses the agent (via LangGraph's `interrupt()`) and waits for a human decision. | see below |

## Quick start (block / alert only)

If none of your policies use `warn`/`approval`, this is the entire integration:

```python
from akto_middleware import AktoGuardrailsMiddleware
from langchain.agents import create_agent

agent = create_agent(
    model="gpt-4.1",
    tools=[...],
    middleware=[AktoGuardrailsMiddleware()],
)

try:
    result = agent.invoke({"messages": [{"role": "user", "content": user_input}]})
except ValueError as e:
    print(f"Blocked by Akto Guardrails: {e}")
```

No checkpointer, no thread management, nothing else to wire up.

## Adding `warn` / `approval` support

A `warn`/`approval` verdict means: don't just block, ask a human first. That
requires two things `block`/`alert` don't:

1. **A checkpointer** on `create_agent(..., checkpointer=...)` — LangGraph's
   `interrupt()` needs somewhere to persist the paused state. `InMemorySaver()`
   is fine for a single process; use a durable one (Postgres, Redis, etc.) if
   the pause must survive a restart or be answered by a different process.
2. **A stable `thread_id`** per conversation, passed in
   `config={"configurable": {"thread_id": ...}}` on every `invoke()`/`resume`
   call for that conversation — it's the key the checkpointer uses to look up
   the paused state, so this is how LangGraph knows which paused execution to
   continue. Any string works, as long as the same one is used for the call
   that paused and the call that resumes it — a mismatch just looks like
   "nothing to resume," it won't error.

   Who generates it is your call, not something the middleware requires one
   way or another:
   - The caller can generate it (e.g. a UUID made once when a chat starts,
     reused for every message in it), or
   - The server can generate it and hand it back in the first response, and
     the caller just echoes it on every later call — often the better fit if
     your app already has its own notion of "conversation ID"/"session ID"
     it wants to own.

   Either way, treat it as the same thing you already use to mean "this
   conversation" elsewhere in your app — it doesn't need to be a new concept
   invented just for guardrails.

Beyond that, **you decide how a human actually gets asked** — that's
inherently your app's concern, not something the middleware can predict. Two
helpers cover the two common shapes:

### `interrupt_payload(result)` — the primitive

```python
from akto_middleware import interrupt_payload

payload = interrupt_payload(result)  # None, or {"phase", "behaviour", "reason", "message"}
```

Checks an `agent.invoke()`/`ainvoke()` result for a pending pause. Use this
directly whenever `resolve_interrupts()` below doesn't fit your app's shape.

### `resolve_interrupts()` — for a CLI, or anywhere blocking is fine

Blocks the calling thread until the pause is resolved. Good fit when the
"human" answering is right there synchronously (a terminal, a script).

```python
from akto_middleware import AktoGuardrailsMiddleware, resolve_interrupts
from langchain.agents import create_agent
from langgraph.checkpoint.memory import InMemorySaver

agent = create_agent(
    model="gpt-4.1",
    tools=[...],
    middleware=[AktoGuardrailsMiddleware()],
    checkpointer=InMemorySaver(),
)

def ask_human(payload: dict) -> bool:
    # payload = {"phase": "request"|"response", "behaviour": "warn"|"approval", "reason": str, "message": str}
    return input(f"{payload['reason']} -- proceed anyway? [y/N]: ").strip().lower() == "y"

config = {"configurable": {"thread_id": "conversation-1"}}
try:
    result = agent.invoke({"messages": [{"role": "user", "content": user_input}]}, config=config)
    result = resolve_interrupts(agent, result, config, ask_human=ask_human)  # ask_human optional, defaults to input()
except ValueError as e:
    print(f"Blocked by Akto Guardrails: {e}")
```

A turn can pause more than once (once for the request, once for the
response) — `resolve_interrupts()` loops until it's fully resolved.

Full runnable version: [`test/test_agent.py`](test/test_agent.py).

### Web apps / anything that can't block on a human answering

An HTTP request can't sit there waiting for someone to click a button — they
might take a minute, or an hour, in a completely separate request. Use
`interrupt_payload()` directly and split the flow across two endpoints
instead of one blocking loop:

- One endpoint sends the message and returns `needs_approval` immediately
  instead of blocking, if `interrupt_payload(result)` isn't `None`.
- A second endpoint is called whenever the human actually answers, and
  resumes with `agent.invoke(Command(resume=decision), config=config)`.

Full runnable example: [`test/examples/flask_app.py`](test/examples/flask_app.py).

## Testing

See [`test/README.md`](test/README.md) for how to exercise this against a
real Akto guardrails backend or a local mock.
