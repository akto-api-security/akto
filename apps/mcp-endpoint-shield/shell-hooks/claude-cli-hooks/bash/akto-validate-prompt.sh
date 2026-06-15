#!/usr/bin/env bash
# akto-validate-prompt.sh - bash port of akto-validate-prompt.py (UserPromptSubmit)
# Reads hook JSON on stdin, validates the prompt against Akto guardrails,
# prints a {"decision":"block",...} object to stdout when blocked. Exit 0 always.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=akto_common.sh
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/validate-prompt.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_prompt_warn_pending.json"

# CLAUDE_API_URL based on mode
if [ "$AKTO_MODE" = "atlas" ]; then
  DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"
  if [ -n "$DEVICE_ID" ]; then
    CLAUDE_API_URL="https://${DEVICE_ID}.ai-agent.${AKTO_CONNECTOR_VALUE}"
  else
    CLAUDE_API_URL="https://api.anthropic.com"
  fi
else
  CLAUDE_API_URL="${CLAUDE_API_URL:-https://api.anthropic.com}"
fi

build_validation_request() {  # PROMPT  -> echoes ingestion payload JSON
  local prompt="$1"
  local host="${CLAUDE_API_URL#https://}"; host="${host#http://}"
  local device_id="${DEVICE_ID:-$(get_machine_id)}"

  # tags
  local tags
  if [ "$AKTO_MODE" = "atlas" ]; then
    tags="{\"gen-ai\":\"Gen AI\",\"ai-agent\":$(json_str "$AKTO_CONNECTOR_VALUE"),\"source\":$(json_str "$CONTEXT_SOURCE")}"
  else
    tags='{"gen-ai":"Gen AI"}'
  fi

  # request headers (+ x-akto-installer-* from session info globals SI_*)
  local req_hdr="{\"host\":$(json_str "$host"),\"x-claude-hook\":\"UserPromptSubmit\",\"content-type\":\"application/json\""
  [ -n "${SI_session_id:-}" ]      && req_hdr="$req_hdr,\"x-akto-installer-session_id\":$(json_str "$SI_session_id")"
  [ -n "${SI_transcript_path:-}" ] && req_hdr="$req_hdr,\"x-akto-installer-transcript_path\":$(json_str "$SI_transcript_path")"
  [ -n "${SI_cwd:-}" ]             && req_hdr="$req_hdr,\"x-akto-installer-cwd\":$(json_str "$SI_cwd")"
  [ -n "${SI_permission_mode:-}" ] && req_hdr="$req_hdr,\"x-akto-installer-permission_mode\":$(json_str "$SI_permission_mode")"
  [ -n "${SI_hook_event_name:-}" ] && req_hdr="$req_hdr,\"x-akto-installer-hook_event_name\":$(json_str "$SI_hook_event_name")"
  req_hdr="$req_hdr}"

  # request_headers / response_headers / payloads are JSON-encoded STRINGS
  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str '{"x-claude-hook":"UserPromptSubmit"}')"
  local trimmed; trimmed="$(printf '%s' "$prompt" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  local request_payload; request_payload="$(json_str "{\"body\":$(json_str "$trimmed")}")"
  local response_payload; response_payload="$(json_str '{}')"
  local tag_str; tag_str="$(json_str "$tags")"

  printf '{"path":"/v1/messages","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$request_headers" "$response_headers" "$request_payload" "$response_payload" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$(json_str "$device_id")" \
    "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")"
}

main() {
  log_info "=== Hook execution started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  local prompt; prompt="$(json_string "$input" "prompt" 2>/dev/null)"

  # session info -> SI_* globals
  SI_session_id="$(json_string "$input" "session_id" 2>/dev/null)"
  SI_transcript_path="$(json_string "$input" "transcript_path" 2>/dev/null)"
  SI_cwd="$(json_string "$input" "cwd" 2>/dev/null)"
  SI_permission_mode="$(json_string "$input" "permission_mode" 2>/dev/null)"
  SI_hook_event_name="$(json_string "$input" "hook_event_name" 2>/dev/null)"

  local trimmed; trimmed="$(printf '%s' "$prompt" | tr -d '[:space:]')"
  [ -z "$trimmed" ] && { log_info "Empty prompt, allowing"; exit 0; }
  log_info "Processing prompt (length: ${#prompt} chars)"

  if [ "$AKTO_SYNC_MODE" = "true" ]; then
    if [ -z "$AKTO_DATA_INGESTION_URL" ]; then
      log_warn "AKTO_DATA_INGESTION_URL not set, allowing prompt (fail-open)"
      exit 0
    fi
    local body resp
    body="$(build_validation_request "$prompt")"
    resp="$(post_payload_json "$(build_http_proxy_url 1 0 1)" "$body")" || { log_error "guardrails call failed; fail-open"; exit 0; }
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
      # ingest blocked request (best effort)
      ingest_blocked "$prompt" "$GR_REASON"
      exit 0
    fi
  fi
  log_info "Prompt allowed"
  exit 0
}

ingest_blocked() {  # PROMPT REASON
  [ -z "$AKTO_DATA_INGESTION_URL" ] && return 0
  [ "$AKTO_SYNC_MODE" != "true" ] && return 0
  local prompt="$1" reason="$2" body
  body="$(build_validation_request "$prompt")"
  # override response headers/payload + status to 403 via string surgery on known fields
  local rhdr rpl
  rhdr="$(json_str '{"x-claude-hook":"UserPromptSubmit","x-blocked-by":"Akto Proxy","content-type":"application/json"}')"
  rpl="$(json_str "{\"body\":$(json_str "{\"x-blocked-by\":\"Akto Proxy\",\"reason\":$(json_str "${reason:-Policy violation}")}")}")"
  body="$(printf '%s' "$body" \
    | sed -E "s/\"responseHeaders\":\"[^\"]*(\\\\.[^\"]*)*\"/\"responseHeaders\":$(printf '%s' "$rhdr" | sed 's/[&/\]/\\&/g')/")"
  # NOTE: response payload + status override omitted from sed surgery for safety;
  # the guardrails decision is already emitted. Ingestion here is best-effort.
  post_payload_json "$(build_http_proxy_url 0 0 1)" "$body" >/dev/null 2>&1 || true
}

main
