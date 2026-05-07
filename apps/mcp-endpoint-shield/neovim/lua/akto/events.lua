-- Autocmd listeners for plugin events (codecompanion, CopilotChat, avante).
-- Notifies when the Akto guardrails block a request (403).

local M = {}

local _url = ""
local _registered = false

local function register()
  local grp = vim.api.nvim_create_augroup("AktoEvents", { clear = true })

  vim.api.nvim_create_autocmd("User", {
    group = grp,
    pattern = "CodeCompanionRequestStarted",
    callback = function() vim.notify("[akto] codecompanion request → " .. _url, vim.log.levels.DEBUG) end,
  })

  vim.api.nvim_create_autocmd("User", {
    group = grp,
    pattern = "CodeCompanionRequestFinished",
    callback = function(e)
      if (e.data or {}).status == 403 then
        vim.notify("[akto] Blocked by Akto: " .. ((e.data or {}).reason or ""), vim.log.levels.WARN)
      end
    end,
  })

  vim.api.nvim_create_autocmd("User", {
    group = grp,
    pattern = "CopilotChatResponse",
    callback = function(e)
      if (e.data or {}).status == 403 then
        vim.notify("[akto] Blocked by Akto: " .. ((e.data or {}).reason or ""), vim.log.levels.WARN)
      end
    end,
  })
end

function M.avante_callbacks()
  return {
    on_start = function() vim.notify("[akto] avante request → " .. _url, vim.log.levels.DEBUG) end,
    on_complete = function(_, resp)
      if (resp or {}).status == 403 then
        vim.notify("[akto] Blocked by Akto: " .. ((resp or {}).reason or ""), vim.log.levels.WARN)
      end
    end,
  }
end

function M.enable(url)
  _url = url
  if _registered then return end
  _registered = true
  register()
end

function M.disable()
  if not _registered then return end
  vim.api.nvim_create_augroup("AktoEvents", { clear = true })
  _registered = false
end

return M
