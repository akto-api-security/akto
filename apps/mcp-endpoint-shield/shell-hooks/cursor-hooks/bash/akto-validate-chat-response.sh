#!/usr/bin/env bash
# akto-validate-chat-response.sh - bash port of akto-validate-chat-response.py
# Cursor afterAgentResponse hook. Observe-only: ingests the agent response with
# response_guardrails=true so Akto can alert server-side. Cannot block.
# Output schema (cursor after hook): {}.  Exit 0 always.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
: "${LOG_DIR:=$HOME/.cursor/akto/chat-logs}"
export LOG_DIR
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/akto-validate-chat-response.log"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"

if [ "$AKTO_MODE" = "atlas" ]; then
  if [ -n "$DEVICE_ID" ]; then API_URL="https://${DEVICE_ID}.ai-agent.cursor"; else API_URL="https://api.anthropic.com"; fi
else
  API_URL="${API_URL:-https://api.anthropic.com}"
fi

build_ingestion_payload() {  # RESPONSE_TEXT
  local rt="$1"
  local host="${API_URL#https://}"; host="${host#http://}"
  local tags
  if [ "$AKTO_MODE" = "atlas" ]; then
    tags="{\"gen-ai\":\"Gen AI\",\"ai-agent\":\"cursor\",\"source\":$(json_str "$CONTEXT_SOURCE")}"
  else
    tags='{"gen-ai":"Gen AI"}'
  fi
  local request_headers; request_headers="$(json_str "{\"host\":$(json_str "$host"),\"x-cursor-hook\":\"afterAgentResponse\",\"content-type\":\"application/json\"}")"
  local response_headers; response_headers="$(json_str '{"x-cursor-hook":"afterAgentResponse","content-type":"application/json"}')"
  local request_payload; request_payload="$(json_str '{}')"
  local response_payload; response_payload="$(json_str "{\"body\":$(json_str "$rt")}")"
  local tag_str; tag_str="$(json_str "$tags")"
  printf '{"path":"/v1/messages","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":null,"status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$request_headers" "$response_headers" "$request_payload" "$response_payload" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$(json_str "$DEVICE_ID")" \
    "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")"
}

main() {
  log_info "=== Chat Response Hook execution started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  [ -z "$input" ] && { printf '{}\n'; exit 0; }

  local response_text; response_text="$(json_string "$input" text 2>/dev/null)"
  local trimmed; trimmed="$(printf '%s' "$response_text" | tr -d '[:space:]')"
  [ -z "$trimmed" ] && { log_warn "Empty response received"; printf '{}\n'; exit 0; }

  if [ -n "$AKTO_DATA_INGESTION_URL" ]; then
    log_info "Ingesting chat response (length: ${#response_text})"
    local body; body="$(build_ingestion_payload "$response_text")"
    # observe-only: response_guardrails=true & ingest_data=true
    post_payload_json "$(build_http_proxy_url 0 1 1)" "$body" >/dev/null 2>&1 || log_error "Ingestion error"
  else
    log_warn "AKTO_DATA_INGESTION_URL not set, skipping ingestion"
  fi

  log_info "Hook execution completed"
  printf '{}\n'
  exit 0
}
main
