#!/usr/bin/env bash
# akto-hooks.sh - bash port of akto-hooks.py + run_observability_hook() (cursor)
# Usage: akto-hooks.sh <HookName>
# Fire-and-forget: read stdin, ingest as observability event, print "{}".
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
: "${LOG_DIR:=$HOME/.cursor/akto/chat-logs}"
export LOG_DIR
. "$SCRIPT_DIR/akto_common.sh"

AKTO_LOG_FILE="$AKTO_LOG_DIR/hook-executions.log"
HOOK_NAME="${1:-}"
[ -z "$HOOK_NAME" ] && { echo "Usage: akto-hooks.sh <hookName>" >&2; exit 1; }

# connector -> short tag (mirrors _CONNECTOR_TAG in akto_ingestion_utility.py)
connector_tag() {
  case "$1" in
    claude_code_cli) echo claudecli;; cursor) echo cursor;; vscode) echo vscode;;
    gemini_cli) echo geminicli;; github) echo github;; codex_cli) echo codexcli;;
    *) echo "$1";;
  esac
}
TAG_NAME="$(connector_tag "$AKTO_CONNECTOR")"
HOOK_HEADER="x-${TAG_NAME}-hook"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"
if [ "$AKTO_MODE" = "atlas" ]; then
  AI_AGENT_API_URL="${DEVICE_ID:+https://${DEVICE_ID}.ai-agent.${TAG_NAME}}"
  [ -z "$AI_AGENT_API_URL" ] && AI_AGENT_API_URL="${AKTO_API_URL:-}"
else
  AI_AGENT_API_URL="${AKTO_API_URL:-}"
fi

main() {
  log_info "=== $HOOK_NAME hook started ==="
  local input; input="$(cat)"
  [ -z "$input" ] && input="{}"

  if [ -z "$AKTO_DATA_INGESTION_URL" ]; then
    log_info "AKTO_DATA_INGESTION_URL not set, skipping ingestion"
    printf '{}\n'; exit 0
  fi

  local host="${AI_AGENT_API_URL#https://}"; host="${host#http://}"
  local tags
  if [ "$AKTO_MODE" = "atlas" ]; then
    tags="{\"gen-ai\":\"Gen AI\",\"hook\":$(json_str "$HOOK_NAME"),\"ai-agent\":$(json_str "$TAG_NAME"),\"source\":$(json_str "$CONTEXT_SOURCE")}"
  else
    tags="{\"gen-ai\":\"Gen AI\",\"hook\":$(json_str "$HOOK_NAME")}"
  fi
  local req_hdr="{\"host\":$(json_str "$host"),$(json_str "$HOOK_HEADER"):$(json_str "$HOOK_NAME"),\"content-type\":\"application/json\"}"
  local resp_hdr="{$(json_str "$HOOK_HEADER"):$(json_str "$HOOK_NAME"),\"content-type\":\"application/json\"}"
  local request_payload; request_payload="$(json_str "{\"body\":$input}")"
  local response_payload; response_payload="$(json_str '{"body":{}}')"
  local tag_str; tag_str="$(json_str "$tags")"

  local body
  body="$(printf '{"path":"/v1/hooks/%s","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$HOOK_NAME" "$(json_str "$req_hdr")" "$(json_str "$resp_hdr")" "$request_payload" "$response_payload" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$(json_str "$DEVICE_ID")" "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")")"

  # observability ingest url includes client_hook=<hook>
  local url="${AKTO_DATA_INGESTION_URL}/api/http-proxy?akto_connector=${AKTO_CONNECTOR}&ingest_data=true&client_hook=${HOOK_NAME}"
  post_payload_json "$url" "$body" >/dev/null 2>&1 || true
  log_info "=== $HOOK_NAME hook completed ==="
  printf '{}\n'
  exit 0
}
main
