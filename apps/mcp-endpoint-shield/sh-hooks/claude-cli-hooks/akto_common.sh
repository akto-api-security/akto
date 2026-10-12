#!/bin/bash

_akto_lower() { printf '%s' "$1" | tr '[:upper:]' '[:lower:]'; }

_akto_lower_trim() {
  printf '%s' "$1" | tr '[:upper:]' '[:lower:]' | sed -e 's/^[[:space:]]*//' -e 's/[[:space:]]*$//'
}

_akto_sha256() {
  if command -v shasum >/dev/null 2>&1; then
    shasum -a 256 | awk '{print $1}'
  else
    sha256sum | awk '{print $1}'
  fi
}

_akto_reverse_file() {
  local f="$1"
  if command -v tac >/dev/null 2>&1; then
    tac -- "$f"
  else
    tail -r -- "$f" 2>/dev/null || awk '{a[NR]=$0} END{for(i=NR;i>=1;i--) print a[i]}' "$f"
  fi
}

_akto_log() {
  local level="$1" logfile="$2" msg="$3"
  mkdir -p "$LOG_DIR" 2>/dev/null
  printf '%s - %s - %s\n' "$(date '+%Y-%m-%d %H:%M:%S')" "$level" "$msg" >> "$LOG_DIR/$logfile" 2>/dev/null
  [[ "$level" == "ERROR" ]] && printf '%s\n' "$msg" >&2
}
log_info()  { _akto_log "INFO"    "$1" "$2"; }
log_warn()  { _akto_log "WARNING" "$1" "$2"; }
log_error() { _akto_log "ERROR"   "$1" "$2"; }

