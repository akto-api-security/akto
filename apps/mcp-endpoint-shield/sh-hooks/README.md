# sh-hooks

Bash implementations of the Akto MCP Endpoint Shield hooks — no runtime
interpreter required, just `bash`, `curl`, and `jq`.

- `claude-cli-hooks/` — hooks for Claude Code CLI
- `codex-cli-hooks/` — hooks for OpenAI Codex CLI
- `copilot-hooks/` — hooks for GitHub Copilot CLI and VS Code Copilot Chat
  (one shared script set; both surfaces read the same `~/.copilot/hooks/hooks.json`)

Each directory is self-contained and flat, matching the layout the installer
copies into `~/.claude/hooks/`, `~/.codex/hooks/`, or `~/.copilot/hooks/akto/`:
every `akto-*.sh` script sources `akto_common.sh` from its own directory, so
it has no dependency on anything outside the folder.

## Requirements

`bash`, `curl`, and `jq`. All three ship by default on macOS and are a
one-line install on any Linux package manager. Each script checks for them
at startup and fails open (allows the request, logs a clear error) if either
is missing, rather than breaking silently.

See each directory's own README for setup and uninstall instructions.
