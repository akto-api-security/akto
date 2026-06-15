# Akto claude-cli hooks — shell port (bash + PowerShell)

Native shell reimplementation of the Python claude-cli hooks, so machines
without Python can run them. **No python, no jq, no installed modules.**

| OS | Runtime used | Extra tools needed |
|----|--------------|--------------------|
| macOS / Linux | `bash` (works on bash 3.2+) | **`curl`** (ships by default; pure bash cannot do TLS) |
| Windows | `pwsh` (PowerShell 7+) or Windows PowerShell 5.1 | none — `Invoke-RestMethod` is built in |

> bash is not on Windows and PowerShell is not the default shell on macOS/Linux,
> so this is two implementations of the same logic. Wire whichever matches the host.

## Status — reference slice (tested)

Proven end-to-end against a mock guardrails server (block / allow / warn-resubmit /
guardrail-modify / non-MCP), both shells:

- [x] `akto-validate-prompt`      (UserPromptSubmit)
- [x] `akto-validate-mcp-request` (PreToolUse — MCP + non-MCP, deny + updatedInput)
- [ ] `akto-validate-response`      (Stop) — pending
- [ ] `akto-validate-mcp-response`  (PostToolUse) — pending
- [ ] other agents (codex, gemini, github, cursor, opencode, argus) — same libs, pending

`akto_common.sh` / `AktoCommon.psm1` hold all shared logic; remaining hooks are
small mains on top of them.

## Wiring (`~/.claude/settings.json`)

bash (macOS/Linux):
```json
{ "hooks": { "UserPromptSubmit": [ { "hooks": [
  { "type": "command", "command": "/abs/path/bash/akto-validate-prompt.sh" } ] } ] } }
```

PowerShell (Windows):
```json
{ "hooks": { "UserPromptSubmit": [ { "hooks": [
  { "type": "command", "command": "pwsh -File C:\\path\\powershell\\akto-validate-prompt.ps1" } ] } ] } }
```

Same env vars as the Python hooks: `AKTO_DATA_INGESTION_URL`, `MODE`, `AKTO_SYNC_MODE`,
`AKTO_CONNECTOR`, `AKTO_CONNECTOR_VALUE`, `AKTO_API_TOKEN`, `LOG_DIR`, `LOG_LEVEL`, etc.
Behaviour matches 1:1: read hook JSON on stdin → POST mirrored request to the proxy →
parse `guardrailsResult` → warn/alert/block flow → emit decision JSON on stdout, exit 0.

## Caveats found during the port (the real cost of shell)

1. **bash needs `curl`.** Pure bash has no TLS — `/dev/tcp` is plaintext only. `curl`
   ships on modern macOS/Windows/Linux but is absent on minimal containers.
2. **bash JSON is a hand-rolled reader** (`json_raw`/`json_string` in `akto_common.sh`).
   It handles top-level keys + nested round-tripping, which is all these hooks need, but
   it is not a full JSON parser and scans char-by-char (fine for hook-sized inputs, slow
   for very large transcripts). PowerShell uses native `ConvertFrom-Json`.
3. **bash 3.2 quirks are real** — e.g. `"${arr[@]}"` on an empty array errors under
   `set -u` (hit and fixed during testing). Apple still ships 3.2.
4. **PowerShell ordering** — fingerprints must use `[ordered]@{}`; plain `@{}` hashtables
   serialize keys in nondeterministic order and break the warn-resubmit dedupe.
5. **Fingerprints are implementation-local** (used only for warn-resubmit state on the
   same machine); they intentionally need not match the Python hash byte-for-byte.

## Local testing

A mock guardrails server (`/tmp/mock_guardrails.py`, test-only) returns canned
`guardrailsResult` objects. Pipe sample hook JSON to a script with
`AKTO_DATA_INGESTION_URL` pointed at it and assert the stdout decision.
