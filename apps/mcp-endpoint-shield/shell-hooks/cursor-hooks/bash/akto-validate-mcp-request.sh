#!/usr/bin/env bash
# akto-validate-mcp-request.sh - bash port of akto-validate-mcp-request.py
# Cursor beforeMCPExecution hook. Wraps the MCP tool call in a JSON-RPC 2.0
# tools/call envelope on /mcp and validates against Akto guardrails.
# Output schema (cursor): {"permission":"allow"|"deny"[,"user_message","agent_message"]}.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
: "${LOG_DIR:=$HOME/.cursor/akto/mcp-logs}"
export LOG_DIR
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/akto-validate-request.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_pretool_warn_pending.json"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"

if [ "$AKTO_MODE" = "atlas" ]; then
  if [ -n "$DEVICE_ID" ]; then API_URL="https://${DEVICE_ID}.ai-agent.${AKTO_CONNECTOR_VALUE}"; else API_URL="https://api.anthropic.com"; fi
else
  API_URL="${API_URL:-https://api.anthropic.com}"
fi

# Iterate mcpServers in a cursor mcp.json file; echo "key\talias" lines for
# both url and command of each server (mirrors _load_mcp_config_aliases).
_emit_aliases_from_file() {  # FILE
  local f="$1" servers alias_block i len c kname kstart kend vstart vend val
  [ -f "$f" ] || return 0
  local content; content="$(cat "$f" 2>/dev/null)"
  servers="$(json_raw "$content" mcpServers 2>/dev/null)" || return 0
  case "$servers" in '{'*) ;; *) return 0;; esac
  # walk top-level keys of servers object; for each server object pull url+command
  _AKTO_JSON="$servers"; _AKTO_JSON_LEN=${#_AKTO_JSON}
  i="$(_json_skip_ws 0)"; i=$((i+1))
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
    i="$(_json_skip_ws "$i")"
    c="${_AKTO_JSON:$i:1}"
    [ "$c" = '}' ] && break
    [ "$c" = ',' ] && { i=$((i+1)); continue; }
    [ "$c" = '"' ] || break
    kstart="$i"; kend="$(_json_scan_string "$i")"
    i="$(_json_skip_ws "$kend")"
    [ "${_AKTO_JSON:$i:1}" = ':' ] || break
    i="$(_json_skip_ws $((i+1)))"
    vstart="$i"; vend="$(_json_scan_value "$i")"
    val="${_AKTO_JSON:$vstart:$((vend-vstart))}"
    i="$vend"
    local alias; alias="${_AKTO_JSON:$((kstart+1)):$((kend-kstart-2))}"
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

_lookup_alias() {  # KEY -> echoes alias if matched in mcp.json candidates
  local key="$1"
  [ -z "$key" ] && return 1
  local line k a
  while IFS="$(printf '\t')" read -r k a; do
    [ "$k" = "$key" ] && { printf '%s' "$a"; return 0; }
  done <<EOF
$(_emit_aliases_from_file "$HOME/.cursor/mcp.json"; _emit_aliases_from_file "$PWD/.cursor/mcp.json")
EOF
  return 1
}

# extract_mcp_server_name INPUT_JSON -> echoes server name (also reads TI_url for tool_input nested url)
extract_mcp_server_name() {  # INPUT_JSON
  local input="$1" url command server tn a
  url="$(json_string "$input" url 2>/dev/null)"
  command="$(json_string "$input" command 2>/dev/null)"
  if [ -n "$url" ] && a="$(_lookup_alias "$url")"; then printf '%s' "$a"; return 0; fi
  if [ -n "$command" ] && a="$(_lookup_alias "$command")"; then printf '%s' "$a"; return 0; fi
  server="$(json_string "$input" server 2>/dev/null)"
  [ -n "$server" ] && { printf '%s' "$server"; return 0; }
  if [ -n "$url" ]; then
    local h="${url#https://}"; h="${h#http://}"; h="${h%%/*}"; printf '%s' "$h"; return 0
  fi
  [ -n "$command" ] && { printf '%s' "$command"; return 0; }
  tn="$(json_string "$input" tool_name 2>/dev/null)"
  case "$tn" in
    mcp__*) local rest="${tn#mcp__}"; printf '%s' "${rest%%__*}"; return 0;;
  esac
  printf 'cursor-unknown'
}

# tool_input may be a JSON-encoded string; return a real object/value (raw)
parse_tool_input() {  # INPUT_JSON -> echoes tool_input object as raw JSON
  local input="$1" raw
  raw="$(json_raw "$input" tool_input 2>/dev/null)" || { printf '{}'; return; }
  case "$raw" in
    '"'*'"')  # JSON-encoded string -> decode then it should be JSON
      local dec; dec="${raw#\"}"; dec="${dec%\"}"; dec="$(_json_unescape "$dec")"
      local t; t="$(printf '%s' "$dec" | tr -d '[:space:]')"
      [ -z "$t" ] && { printf '{}'; return; }
      case "$dec" in
        '{'*|'['*) printf '%s' "$dec";;
        *) printf '{"raw":%s}' "$(json_str "$dec")";;
      esac
      ;;
    ''|null) printf '{}';;
    *) printf '%s' "$raw";;
  esac
}

jsonrpc_arguments() {  # TOOL_INPUT_RAW
  local ti="$1"
  case "$ti" in '{'*) printf '%s' "$ti";; ''|null) printf '{}';; *) printf '{"input":%s}' "$ti";; esac
}
build_tools_call_jsonrpc() {  # TOOL_NAME TOOL_INPUT_RAW
  printf '{"jsonrpc":"2.0","method":"tools/call","params":{"name":%s,"arguments":%s},"id":1}' \
    "$(json_str "$1")" "$(jsonrpc_arguments "$2")"
}

