-- Intercepts windsurf.vim (Codeium) by wrapping vim.fn.jobstart + chansend.
-- Codeium uses: jobstart(["curl","-L",url,...]) + chansend(id, json_data)

local http = require("akto.http")

local M = {}

local _orig_jobstart = nil
local _orig_chansend = nil
local _hooked = false
local _job_data = {}

local function is_codeium_curl(args)
  if type(args) ~= "table" or #args < 3 or args[1] ~= "curl" then return false end
  for _, arg in ipairs(args) do
    if type(arg) == "string" and arg:find("LanguageServerService", 1, true) then return true end
  end
  return false
end

local function extract_method(args)
  for _, arg in ipairs(args) do
    if type(arg) == "string" and arg:find("LanguageServerService", 1, true) then
      return arg:match("LanguageServerService/(%w+)") or "unknown"
    end
  end
  return "unknown"
end

local function ingest(method, req_data, resp_data)
  local payload = http.build_payload(
    "https://codeium.local/windsurf/" .. method,
    req_data or "{}",
    resp_data or "{}",
    200
  )
  http.post_async({ akto_connector = "neovim", ingest_data = "true" }, payload)
end

function M.enable(cfg)
  if _hooked then return end
  _hooked = true

  _orig_jobstart = vim.fn.jobstart
  vim.fn.jobstart = function(args, opts, ...)
    if not (type(args) == "table" and is_codeium_curl(args)) then
      return _orig_jobstart(args, opts, ...)
    end

    local method = extract_method(args)

    if opts and type(opts) == "table" then
      local orig_on_stdout = opts.on_stdout
      local chunks = {}
      opts = vim.tbl_extend("force", opts, {
        on_stdout = function(ch, data, name)
          if data then for _, c in ipairs(data) do if c ~= "" then chunks[#chunks + 1] = c end end end
          if orig_on_stdout then orig_on_stdout(ch, data, name) end
        end,
      })
      local orig_on_exit = opts.on_exit
      opts.on_exit = function(job, status, event)
        if _job_data[job] ~= nil then
          ingest(method, _job_data[job] or "", table.concat(chunks, ""))
          _job_data[job] = nil
        end
        if orig_on_exit then orig_on_exit(job, status, event) end
      end
    end

    local job_id = _orig_jobstart(args, opts, ...)
    if type(job_id) == "number" and job_id > 0 then
      _job_data[job_id] = ""
    end
    return job_id
  end

  _orig_chansend = vim.fn.chansend
  vim.fn.chansend = function(id, data)
    if _job_data[id] ~= nil then
      _job_data[id] = type(data) == "string" and data or vim.fn.json_encode(data)
    end
    return _orig_chansend(id, data)
  end
end

function M.disable()
  if _orig_jobstart then vim.fn.jobstart = _orig_jobstart; _orig_jobstart = nil end
  if _orig_chansend then vim.fn.chansend = _orig_chansend; _orig_chansend = nil end
  _hooked = false
  _job_data = {}
end

return M
