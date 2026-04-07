-- Intercepts copilot.vim requests by wrapping _copilot.lsp_request().

local M = {}

local _orig_lsp_request = nil
local _hooked = false

local COMPLETION_METHODS = {
  getCompletions = true,
  getCompletionsCycling = true,
  ["textDocument/inlineCompletion"] = true,
}

local function hook(mod)
  if _hooked or not mod.lsp_request then return end
  _orig_lsp_request = mod.lsp_request
  _hooked = true

  mod.lsp_request = function(client_id, method, params, bufnr)
    if COMPLETION_METHODS[method] then
      local helpers = require("akto.plenary_hook")
      local summary = vim.fn.json_encode({ method = method, bufnr = bufnr, client_id = client_id })
      local payload = helpers._build_payload("https://copilot.github.com/copilot-vim/" .. method, summary, "{}", 200)
      helpers._akto_post_async({ akto_connector = "neovim", ingest_data = "true" }, payload)
    end
    return _orig_lsp_request(client_id, method, params, bufnr)
  end
end

function M.enable(cfg)
  local ok, mod = pcall(require, "_copilot")
  if ok and mod and mod.lsp_request then
    hook(mod)
    return
  end
  require("akto.plenary_hook")._add_deferred("_copilot", function(mod)
    if not _hooked and mod and mod.lsp_request then hook(mod) end
  end)
end

function M.disable()
  if _orig_lsp_request then
    local mod = package.loaded["_copilot"]
    if mod then mod.lsp_request = _orig_lsp_request end
    _orig_lsp_request = nil
    _hooked = false
  end
end

return M