build_validation_request() {  # TOOL_NAME TOOL_INPUT_RAW SERVER
  local tool_name="$1" tool_input="$2" server="$3"
  local tags host request_payload
  tags="{\"mcp-server\":\"MCP Server\",\"mcp-client\":$(json_str "$AKTO_CONNECTOR_VALUE")"
  [ "$AKTO_MODE" = "atlas" ] && tags="$tags,\"source\":$(json_str "$CONTEXT_SOURCE")"
  tags="$tags,\"mcp_server_name\":$(json_str "$server")}"
  host="${DEVICE_ID}.${AKTO_CONNECTOR_VALUE}.${server}"
  request_payload="$(build_tools_call_jsonrpc "$tool_name" "$tool_input")"

  local req_hdr="{\"host\":$(json_str "$host"),\"x-cursor-hook\":\"beforeMCPExecution\",\"x-mcp-server\":$(json_str "$server"),\"content-type\":\"application/json\"}"
  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str '{"x-cursor-hook":"beforeMCPExecution"}')"
  local request_payload_str; request_payload_str="$(json_str "$request_payload")"
  local response_payload; response_payload="$(json_str '{}')"
  local tag_str; tag_str="$(json_str "$tags")"

  printf '{"path":%s,"requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":0,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$(json_str "$MCP_INGEST_PATH")" "$request_headers" "$response_headers" "$request_payload_str" "$response_payload" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")"
}

ingest_blocked_request() {  # TOOL_NAME TOOL_INPUT_RAW REASON SERVER
  [ -z "$AKTO_DATA_INGESTION_URL" ] && return 0
  [ "$AKTO_SYNC_MODE" != "true" ] && return 0
  local body; body="$(build_validation_request "$1" "$2" "$4")"
  # override response headers/payload + status to 403 (best-effort sed surgery)
  local rhdr rpl
  rhdr="$(json_str '{"x-cursor-hook":"beforeMCPExecution","x-blocked-by":"Akto Proxy","content-type":"application/json"}')"
  rpl="$(json_str "{\"body\":$(json_str "{\"x-blocked-by\":\"Akto Proxy\",\"reason\":$(json_str "${3:-Policy violation}")}")}")"
  body="$(printf '%s' "$body" | sed -E "s/\"responseHeaders\":\"[^\"]*(\\\\.[^\"]*)*\"/\"responseHeaders\":$(printf '%s' "$rhdr" | sed 's/[&/\]/\\&/g')/")"
  body="$(printf '%s' "$body" | sed -E 's/"statusCode":"200"/"statusCode":"403"/; s/"status":"200"/"status":"403"/')"
  post_payload_json "$(build_http_proxy_url 0 0 1)" "$body" >/dev/null 2>&1 || log_error "Ingestion error"
}

main() {
  log_info "=== Hook execution started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  [ -z "$input" ] && { printf '{"permission":"allow"}\n'; exit 0; }

  local tool_name tool_input server
  tool_name="$(json_string "$input" tool_name 2>/dev/null)"
  tool_input="$(parse_tool_input "$input")"
  server="$(extract_mcp_server_name "$input")"

  log_info "Processing MCP tools/call: $tool_name (server=$server)"

  # empty tool input -> allow
  case "$tool_input" in
    ''|'{}'|null) log_info "Empty tool input, allowing request"; printf '{"permission":"allow"}\n'; exit 0;;
  esac

  if [ "$AKTO_SYNC_MODE" = "true" ]; then
    local gr_allowed="true" gr_reason="" gr_behaviour=""
    if [ -n "$AKTO_DATA_INGESTION_URL" ]; then
      local body resp
      body="$(build_validation_request "$tool_name" "$tool_input" "$server")"
      # cursor mcp validate: guardrails=true & ingest_data=false
      if resp="$(post_payload_json "$(build_http_proxy_url 1 0 0)" "$body")"; then
        parse_guardrails_result "$resp"
        gr_allowed="$GR_ALLOWED"; gr_reason="$GR_REASON"; gr_behaviour="$GR_BEHAVIOUR"
      else
        log_error "Guardrails validation error; fail-open"
      fi
    else
      log_warn "AKTO_DATA_INGESTION_URL not set, allowing"
    fi

    # fingerprint: json.dumps({"t":tool,"i":input}, sort_keys=True) -> {"i":..,"t":..}
    local fp; fp="$(printf '{"i":%s,"t":%s}' "$tool_input" "$(json_str "$tool_name")" | sha256_hex)"
    apply_warn_resubmit_flow "$gr_allowed" "$gr_reason" "$gr_behaviour" "$fp" "$WARN_STATE_PATH"

    if [ "$ALLOWED" = "false" ]; then
      local user_message agent_message reason_text="${gr_reason:-Policy violation}"
      if is_warn_behaviour "$gr_behaviour"; then
        user_message="Warning!! Cursor MCP call blocked, send the same request again to bypass. Reason: $reason_text"
      else
        user_message="Request blocked by Akto security policy"
      fi
      agent_message="Blocked by Akto Guardrails: $reason_text"
      log_warn "BLOCKING request - server: $server, reason: $gr_reason"
      printf '{"permission":"deny","user_message":%s,"agent_message":%s}\n' \
        "$(json_str "$user_message")" "$(json_str "$agent_message")"
      ingest_blocked_request "$tool_name" "$tool_input" "$gr_reason" "$server"
      exit 0
    fi
  fi
  log_info "Request allowed"
  printf '{"permission":"allow"}\n'
  exit 0
}
main
