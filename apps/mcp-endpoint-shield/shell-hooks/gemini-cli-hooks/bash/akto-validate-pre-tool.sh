#!/usr/bin/env bash
# akto-validate-pre-tool.sh - bash port of akto-validate-pre-tool.py (BeforeTool)
# Validates MCP / built-in tool requests; emits {"decision":"deny",...} on block.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/akto-validate-pre-tool.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_pretool_warn_pending.json"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"

if [ "$AKTO_MODE" = "atlas" ]; then
  if [ -n "$DEVICE_ID" ]; then GEMINI_API_URL="https://${DEVICE_ID}.ai-agent.${AKTO_CONNECTOR_VALUE}"; else GEMINI_API_URL="${GEMINI_API_URL:-https://generativelanguage.googleapis.com}"; fi
else
  GEMINI_API_URL="${GEMINI_API_URL:-https://generativelanguage.googleapis.com}"
fi

# parse_gemini_tool TOOLNAME MCP_CONTEXT_RAW -> sets IS_MCP(0/1) MCP_SERVER MCP_TOOL
# Gemini MCP tools are named mcp_<server>_<tool> (server = first segment after
# "mcp_", remainder is the tool). mcp_context (server_name/tool_name) wins.
parse_gemini_tool() {
  local tn="$1" mc="$2"; IS_MCP=0; MCP_SERVER=""; MCP_TOOL=""
  case "$mc" in
    '{'*)
      local s t
      s="$(json_string "$mc" server_name 2>/dev/null)"; [ -z "$s" ] && s="$(json_string "$mc" serverName 2>/dev/null)"
      t="$(json_string "$mc" tool_name 2>/dev/null)"; [ -z "$t" ] && t="$(json_string "$mc" toolName 2>/dev/null)"
      if [ -n "$s" ] && [ -n "$t" ]; then MCP_SERVER="$s"; MCP_TOOL="$t"; IS_MCP=1; return 0; fi;;
  esac
  case "$tn" in mcp_*) ;; *) return 0;; esac
  local rest="${tn#mcp_}"
  case "$rest" in *_*) ;; *) return 0;; esac
  MCP_SERVER="${rest%%_*}"
  MCP_TOOL="${rest#*_}"
  { [ -z "$MCP_SERVER" ] || [ -z "$MCP_TOOL" ]; } && { MCP_SERVER=""; MCP_TOOL=""; return 0; }
  IS_MCP=1
}

normalize_tool_name() {
  local s; s="$(printf '%s' "${1:-unknown}" | sed -E 's/[^a-zA-Z0-9._~-]+/-/g; s/-+/-/g; s/^-//; s/-$//')"
  [ -z "$s" ] && s="unknown"
  printf '%s' "$s"
}

