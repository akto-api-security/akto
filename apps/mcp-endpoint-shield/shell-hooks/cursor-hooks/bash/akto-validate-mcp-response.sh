#!/usr/bin/env bash
# akto-validate-mcp-response.sh - bash port of akto-validate-mcp-response.py
# Cursor afterMCPExecution hook. Observe-only: ingests the MCP tools/call result
# wrapped in JSON-RPC 2.0 envelopes on /mcp with response_guardrails=true.
# Output schema (cursor after hook): {}.  Cannot block.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
: "${LOG_DIR:=$HOME/.cursor/akto/mcp-logs}"
export LOG_DIR
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/akto-validate-response.log"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"

if [ "$AKTO_MODE" = "atlas" ]; then
  if [ -n "$DEVICE_ID" ]; then API_URL="https://${DEVICE_ID}.ai-agent.${AKTO_CONNECTOR_VALUE}"; else API_URL="https://api.anthropic.com"; fi
else
  API_URL="${API_URL:-https://api.anthropic.com}"
fi

_emit_aliases_from_file() {  # FILE
  local f="$1" servers i c kname kstart kend vstart vend val alias
  [ -f "$f" ] || return 0
  local content; content="$(cat "$f" 2>/dev/null)"
  servers="$(json_raw "$content" mcpServers 2>/dev/null)" || return 0
  case "$servers" in '{'*) ;; *) return 0;; esac
  _AKTO_JSON="$servers"; _AKTO_JSON_LEN=${#_AKTO_JSON}
  i="$(_json_skip_ws 0)"; i=$((i+1))
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
    i="$(_json_skip_ws "$i")"; c="${_AKTO_JSON:$i:1}"
    [ "$c" = '}' ] && break
    [ "$c" = ',' ] && { i=$((i+1)); continue; }
    [ "$c" = '"' ] || break
    kstart="$i"; kend="$(_json_scan_string "$i")"
    i="$(_json_skip_ws "$kend")"; [ "${_AKTO_JSON:$i:1}" = ':' ] || break
    i="$(_json_skip_ws $((i+1)))"; vstart="$i"; vend="$(_json_scan_value "$i")"
    val="${_AKTO_JSON:$vstart:$((vend-vstart))}"; i="$vend"
    alias="${_AKTO_JSON:$((kstart+1)):$((kend-kstart-2))}"
    case "$val" in
      '{'*)
        local u; u="$(json_string "$val" url 2>/dev/null)"
        local cmd; cmd="$(json_string "$val" command 2>/dev/null)"
        [ -n "$u" ]   && printf '%s\t%s\n' "$u" "$alias"
        [ -n "$cmd" ] && printf '%s\t%s\n' "$cmd" "$alias"
        ;;
    esac
  done
}
_lookup_alias() {  # KEY
  local key="$1" k a
  [ -z "$key" ] && return 1
  while IFS="$(printf '\t')" read -r k a; do
    [ "$k" = "$key" ] && { printf '%s' "$a"; return 0; }
  done <<EOF
$(_emit_aliases_from_file "$HOME/.cursor/mcp.json"; _emit_aliases_from_file "$PWD/.cursor/mcp.json")
EOF
  return 1
}

parse_json_string_field() {  # INPUT_JSON KEY DEFAULT_RAW
  local raw; raw="$(json_raw "$1" "$2" 2>/dev/null)" || { printf '%s' "$3"; return; }
  case "$raw" in
    '"'*'"')
      local dec; dec="${raw#\"}"; dec="${dec%\"}"; dec="$(_json_unescape "$dec")"
      local t; t="$(printf '%s' "$dec" | tr -d '[:space:]')"
      [ -z "$t" ] && { printf '%s' "$3"; return; }
      case "$dec" in '{'*|'['*) printf '%s' "$dec";; *) printf '{"raw":%s}' "$(json_str "$dec")";; esac
      ;;
    ''|null) printf '%s' "$3";;
    *) printf '%s' "$raw";;
  esac
}

