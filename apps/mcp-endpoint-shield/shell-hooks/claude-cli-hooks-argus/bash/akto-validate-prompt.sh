#!/usr/bin/env bash
# akto-validate-prompt.sh - bash port of claude-cli-hooks-argus/akto-validate-prompt.py
# Argus mode: AKTO_HOST header, ip=device IP, vxlan=0, tags always include source.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/validate-prompt.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_prompt_warn_pending.json"
AKTO_HOST="${AKTO_HOST:-https://api.anthropic.com}"
CONTEXT_SOURCE="${CONTEXT_SOURCE:-AGENTIC}"
HOST_HEADER="${AKTO_HOST#https://}"; HOST_HEADER="${HOST_HEADER#http://}"
DEVICE_IP="$(get_device_ip)"

build_session_headers() {
  local frag=""
  [ -n "${SI_session_id:-}" ]      && frag="$frag,\"x-akto-installer-session_id\":$(json_str "$SI_session_id")"
  [ -n "${SI_transcript_path:-}" ] && frag="$frag,\"x-akto-installer-transcript_path\":$(json_str "$SI_transcript_path")"
  [ -n "${SI_cwd:-}" ]             && frag="$frag,\"x-akto-installer-cwd\":$(json_str "$SI_cwd")"
  [ -n "${SI_permission_mode:-}" ] && frag="$frag,\"x-akto-installer-permission_mode\":$(json_str "$SI_permission_mode")"
  [ -n "${SI_hook_event_name:-}" ] && frag="$frag,\"x-akto-installer-hook_event_name\":$(json_str "$SI_hook_event_name")"
  printf '%s' "$frag"
}

build_validation_request() {  # PROMPT
  local prompt="$1"
  local trimmed; trimmed="$(printf '%s' "$prompt" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  local tags; tags="{\"gen-ai\":\"Gen AI\",\"source\":$(json_str "$CONTEXT_SOURCE")}"
  local req_hdr="{\"host\":$(json_str "$HOST_HEADER"),\"x-claude-hook\":\"UserPromptSubmit\",\"content-type\":\"application/json\"$(build_session_headers)}"
  local tag_str; tag_str="$(json_str "$tags")"
  printf '{"path":"/v1/messages","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":0,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$(json_str "$req_hdr")" "$(json_str '{"x-claude-hook":"UserPromptSubmit"}')" \
    "$(json_str "{\"body\":$(json_str "$trimmed")}")" "$(json_str '{}')" \
    "$(json_str "$DEVICE_IP")" "$(date +%s)000" "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")"
}

main() {
  log_info "=== Hook execution started - Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  local prompt; prompt="$(json_string "$input" prompt 2>/dev/null)"
  SI_session_id="$(json_string "$input" session_id 2>/dev/null)"
  SI_transcript_path="$(json_string "$input" transcript_path 2>/dev/null)"
  SI_cwd="$(json_string "$input" cwd 2>/dev/null)"
  SI_permission_mode="$(json_string "$input" permission_mode 2>/dev/null)"
  SI_hook_event_name="$(json_string "$input" hook_event_name 2>/dev/null)"

  local trimmed; trimmed="$(printf '%s' "$prompt" | tr -d '[:space:]')"
  [ -z "$trimmed" ] && { log_info "Empty prompt, allowing"; exit 0; }

  if [ "$AKTO_SYNC_MODE" = "true" ]; then
    [ -z "$AKTO_DATA_INGESTION_URL" ] && { log_warn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; exit 0; }
    local body resp; body="$(build_validation_request "$prompt")"
    resp="$(post_payload_json "$(build_http_proxy_url 1 0 1)" "$body")" || { log_error "guardrails failed; fail-open"; exit 0; }
    parse_guardrails_result "$resp"
    local fp; fp="$(printf '{"a":[],"p":%s}' "$(json_str "$prompt")" | sha256_hex)"
    apply_warn_resubmit_flow "$GR_ALLOWED" "$GR_REASON" "$GR_BEHAVIOUR" "$fp" "$WARN_STATE_PATH"
    if [ "$ALLOWED" = "false" ]; then
      local block_reason
      if is_warn_behaviour "$GR_BEHAVIOUR"; then
        block_reason="Warning!!, prompt blocked, please review it. Send again to bypass. Reason for blocking: $GR_REASON"
      else
        block_reason="Prompt blocked: $GR_REASON"
      fi
      log_warn "BLOCKING prompt - Reason: $GR_REASON"
      printf '{"decision":"block","reason":%s}\n' "$(json_str "$block_reason")"
      post_payload_json "$(build_http_proxy_url 0 0 1)" "$body" >/dev/null 2>&1 || true
      exit 0
    fi
  fi
  log_info "Prompt allowed"
  exit 0
}
main
