# Akto Guardrails — Claude Agent SDK Hooks

Async Python callbacks that integrate Akto guardrails into applications built with the [Claude Agent SDK](https://code.claude.com/docs/en/agent-sdk/overview).

These hooks provide the same four guardrails behaviours as `claude-cli-hooks/`, adapted from the CLI shell-command model to the Agent SDK callback model.

## Hook Coverage

| Hook | Event | Behaviour |
|---|---|---|
| `akto_user_prompt_submit` | `UserPromptSubmit` | Validates user prompt; blocks if denied by guardrails |
| `akto_stop` | `Stop` | Ingests completed conversation turn for observability |
| `akto_pre_tool_use` | `PreToolUse` | Validates MCP/built-in tool calls; blocks if denied |
| `akto_post_tool_use` | `PostToolUse` | Ingests tool execution results for observability |

## Installation

No external dependencies. Requires Python 3.8+ (uses `asyncio.to_thread`).

Place the three Python files alongside your agent code, or install from a path:

```
claude-agent-sdk-hooks/
├── akto_machine_id.py
├── akto_guardrails_core.py
└── hooks.py
```

## Usage

```python
from hooks import (
    akto_user_prompt_submit,
    akto_stop,
    akto_pre_tool_use,
    akto_post_tool_use,
)
from claude_agent_sdk import ClaudeAgentOptions, HookMatcher

options = ClaudeAgentOptions(
    hooks={
        "UserPromptSubmit": [HookMatcher(hooks=[akto_user_prompt_submit])],
        "Stop":             [HookMatcher(hooks=[akto_stop])],
        "PreToolUse":       [HookMatcher(hooks=[akto_pre_tool_use])],
        "PostToolUse":      [HookMatcher(hooks=[akto_post_tool_use])],
    }
)
```

Set environment variables before starting your agent (see below), then pass `options` to your agent.

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `AKTO_DATA_INGESTION_URL` | *(required)* | Base URL for Akto's data ingestion service |
| `AGENT_ID` | `""` | Stable identifier for this server/agent instance (pod name, service name, etc.). Used as `akto_vxlan_id` in payloads. |
| `AKTO_HOST` | `api.anthropic.com` | Hostname written into request headers for Akto's HTTP proxy |
| `AKTO_SYNC_MODE` | `true` | `true` = block on violations; `false` = observe-only (no blocking) |
| `AKTO_TIMEOUT` | `5` | HTTP request timeout in seconds |
| `MODE` | `argus` | `argus` (default) or `atlas` |
| `AKTO_CONNECTOR` | `claude_agent_sdk` | Source label shown in Akto dashboard |
| `LOG_DIR` | `~/.claude/akto/logs` | Directory for log files |
| `LOG_LEVEL` | `INFO` | Logging level (`DEBUG`, `INFO`, `WARNING`, `ERROR`) |
| `LOG_PAYLOADS` | `false` | Set to `true` to log full request/response bodies |
| `SSL_CERT_PATH` | *(unset)* | Path to custom CA certificate bundle |

## Differences from `claude-cli-hooks`

| Aspect | CLI hooks | Agent SDK hooks |
|---|---|---|
| Invocation | Shell command via `settings.json` | Async Python callback function |
| Input | JSON via stdin | `input_data` dict argument |
| Block (UserPromptSubmit) | `print({"decision":"block",...})` | `return {"continue_": False, "systemMessage": ...}` |
| Block (PreToolUse) | `print({"decision":"block",...})` | `return {"hookSpecificOutput": {"permissionDecision": "deny", ...}}` |
| HTTP calls | Synchronous `urllib` | `urllib` wrapped in `asyncio.to_thread` |
| Configuration | Set by `.sh` wrapper scripts | Standard environment variables |
| `AKTO_CONNECTOR` default | `claude_code_cli` | `claude_agent_sdk` |
| Device/server ID | Derived from machine UUID | `AGENT_ID` env var |
| Host in headers | Derived from `CLAUDE_API_URL` | `AKTO_HOST` env var |
| `contextSource` | `ENDPOINT` | `AGENTIC` (hardcoded) |
| `source` tag | set to `CONTEXT_SOURCE` value | omitted |

## Logging

All hooks write to a single log file:

```
$LOG_DIR/akto-agent-sdk.log
```

## Sync vs Async Mode

**`AKTO_SYNC_MODE=true` (default):**
- `UserPromptSubmit`: validates before the model is called; blocks denied prompts
- `PreToolUse`: validates before tool execution; blocks denied tool calls
- `Stop` / `PostToolUse`: always ingest (fire-and-forget, non-blocking)

**`AKTO_SYNC_MODE=false`:**
- All hooks are observational only — nothing is blocked
- `Stop` and `PostToolUse` ingest with combined guardrails+ingest call

## Warn Flow (UserPromptSubmit)

When a guardrail returns `behaviour=warn`, the prompt is blocked on first submission with a warning message. If the user submits the identical prompt a second time, it is allowed through. The pending fingerprint is stored in `$LOG_DIR/akto_prompt_warn_pending.json`.
