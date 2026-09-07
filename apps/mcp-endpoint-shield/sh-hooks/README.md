# sh-hooks

Bash implementations of the Akto MCP Endpoint Shield hooks — no runtime
interpreter required, just `bash`, `curl`, and `jq`.

- `claude-cli-hooks/` — hooks for Claude Code CLI
- `codex-cli-hooks/` — hooks for OpenAI Codex CLI

Each directory is self-contained and flat, matching the layout the installer
copies into `~/.claude/hooks/` or `~/.codex/hooks/`: every `akto-*.sh` script
sources `akto_common.sh` from its own directory, so it has no dependency on
anything outside the folder.

## Requirements

`bash`, `curl`, and `jq`. All three ship by default on macOS and are a
one-line install on any Linux package manager. Each script checks for them
at startup and fails open (allows the request, logs a clear error) if either
is missing, rather than breaking silently.

See each directory's own README for setup instructions.
