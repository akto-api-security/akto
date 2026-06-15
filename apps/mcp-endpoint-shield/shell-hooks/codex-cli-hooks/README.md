# Akto codex-cli shell hooks (no Python, no jq)

Native shell (bash + PowerShell) port of the Akto **codex-cli** Python hooks. These run
with zero Python and zero `jq` dependency — only `bash`/`curl` (or `pwsh`). The bash
hooks target **bash 3.2** (the version Apple ships with macOS).

This is a 1:1 behavioural port of the Python hooks in
`apps/mcp-endpoint-shield/codex-cli-hooks/`, following the tested patterns from
`shell-hooks/claude-cli-hooks/`.

## What was ported

| Hook event       | Python source                  | bash                          | PowerShell                     |
|------------------|--------------------------------|-------------------------------|--------------------------------|
| UserPromptSubmit | `akto-validate-prompt.py`      | `akto-validate-prompt.sh`     | `akto-validate-prompt.ps1`     |
| Stop             | `akto-validate-response.py`    | `akto-validate-response.sh`   | `akto-validate-response.ps1`   |
| PreToolUse       | `akto-validate-pre-tool.py`    | `akto-validate-pre-tool.sh`   | `akto-validate-pre-tool.ps1`   |
| PostToolUse      | `akto-validate-post-tool.py`   | `akto-validate-post-tool.sh`  | `akto-validate-post-tool.ps1`  |
| Observability    | `akto-hooks.py` + utility      | `akto-hooks.sh`               | `akto-hooks.ps1`               |

Shared machinery (config, logging, pure-shell JSON reader, machine id / username,
curl/Invoke-RestMethod POST, guardrails parsing, warn-resubmit flow, sha256
fingerprint) lives in `bash/akto_common.sh` and `powershell/AktoCommon.psm1`.

`install-shell-hooks.sh` deploys the bash hooks into `~/.codex/hooks`, generates
runtime-detecting wrappers (prefer `python3` if present, else run the shell hook;
force shell with `AKTO_HOOK_RUNTIME=shell`), and writes `~/.codex/hooks.json`
(codex registers hooks via `hooks.json`, **not** `settings.json`).

## Connector identity

- `AKTO_CONNECTOR=codex_cli`
- `AKTO_CONNECTOR_VALUE` / tag = `codexcli`
- per-hook header: `x-codex-hook`
- atlas-mode mirror host: `https://<device-id>.ai-agent.codexcli`

## Per-CLI schema differences vs claude-cli

These are the points where the codex hooks diverge from the claude reference and are
faithfully reproduced here:

1. **Log dir default**: `~/.codex/akto/logs` (claude uses `~/.claude/akto/logs`).
2. **API host/path auto-detection** (codex-specific, mirrors `_detect_codex_api`):
   - `OPENAI_BASE_URL` set → that base + path `/v1/responses`
   - else `OPENAI_API_KEY` set → `https://api.openai.com` + `/v1/responses`
   - else → `https://chatgpt.com` + `/backend-api/codex/responses`
   The mirrored `path` is therefore **not** the fixed `/v1/messages` claude uses.
3. **Hook header** is `x-codex-hook` (claude: `x-claude-hook`).
4. **Session-info fields**: prompt/response use
   `session_id, transcript_path, cwd, hook_event_name, model, turn_id`;
   pre/post-tool additionally include `tool_use_id`. There is **no** `permission_mode`
   (claude includes it).
5. **`akto_vxlan_id`** is `0` for prompt/response/pre/post-tool payloads; only the
   observability hook uses the device id.
6. **Transcript format** (Stop hook): codex JSONL entries are
   `{"type":"response_item","payload":{"type":"message","role":"user","content":[{"type":"input_text"|"output_text"|"text","text":"..."}]}}`.
   The bash/ps reader walks these and concatenates the text blocks (claude used a
   flat `{"type":"user","message":{"content":...}}` shape).
7. **Non-MCP tags**: PreToolUse adds `tool_name`; PostToolUse adds `tool-use` +
   `tool_name`.
8. **Output decision schemas** (matched to the python exactly):
   - UserPromptSubmit block → `{"decision":"block","reason":...}`
   - PreToolUse deny → `{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":...}}`
     (codex pre-tool has **no** modified-input / `updatedInput` path — unlike claude).
   - PreToolUse non-warn block reason is the bare guardrails reason (claude prefixes
     `Tool request blocked:`).
   - PostToolUse block → `{"decision":"block","reason":...,"hookSpecificOutput":{"hookEventName":"PostToolUse","additionalContext":...}}`
   - **Stop block (the big difference)**: a *warn* uses `{"decision":"block","reason":...}`
     (continue with the reason as a synthetic prompt); a *strict deny* uses
     `{"continue":false,"stopReason":...,"systemMessage":...}` to end the turn.
     Claude's Stop hook only ever emits `{"decision":"block",...}`.
9. **Fingerprint canonical forms** (sorted-key JSON, sha256-hex):
   - prompt: `{"a":[],"p":<prompt>}`
   - pre-tool: `{"i":<tool_input>,"t":<tool_name>}`
   - response: `{"p":<prompt>,"r":<response>}`
   - post-tool: `{"i":<input>,"r":<response>,"t":<name>,"u":<tool_use_id>}`
     (4-key form including `tool_use_id`; claude post-tool used only `{"i":...}`).
10. Registration via `~/.codex/hooks.json` (with a `SessionStart` observability hook),
    not `settings.json`.

## Proxy query params per hook (unchanged from claude semantics)

- prompt / pre-tool: `guardrails=true&akto_connector=codex_cli&ingest_data=true`
- response / post-tool (sync guardrails check): `response_guardrails=true&akto_connector=codex_cli`
- observability: `akto_connector=codex_cli&ingest_data=true&client_hook=<HookName>`
- blocked-request ingestion: `akto_connector=codex_cli&ingest_data=true`

Fail-open everywhere: missing `AKTO_DATA_INGESTION_URL` or any proxy/curl error
allows the action and exits 0.

## Test results

A python mock guardrails server (`/tmp`, mock only — hooks stay python-free) was
pointed at via `AKTO_DATA_INGESTION_URL`, and representative codex-shaped stdin was
piped to every hook in both shells (`bash <hook>.sh`, `pwsh <hook>.ps1`), with
`LOG_DIR` redirected to `/tmp`.

Matrix — **28/28 checks pass (bash + pwsh)**:

| Case                                    | bash | pwsh |
|-----------------------------------------|------|------|
| prompt block → `decision:block`         | PASS | PASS |
| prompt allow → empty                    | PASS | PASS |
| prompt warn → block then resubmit allow | PASS | PASS |
| pre-tool block → `permissionDecision:deny` | PASS | PASS |
| pre-tool allow → empty                  | PASS | PASS |
| post-tool block → `decision:block` + PostToolUse hookSpecificOutput | PASS | PASS |
| post-tool allow → empty                 | PASS | PASS |
| response (Stop) strict deny → `continue:false` + `systemMessage` | PASS | PASS |
| response allow → empty                  | PASS | PASS |
| response warn → `decision:block`        | PASS | PASS |
| observability hook → `{}`               | PASS | PASS |

Additionally verified under the system **bash 3.2.57** (`/bin/bash`) on macOS: all
four validate hooks produce valid JSON matching the python output schemas, and no
`declare -A` / unguarded `set -u` array expansion is used.

## Usage

```sh
./install-shell-hooks.sh --ingestion-url https://your-akto-host --api-token "$TOKEN"
```

Then restart Codex CLI. To force shell runtime even when python3 is present:
`export AKTO_HOOK_RUNTIME=shell`.
