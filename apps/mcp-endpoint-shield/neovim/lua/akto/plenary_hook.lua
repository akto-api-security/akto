-- Intercepts plenary.curl and plenary.job calls to LLM APIs.
-- Covers: avante, codecompanion, CopilotChat (plenary.curl), ChatGPT (plenary.job).

local http = require("akto.http")

local M = {}

local _sync_mode = true
local _orig_curl = nil
local _orig_job = nil
local _curl_wrapped = false
local _job_wrapped = false
local _original_require = nil
local _deferred_hooks = {}
local _wrapped_fns = {}

local LLM_HOSTS = {
  "api.openai.com",
  "api.anthropic.com",
  "generativelanguage.googleapis.com",
  "api.cohere.ai",
  "api.mistral.ai",
  "api.groq.com",
  "openrouter.ai",
}

local function is_llm_url(url)
  if type(url) ~= "string" then return false end
  for _, host in ipairs(LLM_HOSTS) do
    if url:find(host, 1, true) then return true end
  end
  return false
end

local function notify_blocked(reason)
  local msg = reason ~= "" and ("Blocked by Akto: " .. reason) or "Blocked by Akto"
  vim.schedule(function() vim.notify("[akto] " .. msg, vim.log.levels.WARN) end)
  return msg
end

-- plenary.curl wrapper
local function wrap_curl_fn(orig_fn)
  return function(url, opts, ...)
    if not is_llm_url(url) then return orig_fn(url, opts, ...) end
    local body = (opts and (opts.body or opts.data)) or "{}"
    if _sync_mode then
      local allowed, reason = http.check_guardrails(url, body)
      if not allowed then
        local msg = notify_blocked(reason)
        local blocked = { status = 403, body = vim.fn.json_encode({ error = { message = msg } }) }
        http.ingest_async(url, body, blocked.body, 403)
        return blocked
      end
      local response = orig_fn(url, opts, ...)
      http.ingest_async(url, body, (response and response.body) or "{}", (response and response.status) or 200)
      return response
    else
      local response = orig_fn(url, opts, ...)
      http.guardrails_and_ingest_async(url, body, (response and response.body) or "{}", (response and response.status) or 200)
      return response
    end
  end
end

-- Wrap in-place on the original module table so cached references are also intercepted.
local function do_wrap_curl(original)
  if _curl_wrapped then return end
  _orig_curl = original
  _curl_wrapped = true
  for _, method in ipairs({ "post", "get", "put", "delete", "patch", "head", "request" }) do
    local orig_fn = original[method]
    if type(orig_fn) == "function" then
      _wrapped_fns[method] = orig_fn
      original[method] = wrap_curl_fn(orig_fn)
    end
  end
end

-- plenary.job wrapper (ChatGPT.nvim: job:new({ command="curl", args={url,...} }))
local function parse_curl_args(args)
  if not args or #args == 0 then return nil, nil end
  local url, body_file = nil, nil
  for i, arg in ipairs(args) do
    if not url and type(arg) == "string" and arg:match("^https?://") then url = arg end
    if arg == "-d" and args[i + 1] then
      local next_arg = args[i + 1]
      if type(next_arg) == "string" and next_arg:sub(1, 1) == "@" then
        body_file = next_arg:sub(2)
      else
        return url, next_arg
      end
    end
  end
  local body = "{}"
  if body_file then
    local f = io.open(body_file, "r")
    if f then body = f:read("*a") or "{}"; f:close() end
  end
  return url, body
end

local function do_wrap_job(original)
  if _job_wrapped then return end
  _orig_job = original
  _job_wrapped = true

  local orig_new = _orig_job.new
  local proxy_job = setmetatable({}, { __index = _orig_job })

  proxy_job.new = function(self, opts, ...)
    if not opts or opts.command ~= "curl" then return orig_new(self, opts, ...) end
    local url, body = parse_curl_args(opts.args)
    if not url or not is_llm_url(url) then return orig_new(self, opts, ...) end

    if _sync_mode then
      local allowed, reason = http.check_guardrails(url, body or "{}")
      if not allowed then
        local msg = notify_blocked(reason)
        http.ingest_async(url, body or "{}", vim.fn.json_encode({ error = { message = msg } }), 403)
        local orig_on_exit = opts.on_exit
        local dummy = vim.tbl_extend("force", opts, { command = "echo", args = { msg } })
        dummy.on_exit = function(j, code) if orig_on_exit then orig_on_exit(j, 1) end end
        return orig_new(self, dummy, ...)
      end
    end

    local orig_on_exit = opts.on_exit
    opts = vim.tbl_extend("force", opts, {
      on_exit = function(j, exit_code, ...)
        local resp_body = table.concat(j:result() or {}, "\n")
        local status = exit_code == 0 and 200 or 500
        if _sync_mode then
          http.ingest_async(url, body or "{}", resp_body, status)
        else
          http.guardrails_and_ingest_async(url, body or "{}", resp_body, status)
        end
        if orig_on_exit then orig_on_exit(j, exit_code, ...) end
      end,
    })
    return orig_new(self, opts, ...)
  end

  package.loaded["plenary.job"] = proxy_job
end

-- Single require() hook for deferred loading.
local function hook_require()
  if _original_require then return end
  _original_require = require
  ---@diagnostic disable-next-line: lowercase-global
  require = function(mod, ...)
    local result = _original_require(mod, ...)
    if mod == "plenary.curl" and not _curl_wrapped and result then
      do_wrap_curl(result)
    end
    if mod == "plenary.job" and not _job_wrapped and result then
      do_wrap_job(result)
      return package.loaded["plenary.job"]
    end
    local cb = _deferred_hooks[mod]
    if cb and result then cb(result); _deferred_hooks[mod] = nil end
    return result
  end
end

local function unhook_require()
  if not _original_require then return end
  ---@diagnostic disable-next-line: lowercase-global
  require = _original_require
  _original_require = nil
end

-- Other hook modules register deferred callbacks here.
function M._add_deferred(mod_name, callback)
  _deferred_hooks[mod_name] = callback
  local existing = package.loaded[mod_name]
  if existing then callback(existing) end
end

function M.enable(cfg)
  _sync_mode = cfg.sync_mode
  local curl = package.loaded["plenary.curl"]
  if curl then do_wrap_curl(curl) end
  local job = package.loaded["plenary.job"]
  if job then do_wrap_job(job) end
  if not _curl_wrapped or not _job_wrapped then hook_require() end
end

function M.disable()
  unhook_require()
  if _orig_curl then
    for method, orig_fn in pairs(_wrapped_fns) do
      _orig_curl[method] = orig_fn
    end
    _wrapped_fns = {}
    _orig_curl = nil
    _curl_wrapped = false
  end
  if _orig_job then
    package.loaded["plenary.job"] = _orig_job
    _orig_job = nil
    _job_wrapped = false
  end
end

return M
