#!/usr/bin/env bash
# akto-validate-prompt.sh - bash port of akto-validate-prompt.py (BeforeModel)
# Reads gemini hook JSON on stdin, validates the prompt against Akto guardrails,
# prints {"decision":"deny",...} when blocked, else {}. Exit 0 always.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=akto_common.sh
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/akto-validate-prompt.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_prompt_warn_pending.json"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"

# GEMINI_API_URL based on mode
if [ "$AKTO_MODE" = "atlas" ]; then
  if [ -n "$DEVICE_ID" ]; then
    GEMINI_API_URL="https://${DEVICE_ID}.ai-agent.geminicli"
  else
    GEMINI_API_URL="${GEMINI_API_URL:-https://generativelanguage.googleapis.com}"
  fi
else
  GEMINI_API_URL="${GEMINI_API_URL:-https://generativelanguage.googleapis.com}"
fi

# parse_user_prompt INPUT_JSON -> echoes last user message text
# Walks llm_request.messages, finds the last {role:"user"} entry, returns its
# content (string) or the concatenation of {text} parts.
parse_user_prompt() {
  local input="$1" llm messages
  llm="$(json_raw "$input" llm_request 2>/dev/null)" || { printf ''; return; }
  messages="$(json_raw "$llm" messages 2>/dev/null)" || { printf ''; return; }
  case "$messages" in '['*) ;; *) printf ''; return;; esac
  # iterate the array elements (objects). We scan and capture the last user one.
  _AKTO_JSON="$messages"; _AKTO_JSON_LEN=${#messages}
  local i c estart eend elem role text last=""
  i="$(_json_skip_ws 1)"   # skip '['
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
    i="$(_json_skip_ws "$i")"
    c="${_AKTO_JSON:$i:1}"
    [ "$c" = ']' ] && break
    [ "$c" = ',' ] && { i=$((i+1)); continue; }
    [ "$c" = '{' ] || { i=$((i+1)); continue; }
    estart="$i"; eend="$(_json_scan_value "$i")"
    elem="${_AKTO_JSON:$estart:$((eend-estart))}"
    role="$(json_string "$elem" role 2>/dev/null)"
    if [ "$role" = "user" ]; then
      text="$(extract_message_content "$elem")"
      last="$text"
    fi
    # restore reader state (json_string/extract clobbered it)
    _AKTO_JSON="$messages"; _AKTO_JSON_LEN=${#messages}
    i="$eend"
  done
  printf '%s' "$last"
}

# extract_message_content MESSAGE_JSON -> content string or joined {text} parts
extract_message_content() {
  local msg="$1" content
  content="$(json_raw "$msg" content 2>/dev/null)" || { printf ''; return; }
  case "$content" in
    '"'*'"')  # plain string
      content="${content#\"}"; content="${content%\"}"; _json_unescape "$content";;
    '['*)     # array of {text:...} parts
      printf '%s' "$content" | grep -oE '"text"[[:space:]]*:[[:space:]]*"([^"\\]|\\.)*"' \
        | sed -E 's/.*"text"[[:space:]]*:[[:space:]]*"//; s/"$//' | tr -d '\n';;
    *) printf '';;
  esac
}

build_session_metadata() {  # -> echoes gemini_cli_session object JSON (or empty)
  local frag="" first=1
  add() {  # KEY VALUE
    [ -z "$2" ] && return 0
    [ "$first" -eq 1 ] && first=0 || frag="$frag,"
    frag="$frag$(json_str "$1"):$(json_str "$2")"
  }
  add session_id "${SI_session_id:-}"
  add transcript_path "${SI_transcript_path:-}"
  add cwd "${SI_cwd:-}"
  add hook_event_name "${SI_hook_event_name:-}"
  add hook_timestamp "${SI_timestamp:-}"
  [ "$first" -eq 1 ] && { printf ''; return; }
  printf '{%s}' "$frag"
}

build_validation_request() {  # PROMPT MODEL  -> echoes ingestion payload JSON
  local prompt="$1" model="$2"
  local host="${GEMINI_API_URL#https://}"; host="${host#http://}"

  # tags
  local tags
  if [ "$AKTO_MODE" = "atlas" ]; then
    tags="{\"gen-ai\":\"Gen AI\",\"ai-agent\":\"geminicli\",\"source\":\"ENDPOINT\"}"
  else
    tags='{"gen-ai":"Gen AI"}'
  fi

  # metadata
  local meta="{\"model\":$(json_str "$model")"
  if [ "$AKTO_MODE" = "atlas" ]; then
    meta="$meta,\"machine_id\":$(json_str "$DEVICE_ID"),\"log_storage\":{\"type\":\"local_file\",\"path\":$(json_str "$AKTO_LOG_DIR")}"
  fi
  local sess; sess="$(build_session_metadata)"
  [ -n "$sess" ] && meta="$meta,\"gemini_cli_session\":$sess"
  meta="$meta}"

  local req_hdr="{\"host\":$(json_str "$host"),\"x-geminicli-hook\":\"BeforeModel\",\"content-type\":\"application/json\"}"
  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str '{"x-geminicli-hook":"BeforeModel"}')"
  local trimmed; trimmed="$(printf '%s' "$prompt" | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//')"
  local request_payload; request_payload="$(json_str "{\"body\":$(json_str "$trimmed")}")"
  local response_payload; response_payload="$(json_str '{}')"
  local tag_str; tag_str="$(json_str "$tags")"
  local meta_str; meta_str="$(json_str "$meta")"

  printf '{"path":"/gemini/chat","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":"ENDPOINT"}' \
    "$request_headers" "$response_headers" "$request_payload" "$response_payload" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$(json_str "$DEVICE_ID")" \
    "$tag_str" "$meta_str"
}

