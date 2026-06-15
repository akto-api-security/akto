#!/usr/bin/env bash
# akto-validate-prompt.sh - bash port of codex akto-validate-prompt.py (UserPromptSubmit)
# Reads hook JSON on stdin, validates the prompt against Akto guardrails,
# prints a {"decision":"block",...} object to stdout when blocked. Exit 0 always.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=akto_common.sh
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/validate-prompt.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_prompt_warn_pending.json"

# Detect Codex API host + path from the same env vars Codex CLI uses.
detect_codex_api() {  # -> sets CODEX_DETECTED_HOST and CODEX_API_PATH
  if [ -n "${OPENAI_BASE_URL:-}" ]; then
    CODEX_DETECTED_HOST="${OPENAI_BASE_URL%/}"; CODEX_API_PATH="/v1/responses"
  elif [ -n "${OPENAI_API_KEY:-}" ]; then
    CODEX_DETECTED_HOST="https://api.openai.com"; CODEX_API_PATH="/v1/responses"
  else
    CODEX_DETECTED_HOST="https://chatgpt.com"; CODEX_API_PATH="/backend-api/codex/responses"
  fi
}
detect_codex_api

if [ "$AKTO_MODE" = "atlas" ]; then
  DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"
  if [ -n "$DEVICE_ID" ]; then
    CODEX_API_HOST="https://${DEVICE_ID}.ai-agent.codexcli"
  else
    CODEX_API_HOST="$CODEX_DETECTED_HOST"
  fi
  log_info "MODE: $AKTO_MODE, Device ID: $DEVICE_ID, CODEX_API_HOST: $CODEX_API_HOST, CODEX_API_PATH: $CODEX_API_PATH"
else
  CODEX_API_HOST="$CODEX_DETECTED_HOST"
  log_info "MODE: $AKTO_MODE, CODEX_API_HOST: $CODEX_API_HOST, CODEX_API_PATH: $CODEX_API_PATH"
fi

build_session_headers() {  # echoes ,"x-akto-installer-<k>":"<v>" fragment from SI_* globals
  local frag=""
  [ -n "${SI_session_id:-}" ]      && frag="$frag,\"x-akto-installer-session_id\":$(json_str "$SI_session_id")"
  [ -n "${SI_transcript_path:-}" ] && frag="$frag,\"x-akto-installer-transcript_path\":$(json_str "$SI_transcript_path")"
  [ -n "${SI_cwd:-}" ]             && frag="$frag,\"x-akto-installer-cwd\":$(json_str "$SI_cwd")"
  [ -n "${SI_hook_event_name:-}" ] && frag="$frag,\"x-akto-installer-hook_event_name\":$(json_str "$SI_hook_event_name")"
  [ -n "${SI_model:-}" ]           && frag="$frag,\"x-akto-installer-model\":$(json_str "$SI_model")"
  [ -n "${SI_turn_id:-}" ]         && frag="$frag,\"x-akto-installer-turn_id\":$(json_str "$SI_turn_id")"
  printf '%s' "$frag"
}

build_validation_request() {  # PROMPT  -> echoes ingestion payload JSON
  local prompt="$1"
  local host="${CODEX_API_HOST#https://}"; host="${host#http://}"

  local tags
  if [ "$AKTO_MODE" = "atlas" ]; then
    tags="{\"gen-ai\":\"Gen AI\",\"ai-agent\":\"codexcli\",\"source\":$(json_str "$CONTEXT_SOURCE")}"
  else
    tags='{"gen-ai":"Gen AI"}'
  fi

  local req_hdr="{\"host\":$(json_str "$host"),\"x-codex-hook\":\"UserPromptSubmit\",\"content-type\":\"application/json\"$(build_session_headers)}"

  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str '{"x-codex-hook":"UserPromptSubmit"}')"
  local trimmed; trimmed="$(printf '%s' "$prompt" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  local request_payload; request_payload="$(json_str "{\"body\":$(json_str "$trimmed")}")"
  local response_payload; response_payload="$(json_str '{}')"
  local tag_str; tag_str="$(json_str "$tags")"

  printf '{"path":%s,"requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":0,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$(json_str "$CODEX_API_PATH")" "$request_headers" "$response_headers" "$request_payload" "$response_payload" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")"
}

ingest_blocked() {  # PROMPT REASON
  [ -z "$AKTO_DATA_INGESTION_URL" ] && return 0
  [ "$AKTO_SYNC_MODE" != "true" ] && return 0
  local prompt="$1" reason="$2" body
  body="$(build_validation_request "$prompt")"
  local rhdr
  rhdr="$(json_str '{"x-codex-hook":"UserPromptSubmit","x-blocked-by":"Akto Proxy","content-type":"application/json"}')"
  body="$(printf '%s' "$body" \
    | sed -E "s/\"responseHeaders\":\"[^\"]*(\\\\.[^\"]*)*\"/\"responseHeaders\":$(printf '%s' "$rhdr" | sed 's/[&/\]/\\&/g')/")"
  post_payload_json "$(build_http_proxy_url 0 0 1)" "$body" >/dev/null 2>&1 || true
}

main() {
  log_info "=== Hook execution started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"
  local prompt; prompt="$(json_string "$input" "prompt" 2>/dev/null)"

  SI_session_id="$(json_string "$input" "session_id" 2>/dev/null)"
  SI_transcript_path="$(json_string "$input" "transcript_path" 2>/dev/null)"
  SI_cwd="$(json_string "$input" "cwd" 2>/dev/null)"
  SI_hook_event_name="$(json_string "$input" "hook_event_name" 2>/dev/null)"
  SI_model="$(json_string "$input" "model" 2>/dev/null)"
  SI_turn_id="$(json_string "$input" "turn_id" 2>/dev/null)"

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
      ingest_blocked "$prompt" "${GR_REASON:-Policy violation}"
      exit 0
    fi
  fi
  log_info "Prompt allowed"
  exit 0
}

main
