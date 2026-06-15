#!/usr/bin/env bash
# akto-validate-response.sh - bash port of claude-cli-hooks-argus/akto-validate-response.py (Stop)
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/akto_common.sh"
AKTO_LOG_FILE="$AKTO_LOG_DIR/validate-response.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_response_warn_pending.json"
AKTO_HOST="${AKTO_HOST:-https://api.anthropic.com}"
CONTEXT_SOURCE="${CONTEXT_SOURCE:-AGENTIC}"
HOST_HEADER="${AKTO_HOST#https://}"; HOST_HEADER="${HOST_HEADER#http://}"
DEVICE_IP="$(get_device_ip)"

extract_entry_text() {
  local entry="$1" msg content
  msg="$(json_raw "$entry" message 2>/dev/null)" || { printf ''; return; }
  content="$(json_raw "$msg" content 2>/dev/null)" || { printf ''; return; }
  case "$content" in
    '"'*'"') content="${content#\"}"; content="${content%\"}"; _json_unescape "$content";;
    '['*) printf '%s' "$content" | grep -oE '"type"[[:space:]]*:[[:space:]]*"text"[^}]*"text"[[:space:]]*:[[:space:]]*"([^"\\]|\\.)*"' \
            | sed -E 's/.*"text"[[:space:]]*:[[:space:]]*"//; s/"$//' | tr -d '\n';;
    *) printf '';;
  esac
}
get_last_user_prompt() {
  local tp="$1"; [ -f "$tp" ] || { printf ''; return; }
  local last="" line typ txt
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    typ="$(json_string "$line" type 2>/dev/null)"; [ "$typ" = "user" ] || continue
    txt="$(extract_entry_text "$line")"; [ -n "$txt" ] && last="$txt"
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
  local tags; tags="{\"gen-ai\":\"Gen AI\",\"source\":$(json_str "$CONTEXT_SOURCE")}"
  local req_hdr="{\"host\":$(json_str "$HOST_HEADER"),\"x-claude-hook\":\"Stop\",\"content-type\":\"application/json\"$(build_session_headers)}"
  local tag_str; tag_str="$(json_str "$tags")"
  printf '{"path":"/v1/messages","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":0,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$(json_str "$req_hdr")" "$(json_str '{"x-claude-hook":"Stop","content-type":"application/json"}')" \
    "$(json_str "{\"body\":$(json_str "$up")}")" "$(json_str "{\"body\":$(json_str "$rt")}")" \
    "$(json_str "$DEVICE_IP")" "$(date +%s)000" "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")"
}
main() {
  log_info "=== Hook execution started - Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  SI_session_id="$(json_string "$input" session_id 2>/dev/null)"
  SI_transcript_path="$(json_string "$input" transcript_path 2>/dev/null)"
  SI_cwd="$(json_string "$input" cwd 2>/dev/null)"
  SI_permission_mode="$(json_string "$input" permission_mode 2>/dev/null)"
  SI_hook_event_name="$(json_string "$input" hook_event_name 2>/dev/null)"
  local transcript_path="$SI_transcript_path"
  [ -z "$transcript_path" ] && { log_info "No transcript path"; exit 0; }
  case "$transcript_path" in "~"/*) transcript_path="$HOME/${transcript_path#"~/"}";; esac
  local response_text; response_text="$(json_string "$input" last_assistant_message 2>/dev/null)"
  response_text="$(printf '%s' "$response_text" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  local stop_active; stop_active="$(json_bool "$input" stop_hook_active)"
  local user_prompt; user_prompt="$(get_last_user_prompt "$transcript_path")"
  { [ -z "$user_prompt" ] || [ -z "$response_text" ]; } && { log_info "No complete interaction"; exit 0; }
  if [ "$AKTO_SYNC_MODE" = "true" ] && [ "$stop_active" != "true" ]; then
    [ -z "$AKTO_DATA_INGESTION_URL" ] && { log_warn "no URL, fail-open"; exit 0; }
    local body resp; body="$(build_ingestion_payload "$user_prompt" "$response_text")"
    resp="$(post_payload_json "$(build_http_proxy_url 0 1 0)" "$body")" || { log_error "guardrails failed"; exit 0; }
    parse_guardrails_result "$resp"
    local fp; fp="$(printf '{"p":%s,"r":%s}' "$(json_str "$user_prompt")" "$(json_str "$response_text")" | sha256_hex)"
    apply_warn_resubmit_flow "$GR_ALLOWED" "$GR_REASON" "$GR_BEHAVIOUR" "$fp" "$WARN_STATE_PATH"
    if [ "$ALLOWED" = "false" ]; then
      local br
      if is_warn_behaviour "$GR_BEHAVIOUR"; then br="Warning!!, response blocked, please review it. Send again to bypass. Reason for blocking: $GR_REASON"; else br="Response blocked: $GR_REASON"; fi
      log_warn "BLOCKING Stop - Reason: $GR_REASON"
      printf '{"decision":"block","reason":%s}\n' "$(json_str "$br")"
      post_payload_json "$(build_http_proxy_url 0 0 1)" "$body" >/dev/null 2>&1 || true
      exit 0
    fi
  fi
  if [ -n "$AKTO_DATA_INGESTION_URL" ]; then
    local rg=0; [ "$AKTO_SYNC_MODE" != "true" ] && rg=1
    local body; body="$(build_ingestion_payload "$user_prompt" "$response_text")"
    post_payload_json "$(build_http_proxy_url 0 "$rg" 1)" "$body" >/dev/null 2>&1 || true
  fi
  exit 0
}
main
