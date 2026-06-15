#!/usr/bin/env bash
# akto-validate-response.sh - bash port of akto-validate-response.py (AfterModel)
# Streaming chunk accumulation per session; validate + ingest on finishReason.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/akto-validate-response.log"
WARN_STATE_PATH="$AKTO_LOG_DIR/akto_response_warn_pending.json"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"

if [ "$AKTO_MODE" = "atlas" ]; then
  if [ -n "$DEVICE_ID" ]; then GEMINI_API_URL="https://${DEVICE_ID}.ai-agent.geminicli"; else GEMINI_API_URL="${GEMINI_API_URL:-https://generativelanguage.googleapis.com}"; fi
else
  GEMINI_API_URL="${GEMINI_API_URL:-https://generativelanguage.googleapis.com}"
fi

AKTO_CHUNKS_DIR="${TMPDIR:-/tmp}"; AKTO_CHUNKS_DIR="${AKTO_CHUNKS_DIR%/}/akto_gemini_cli_chunks"

chunks_filepath() { printf '%s/%s.txt' "$AKTO_CHUNKS_DIR" "$1"; }

append_chunk() {  # SESSION_ID TEXT
  mkdir -p "$AKTO_CHUNKS_DIR" 2>/dev/null || true
  printf '%s' "$2" >> "$(chunks_filepath "$1")" 2>/dev/null || true
}

read_accumulated_and_clear() {  # SESSION_ID -> echoes accumulated text, removes file
  local f; f="$(chunks_filepath "$1")"
  [ -f "$f" ] && cat "$f"
  rm -f "$f" 2>/dev/null || true
}

# extract_chunk_text LLM_RESPONSE_JSON -> echoes concatenated non-thought text parts
extract_chunk_text() {
  local resp="$1" candidates first content parts
  candidates="$(json_raw "$resp" candidates 2>/dev/null)" || { printf ''; return; }
  case "$candidates" in '['*) ;; *) printf ''; return;; esac
  # take candidates[0]
  _AKTO_JSON="$candidates"; _AKTO_JSON_LEN=${#candidates}
  local i estart eend
  i="$(_json_skip_ws 1)"
  [ "${_AKTO_JSON:$i:1}" = '{' ] || { printf ''; return; }
  estart="$i"; eend="$(_json_scan_value "$i")"
  first="${candidates:$((estart)):$((eend-estart))}"
  content="$(json_raw "$first" content 2>/dev/null)" || { printf ''; return; }
  parts="$(json_raw "$content" parts 2>/dev/null)" || { printf ''; return; }
  case "$parts" in '['*) ;; *) printf ''; return;; esac
  # iterate parts, emit {text} for parts where thought is not true
  _AKTO_JSON="$parts"; _AKTO_JSON_LEN=${#parts}
  local c pstart pend part thought txt out=""
  i="$(_json_skip_ws 1)"
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
    i="$(_json_skip_ws "$i")"
    c="${_AKTO_JSON:$i:1}"
    [ "$c" = ']' ] && break
    [ "$c" = ',' ] && { i=$((i+1)); continue; }
    if [ "$c" = '{' ]; then
      pstart="$i"; pend="$(_json_scan_value "$i")"
      part="${_AKTO_JSON:$pstart:$((pend-pstart))}"
      thought="$(json_bool "$part" thought 2>/dev/null)"
      if [ "$thought" != "true" ]; then
        txt="$(json_string "$part" text 2>/dev/null)"
        out="$out$txt"
      fi
      _AKTO_JSON="$parts"; _AKTO_JSON_LEN=${#parts}
      i="$pend"
    elif [ "$c" = '"' ]; then
      # plain string part
      pstart="$i"; pend="$(_json_scan_string "$i")"
      part="${_AKTO_JSON:$((pstart+1)):$((pend-pstart-2))}"
      out="$out$(_json_unescape "$part")"
      i="$pend"
    else
      i=$((i+1))
    fi
  done
  printf '%s' "$out"
}

# strip thinking blocks: remove leading "**Header**\n\n....\n\n\n+" blocks.
# Mirrors python THINKING_BLOCK_PATTERN (MULTILINE, repeated): a bold header on
# its own line, a blank line, body text, then 3+ consecutive newlines.
# Implemented as a portable awk paragraph scanner (no PCRE / gensub).
strip_thinking_blocks() {  # TEXT
  local text="$1"
  [ -z "$(printf '%s' "$text" | tr -d '[:space:]')" ] && { printf '%s' "$text"; return; }
  printf '%s' "$text" | awk '
    BEGIN { RS=""; FS="\n"; out=""; started=0 }
    {
      block=$0
      # A thinking block: first line is **...**, separated from the rest of the
      # text by a blank-line gap (>=3 newlines) in the original. RS="" already
      # split on blank-line boundaries; drop leading bold-header paragraphs.
      if (!started && block ~ /^\*\*[^*]+\*\*[ \t]*$/) { next }
      if (!started && block ~ /^\*\*[^*]+\*\*[ \t]*\n/) {
        # header followed immediately by body inside same paragraph: drop it
        next
      }
      started=1
      if (out == "") out=block; else out=out "\n\n" block
    }
    END {
      gsub(/^[ \t\r\n]+/, "", out); gsub(/[ \t\r\n]+$/, "", out)
      printf "%s", out
    }' 2>/dev/null || printf '%s' "$text"
}

build_session_metadata() {
  local frag="" first=1
  add() { [ -z "$2" ] && return 0; [ "$first" -eq 1 ] && first=0 || frag="$frag,"; frag="$frag$(json_str "$1"):$(json_str "$2")"; }
  add session_id "${SI_session_id:-}"
  add transcript_path "${SI_transcript_path:-}"
  add cwd "${SI_cwd:-}"
  add hook_event_name "${SI_hook_event_name:-}"
  add hook_timestamp "${SI_timestamp:-}"
  [ "$first" -eq 1 ] && { printf ''; return; }
  printf '{%s}' "$frag"
}

build_akto_request() {  # USER_PROMPT RESPONSE_TEXT MODEL USAGE_RAW
  local up="$1" rt="$2" model="$3" usage="$4"
  local host="${GEMINI_API_URL#https://}"; host="${host#http://}"
  local tags
  if [ "$AKTO_MODE" = "atlas" ]; then
    tags="{\"gen-ai\":\"Gen AI\",\"ai-agent\":\"geminicli\",\"source\":\"ENDPOINT\"}"
  else
    tags='{"gen-ai":"Gen AI"}'
  fi
  local meta="{\"model\":$(json_str "$model")"
  if [ "$AKTO_MODE" = "atlas" ]; then
    meta="$meta,\"machine_id\":$(json_str "$DEVICE_ID"),\"log_storage\":{\"type\":\"local_file\",\"path\":$(json_str "$AKTO_LOG_DIR")}"
  fi
  local sess; sess="$(build_session_metadata)"
  [ -n "$sess" ] && meta="$meta,\"gemini_cli_session\":$sess"
  meta="$meta}"

  local req_hdr="{\"host\":$(json_str "$host"),\"x-geminicli-hook\":\"AfterModel\",\"content-type\":\"application/json\"}"
  local request_headers; request_headers="$(json_str "$req_hdr")"
  local response_headers; response_headers="$(json_str '{"x-geminicli-hook":"AfterModel","content-type":"application/json"}')"
  local request_payload; request_payload="$(json_str "{\"body\":$(json_str "$up")}")"
  # response body = json string of {"result":..., usageMetadata?}
  local resp_body="{\"result\":$(json_str "$rt")"
  case "$usage" in '{'*) resp_body="$resp_body,\"usageMetadata\":$usage";; esac
  resp_body="$resp_body}"
  local response_payload; response_payload="$(json_str "{\"body\":$(json_str "$resp_body")}")"
  local tag_str; tag_str="$(json_str "$tags")"
  local meta_str; meta_str="$(json_str "$meta")"

  printf '{"path":"/gemini/chat","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":"ENDPOINT"}' \
    "$request_headers" "$response_headers" "$request_payload" "$response_payload" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$(json_str "$DEVICE_ID")" "$tag_str" "$meta_str"
}

