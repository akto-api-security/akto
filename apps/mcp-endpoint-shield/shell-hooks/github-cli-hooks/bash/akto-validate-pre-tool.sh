#!/usr/bin/env bash
# akto-validate-pre-tool.sh - bash port of akto-validate-pre-tool.py (preToolUse)
# Validates a tool call against Akto guardrails; on block emits a deny decision
# (top-level + hookSpecificOutput) and exits with the connector blocked_exit_code.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/akto_common.sh"

# build_akto_request TOOL_NAME TOOL_ARGS_STR TIMESTAMP -> echoes payload JSON
# TOOL_ARGS_STR is the normalised tool args as a JSON-encoded STRING (matches python).
build_akto_request() {
  local tool_name="$1" tool_args="$2" timestamp="$3"
  local device_id="${DEVICE_ID:-$(get_machine_id)}"
  local tags host req_hdr request_payload path

  if [ "$IS_MCP" = "1" ]; then
    tags="{\"mcp-server\":\"MCP Server\",\"mcp-client\":$(json_str "$CFG_AI_AGENT_TAG")"
    host="${device_id}.${CFG_AI_AGENT_TAG}.${MCP_SERVER}"
    # parsed_input: tool_args may be a JSON string -> parse; else {"raw":..}
    local parsed_input
    case "$tool_args" in
      '{'*|'['*) parsed_input="$tool_args";;
      *) parsed_input="{\"raw\":$(json_str "$tool_args")}";;
    esac
    request_payload="$(build_tools_call_jsonrpc "$MCP_TOOL" "$parsed_input")"
    path="$MCP_INGEST_PATH"
  else
    tags="{\"gen-ai\":\"Gen AI\",\"tool-use\":\"Tool Execution\""
    [ "$AKTO_MODE" = "atlas" ] && tags="$tags,\"ai-agent\":$(json_str "$CFG_AI_AGENT_TAG")"
    host="${CFG_API_URL#https://}"; host="${host#http://}"
    # requestPayload = {"body": json.dumps({"toolName":.., "toolArgs":<string>})}
    local inner; inner="{\"toolName\":$(json_str "$tool_name"),\"toolArgs\":$(json_str "$tool_args")}"
    request_payload="{\"body\":$(json_str "$inner")}"
    path="/copilot/tool/$tool_name"
  fi
  [ "$AKTO_MODE" = "atlas" ] && tags="$tags,\"source\":$(json_str "$CONTEXT_SOURCE")"
  tags="$tags}"

  req_hdr="{\"host\":$(json_str "$host"),$(json_str "$CFG_HOOK_HEADER"):\"PreToolUse\",\"content-type\":\"application/json\""
  [ "$IS_MCP" = "1" ] && [ -n "$MCP_SERVER" ] && req_hdr="$req_hdr,\"x-mcp-server\":$(json_str "$MCP_SERVER")"
  req_hdr="$req_hdr}"
  local resp_hdr="{$(json_str "$CFG_HOOK_HEADER"):\"PreToolUse\"}"

  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str "$resp_hdr")"
  local request_payload_str; request_payload_str="$(json_str "$request_payload")"
  local response_payload; response_payload="$(json_str '{}')"
  local tag_str; tag_str="$(json_str "$tags")"

  printf '{"path":%s,"requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":%s,"statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":"ENDPOINT"}' \
    "$(json_str "$path")" "$request_headers" "$response_headers" "$request_payload_str" "$response_payload" \
    "$(json_str "$(get_username)")" "$(json_str "$timestamp")" "$(json_str "$device_id")" "$tag_str" "$tag_str"
}

ingest_blocked() {  # TOOL_NAME TOOL_ARGS_STR TIMESTAMP REASON
  [ -z "$AKTO_DATA_INGESTION_URL" ] && return 0
  local body; body="$(build_akto_request "$1" "$2" "$3")"
  local reason="$4"; [ -z "$reason" ] && reason="Policy violation"
  local rpl; rpl="$(json_str "{\"body\":$(json_str "{\"x-blocked-by\":\"Akto Proxy\",\"reason\":$(json_str "$reason")}")}")"
  body="$(printf '%s' "$body" \
    | sed -E "s/\"responsePayload\":\"[^\"]*(\\\\.[^\"]*)*\"/\"responsePayload\":$(printf '%s' "$rpl" | sed 's/[&/\]/\\&/g')/" \
    | sed -E 's/"statusCode":"200"/"statusCode":"403"/; s/"status":"200"/"status":"403"/')"
  post_payload_json "$(build_proxy_url ingest)" "$body" >/dev/null 2>&1 || true
}

