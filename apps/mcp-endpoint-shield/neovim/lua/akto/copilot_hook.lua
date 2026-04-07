-- Intercepts copilot.lua requests by wrapping copilot.api.request().

local M = {}

local _orig_request = nil
local _hooked = false

local COMPLETION_METHODS = {
  getCompletions = true,
  getCompletionsCycling = true,
  getPanelCompletions = true,
  ["textDocument/inlineCompletion"] = true,
}

local function hook(api_mod)
  if _hooked then return end
  _orig_request = api_mod.request
  _hooked = true

  api_mod.request = function(client, method, params, callback)
    if not COMPLETION_METHODS[method] then
      return _orig_request(client, method, params, callback)
    end

    local helpers = require("akto.plenary_hook")
    local doc = (params and params.doc) or {}
    local summary = vim.fn.json_encode({
      method = method,
      uri = doc.uri or (params.textDocument and params.textDocument.uri) or "",
      position = doc.position or params.position or {},
    })
    local payload = helpers._build_payload("https://copilot.github.com/copilot/" .. method, summary, "{}", 200)
    helpers._akto_post_async({ akto_connector = "neovim", ingest_data = "true" }, payload)

    -- Wrap callback to capture response
    local orig_cb = callback
    local wrapped_cb = orig_cb and function(err, data, ctx)
      if data then
        local count = #(data.completions or data.items or {})
        local resp = vim.fn.json_encode({ method = method, completion_count = count })
        local p = helpers._build_payload("https://copilot.github.com/copilot/" .. method, summary, resp, 200)
        helpers._akto_post_async({ akto_connector = "neovim", ingest_data = "true" }, p)
      end
      return orig_cb(err, data, ctx)
    end

    return _orig_request(client, method, params, wrapped_cb or callback)
  end
end

function M.enable(cfg)
  local ok, api = pcall(require, "copilot.api")
  if ok and api and api.request then
    hook(api)
    return
  end
  require("akto.plenary_hook")._add_deferred("copilot.api", function(mod)
    if not _hooked and mod and mod.request then hook(mod) end
  end)
end

function M.disable()
  if _orig_request then
    local api = package.loaded["copilot.api"]
    if api then api.request = _orig_request end
    _orig_request = nil
    _hooked = false
  end
end

return M
