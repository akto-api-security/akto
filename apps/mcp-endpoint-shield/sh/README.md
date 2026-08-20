# Akto shell hooks

One hook handler for every agent, with no third-party runtime. Replaces the
per-agent Python validators under `../<agent>-hooks/`.

```
akto-hook.sh  <connector> <event>     # macOS / Linux
akto-hook.ps1 <connector> <event>     # Windows
```

Both read the agent's hook JSON on stdin and write that agent's decision to
stdout and/or signal it through the exit code.

## Why there is no Python (and no jq)

| Need | Unix | Windows |
|---|---|---|
| JSON read/write | `awk` (`lib/json.awk`) | native `ConvertFrom-Json` / `ConvertTo-Json` |
| HTTP | `curl` | `Invoke-WebRequest` |
| SHA-256 | `shasum` / `sha256sum` / `openssl` | `System.Security.Cryptography` |

Every one of these is part of the base OS. Nothing is installed, and there is no
`jq` dependency — the usual blocker for JSON in shell.

The trick that makes shell JSON safe here is that **user content is never decoded**.
`lib/json.awk` returns the *raw* JSON text of a value — a prompt comes back still
quoted and still escaped — so it is spliced into the outgoing payload without ever
being unescaped and re-escaped. Escaping bugs on user content are therefore not
possible; the only strings the shell side encodes are ones it constructs itself.

## Layout

```
akto-hook.sh              entry point and event pipeline
lib/json.awk              JSON reader/writer (POSIX awk, no gawk extensions)
lib/akto_core.sh          config, logging, HTTP, payload, guardrails, warn flow
lib/akto_adapters.sh      the per-connector differences, and only those
ps/akto-hook.ps1          Windows twin (PowerShell 5.1 and 7+)
config/                   ready-to-use hook configs per agent
tests/                    132 tests — run tests/run_all.sh
```

`lib/akto_adapters.sh` is the whole of what varies between agents: which stdin
field carries the prompt and tool call, how an MCP tool is recognised, and how a
deny is expressed. Everything else is shared.

## Configuration

No per-hook wrapper scripts. `lib/akto_core.sh` reads, in order:

1. `~/.akto-endpoint-shield/config/config.env` — the agent's own config, so the
   `ENABLE_PROMPT_HOOKS_*` / `ENABLE_MCP_HOOKS_*` kill switches and a seeded
   `DEVICE_ID` apply automatically
2. `~/.akto/hooks.env` — hook settings (override with `AKTO_CONFIG_FILE`)

A real environment variable always wins over both, so an installer can still
override any single value.

```sh
# ~/.akto/hooks.env
AKTO_DATA_INGESTION_URL=https://your-akto-host
AKTO_API_TOKEN=...
MODE=atlas
AKTO_SYNC_MODE=true      # false = mirror only, never block
AKTO_TIMEOUT=5
LOG_LEVEL=INFO
LOG_PAYLOADS=false
```

Setting `ENABLE_PROMPT_HOOKS_CLAUDE=false` (or the matching `ENABLE_MCP_HOOKS_*`)
disables a hook in place, without uninstalling it.

## Supported connectors and events

| Connector | Prompt | Tool call | Tool result | Response |
|---|---|---|---|---|
| `claude_code_cli` | `UserPromptSubmit` | `PreToolUse` | `PostToolUse` | `Stop` |
| `codex_cli` | `UserPromptSubmit` | `PreToolUse` | `PostToolUse` | `Stop` |
| `cursor` | `beforeSubmitPrompt` | `beforeMCPExecution` | `afterMCPExecution` | `afterAgentResponse` |
| `gemini_cli` | `BeforeAgent` | `BeforeTool` | `AfterTool` | `AfterAgent` |
| `github` | `userPromptSubmitted` | `preToolUse` | `postToolUse` | — |
| `vscode` | `userPromptSubmitted` | `preToolUse` | `postToolUse` | — |
| `kiro_cli` | `userPromptSubmit` | `preToolUse` | `postToolUse` | — |

Any other event name is mirrored as fire-and-forget observability and never blocks.

### How each agent is told "deny"

Taken from each vendor's hook reference, and implemented in `adapter_emit_deny`:

- **Claude Code / Codex** — `hookSpecificOutput.permissionDecision` for tool
  events, `{"decision":"block"}` for prompt and stop. Exit 0 either way.
- **Cursor** — `{"permission":"deny", user_message, agent_message}`.
- **GitHub Copilot / VS Code Copilot** — `{"permissionDecision":"deny"}`.
- **Gemini CLI** — `{"decision":"block","reason":...}`.
- **Kiro CLI** — `preToolUse` blocks with **exit 2** and stderr goes back to the
  model. `userPromptSubmit` *cannot* block at all, so a violation is injected into
  the model's context instead (exit 0, stdout becomes context).

## Wiring

Examples in `config/`. Claude Code, using exec form so no shell is involved:

```json
{ "hooks": { "PreToolUse": [ { "hooks": [ {
  "type": "command",
  "command": "~/.akto/hooks/akto-hook.sh",
  "args": ["claude_code_cli", "PreToolUse"],
  "timeout": 10 } ] } ] } }
```

GitHub Copilot CLI takes a single cross-platform `command` key, which it copies to
both `bash` and `powershell` — so one entry covers all three OSes:

```json
{ "version": 1, "hooks": { "preToolUse": [ {
  "type": "command",
  "command": "akto-hook <connector> preToolUse",
  "timeoutSec": 30 } ] } }
```

## Failure behaviour

Every path fails **open**. A missing config, an unreachable backend, a malformed
response, a timeout or an internal error all allow the action. Guardrails must
never wedge the agent they are protecting. `tests/test_hooks.sh` asserts this for
backend-down, non-JSON stdin and empty stdin.

## Tests

```sh
bash tests/run_all.sh
```

- `test_json.sh` — 33 cases over `lib/json.awk`: JSON syntax embedded in string
  values, escaped quotes, UTF-8, pretty-printed input, prefix keys, content blocks
- `test_hooks.sh` — 42 cases end-to-end against a mock ingestion endpoint: allow,
  deny per connector, MCP vs non-MCP paths, warn/alert behaviours, the kill switch,
  the fail-open guarantees, and the Python-parity details (bare `0` vxlan for MCP,
  the Stop prompt/response pair, the non-MCP block gate, `response_guardrails`)
- `test_hardening.sh` — 27 cases on hostile input and scale: command substitution
  and backticks in prompts must never execute, 200KB payloads, deep nesting,
  multi-fingerprint warn state, 8 concurrent writers, malformed input
- `test_parity.sh` — 30 cases asserting the PowerShell twin makes identical
  decisions *and* sends identical wire payloads (skips when `pwsh` is absent)

Three bugs these caught, kept as regression cases: `Write-Output` inside a
value-captured PowerShell function swallows stdout; `substr(s,i,1)` is O(n) in BWK
awk, making both scan loops quadratic; and a JSON literal written inline inside a
nested `"$( ... )"` is brace-expanded and split on its commas.

`tests/mock_server.py` is test-only scaffolding; the hooks have no Python
dependency.
