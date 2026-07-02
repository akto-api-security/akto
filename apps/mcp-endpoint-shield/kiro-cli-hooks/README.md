# Akto Guardrails for Kiro CLI

Validation and monitoring for `kiro-cli` (Kiro's terminal agent, from the Amazon Q
Developer CLI lineage). Mirrors the Cursor / Gemini CLI hook integrations.

Unlike the **Kiro IDE**, whose Agent Hooks are automation-only (file events →
`alert`/`askAgent`, no command execution and no blocking), **kiro-cli exposes true
lifecycle hooks that run a command and can reject the action**:

Per the [Kiro CLI hooks docs](https://kiro.dev/docs/cli/hooks/), **only `preToolUse`
can block** (exit code 2 rejects the tool, STDERR goes to the LLM). `userPromptSubmit`
explicitly *cannot* block the prompt — it can only add context or show a warning.

| Event | Purpose | Blocking? |
|-------|---------|-----------|
| `userPromptSubmit` | Flag/ingest the prompt for visibility; warn the user | ⚠️ warn only — **cannot block** (exit 1 → warning shown, prompt proceeds) |
| `preToolUse`       | Validate a tool call before it executes    | ✅ **blocks** — exit 2 rejects the tool |
| `postToolUse`      | Ingest tool results for monitoring         | observe only |

## How it works

1. kiro-cli invokes the registered `command` with the event payload on **stdin**.
2. The wrapper script sets the Akto env (connector `kiro_cli`, ingestion URL, token)
   and execs `akto-hooks.py <event>`.
3. `akto-hooks.py` routes blocking events to `run_exit_code_blocking_hook` and
   observability events to `run_observability_hook` (shared `akto_ingestion_utility`).
4. For blocking events, the payload is sent to Akto with `guardrails=true`. If the
   guardrail result is not `Allowed`, the hook prints the reason to **stderr** and
   exits with code **2**, which kiro-cli surfaces as a "pre-tool hook rejection".

## Install

Files are placed in `~/.kiro/hooks/akto/`:

- `akto-hooks.py` — event dispatch
- `akto-validate-prompt-wrapper.sh` — userPromptSubmit
- `akto-validate-pre-tool-wrapper.sh` — preToolUse
- `akto-validate-post-tool-wrapper.sh` — postToolUse
- `akto_ingestion_utility.py`, `akto_machine_id.py` — shared runtime

Then the `hooks` block from `agent-hooks.example.json` is merged into the kiro-cli
agent config (global agent dir `~/.kiro/agents/`, or via `kiro-cli agent edit`).

Managed by the MCP Endpoint Shield installer and gated by the
`ENABLE_PROMPT_HOOKS_KIRO_CLI` / `ENABLE_MCP_HOOKS_KIRO_CLI` env flags.
