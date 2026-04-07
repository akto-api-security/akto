# Akto Endpoint Shield — Neovim

Guardrails and observability for Neovim AI plugins. Intercepts LLM API calls, validates prompts via Akto guardrails, and ingests request/response data — without proxying or redirecting traffic. Plugins talk to LLM APIs directly.

## Supported Plugins

All 7 major Neovim AI plugins are covered:

| Plugin | Stars | Hook | How it works |
|--------|-------|------|-------------|
| avante.nvim | 17.7k | `plenary_hook` | Wraps `plenary.curl` |
| copilot.vim | 11.5k | `copilot_vim_hook` | Wraps `_copilot.lsp_request` |
| codecompanion.nvim | 6.4k | `plenary_hook` | Wraps `plenary.curl` |
| windsurf.vim | 5.1k | `windsurf_hook` | Wraps `vim.fn.jobstart` + `chansend` |
| copilot.lua | 4.0k | `copilot_hook` | Wraps `copilot.api.request` |
| ChatGPT.nvim | 4.0k | `plenary_hook` | Wraps `plenary.job` |
| CopilotChat.nvim | 3.6k | `plenary_hook` | Wraps `plenary.curl` |

## Installation

### 1. Copy plugin files

```bash
mkdir -p ~/.config/nvim/lua/akto
cp lua/akto/*.lua ~/.config/nvim/lua/akto/
```

### 2. Add to your Neovim config

```lua
-- ~/.config/nvim/init.lua
-- Load plugins first (lazy.nvim, packer, etc.), then akto:

require("lazy").setup({ ... })

require("akto").setup({
  akto_url = "http://localhost:9091",
})
```

That's it. All hooks activate automatically.

## Configuration

```lua
require("akto").setup({
  akto_url         = "",      -- Akto backend URL (required)
  sync_mode        = true,    -- true: block before LLM call, false: observe after
  timeout          = 5,       -- seconds for guardrails check
  plenary_hook     = true,    -- avante, codecompanion, CopilotChat, ChatGPT
  copilot_hook     = true,    -- copilot.lua
  copilot_vim_hook = true,    -- copilot.vim
  windsurf_hook    = true,    -- windsurf.vim
  events           = true,    -- autocmd listeners
})
```

### Sync vs Async mode

**Sync mode** (`sync_mode = true`, default):
```
User prompt → guardrails check → blocked or allowed → LLM call → ingest
```
- Guardrails run **before** the LLM call
- Blocked prompts never reach the LLM API
- Adds latency (guardrails check is synchronous)

**Async mode** (`sync_mode = false`):
```
User prompt → LLM call → guardrails + ingest (async, non-blocking)
```
- LLM call goes through immediately, no delay
- Guardrails and ingestion happen after the call
- Good for observability without blocking

### Disabling specific hooks

```lua
-- Only cover plenary-based plugins, skip copilot and windsurf
require("akto").setup({
  akto_url         = "http://localhost:9091",
  copilot_hook     = false,
  copilot_vim_hook = false,
  windsurf_hook    = false,
})
```

## Commands

| Command | Description |
|---------|-------------|
| `:AktoEnable` | Enable all hooks |
| `:AktoDisable` | Disable all hooks, restore originals |
| `:AktoStatus` | Show current state |

## File structure

```
lua/akto/
├── init.lua              — setup, enable/disable, commands
├── plenary_hook.lua      — wraps plenary.curl + plenary.job
├── copilot_hook.lua      — wraps copilot.lua API
├── copilot_vim_hook.lua  — wraps copilot.vim LSP bridge
├── windsurf_hook.lua     — wraps vim.fn.jobstart for Codeium
└── events.lua            — autocmd listeners
```

## How it works

The plugin wraps the HTTP/LSP functions that each AI plugin uses internally. When a plugin makes an LLM API call:

1. The hook intercepts the call
2. In sync mode: sends the request body to `{akto_url}/api/http-proxy?guardrails=true` for validation
3. If blocked: returns a 403 error to the plugin, LLM is never called
4. If allowed (or async mode): the plugin talks to the LLM API directly
5. After the call: sends request + response to `{akto_url}/api/http-proxy?ingest_data=true`

This is the same Akto backend API used by Claude CLI hooks and Cursor hooks. No proxy server, no traffic redirection — plugins always talk to LLM APIs directly using their original URLs.

## Requirements

- Neovim 0.9+
- `curl` on PATH (used for Akto backend calls)
- Akto data ingestion service running (e.g. `http://localhost:9091`)