non_mcp_ingest_path() {  # TOOLNAME
  local fixed="${NON_MCP_INGEST_PATH:-}"
  if [ -n "$fixed" ]; then case "$fixed" in /*) printf '%s' "$fixed";; *) printf '/%s' "$fixed";; esac; return 0; fi
  local prefix="$NON_MCP_TOOL_PATH_PREFIX"
  case "$prefix" in /*) ;; *) prefix="/$prefix";; esac
  prefix="${prefix%/}"; [ -z "$prefix" ] && prefix="/tool"
  printf '%s/%s' "$prefix" "$(normalize_tool_name "$1")"
}

jsonrpc_arguments() {  # TOOL_INPUT_RAW
  local ti="$1"
  case "$ti" in
    '{'*) printf '%s' "$ti";;
    ''|null) printf '{}';;
    *) printf '{"input":%s}' "$ti";;
  esac
}

build_tools_call_jsonrpc() {  # MCP_TOOL TOOL_INPUT_RAW
  printf '{"jsonrpc":"2.0","method":"tools/call","params":{"name":%s,"arguments":%s},"id":1}' \
    "$(json_str "$1")" "$(jsonrpc_arguments "$2")"
}

build_session_headers() {  # echoes ,"x-akto-installer-KEY":"VAL" fragments
  local frag=""
  [ -n "${SI_session_id:-}" ]            && frag="$frag,\"x-akto-installer-session_id\":$(json_str "$SI_session_id")"
  [ -n "${SI_transcript_path:-}" ]       && frag="$frag,\"x-akto-installer-transcript_path\":$(json_str "$SI_transcript_path")"
  [ -n "${SI_cwd:-}" ]                   && frag="$frag,\"x-akto-installer-cwd\":$(json_str "$SI_cwd")"
  [ -n "${SI_hook_event_name:-}" ]       && frag="$frag,\"x-akto-installer-hook_event_name\":$(json_str "$SI_hook_event_name")"
  [ -n "${SI_timestamp:-}" ]             && frag="$frag,\"x-akto-installer-timestamp\":$(json_str "$SI_timestamp")"
  [ -n "${SI_original_request_name:-}" ] && frag="$frag,\"x-akto-installer-original_request_name\":$(json_str "$SI_original_request_name")"
  printf '%s' "$frag"
}

build_validation_request() {  # TOOL_NAME TOOL_INPUT_RAW
  local tool_name="$1" tool_input="$2"
  local tags host req_hdr request_payload path
  if [ "$IS_MCP" = "1" ]; then
    tags="{\"mcp-server\":\"MCP Server\",\"mcp-client\":$(json_str "$AKTO_CONNECTOR_VALUE")"
    host="${DEVICE_ID}.${AKTO_CONNECTOR_VALUE}.${MCP_SERVER}"
    request_payload="$(build_tools_call_jsonrpc "$MCP_TOOL" "$tool_input")"
    path="$MCP_INGEST_PATH"
  else
    tags="{\"gen-ai\":\"Gen AI\",\"ai-agent\":$(json_str "$AKTO_CONNECTOR_VALUE")"
    host="${GEMINI_API_URL#https://}"; host="${host#http://}"
    request_payload="{\"body\":${tool_input:-null},\"toolName\":$(json_str "$tool_name")}"
    path="$(non_mcp_ingest_path "$tool_name")"
  fi
  [ "$AKTO_MODE" = "atlas" ] && tags="$tags,\"source\":$(json_str "$CONTEXT_SOURCE")"
  tags="$tags}"

  req_hdr="{\"host\":$(json_str "$host"),\"x-gemini-hook\":\"BeforeTool\",\"content-type\":\"application/json\""
  [ "$IS_MCP" = "1" ] && [ -n "$MCP_SERVER" ] && req_hdr="$req_hdr,\"x-mcp-server\":$(json_str "$MCP_SERVER")"
  req_hdr="$req_hdr$(build_session_headers)}"

  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str '{"x-gemini-hook":"BeforeTool"}')"
  local request_payload_str; request_payload_str="$(json_str "$request_payload")"
  local response_payload; response_payload="$(json_str '{}')"
  local tag_str; tag_str="$(json_str "$tags")"

  printf '{"path":%s,"requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":0,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$(json_str "$path")" "$request_headers" "$response_headers" "$request_payload_str" "$response_payload" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")"
}

emit_deny() {  # REASON
  printf '{"decision":"deny","reason":%s}' "$(json_str "$1")"
}

ingest_blocked() {  # TOOL_NAME TOOL_INPUT_RAW REASON
  [ -z "$AKTO_DATA_INGESTION_URL" ] && return 0
  [ "$AKTO_SYNC_MODE" != "true" ] && return 0
  if [ "$IS_MCP" != "1" ] && [ "$AKTO_INGEST_NON_MCP_TOOLS" != "true" ]; then
    log_info "Skipping non-MCP blocked-request ingestion"; return 0
  fi
  local reason="$3" body; body="$(build_validation_request "$1" "$2")"
  local rhdr rpl
  rhdr="$(json_str '{"x-gemini-hook":"BeforeTool","x-blocked-by":"Akto Proxy","content-type":"application/json"}')"
  rpl="$(json_str "{\"body\":{\"x-blocked-by\":\"Akto Proxy\",\"reason\":$(json_str "${reason:-Policy violation}")}}")"
  body="$(printf '%s' "$body" \
    | sed -E "s/\"responseHeaders\":\"[^\"]*(\\\\.[^\"]*)*\"/\"responseHeaders\":$(printf '%s' "$rhdr" | sed 's/[&/\]/\\&/g')/; s/\"responsePayload\":\"[^\"]*(\\\\.[^\"]*)*\"/\"responsePayload\":$(printf '%s' "$rpl" | sed 's/[&/\]/\\&/g')/; s/\"statusCode\":\"200\"/\"statusCode\":\"403\"/; s/\"status\":\"200\"/\"status\":\"403\"/")"
  post_payload_json "$(build_http_proxy_url 0 0 1)" "$body" >/dev/null 2>&1 || true
}

main() {
  log_info "=== BeforeTool Hook started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"

  local hook_event; hook_event="$(json_string "$input" hook_event_name 2>/dev/null)"
  if [ -n "$hook_event" ] && [ "$hook_event" != "BeforeTool" ]; then
    log_info "Ignoring non-BeforeTool event: $hook_event"; printf '{}'; exit 0
  fi

  SI_session_id="$(json_string "$input" session_id 2>/dev/null)"
  SI_transcript_path="$(json_string "$input" transcript_path 2>/dev/null)"
  SI_cwd="$(json_string "$input" cwd 2>/dev/null)"
  SI_hook_event_name="$(json_string "$input" hook_event_name 2>/dev/null)"
  SI_timestamp="$(json_string "$input" timestamp 2>/dev/null)"
  SI_original_request_name="$(json_string "$input" original_request_name 2>/dev/null)"

  local tool_name tool_input mcp_context
  tool_name="$(json_string "$input" tool_name 2>/dev/null)"
  tool_input="$(json_raw "$input" tool_input 2>/dev/null)"; [ -z "$tool_input" ] && tool_input="{}"
  mcp_context="$(json_raw "$input" mcp_context 2>/dev/null)"; [ -z "$mcp_context" ] && mcp_context="{}"
  parse_gemini_tool "$tool_name" "$mcp_context"

  if [ "$IS_MCP" = "1" ]; then
    log_info "Processing MCP tool request: $tool_name (server=$MCP_SERVER, mcpTool=$MCP_TOOL)"
  else
    log_info "Processing non-MCP tool request (gen-ai only): $tool_name"
  fi

  if [ "$AKTO_SYNC_MODE" = "true" ]; then
    # python call_guardrails returns allow when tool_input is empty
    if [ "$tool_input" = "{}" ] || [ -z "$tool_input" ]; then
      log_info "Empty tool input, allowing"; printf '{}'; exit 0
    fi
    if [ -z "$AKTO_DATA_INGESTION_URL" ]; then log_warn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; printf '{}'; exit 0; fi
    local body resp
    body="$(build_validation_request "$tool_name" "$tool_input")"
    resp="$(post_payload_json "$(build_http_proxy_url 1 0 1)" "$body")" || { log_error "guardrails failed; fail-open"; printf '{}'; exit 0; }
    parse_guardrails_result "$resp"

    # fingerprint matches python json.dumps({"t":name,"i":input}, sort_keys=True)
    local fp; fp="$(printf '{"i": %s, "t": %s}' "$tool_input" "$(json_str "$tool_name")" | sha256_hex)"
    apply_warn_resubmit_flow "$GR_ALLOWED" "$GR_REASON" "$GR_BEHAVIOUR" "$fp" "$WARN_STATE_PATH"

    if [ "$ALLOWED" = "false" ]; then
      local block_reason
      if is_warn_behaviour "$GR_BEHAVIOUR"; then
        block_reason="Warning!!, tool request blocked, please review it. Send again to bypass. Reason for blocking: $GR_REASON"
      else
        block_reason="Tool request blocked: $GR_REASON"
      fi
      log_warn "BLOCKING tool request - Tool: $tool_name, Reason: $block_reason"
      emit_deny "$block_reason"
      ingest_blocked "$tool_name" "$tool_input" "$GR_REASON"
      exit 0
    fi
  fi
  log_info "Tool request allowed for $tool_name"
  printf '{}'
  exit 0
}

main