main() {
  local input; input="$(cat)"
  [ -z "$input" ] && { log_error "Invalid JSON input"; exit 0; }

  local connector; connector="$(detect_connector "$input")"
  load_connector_config "$connector"
  AKTO_LOG_DIR="$(resolve_log_dir)"
  mkdir -p "$AKTO_LOG_DIR" 2>/dev/null || true
  AKTO_LOG_FILE="$AKTO_LOG_DIR/validate-pre-tool.log"
  WARN_STATE_PATH="$AKTO_LOG_DIR/akto_pretool_warn_pending.json"
  send_heartbeat "$AKTO_LOG_DIR"

  log_info "=== Pre-Tool Use Hook - Connector: $connector, Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  log_info "MODE: $AKTO_MODE, API_URL: $CFG_API_URL"

  # Parse input - key names differ between connectors.
  local tool_name raw_args
  if [ "$CFG_IS_VSCODE" = "1" ]; then
    tool_name="$(json_string "$input" tool_name 2>/dev/null)"; [ -z "$tool_name" ] && tool_name="unknown"
    raw_args="$(json_raw "$input" tool_input 2>/dev/null)"; [ -z "$raw_args" ] && raw_args="{}"
  else
    tool_name="$(json_string "$input" toolName 2>/dev/null)"
    [ -z "$tool_name" ] && tool_name="$(json_string "$input" tool_name 2>/dev/null)"
    [ -z "$tool_name" ] && tool_name="unknown"
    raw_args="$(json_raw "$input" toolArgs 2>/dev/null)"
    [ -z "$raw_args" ] && raw_args="$(json_raw "$input" tool_input 2>/dev/null)"
    [ -z "$raw_args" ] && raw_args="{}"
  fi
  # Normalise tool_args to a JSON-encoded STRING. If raw_args is a JSON string
  # literal, unwrap it (python: raw if isinstance str else json.dumps(raw)).
  local tool_args
  case "$raw_args" in
    '"'*'"') tool_args="${raw_args#\"}"; tool_args="${tool_args%\"}"; tool_args="$(_json_unescape "$tool_args")";;
    *) tool_args="$raw_args";;
  esac

  local raw_ts; raw_ts="$(json_raw "$input" timestamp 2>/dev/null)"
  local timestamp
  case "$raw_ts" in ''|*[!0-9]*) timestamp="$(date +%s)000";; *) timestamp="$raw_ts";; esac

  parse_github_tool "$tool_name"

  log_info "Parsed: tool_name='$tool_name', tool_args_len=${#tool_args}, timestamp=$timestamp"
  if [ "$tool_name" = "unknown" ]; then
    log_warn "tool_name fell back to 'unknown'. connector=$connector. Expected 'toolName' (Copilot CLI) or 'tool_name' (VSCode)."
  fi
  if [ "$IS_MCP" = "1" ]; then
    log_info "Tool: $tool_name (MCP server=$MCP_SERVER, mcpTool=$MCP_TOOL)"
  else
    log_info "Tool: $tool_name"
  fi

  if [ "$AKTO_SYNC_MODE" != "true" ] || [ -z "$AKTO_DATA_INGESTION_URL" ]; then
    log_info "Guardrails disabled (sync mode off or no URL)"
    exit 0
  fi

  local body resp
  body="$(build_akto_request "$tool_name" "$tool_args" "$timestamp")"
  resp="$(post_payload_json "$(build_proxy_url prompt_guard)" "$body")" || { log_error "guardrails failed; fail-open"; exit 0; }
  parse_guardrails_result "$resp"

  # fingerprint: {"a": tool_args, "t": tool_name} sorted keys -> a then t
  local fp; fp="$(printf '{"a":%s,"t":%s}' "$(json_str "$tool_args")" "$(json_str "$tool_name")" | sha256_hex)"
  apply_warn_resubmit_flow "$GR_ALLOWED" "$GR_REASON" "$GR_BEHAVIOUR" "$fp" "$WARN_STATE_PATH"

  if [ "$ALLOWED" = "false" ]; then
    local denial_reason
    if is_warn_behaviour "$GR_BEHAVIOUR"; then
      denial_reason="Warning!! Tool use blocked, please review it. Send again to bypass. Reason for blocking: $GR_REASON"
    else
      denial_reason="Blocked by Akto Guardrails: ${GR_REASON:-Policy violation}"
    fi
    log_warn "BLOCKING tool use: $tool_name, Reason: $denial_reason"
    local dr; dr="$(json_str "$denial_reason")"
    printf '{"permissionDecision":"deny","permissionDecisionReason":%s,"hookSpecificOutput":{"permissionDecision":"deny","permissionDecisionReason":%s}}' "$dr" "$dr"
    ingest_blocked "$tool_name" "$tool_args" "$timestamp" "$GR_REASON"
    exit "$CFG_BLOCKED_EXIT_CODE"
  fi

  log_info "Tool use PASSED guardrails for $tool_name"
  exit 0
}

main
