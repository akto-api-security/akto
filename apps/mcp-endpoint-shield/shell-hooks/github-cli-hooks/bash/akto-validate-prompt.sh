#!/usr/bin/env bash
# akto-validate-prompt.sh - bash port of akto-validate-prompt.py (userPromptSubmitted)
# Reads hook JSON on stdin, validates the prompt against Akto guardrails.
# On block: writes a warning to stderr, prints {"continue":false,"stopReason":...}
# to stdout, and exits with the connector's blocked_exit_code (2 vscode, 0 copilot).
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=akto_common.sh
. "$SCRIPT_DIR/akto_common.sh"

# build_validation_request PROMPT TIMESTAMP -> echoes ingestion payload JSON
build_validation_request() {
  local prompt="$1" timestamp="$2"
  local host="${CFG_API_URL#https://}"; host="${host#http://}"
  local device_id="${DEVICE_ID:-$(get_machine_id)}"

  # tags
  local tags
  if [ "$AKTO_MODE" = "atlas" ]; then
    tags="{\"gen-ai\":\"Gen AI\",\"ai-agent\":$(json_str "$CFG_AI_AGENT_TAG"),\"source\":$(json_str "$CONTEXT_SOURCE")}"
  else
    tags='{"gen-ai":"Gen AI"}'
  fi

  local hook_value="UserPromptSubmitted"
  local req_hdr="{\"host\":$(json_str "$host"),$(json_str "$CFG_HOOK_HEADER"):$(json_str "$hook_value"),\"content-type\":\"application/json\"}"
  local resp_hdr="{$(json_str "$CFG_HOOK_HEADER"):$(json_str "$hook_value")}"

  local trimmed; trimmed="$(printf '%s' "$prompt" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  # requestPayload = json.dumps({"body": prompt.strip()})  -> body is the plain string
  local request_payload; request_payload="$(json_str "{\"body\":$(json_str "$trimmed")}")"
  local response_payload; response_payload="$(json_str '{}')"
  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str "$resp_hdr")"
  local tag_str; tag_str="$(json_str "$tags")"

  printf '{"path":"/copilot/chat","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":%s,"statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":"ENDPOINT"}' \
    "$request_headers" "$response_headers" "$request_payload" "$response_payload" \
    "$(json_str "$(get_username)")" "$(json_str "$timestamp")" "$tag_str" "$tag_str"
}

ingest_blocked() {  # PROMPT TIMESTAMP REASON
  [ -z "$AKTO_DATA_INGESTION_URL" ] && return 0
  local prompt="$1" timestamp="$2" reason="$3" body
  body="$(build_validation_request "$prompt" "$timestamp")"
  # blocked: responsePayload = {"body": json.dumps({"x-blocked-by":..,"reason":..})}, status 403
  local rpl; rpl="$(json_str "{\"body\":$(json_str "{\"x-blocked-by\":\"Akto Proxy\",\"reason\":$(json_str "$reason")}")}")"
  body="$(printf '%s' "$body" \
    | sed -E "s/\"responsePayload\":\"[^\"]*(\\\\.[^\"]*)*\"/\"responsePayload\":$(printf '%s' "$rpl" | sed 's/[&/\]/\\&/g')/" \
    | sed -E 's/"statusCode":"200"/"statusCode":"403"/; s/"status":"200"/"status":"403"/')"
  post_payload_json "$(build_proxy_url ingest)" "$body" >/dev/null 2>&1 || true
}

main() {
  local input; input="$(cat)"
  [ -z "$input" ] && { log_error "Invalid JSON input"; exit 0; }

  local connector; connector="$(detect_connector "$input")"
  load_connector_config "$connector"
  AKTO_LOG_DIR="$(resolve_log_dir)"
  mkdir -p "$AKTO_LOG_DIR" 2>/dev/null || true
  AKTO_LOG_FILE="$AKTO_LOG_DIR/validate-prompt.log"
  WARN_STATE_PATH="$AKTO_LOG_DIR/akto_prompt_warn_pending.json"
  send_heartbeat "$AKTO_LOG_DIR"

  log_info "=== User Prompt Submitted Hook - Connector: $connector, Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  log_info "MODE: $AKTO_MODE, API_URL: $CFG_API_URL"

  local prompt; prompt="$(json_string "$input" "prompt" 2>/dev/null)"

  # timestamp: numeric -> use as-is; else now in ms
  local raw_ts; raw_ts="$(json_raw "$input" timestamp 2>/dev/null)"
  local timestamp
  case "$raw_ts" in
    ''|*[!0-9]*) timestamp="$(date +%s)000";;
    *) timestamp="$raw_ts";;
  esac

  local trimmed; trimmed="$(printf '%s' "$prompt" | tr -d '[:space:]')"
  [ -z "$trimmed" ] && { log_info "Empty prompt, skipping validation"; exit 0; }
  log_info "Prompt length: ${#prompt} chars"

  if [ "$AKTO_SYNC_MODE" = "true" ] && [ -n "$AKTO_DATA_INGESTION_URL" ]; then
    local body resp
    body="$(build_validation_request "$prompt" "$timestamp")"
    resp="$(post_payload_json "$(build_proxy_url prompt_guard)" "$body")" || { log_error "guardrails call failed; fail-open"; exit 0; }
    parse_guardrails_result "$resp"

    local fp; fp="$(printf '{"p":%s}' "$(json_str "$prompt")" | sha256_hex)"
    apply_warn_resubmit_flow "$GR_ALLOWED" "$GR_REASON" "$GR_BEHAVIOUR" "$fp" "$WARN_STATE_PATH"

    if [ "$ALLOWED" = "false" ]; then
      local block_reason
      if is_warn_behaviour "$GR_BEHAVIOUR"; then
        block_reason="Warning!!, prompt blocked, please review it. Send again to bypass. Reason for blocking: $GR_REASON"
      else
        block_reason="Prompt blocked: $GR_REASON"
      fi
      log_warn "BLOCKING prompt - Reason: $block_reason"
      ingest_blocked "$prompt" "$timestamp" "$GR_REASON"
      printf '%s  Akto Guardrails flagged prompt: %s\n' "Warning:" "${GR_REASON:-Policy violation}" >&2
      printf '{"continue":false,"stopReason":%s}' "$(json_str "$block_reason")"
      exit "$CFG_BLOCKED_EXIT_CODE"
    fi
  fi
  log_info "Hook completed"
  exit 0
}

main
