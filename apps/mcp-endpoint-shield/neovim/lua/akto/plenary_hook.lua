-- Intercepts plenary.curl and plenary.job calls to LLM APIs.
-- Covers: avante, codecompanion, CopilotChat (plenary.curl), ChatGPT (plenary.job).

local M = {}

local _akto_url = ""
local _sync_mode = true
local _timeout = 5
local _orig_curl = nil
local _orig_job = nil
local _curl_wrapped = false
local _job_wrapped = false
local _original_require = nil
local _deferred_hooks = {}

local CONNECTOR = "neovim"

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

local function extract_host(url) return url:match("https?://([^/]+)") or "unknown" end
local function extract_path(url) return url:match("https?://[^/]+(/.*)") or "/" end

-- Build the Akto /api/http-proxy payload (same format as Claude CLI hooks).
local function build_payload(url, req_body, resp_body, status_code)
  local tags = vim.fn.json_encode({ ["gen-ai"] = "Gen AI", ["ai-agent"] = "neovim" })
  return vim.fn.json_encode({
    path = extract_path(url),
    requestHeaders = vim.fn.json_encode({ host = extract_host(url), ["content-type"] = "application/json", ["x-akto-hook"] = "neovim" }),
    responseHeaders = vim.fn.json_encode({ ["content-type"] = "application/json" }),
    method = "POST",
    requestPayload = req_body or "{}",
    responsePayload = resp_body or "{}",
    ip = os.getenv("USER") or "0.0.0.0",
    destIp = "127.0.0.1",
    time = tostring(math.floor(vim.loop.now())),
    statusCode = tostring(status_code or 200),
    status = tostring(status_code or 200),
    akto_account_id = "1000000",
    akto_vxlan_id = "0",
    is_pending = "false",
    source = "MIRRORING",
    tag = tags,
    metadata = tags,
    contextSource = "ENDPOINT",
  })
end

-- Sync POST to Akto backend.
local function akto_post_sync(params, payload)
  local qs = {}
  for k, v in pairs(params) do qs[#qs + 1] = k .. "=" .. v end
  local url = _akto_url .. "/api/http-proxy?" .. table.concat(qs, "&")
  local result = vim.fn.system({
    "curl", "-s", "-X", "POST", url,
    "-H", "Content-Type: application/json",
    "--max-time", tostring(_timeout),
    "-d", payload,
  })
  if result and result ~= "" then
    local ok, data = pcall(vim.fn.json_decode, result)
    if ok then return data end
  end
  return nil
end

-- Async POST to Akto backend (fire-and-forget).
local function akto_post_async(params, payload)
  local qs = {}
  for k, v in pairs(params) do qs[#qs + 1] = k .. "=" .. v end
  local url = _akto_url .. "/api/http-proxy?" .. table.concat(qs, "&")
  vim.fn.jobstart({
    "curl", "-s", "-X", "POST", url,
    "-H", "Content-Type: application/json",
    "--max-time", tostring(_timeout),
    "-d", payload,
  }, { detach = true })
end

local function parse_guardrails(resp)
  if not resp then return true, "" end
  local gr = ((resp.data or {}).guardrailsResult) or {}
  local allowed = gr.Allowed
  if allowed == nil then allowed = true end
  return allowed, gr.Reason or ""
end

local function check_guardrails(url, body)
  if _akto_url == "" then return true, "" end
  local resp = akto_post_sync({ guardrails = "true", akto_connector = CONNECTOR }, build_payload(url, body))
  return parse_guardrails(resp)
end

local function ingest_async(url, req_body, resp_body, status)
  if _akto_url == "" then return end
  akto_post_async({ akto_connector = CONNECTOR, ingest_data = "true" }, build_payload(url, req_body, resp_body, status))
end

local function guardrails_and_ingest_async(url, req_body, resp_body, status)
  if _akto_url == "" then return end
  akto_post_async({ guardrails = "true", ingest_data = "true", akto_connector = CONNECTOR }, build_payload(url, req_body, resp_body, status))
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
      local allowed, reason = check_guardrails(url, body)
      if not allowed then
        local msg = notify_blocked(reason)
        local blocked = { status = 403, body = vim.fn.json_encode({ error = { message = msg } }) }
        ingest_async(url, body, blocked.body, 403)
        return blocked
      end
      local response = orig_fn(url, opts, ...)
      ingest_async(url, body, (response and response.body) or "{}", (response and response.status) or 200)
      return response
    else
      local response = orig_fn(url, opts, ...)
      guardrails_and_ingest_async(url, body, (response and response.body) or "{}", (response and response.status) or 200)
      return response
    end
  end
end

local function do_wrap_curl(original)
  if _curl_wrapped then return end
  _orig_curl = original
  _curl_wrapped = true
  package.loaded["plenary.curl"] = setmetatable({}, {
    __index = function(_, key)
      local val = _orig_curl[key]
      return type(val) == "function" and wrap_curl_fn(val) or val
    end,
  })
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
      local allowed, reason = check_guardrails(url, body or "{}")
      if not allowed then
        local msg = notify_blocked(reason)
        ingest_async(url, body or "{}", vim.fn.json_encode({ error = { message = msg } }), 403)
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
          ingest_async(url, body or "{}", resp_body, status)
        else
          guardrails_and_ingest_async(url, body or "{}", resp_body, status)
        end
        if orig_on_exit then orig_on_exit(j, exit_code, ...) end
      end,
    })
    return orig_new(self, opts, ...)
  end

  package.loaded["plenary.job"] = proxy_job
end

-- Single require() hook for deferred loading of all modules.
local function hook_require()
  if _original_require then return end
  _original_require = require
  ---@diagnostic disable-next-line: lowercase-global
  require = function(mod, ...)
    local result = _original_require(mod, ...)
    if mod == "plenary.curl" and not _curl_wrapped and result then
      do_wrap_curl(result)
      return package.loaded["plenary.curl"]
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

-- Shared: other hook modules register deferred callbacks here.
function M._add_deferred(mod_name, callback)
  _deferred_hooks[mod_name] = callback
  local existing = package.loaded[mod_name]
  if existing then callback(existing) end
end

-- Shared: async POST for use by copilot/windsurf hooks.
M._akto_post_async = akto_post_async
M._build_payload = build_payload

function M.enable(cfg)
  _akto_url = cfg.akto_url or ""
  _sync_mode = cfg.sync_mode
  _timeout = cfg.timeout or 5

  local curl = package.loaded["plenary.curl"]
  if curl then do_wrap_curl(curl) end
  local job = package.loaded["plenary.job"]
  if job then do_wrap_job(job) end
  if not _curl_wrapped or not _job_wrapped then hook_require() end
end

function M.disable()
  unhook_require()
  if _orig_curl then package.loaded["plenary.curl"] = _orig_curl; _orig_curl = nil; _curl_wrapped = false end
  if _orig_job then package.loaded["plenary.job"] = _orig_job; _orig_job = nil; _job_wrapped = false end
end

return M
