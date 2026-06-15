#!/usr/bin/env bash
# akto-validate-chat-prompt.sh - bash port of akto-validate-chat-prompt.py
# Cursor beforeSubmitPrompt hook. Validates the prompt against Akto guardrails.
# Output schema (cursor): {"continue":<bool>[, "user_message":<str>]}.  Exit 0 always.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# default cursor chat log dir before sourcing common (common honours LOG_DIR)
: "${LOG_DIR:=$HOME/.cursor/akto/chat-logs}"
export LOG_DIR
# shellcheck source=akto_common.sh
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/akto-validate-chat-prompt.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_chat_prompt_warn_pending.json"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"

if [ "$AKTO_MODE" = "atlas" ]; then
  if [ -n "$DEVICE_ID" ]; then API_URL="https://${DEVICE_ID}.ai-agent.cursor"; else API_URL="https://api.anthropic.com"; fi
else
  API_URL="${API_URL:-https://api.anthropic.com}"
fi

build_session_headers() {  # echoes header fragment for session_info fields
  local frag=""
  [ -n "${SI_conversation_id:-}" ] && frag="$frag,\"x-akto-installer-conversation_id\":$(json_str "$SI_conversation_id")"
  [ -n "${SI_generation_id:-}" ]   && frag="$frag,\"x-akto-installer-generation_id\":$(json_str "$SI_generation_id")"
  [ -n "${SI_model:-}" ]           && frag="$frag,\"x-akto-installer-model\":$(json_str "$SI_model")"
  [ -n "${SI_transcript_path:-}" ] && frag="$frag,\"x-akto-installer-transcript_path\":$(json_str "$SI_transcript_path")"
  [ -n "${SI_user_email:-}" ]      && frag="$frag,\"x-akto-installer-user_email\":$(json_str "$SI_user_email")"
  printf '%s' "$frag"
}

build_validation_request() {  # PROMPT ATTACHMENTS_RAW
  local prompt="$1" attachments="$2"
  local host="${API_URL#https://}"; host="${host#http://}"
  local tags
  if [ "$AKTO_MODE" = "atlas" ]; then
    tags="{\"gen-ai\":\"Gen AI\",\"ai-agent\":\"cursor\",\"source\":$(json_str "$CONTEXT_SOURCE")"
  else
    tags='{"gen-ai":"Gen AI"'
  fi
  if [ -n "$attachments" ] && [ "$attachments" != "[]" ] && [ "$attachments" != "null" ]; then
    tags="$tags,\"attachments_count\":$ATT_COUNT,\"attachment_types\":$(json_str "$ATT_TYPES")"
  fi
  tags="$tags}"

  local req_hdr="{\"host\":$(json_str "$host"),\"x-cursor-hook\":\"beforeSubmitPrompt\",\"content-type\":\"application/json\"$(build_session_headers)}"
  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str '{"x-cursor-hook":"beforeSubmitPrompt"}')"
  # cursor: requestPayload body is the raw prompt string
  local request_payload; request_payload="$(json_str "{\"body\":$(json_str "$prompt")}")"
  local response_payload; response_payload="$(json_str '{}')"
  local tag_str; tag_str="$(json_str "$tags")"

  printf '{"path":"/v1/messages","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":null,"status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$request_headers" "$response_headers" "$request_payload" "$response_payload" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$(json_str "$DEVICE_ID")" \
    "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")"
}

main() {
  log_info "=== Chat Prompt Hook execution started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  [ -z "$input" ] && { printf '{"continue":true}\n'; exit 0; }

  local prompt; prompt="$(json_string "$input" prompt 2>/dev/null)"
  local attachments; attachments="$(json_raw "$input" attachments 2>/dev/null)"; [ -z "$attachments" ] && attachments="[]"

  SI_conversation_id="$(json_string "$input" conversation_id 2>/dev/null)"
  SI_generation_id="$(json_string "$input" generation_id 2>/dev/null)"
  SI_model="$(json_string "$input" model 2>/dev/null)"
  SI_transcript_path="$(json_string "$input" transcript_path 2>/dev/null)"
  SI_user_email="$(json_string "$input" user_email 2>/dev/null)"

  # attachment count/types for tags (best-effort)
  ATT_COUNT=0; ATT_TYPES=""
  if [ "$attachments" != "[]" ] && [ "$attachments" != "null" ] && [ -n "$attachments" ]; then
    ATT_COUNT="$(printf '%s' "$attachments" | grep -oE '"type"[[:space:]]*:' | wc -l | tr -d ' ')"
    [ -z "$ATT_COUNT" ] && ATT_COUNT=0
    ATT_TYPES="$(printf '%s' "$attachments" | grep -oE '"type"[[:space:]]*:[[:space:]]*"[^"]*"' | sed -E 's/.*:[[:space:]]*"//; s/"$//' | paste -sd, - 2>/dev/null)"
  fi

  local trimmed; trimmed="$(printf '%s' "$prompt" | tr -d '[:space:]')"
  [ -z "$trimmed" ] && { log_warn "Empty prompt received, allowing"; printf '{"continue":true}\n'; exit 0; }
  log_info "Processing prompt (length: ${#prompt} chars)"

  local gr_allowed="true" gr_reason="" gr_behaviour=""
  if [ -n "$AKTO_DATA_INGESTION_URL" ]; then
    local body resp
    body="$(build_validation_request "$prompt" "$attachments")"
    # cursor: guardrails=AKTO_SYNC_MODE & ingest_data=true
    local g=0; [ "$AKTO_SYNC_MODE" = "true" ] && g=1
    if resp="$(post_payload_json "$(build_http_proxy_url "$g" 0 1)" "$body")"; then
      parse_guardrails_result "$resp"
      gr_allowed="$GR_ALLOWED"; gr_reason="$GR_REASON"; gr_behaviour="$GR_BEHAVIOUR"
    else
      log_error "Guardrails validation error; fail-open"
      gr_allowed="true"; gr_reason=""; gr_behaviour=""
    fi
  else
    log_warn "AKTO_DATA_INGESTION_URL not set, allowing prompt"
  fi

  # fingerprint: json.dumps({"p":prompt,"a":attachments}, sort_keys=True) -> {"a":..,"p":..}
  local fp; fp="$(printf '{"a":%s,"p":%s}' "$attachments" "$(json_str "$prompt")" | sha256_hex)"
  apply_warn_resubmit_flow "$gr_allowed" "$gr_reason" "$gr_behaviour" "$fp" "$WARN_STATE_PATH"

  if [ "$ALLOWED" = "false" ]; then
    local user_message
    if is_warn_behaviour "$gr_behaviour"; then
      user_message="Warning!!, prompt blocked, please review it. Send again to bypass. Reason for blocking: $gr_reason"
    else
      user_message="Prompt blocked: $gr_reason"
    fi
    log_warn "BLOCKING prompt - Reason: $gr_reason"
    printf '{"continue":false,"user_message":%s}\n' "$(json_str "$user_message")"
    exit 0
  fi
  log_info "Prompt allowed"
  printf '{"continue":true}\n'
  exit 0
}
main