akto_check_deps() {
  local logfile="${1:-hook-executions.log}" missing=()
  command -v jq   >/dev/null 2>&1 || missing+=("jq")
  command -v curl >/dev/null 2>&1 || missing+=("curl")
  [[ ${#missing[@]} -eq 0 ]] && return 0
  log_error "$logfile" "Akto hook disabled: missing required command(s): ${missing[*]}. Install with 'brew install ${missing[*]}' (macOS) or your Linux package manager (e.g. 'apt install ${missing[*]}' / 'dnf install ${missing[*]}' / 'apk add ${missing[*]}'). Failing open (allowing) until resolved."
  return 1
}

_AKTO_MACHINE_ID=""
_akto_generate_machine_id() {
  local uuid_val=""
  if command -v ioreg >/dev/null 2>&1; then
    uuid_val=$(ioreg -rd1 -c IOPlatformExpertDevice 2>/dev/null \
      | sed -n 's/.*"IOPlatformUUID" = "\(.*\)".*/\1/p' | head -1)
  fi
  if [[ -n "$uuid_val" ]]; then
    printf '%s' "${uuid_val//-/}" | tr '[:upper:]' '[:lower:]'
    return
  fi
  if [[ -f /etc/machine-id ]]; then
    tr '[:upper:]' '[:lower:]' < /etc/machine-id
    return
  fi
  local mac=""
  if command -v ifconfig >/dev/null 2>&1; then
    mac=$(ifconfig 2>/dev/null | awk '/ether /{print $2; exit}')
  elif command -v ip >/dev/null 2>&1; then
    mac=$(ip link 2>/dev/null | awk '/ether /{print $2; exit}')
  fi
  if [[ -n "$mac" ]]; then
    printf '%s' "${mac//:/}" | tr '[:upper:]' '[:lower:]'
    return
  fi
  printf ''
}

_akto_resolve_device_name_source() {
  local raw=""
  if [[ "$(uname -s)" == "Darwin" ]] && command -v scutil >/dev/null 2>&1; then
    raw=$(scutil --get ComputerName 2>/dev/null)
  fi
  if [[ -z "$raw" ]]; then
    raw=$(hostname 2>/dev/null)
    raw="${raw%.local}"
  fi
  if [[ -z "$raw" ]]; then
    raw=$(_akto_generate_machine_id)
  fi
  if [[ -n "$raw" ]]; then
    printf '%s' "$raw" | tr '[:upper:]' '[:lower:]' | sed -E 's/[^a-zA-Z0-9]/-/g'
  fi
}

get_machine_id() {
  if [[ -z "$_AKTO_MACHINE_ID" ]]; then
    _AKTO_MACHINE_ID="$(_akto_resolve_device_name_source)"
  fi
  printf '%s' "$_AKTO_MACHINE_ID"
}

_akto_claude_account_email() {
  command -v jq >/dev/null 2>&1 || return
  local f="$HOME/.claude.json"
  [[ -f "$f" ]] || return
  jq -r '.oauthAccount.emailAddress // empty' "$f" 2>/dev/null
}

_AKTO_USERNAME=""
get_username() {
  if [[ -n "$_AKTO_USERNAME" ]]; then
    printf '%s' "$_AKTO_USERNAME"
    return
  fi

  local account_email
  account_email="$(_akto_claude_account_email)"
  if [[ -n "$account_email" ]]; then
    _AKTO_USERNAME="$account_email"
    printf '%s' "$_AKTO_USERNAME"
    return
  fi

  local sudo_user="${SUDO_USER:-}"
  if [[ -n "$sudo_user" && "$sudo_user" != "root" ]]; then
    _AKTO_USERNAME="$sudo_user"
    printf '%s' "$_AKTO_USERNAME"
    return
  fi

  local current_user
  current_user=$(id -un 2>/dev/null)
  local is_root=false
  [[ "$current_user" == "root" || "$(id -u 2>/dev/null)" == "0" ]] && is_root=true

  if $is_root; then
    case "$(uname -s)" in
      Darwin)
        local u
        u=$(stat -f %Su /dev/console 2>/dev/null)
        if [[ -n "$u" && "$u" != "root" ]]; then
          _AKTO_USERNAME="$u"; printf '%s' "$_AKTO_USERNAME"; return
        fi
        u=$(scutil 2>/dev/null < /dev/null | awk '/ConsoleUser/{print $3; exit}')
        if [[ -n "$u" && "$u" != "root" && "$u" != "loginwindow" ]]; then
          _AKTO_USERNAME="$u"; printf '%s' "$_AKTO_USERNAME"; return
        fi
        ;;
      Linux)
        local u
        u=$(getent passwd 2>/dev/null | awk -F: '$1!="root" && $6 ~ /^\/home\// {print $1; exit}')
        if [[ -n "$u" ]]; then
          _AKTO_USERNAME="$u"; printf '%s' "$_AKTO_USERNAME"; return
        fi
        ;;
    esac
  fi

  if [[ -n "$current_user" ]]; then
    _AKTO_USERNAME="$current_user"
  else
    _AKTO_USERNAME="unknown"
  fi
  printf '%s' "$_AKTO_USERNAME"
}

akto_post_json() {
  local url="$1" payload="$2" logfile="$3"
  log_info "$logfile" "API CALL: POST $url"
  [[ "${LOG_PAYLOADS:-false}" == "true" ]] && log_info "$logfile" "Request payload: ${payload:0:1000}..."

  local hdrs=(-H "Content-Type: application/json")
  [[ -n "${AKTO_API_TOKEN:-}" ]] && hdrs+=(-H "Authorization: ${AKTO_API_TOKEN}")

  local start end dur out rc status body
  start=$(date +%s)
  out=$(curl -sS -k --max-time "${AKTO_TIMEOUT:-5}" -X POST "${hdrs[@]}" --data-binary "$payload" \
    -w $'\n%{http_code}' "$url" 2>>"$LOG_DIR/$logfile")
  rc=$?
  end=$(date +%s)
  dur=$(( (end - start) * 1000 ))

  if [[ $rc -ne 0 ]]; then
    log_error "$logfile" "API CALL FAILED after ${dur}ms: curl exit $rc"
    return 1
  fi

  status="${out##*$'\n'}"
  body="${out%$'\n'*}"

  if [[ "$status" -lt 200 || "$status" -ge 300 ]]; then
    log_error "$logfile" "API CALL FAILED after ${dur}ms: HTTP $status"
    return 1
  fi

  log_info "$logfile" "API RESPONSE: Status $status, Duration: ${dur}ms, Size: ${#body} bytes"
  [[ "${LOG_PAYLOADS:-false}" == "true" ]] && log_info "$logfile" "Response body: ${body:0:1000}..."
  printf '%s' "$body"
}

SESSION_ID_FIELD="session_id"
CONVERSATION_FIELD=""
MESSAGE_ID_FIELD=""
MESSAGE_ID_STRATEGY="transcript_uuid"
STATE_KEY_FIELD="session_id"
_AKTO_ID_FIELDS_JSON='["session_id","transcript_path","cwd","permission_mode","hook_event_name"]'

_akto_extract_session_info() {
  local input_json="$1"
  jq -c --argjson fields "$_AKTO_ID_FIELDS_JSON" '
    . as $in | reduce $fields[] as $f ({}; if ($in[$f] != null) then . + {($f): $in[$f]} else . end)
  ' <<<"$input_json"
}

_akto_state_key() {
  local input_json="$1" session_info_json="$2" val
  val=$(jq -r --arg f "$STATE_KEY_FIELD" '.[$f] // empty' <<<"$input_json")
  if [[ -z "$val" ]]; then
    val=$(jq -r --arg f "$STATE_KEY_FIELD" '.[$f] // empty' <<<"$session_info_json")
  fi
  if [[ -z "$val" ]]; then
    val=$(jq -r --arg f "$SESSION_ID_FIELD" '.[$f] // empty' <<<"$session_info_json")
  fi
  if [[ -z "$val" && -n "$CONVERSATION_FIELD" ]]; then
    val=$(jq -r --arg f "$CONVERSATION_FIELD" '.[$f] // empty' <<<"$session_info_json")
  fi
  [[ -z "$val" ]] && val="_latest"
  printf '%s' "$val"
}

SESSION_STATE_PATH="${LOG_DIR}/akto_session_state.json"

_akto_load_session_state() {
  local key="$1"
  [[ -f "$SESSION_STATE_PATH" ]] || { echo '{}'; return; }
  jq -c --arg k "$key" '.[$k] // {}' "$SESSION_STATE_PATH" 2>/dev/null || echo '{}'
}

_akto_save_session_state() {
  local key="$1" session_info_json="$2"
  mkdir -p "$(dirname "$SESSION_STATE_PATH")" 2>/dev/null
  local data tmp
  data='{}'
  [[ -f "$SESSION_STATE_PATH" ]] && data=$(cat "$SESSION_STATE_PATH" 2>/dev/null)
  [[ -z "$data" ]] && data='{}'
  tmp="${SESSION_STATE_PATH}.tmp.$$"
  if jq -c --arg k "$key" --argjson info "$session_info_json" '
       (.[$k] // {}) as $row
       | (.[$k] = ($row + ($info | with_entries(select(.value != null)))))
     ' <<<"$data" > "$tmp" 2>/dev/null; then
    mv "$tmp" "$SESSION_STATE_PATH"
  else
    log_error "hook-executions.log" "Could not persist session state"
    rm -f "$tmp"
  fi
}

_akto_get_last_entry_uuid() {
  local path="$1"
  [[ -z "$path" ]] && { printf ''; return; }
  path="${path/#\~/$HOME}"
  [[ -f "$path" ]] || { printf ''; return; }
  local uuid=""
  while IFS= read -r line; do
    uuid=$(jq -r '.uuid // empty' 2>/dev/null <<<"$line")
    [[ -n "$uuid" ]] && break
  done < <(_akto_reverse_file "$path")
  printf '%s' "$uuid"
}

_akto_open_message_turn() {
  local input_json="$1" session_info_json="$2" state_key="$3" row_json="$4"
  local message_id=""

  if [[ "$MESSAGE_ID_STRATEGY" == "passthrough" && -n "$MESSAGE_ID_FIELD" ]]; then
    message_id=$(jq -r --arg f "$MESSAGE_ID_FIELD" '.[$f] // empty' <<<"$input_json")
    [[ -z "$message_id" ]] && message_id=$(jq -r --arg f "$MESSAGE_ID_FIELD" '.[$f] // empty' <<<"$session_info_json")
    if [[ -n "$message_id" ]]; then
      jq -n -c --arg m "$message_id" '{current_message_id: $m}'
      return
    fi
  fi

  if [[ "$MESSAGE_ID_STRATEGY" == "transcript_uuid" ]]; then
    local tpath
    tpath=$(jq -r '.transcript_path // empty' <<<"$input_json")
    message_id=$(_akto_get_last_entry_uuid "$tpath")
    if [[ -n "$message_id" ]]; then
      jq -n -c --arg m "$message_id" '{current_message_id: $m}'
      return
    fi
  fi

  local seq
  seq=$(jq -r '.turn_seq // 0' <<<"$row_json")
  seq=$((seq + 1))
  jq -n -c --arg m "${state_key}:${seq}" --argjson s "$seq" '{turn_seq: $s, current_message_id: $m}'
}

resolve_session_info() {
  local input_json="$1" is_prompt_hook="${2:-false}"
  local session_info state_key row turn_fields merged

  session_info=$(_akto_extract_session_info "$input_json") || { _akto_extract_session_info "$input_json" 2>/dev/null; return; }
  state_key=$(_akto_state_key "$input_json" "$session_info")
  row=$(_akto_load_session_state "$state_key")

  if [[ "$is_prompt_hook" == "true" ]]; then
    turn_fields=$(_akto_open_message_turn "$input_json" "$session_info" "$state_key" "$row")
    session_info=$(jq -n -c --argjson a "$session_info" --argjson b "$turn_fields" '$a + $b')
  fi

  _akto_save_session_state "$state_key" "$session_info"

  merged=$(jq -n -c --argjson a "$row" --argjson b "$session_info" '$a + $b')
  printf '%s' "$merged"
}

_akto_installer_headers() {
  local session_info_json="$1" input_json="${2:-null}"
  jq -n -c \
    --argjson session_info "$session_info_json" \
    --argjson input "$input_json" \
    --arg sidf "$SESSION_ID_FIELD" \
    --arg convf "$CONVERSATION_FIELD" \
    --arg msgf "$MESSAGE_ID_FIELD" \
    --arg username "$(get_username)" '
    def hdrval: if (type=="object" or type=="array") then tojson else tostring end;
    ( $session_info
      | with_entries(select(.key != "turn_seq" and .value != null))
      | to_entries
      | map({key: ("x-akto-installer-" + .key), value: (.value | hdrval)})
      | from_entries
    ) as $base
    | ( if $input != null then
          reduce ([$sidf, $convf, $msgf] | map(select(. != ""))[]) as $k
            ($session_info; if ($input[$k] != null) then . + {($k): $input[$k]} else . end)
        else $session_info end
      ) as $src
    | $base
      + (if $sidf != "" and $src[$sidf] != null then {("x-akto-installer-akto_session_id"): ($src[$sidf] | hdrval)} else {} end)
      + (if $convf != "" and $src[$convf] != null then {("x-akto-installer-akto_conversation_id"): ($src[$convf] | hdrval)} else {} end)
      + ( ( $src.current_message_id // (if $msgf != "" then $src[$msgf] else null end) ) as $mid
          | if $mid != null then {("x-akto-installer-akto_message_id"): ($mid | hdrval)} else {} end
        )
      + (if $username != "" then {("x-akto-installer-user_email"): $username} else {} end)
  '
}

_akto_load_warn_pending() {
  local path="$1"
  [[ -f "$path" ]] || { echo '[]'; return; }
  jq -c '.warn_pending // []' "$path" 2>/dev/null || echo '[]'
}

_akto_save_warn_pending() {
  local path="$1" arr_json="$2" tmp
  tmp="${path}.tmp.$$"
  if jq -n -c --argjson w "$arr_json" '{warn_pending: ($w | unique | sort)}' > "$tmp" 2>/dev/null; then
    mv "$tmp" "$path"
  else
    rm -f "$tmp"
  fi
}

apply_warn_resubmit_flow() {
  local gr_allowed="$1" behaviour="$2" fingerprint="$3" warn_state_path="$4" logfile="$5"
  if [[ "$gr_allowed" == "true" ]]; then
    echo "true"; return
  fi

  local b_lower
  b_lower=$(_akto_lower_trim "$behaviour")
  if [[ "$b_lower" == "alert" ]]; then
    log_info "$logfile" "Alert behaviour: allowing despite violation (server-side alert only)"
    echo "true"; return
  fi
  if [[ "$b_lower" != "warn" ]]; then
    echo "false"; return
  fi

  local pending contains
  pending=$(_akto_load_warn_pending "$warn_state_path")
  contains=$(jq -r --arg f "$fingerprint" 'index($f) != null' <<<"$pending")
  if [[ "$contains" == "true" ]]; then
    pending=$(jq -c --arg f "$fingerprint" 'map(select(. != $f))' <<<"$pending")
    _akto_save_warn_pending "$warn_state_path" "$pending"
    log_info "$logfile" "Warn flow: allowing resubmit; removed fingerprint from map"
    echo "true"; return
  fi

  pending=$(jq -c --arg f "$fingerprint" '. + [$f] | unique' <<<"$pending")
  _akto_save_warn_pending "$warn_state_path" "$pending"
  echo "false"
}

_akto_extract_claude_text() {
  jq -r '
    .message.content as $c
    | if ($c|type)=="string" then ($c | gsub("^\\s+|\\s+$";""))
      elif ($c|type)=="array" then
        (([$c[] | select(type=="object" and .type=="text") | .text // ""]) | join("")) | gsub("^\\s+|\\s+$";"")
      else "" end
  ' 2>/dev/null
}

get_last_user_prompt() {
  local path="$1"
  [[ -n "$path" && -f "$path" ]] || { printf ''; return; }
  local last=""
  while IFS= read -r line; do
    [[ -z "$line" ]] && continue
    local etype text
    etype=$(jq -r '.type // ""' 2>/dev/null <<<"$line") || continue
    if [[ "$etype" == "user" ]]; then
      text=$(_akto_extract_claude_text <<<"$line")
      [[ -n "$text" ]] && last="$text"
    fi
  done < "$path"
  printf '%s' "$last"
}
