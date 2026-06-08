-- Shared HTTP helpers for all hooks.
-- Extracted so hooks work independently of plenary_hook's enable state.

local M = {}

M._akto_url = ""
M._timeout = 5

local CONNECTOR = "neovim"

local function extract_host(url) return url:match("https?://([^/]+)") or "unknown" end
local function extract_path(url) return url:match("https?://[^/]+(/.*)") or "/" end

function M.build_payload(url, req_body, resp_body, status_code, method)
  local tags = vim.fn.json_encode({ ["gen-ai"] = "Gen AI", ["ai-agent"] = "neovim" })
  return vim.fn.json_encode({
    path = extract_path(url),
    requestHeaders = vim.fn.json_encode({ host = extract_host(url), ["content-type"] = "application/json", ["x-akto-hook"] = "neovim" }),
    responseHeaders = vim.fn.json_encode({ ["content-type"] = "application/json" }),
    method = method or "POST",
    requestPayload = req_body or "{}",
    responsePayload = resp_body or "{}",
    ip = os.getenv("USER") or "0.0.0.0",
    destIp = "127.0.0.1",
    time = tostring(math.floor(os.time() * 1000)),
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

function M.post_sync(params, payload)
  if M._akto_url == "" then return nil end
  local qs = {}
  for k, v in pairs(params) do qs[#qs + 1] = k .. "=" .. v end
  local url = M._akto_url .. "/api/http-proxy?" .. table.concat(qs, "&")
  local result = vim.fn.system({
    "curl", "-s", "-X", "POST", url,
    "-H", "Content-Type: application/json",
    "--max-time", tostring(M._timeout),
    "-d", payload,
  })
  if result and result ~= "" then
    local ok, data = pcall(vim.fn.json_decode, result)
    if ok then return data end
  end
  return nil
end

function M.post_async(params, payload)
  if M._akto_url == "" then return end
  local qs = {}
  for k, v in pairs(params) do qs[#qs + 1] = k .. "=" .. v end
  local url = M._akto_url .. "/api/http-proxy?" .. table.concat(qs, "&")
  vim.fn.jobstart({
    "curl", "-s", "-X", "POST", url,
    "-H", "Content-Type: application/json",
    "--max-time", tostring(M._timeout),
    "-d", payload,
  }, { detach = true })
end

function M.parse_guardrails(resp)
  if not resp then return true, "" end
  local gr = ((resp.data or {}).guardrailsResult) or {}
  local allowed = gr.Allowed
  if allowed == nil then allowed = true end
  return allowed, gr.Reason or ""
end

function M.check_guardrails(url, body)
  local resp = M.post_sync({ guardrails = "true", akto_connector = CONNECTOR }, M.build_payload(url, body))
  return M.parse_guardrails(resp)
end

function M.ingest_async(url, req_body, resp_body, status)
  M.post_async({ akto_connector = CONNECTOR, ingest_data = "true" }, M.build_payload(url, req_body, resp_body, status))
end

function M.guardrails_and_ingest_async(url, req_body, resp_body, status)
  M.post_async({ guardrails = "true", ingest_data = "true", akto_connector = CONNECTOR }, M.build_payload(url, req_body, resp_body, status))
end

function M.configure(cfg)
  M._akto_url = cfg.akto_url or ""
  M._timeout = cfg.timeout or 5
end

return M
