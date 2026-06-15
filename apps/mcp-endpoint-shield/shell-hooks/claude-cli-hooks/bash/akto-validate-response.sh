#!/usr/bin/env bash
# akto-validate-response.sh - bash port of akto-validate-response.py (Stop)
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/validate-response.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_response_warn_pending.json"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"

if [ "$AKTO_MODE" = "atlas" ]; then
  if [ -n "$DEVICE_ID" ]; then CLAUDE_API_URL="https://${DEVICE_ID}.ai-agent.${AKTO_CONNECTOR_VALUE}"; else CLAUDE_API_URL="https://api.anthropic.com"; fi
else
  CLAUDE_API_URL="${CLAUDE_API_URL:-https://api.anthropic.com}"
fi

# Extract text from a transcript entry's message.content (string or [{type:text,text}])
extract_entry_text() {  # ENTRY_JSON
  local entry="$1" msg content
  msg="$(json_raw "$entry" message 2>/dev/null)" || { printf ''; return; }
  content="$(json_raw "$msg" content 2>/dev/null)" || { printf ''; return; }
  case "$content" in
    '"'*'"')  # plain string
      content="${content#\"}"; content="${content%\"}"; _json_unescape "$content";;
    '['*)     # array of blocks: concatenate text of {"type":"text",...}
      # naive: pull each "text":"..." occurrence where a type:text precedes it
      printf '%s' "$content" | grep -oE '"type"[[:space:]]*:[[:space:]]*"text"[^}]*"text"[[:space:]]*:[[:space:]]*"([^"\\]|\\.)*"' \
        | sed -E 's/.*"text"[[:space:]]*:[[:space:]]*"//; s/"$//' | tr -d '\n';;
    *) printf '';;
  esac
}

get_last_user_prompt() {  # TRANSCRIPT_PATH
  local tp="$1"
  [ -f "$tp" ] || { printf ''; return; }
  local last="" line typ txt
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    typ="$(json_string "$line" type 2>/dev/null)"
    [ "$typ" = "user" ] || continue
    txt="$(extract_entry_text "$line")"
    [ -n "$txt" ] && last="$txt"
  done < "$tp"
  printf '%s' "$last"
}

build_session_headers() {
  local frag=""
  [ -n "${SI_session_id:-}" ]      && frag="$frag,\"x-akto-installer-session_id\":$(json_str "$SI_session_id")"
  [ -n "${SI_transcript_path:-}" ] && frag="$frag,\"x-akto-installer-transcript_path\":$(json_str "$SI_transcript_path")"
  [ -n "${SI_cwd:-}" ]             && frag="$frag,\"x-akto-installer-cwd\":$(json_str "$SI_cwd")"
  [ -n "${SI_permission_mode:-}" ] && frag="$frag,\"x-akto-installer-permission_mode\":$(json_str "$SI_permission_mode")"
  [ -n "${SI_hook_event_name:-}" ] && frag="$frag,\"x-akto-installer-hook_event_name\":$(json_str "$SI_hook_event_name")"
  printf '%s' "$frag"
}

build_ingestion_payload() {  # USER_PROMPT RESPONSE_TEXT
  local up="$1" rt="$2"
  local host="${CLAUDE_API_URL#https://}"; host="${host#http://}"
  local tags
  if [ "$AKTO_MODE" = "atlas" ]; then
    tags="{\"gen-ai\":\"Gen AI\",\"ai-agent\":$(json_str "$AKTO_CONNECTOR_VALUE"),\"source\":$(json_str "$CONTEXT_SOURCE")}"
  else
    tags='{"gen-ai":"Gen AI"}'
  fi
  local req_hdr="{\"host\":$(json_str "$host"),\"x-claude-hook\":\"Stop\",\"content-type\":\"application/json\"$(build_session_headers)}"
  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str '{"x-claude-hook":"Stop","content-type":"application/json"}')"
  local request_payload; request_payload="$(json_str "{\"body\":$(json_str "$up")}")"
  local response_payload; response_payload="$(json_str "{\"body\":$(json_str "$rt")}")"
  local tag_str; tag_str="$(json_str "$tags")"
  printf '{"path":"/v1/messages","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$request_headers" "$response_headers" "$request_payload" "$response_payload" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$(json_str "$DEVICE_ID")" "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")"
}

main() {
  log_info "=== Hook execution started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  SI_session_id="$(json_string "$input" session_id 2>/dev/null)"
  SI_transcript_path="$(json_string "$input" transcript_path 2>/dev/null)"
  SI_cwd="$(json_string "$input" cwd 2>/dev/null)"
  SI_permission_mode="$(json_string "$input" permission_mode 2>/dev/null)"
  SI_hook_event_name="$(json_string "$input" hook_event_name 2>/dev/null)"

  local transcript_path; transcript_path="$SI_transcript_path"
  [ -z "$transcript_path" ] && { log_info "No transcript path provided"; exit 0; }
  case "$transcript_path" in "~"/*) transcript_path="$HOME/${transcript_path#"~/"}";; esac

  local response_text; response_text="$(json_string "$input" last_assistant_message 2>/dev/null)"
  response_text="$(printf '%s' "$response_text" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  local stop_active; stop_active="$(json_bool "$input" stop_hook_active)"
  local user_prompt; user_prompt="$(get_last_user_prompt "$transcript_path")"

  { [ -z "$user_prompt" ] || [ -z "$response_text" ]; } && { log_info "No complete interaction found"; exit 0; }

  if [ "$AKTO_SYNC_MODE" = "true" ] && [ "$stop_active" != "true" ]; then
    if [ -z "$AKTO_DATA_INGESTION_URL" ]; then log_warn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; exit 0; fi
    local body resp
    body="$(build_ingestion_payload "$user_prompt" "$response_text")"
    resp="$(post_payload_json "$(build_http_proxy_url 0 1 0)" "$body")" || { log_error "guardrails failed; fail-open"; exit 0; }
    parse_guardrails_result "$resp"
    local fp; fp="$(printf '{"p":%s,"r":%s}' "$(json_str "$user_prompt")" "$(json_str "$response_text")" | sha256_hex)"
    apply_warn_resubmit_flow "$GR_ALLOWED" "$GR_REASON" "$GR_BEHAVIOUR" "$fp" "$WARN_STATE_PATH"
    if [ "$ALLOWED" = "false" ]; then
      local block_reason
      if is_warn_behaviour "$GR_BEHAVIOUR"; then
        block_reason="Warning!!, response blocked, please review it. Send again to bypass. Reason for blocking: $GR_REASON"
      else
        block_reason="Response blocked: $GR_REASON"
      fi
      log_warn "BLOCKING Stop - Reason: $GR_REASON"
      printf '{"decision":"block","reason":%s}\n' "$(json_str "$block_reason")"
      # ingest blocked (best effort)
      [ -n "$AKTO_DATA_INGESTION_URL" ] && post_payload_json "$(build_http_proxy_url 0 0 1)" "$body" >/dev/null 2>&1 || true
      exit 0
    fi
  fi
  # normal ingestion
  if [ -n "$AKTO_DATA_INGESTION_URL" ]; then
    local rg=0; [ "$AKTO_SYNC_MODE" != "true" ] && rg=1
    local body; body="$(build_ingestion_payload "$user_prompt" "$response_text")"
    post_payload_json "$(build_http_proxy_url 0 "$rg" 1)" "$body" >/dev/null 2>&1 || true
  fi
  log_info "Hook execution completed"
  exit 0
}
main