extract_mcp_server_name() {  # INPUT_JSON TOOL_INPUT_RAW
  local input="$1" tool_input="$2" url command server tn a
  url="$(json_string "$input" url 2>/dev/null)"
  command="$(json_string "$input" command 2>/dev/null)"
  if [ -n "$url" ] && a="$(_lookup_alias "$url")"; then printf '%s' "$a"; return 0; fi
  if [ -n "$command" ] && a="$(_lookup_alias "$command")"; then printf '%s' "$a"; return 0; fi
  server="$(json_string "$input" server 2>/dev/null)"
  [ -n "$server" ] && { printf '%s' "$server"; return 0; }
  if [ -n "$url" ]; then local h="${url#https://}"; h="${h#http://}"; h="${h%%/*}"; printf '%s' "$h"; return 0; fi
  # nested url inside tool_input
  case "$tool_input" in
    '{'*)
      local nu; nu="$(json_string "$tool_input" url 2>/dev/null)"
      if [ -n "$nu" ]; then local h2="${nu#https://}"; h2="${h2#http://}"; h2="${h2%%/*}"; printf '%s' "$h2"; return 0; fi
      ;;
  esac
  [ -n "$command" ] && { printf '%s' "$command"; return 0; }
  tn="$(json_string "$input" tool_name 2>/dev/null)"
  case "$tn" in mcp__*) local rest="${tn#mcp__}"; printf '%s' "${rest%%__*}"; return 0;; esac
  printf 'cursor-unknown'
}

jsonrpc_arguments() {  # TOOL_INPUT_RAW
  local ti="$1"; case "$ti" in '{'*) printf '%s' "$ti";; ''|null) printf '{}';; *) printf '{"input":%s}' "$ti";; esac
}
build_tools_call_jsonrpc() {  # TOOL_NAME TOOL_INPUT_RAW
  printf '{"jsonrpc":"2.0","method":"tools/call","params":{"name":%s,"arguments":%s},"id":1}' "$(json_str "$1")" "$(jsonrpc_arguments "$2")"
}
build_tools_call_result_jsonrpc() {  # TOOL_RESPONSE_RAW
  local tr="$1" result_body
  case "$tr" in '{'*) result_body="$tr";; *) result_body="{\"output\":${tr:-null}}";; esac
  printf '{"jsonrpc":"2.0","id":1,"result":%s}' "$result_body"
}

build_ingestion_payload() {  # TOOL_NAME TOOL_INPUT_RAW TOOL_RESPONSE_RAW SERVER
  local tool_name="$1" tool_input="$2" tool_response="$3" server="$4"
  local tags host request_payload response_payload
  tags="{\"mcp-server\":\"MCP Server\",\"mcp-client\":$(json_str "$AKTO_CONNECTOR_VALUE")"
  [ "$AKTO_MODE" = "atlas" ] && tags="$tags,\"source\":$(json_str "$CONTEXT_SOURCE")"
  tags="$tags,\"mcp_server_name\":$(json_str "$server")}"
  host="${DEVICE_ID}.${AKTO_CONNECTOR_VALUE}.${server}"
  request_payload="$(build_tools_call_jsonrpc "$tool_name" "$tool_input")"
  response_payload="$(build_tools_call_result_jsonrpc "$tool_response")"

  local req_hdr="{\"host\":$(json_str "$host"),\"x-cursor-hook\":\"afterMCPExecution\",\"x-mcp-server\":$(json_str "$server"),\"content-type\":\"application/json\"}"
  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str '{"x-cursor-hook":"afterMCPExecution","content-type":"application/json"}')"
  local tag_str; tag_str="$(json_str "$tags")"
  printf '{"path":%s,"requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":0,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$(json_str "$MCP_INGEST_PATH")" "$request_headers" "$response_headers" "$(json_str "$request_payload")" "$(json_str "$response_payload")" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")"
}

main() {
  log_info "=== Hook execution started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  [ -z "$input" ] && { printf '{}\n'; exit 0; }

  local tool_name tool_input tool_response server
  tool_name="$(json_string "$input" tool_name 2>/dev/null)"
  tool_input="$(parse_json_string_field "$input" tool_input '{}')"
  tool_response="$(parse_json_string_field "$input" result_json '{}')"
  server="$(extract_mcp_server_name "$input" "$tool_input")"

  log_info "Processing afterMCPExecution: $tool_name (server=$server)"

  case "$tool_input" in ''|'{}'|null) log_info "Empty input or result, skipping ingestion"; printf '{}\n'; exit 0;; esac
  case "$tool_response" in ''|'{}'|null) log_info "Empty input or result, skipping ingestion"; printf '{}\n'; exit 0;; esac

  if [ -n "$AKTO_DATA_INGESTION_URL" ]; then
    log_info "Ingesting MCP tools/call result for $tool_name (server=$server)"
    local body; body="$(build_ingestion_payload "$tool_name" "$tool_input" "$tool_response" "$server")"
    # observe-only: response_guardrails=true & ingest_data=true
    post_payload_json "$(build_http_proxy_url 0 1 1)" "$body" >/dev/null 2>&1 || log_error "Ingestion error for $server"
  fi

  log_info "Response ingestion completed"
  printf '{}\n'
  exit 0
}
main