ingest_blocked() {  # PROMPT MODEL
  [ -z "$AKTO_DATA_INGESTION_URL" ] && return 0
  [ "$AKTO_SYNC_MODE" != "true" ] && return 0
  local prompt="$1" model="$2" body
  body="$(build_validation_request "$prompt" "$model")"
  # override responsePayload + status 403 to mirror python ingest_blocked_request
  local rpl; rpl="$(json_str "{\"body\":$(json_str '{"x-blocked-by":"Akto Proxy"}')}")"
  body="$(printf '%s' "$body" | sed -E "s/\"responsePayload\":\"[^\"]*(\\\\.[^\"]*)*\"/\"responsePayload\":$(printf '%s' "$rpl" | sed 's/[&/\]/\\&/g')/; s/\"statusCode\":\"200\"/\"statusCode\":\"403\"/; s/\"status\":\"200\"/\"status\":\"403\"/")"
  post_payload_json "$(build_http_proxy_url 0 0 1)" "$body" >/dev/null 2>&1 || true
}

main() {
  log_info "=== Hook execution started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"

  local hook_event; hook_event="$(json_string "$input" hook_event_name 2>/dev/null)"
  if [ -n "$hook_event" ] && [ "$hook_event" != "BeforeModel" ]; then
    printf '{}'; exit 0
  fi

  # session info -> SI_* globals
  SI_session_id="$(json_string "$input" session_id 2>/dev/null)"
  SI_transcript_path="$(json_string "$input" transcript_path 2>/dev/null)"
  SI_cwd="$(json_string "$input" cwd 2>/dev/null)"
  SI_hook_event_name="$(json_string "$input" hook_event_name 2>/dev/null)"
  SI_timestamp="$(json_string "$input" timestamp 2>/dev/null)"

  local llm model
  llm="$(json_raw "$input" llm_request 2>/dev/null)" || llm=""
  model="$(json_string "$llm" model 2>/dev/null)"

  local prompt; prompt="$(parse_user_prompt "$input")"
  local trimmed; trimmed="$(printf '%s' "$prompt" | tr -d '[:space:]')"
  [ -z "$trimmed" ] && { log_info "Empty prompt, allowing"; printf '{}'; exit 0; }
  log_info "Processing prompt (length: ${#prompt} chars)"

  if [ "$AKTO_SYNC_MODE" = "true" ]; then
    if [ -z "$AKTO_DATA_INGESTION_URL" ]; then
      log_warn "AKTO_DATA_INGESTION_URL not set, allowing prompt (fail-open)"
      printf '{}'; exit 0
    fi
    local body resp
    body="$(build_validation_request "$prompt" "$model")"
    resp="$(post_payload_json "$(build_http_proxy_url 1 0 0)" "$body")" || { log_error "guardrails call failed; fail-open"; printf '{}'; exit 0; }
    parse_guardrails_result "$resp"

    # fingerprint matches python json.dumps({"p":query}, sort_keys=True)
    local fp; fp="$(printf '{"p": %s}' "$(json_str "$prompt")" | sha256_hex)"
    apply_warn_resubmit_flow "$GR_ALLOWED" "$GR_REASON" "$GR_BEHAVIOUR" "$fp" "$WARN_STATE_PATH"

    if [ "$ALLOWED" = "false" ]; then
      local deny_reason
      if is_warn_behaviour "$GR_BEHAVIOUR"; then
        deny_reason="Warning!! Prompt blocked, please review it. Send the same prompt again to bypass. Reason: ${GR_REASON:-Policy violation}"
      else
        deny_reason="Blocked by Akto Guardrails: ${GR_REASON:-Policy violation}"
      fi
      log_warn "BLOCKING prompt - behaviour='$GR_BEHAVIOUR', Reason: $GR_REASON"
      printf '{"decision":"deny","reason":%s}' "$(json_str "$deny_reason")"
      ingest_blocked "$prompt" "$model"
      exit 0
    fi
  fi
  log_info "Prompt allowed"
  printf '{}'
  exit 0
}

main
