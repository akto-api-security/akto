# Akto Guardrails — Claude Agent SDK Hooks

Async Python callbacks that integrate Akto guardrails into applications built with the [Claude Agent SDK](https://code.claude.com/docs/en/agent-sdk/overview).

These hooks provide the same four guardrails behaviours as `claude-cli-hooks/`, adapted from the CLI shell-command model to the Agent SDK callback model.

## Hook Coverage

| Hook | Event | Behaviour |
|---|---|---|
| `akto_user_prompt_submit` | `UserPromptSubmit` | Validates user prompt; denies on a hard `block` (warn/alert pass through) |
| `akto_stop` | `Stop` | Validates the agent response and ingests the turn; denies on a hard `block` (warn/alert pass through) |
| `akto_pre_tool_use` | `PreToolUse` | Validates MCP/built-in tool calls; denies on a hard `block` (warn/alert pass through) |
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
| `AKTO_CONNECTOR_VALUE` | `claude_agent_sdk` | Client label used in tool tags (`mcp-client` for MCP, `ai-agent` for built-in tools) |
| `MCP_INGEST_PATH` | `/mcp` | Mirrored path for MCP tool calls (must match Akto's MCP path detection) |
| `NON_MCP_TOOL_PATH_PREFIX` | `/tool` | Path prefix for built-in / non-MCP tool calls (`/tool/<tool-name>`) |
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

## MCP traffic classification

Tool calls are shaped so Akto's runtime classifies them correctly (`McpRequestResponseUtils.isMcpRequest` / `JsonRpcUtils.isMcpPath`):

| | MCP tool (`mcp__<server>__<tool>`) | Built-in / non-MCP tool |
|---|---|---|
| Path | `/mcp` | `/tool/<normalized-tool-name>` |
| Request body | JSON-RPC `tools/call` (`{"jsonrpc":"2.0","method":"tools/call","params":{"name","arguments"},"id"}`) | `{"body": <input>, "toolName": <name>}` |
| Response body (PostToolUse) | JSON-RPC result (`{"jsonrpc":"2.0","id","result"}`) | `{"body":{"result": <output>}}` |
| Tags | `{mcp-server, mcp-client}` | `{gen-ai, ai-agent}` |
| Header | adds `x-mcp-server: <server>` | — |

`parse_claude_tool()` splits `mcp__<server>__<tool>`; anything else is treated as a built-in tool. The `Stop`/PostToolUse hooks remain observational — PostToolUse never blocks (the Agent SDK cannot un-run an already-executed tool).

## Logging

All hooks write to a single log file:

```
$LOG_DIR/akto-agent-sdk.log
```

## Sync vs Async Mode

**`AKTO_SYNC_MODE=true` (default):**
- `UserPromptSubmit`: validates before the model is called; resolves the verdict via `resolve_guardrail_decision`
- `PreToolUse`: validates before tool execution; resolves the verdict via `resolve_guardrail_decision`
- `Stop` / `PostToolUse`: always ingest (fire-and-forget, non-blocking)

**`AKTO_SYNC_MODE=false`:**
- All hooks are observational only — nothing is blocked
- `Stop` and `PostToolUse` ingest with combined guardrails+ingest call

## Guardrail Behaviours (UserPromptSubmit, PreToolUse & Stop)

All three validating hooks resolve a guardrail verdict through `resolve_guardrail_decision`, honouring the `behaviour` field returned by the guardrail:

| Behaviour | Effect |
|---|---|
| *(allowed)* | Pass through |
| `block` (or unset) | Deny |
| `alert` | Allow; the violation is recorded server-side |
| `warn` | Allow; treated as `alert` in the Agent SDK (see below) |

### Why `warn` is not a resubmit gate here

The CLI hooks implement `warn` as "deny on first sight, allow on an identical resubmit" — a human reads the warning and consciously resends. That model **does not apply to the Agent SDK**:

- `UserPromptSubmit`: `continue_: False` ends the turn; there is no built-in resubmit (the calling app must issue a new query), and the message isn't surfaced unless `include_hook_events` is set.
- `PreToolUse`: a `deny` reason is fed back to the *model*, which is designed to **avoid retrying** and try a different approach — so an identical retry effectively never happens, and any retry is the model self-approving, not a human review.

Because there is no human-in-the-loop "review and resend" step, `warn` is treated as `alert`: allowed through, with the violation recorded server-side during guardrail evaluation. No client-side resubmit state is kept.
