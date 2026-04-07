-- Akto Endpoint Shield for Neovim
-- Intercepts LLM API calls for guardrails and observability.
--
-- Usage:
--   require("lazy").setup({ ... })
--   require("akto").setup({ akto_url = "https://your-akto-instance.com" })

local M = {}

local defaults = {
  akto_url         = "",
  sync_mode        = true,
  timeout          = 5,
  plenary_hook     = true,
  copilot_hook     = true,
  copilot_vim_hook = true,
  windsurf_hook    = true,
  events           = true,
}

local _config = {}
local _enabled = false

function M.setup(opts)
  _config = vim.tbl_deep_extend("force", defaults, opts or {})

  if _config.akto_url == "" then
    vim.notify("[akto] akto_url is required", vim.log.levels.ERROR)
    return
  end

  _config.akto_url = _config.akto_url:gsub("/$", "")
  M.enable()

  vim.api.nvim_create_user_command("AktoEnable", M.enable, {})
  vim.api.nvim_create_user_command("AktoDisable", M.disable, {})
  vim.api.nvim_create_user_command("AktoStatus", M.status, {})
end

function M.enable()
  if _config.akto_url == "" then return end
  local cfg = { akto_url = _config.akto_url, sync_mode = _config.sync_mode, timeout = _config.timeout }

  if _config.plenary_hook     then require("akto.plenary_hook").enable(cfg) end
  if _config.copilot_hook     then require("akto.copilot_hook").enable(cfg) end
  if _config.copilot_vim_hook then require("akto.copilot_vim_hook").enable(cfg) end
  if _config.windsurf_hook    then require("akto.windsurf_hook").enable(cfg) end
  if _config.events           then require("akto.events").enable(_config.akto_url) end

  _enabled = true
  local mode = _config.sync_mode and "sync" or "async"
  vim.notify("[akto] enabled (" .. mode .. "): " .. _config.akto_url)
end

function M.disable()
  if _config.plenary_hook     then require("akto.plenary_hook").disable() end
  if _config.copilot_hook     then require("akto.copilot_hook").disable() end
  if _config.copilot_vim_hook then require("akto.copilot_vim_hook").disable() end
  if _config.windsurf_hook    then require("akto.windsurf_hook").disable() end

  _enabled = false
  vim.notify("[akto] disabled")
end

function M.status()
  if not _enabled then
    print("[akto] DISABLED")
    return
  end
  local mode = _config.sync_mode and "sync" or "async"
  print("[akto] ACTIVE (" .. mode .. "): " .. _config.akto_url)
  print("  plenary_hook     = " .. tostring(_config.plenary_hook))
  print("  copilot_hook     = " .. tostring(_config.copilot_hook))
  print("  copilot_vim_hook = " .. tostring(_config.copilot_vim_hook))
  print("  windsurf_hook    = " .. tostring(_config.windsurf_hook))
  print("  events           = " .. tostring(_config.events))
end

return M
