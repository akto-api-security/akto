#!/usr/bin/env bash
# akto-validate-prompt.sh - bash port of akto-validate-prompt.py (experimental.chat.messages.transform)
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/validate-prompt.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_prompt_warn_pending.json"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"
OPENCODE_API_URL="$(resolve_opencode_api_url)"

build_validation_request() {  # PROMPT SESSION_INFO_JSON
  local prompt="$1" session_info="${2:-}"
  local host="${OPENCODE_API_URL#https://}"; host="${host#http://}"
  local tags='{"gen-ai":"Gen AI"}'
  [ "$AKTO_MODE" = "atlas" ] && tags="{\"gen-ai\":\"Gen AI\",\"ai-agent\":\"opencode\",\"source\":$(json_str "$CONTEXT_SOURCE")}"

  local req_hdr="{\"host\":$(json_str "$host"),\"x-claude-hook\":\"UserPromptSubmit\",\"content-type\":\"application/json\"}"
  local resp_hdr='{"x-claude-hook":"UserPromptSubmit"}'
  local trimmed; trimmed="$(printf '%s' "$prompt" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"

  printf '{"path":"/v1/messages","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$(json_str "$req_hdr")" "$(json_str "$resp_hdr")" "$(json_str "{\"body\":$(json_str "$trimmed")}")" "$(json_str '{}')" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$(json_str "$DEVICE_ID")" "$(json_str "$tags")" "$(json_str "$tags")" "$(json_str "$CONTEXT_SOURCE")"
}

main() {
  log_info "=== Prompt hook started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  [ -z "$input" ] && { log_error "Empty input"; exit 0; }

  # OpenCode always sends prompt as a top-level string field in UserPromptSubmit
  local prompt; prompt="$(json_string "$input" prompt 2>/dev/null)"
  local trimmed; trimmed="$(printf '%s' "$prompt" | tr -d '[:space:]')"
  [ -z "$trimmed" ] && { log_info "Empty prompt, skipping"; exit 0; }
  log_info "Prompt length: ${#prompt} chars"

  if [ "$AKTO_SYNC_MODE" = "true" ] && [ -n "$AKTO_DATA_INGESTION_URL" ]; then
    local body resp
    body="$(build_validation_request "$prompt")"
    resp="$(post_payload_json "$(build_http_proxy_url 1 1)" "$body")" || { log_error "guardrails failed; fail-open"; exit 0; }
    parse_guardrails_result "$resp"

    # fingerprint: json.dumps({"p": prompt, "a": []}, sort_keys=True) → {"a": [], "p": "..."}
    local fp; fp="$(printf '{"a": [], "p": %s}' "$(json_str "$prompt")" | sha256_hex)"
    apply_warn_resubmit_flow "$GR_ALLOWED" "$GR_REASON" "$GR_BEHAVIOUR" "$fp" "$WARN_STATE_PATH"

    if [ "$ALLOWED" = "false" ]; then
      local block_reason
      if is_warn_behaviour "$GR_BEHAVIOUR"; then
        block_reason="Warning!!, prompt blocked, please review it. Send again to bypass. Reason for blocking: $GR_REASON"
      else
        block_reason="Prompt blocked: $GR_REASON"
      fi
      log_warn "BLOCKING prompt - Reason: $block_reason"
      # ingest blocked
      local blocked_body; blocked_body="$(build_validation_request "$prompt")"
      post_payload_json "$(build_http_proxy_url 0 1)" "$blocked_body" >/dev/null 2>&1 || true
      printf '{"decision":"block","reason":%s}' "$(json_str "$block_reason")"
      exit 0
    fi
    log_info "Prompt ALLOWED"
  fi
  exit 0
}
main