# parse_user_prompt INPUT -> last user message text (same as prompt hook)
extract_message_content() {
  local msg="$1" content
  content="$(json_raw "$msg" content 2>/dev/null)" || { printf ''; return; }
  case "$content" in
    '"'*'"') content="${content#\"}"; content="${content%\"}"; _json_unescape "$content";;
    '['*) printf '%s' "$content" | grep -oE '"text"[[:space:]]*:[[:space:]]*"([^"\\]|\\.)*"' \
        | sed -E 's/.*"text"[[:space:]]*:[[:space:]]*"//; s/"$//' | tr -d '\n';;
    *) printf '';;
  esac
}
parse_user_prompt() {
  local input="$1" llm messages
  llm="$(json_raw "$input" llm_request 2>/dev/null)" || { printf ''; return; }
  messages="$(json_raw "$llm" messages 2>/dev/null)" || { printf ''; return; }
  case "$messages" in '['*) ;; *) printf ''; return;; esac
  _AKTO_JSON="$messages"; _AKTO_JSON_LEN=${#messages}
  local i c estart eend elem role last=""
  i="$(_json_skip_ws 1)"
  while [ "$i" -lt "$_AKTO_JSON_LEN" ]; do
    i="$(_json_skip_ws "$i")"
    c="${_AKTO_JSON:$i:1}"
    [ "$c" = ']' ] && break
    [ "$c" = ',' ] && { i=$((i+1)); continue; }
    [ "$c" = '{' ] || { i=$((i+1)); continue; }
    estart="$i"; eend="$(_json_scan_value "$i")"
    elem="${_AKTO_JSON:$estart:$((eend-estart))}"
    role="$(json_string "$elem" role 2>/dev/null)"
    [ "$role" = "user" ] && last="$(extract_message_content "$elem")"
    _AKTO_JSON="$messages"; _AKTO_JSON_LEN=${#messages}
    i="$eend"
  done
  printf '%s' "$last"
}

main() {
  log_info "=== Response hook execution started - Mode: $AKTO_MODE, Sync: $AKTO_SYNC_MODE ==="
  local input; input="$(cat)"

  local hook_event; hook_event="$(json_string "$input" hook_event_name 2>/dev/null)"
  if [ "$hook_event" != "AfterModel" ]; then printf '{}'; exit 0; fi

  local session_id; session_id="$(json_string "$input" session_id 2>/dev/null)"
  [ -z "$session_id" ] && session_id="default"

  SI_session_id="$(json_string "$input" session_id 2>/dev/null)"
  SI_transcript_path="$(json_string "$input" transcript_path 2>/dev/null)"
  SI_cwd="$(json_string "$input" cwd 2>/dev/null)"
  SI_hook_event_name="$hook_event"
  SI_timestamp="$(json_string "$input" timestamp 2>/dev/null)"

  local llm model; llm="$(json_raw "$input" llm_request 2>/dev/null)" || llm=""
  model="$(json_string "$llm" model 2>/dev/null)"
  local user_prompt; user_prompt="$(parse_user_prompt "$input")"

  local llm_response; llm_response="$(json_raw "$input" llm_response 2>/dev/null)" || llm_response="{}"

  local chunk_text; chunk_text="$(extract_chunk_text "$llm_response")"
  [ -n "$chunk_text" ] && append_chunk "$session_id" "$chunk_text"

  # finishReason from candidates[0]
  local candidates first finish_reason=""
  candidates="$(json_raw "$llm_response" candidates 2>/dev/null)" || candidates=""
  case "$candidates" in
    '['*)
      _AKTO_JSON="$candidates"; _AKTO_JSON_LEN=${#candidates}
      local ci cs ce
      ci="$(_json_skip_ws 1)"
      if [ "${_AKTO_JSON:$ci:1}" = '{' ]; then
        cs="$ci"; ce="$(_json_scan_value "$ci")"
        first="${candidates:$cs:$((ce-cs))}"
        finish_reason="$(json_string "$first" finishReason 2>/dev/null)"
      fi;;
  esac

  if [ -n "$user_prompt" ] && [ -n "$finish_reason" ]; then
    local full_response_text; full_response_text="$(read_accumulated_and_clear "$session_id")"
    full_response_text="$(strip_thinking_blocks "$full_response_text")"
    local usage; usage="$(json_raw "$llm_response" usageMetadata 2>/dev/null)" || usage=""

    if [ "$AKTO_SYNC_MODE" = "true" ]; then
      if [ -z "$AKTO_DATA_INGESTION_URL" ]; then
        log_warn "AKTO_DATA_INGESTION_URL not set, allowing response (fail-open)"
        printf '{}'; exit 0
      fi
      if [ -n "$(printf '%s' "$full_response_text" | tr -d '[:space:]')" ]; then
        local body resp
        body="$(build_akto_request "$user_prompt" "$full_response_text" "$model" "$usage")"
        resp="$(post_payload_json "$(build_http_proxy_url 0 1 0)" "$body")" || { log_error "response guardrails failed; fail-open"; printf '{}'; exit 0; }
        parse_guardrails_result "$resp"
        local fp; fp="$(printf '{"p": %s, "r": %s}' "$(json_str "$user_prompt")" "$(json_str "$full_response_text")" | sha256_hex)"
        apply_warn_resubmit_flow "$GR_ALLOWED" "$GR_REASON" "$GR_BEHAVIOUR" "$fp" "$WARN_STATE_PATH"
        if [ "$ALLOWED" = "false" ]; then
          local deny_reason
          if is_warn_behaviour "$GR_BEHAVIOUR"; then
            deny_reason="Warning!! Response blocked, please review it. Reason: ${GR_REASON:-Policy violation}"
          else
            deny_reason="Blocked by Akto Guardrails: ${GR_REASON:-Policy violation}"
          fi
          log_warn "BLOCKING response - behaviour='$GR_BEHAVIOUR', Reason: $GR_REASON"
          printf '{"decision":"deny","reason":%s}' "$(json_str "$deny_reason")"
          exit 0
        fi
      fi
    fi
    # normal ingestion
    if [ -n "$AKTO_DATA_INGESTION_URL" ] && [ -n "$(printf '%s' "$user_prompt" | tr -d '[:space:]')" ]; then
      local rg=0; [ "$AKTO_SYNC_MODE" != "true" ] && rg=1
      local body; body="$(build_akto_request "$user_prompt" "$full_response_text" "$model" "$usage")"
      post_payload_json "$(build_http_proxy_url 0 "$rg" 1)" "$body" >/dev/null 2>&1 || true
    fi
  else
    log_info "No final response to ingest yet (waiting for finishReason/user_prompt)"
  fi

  printf '{}'
  exit 0
}
main
