#!/usr/bin/env bash
# akto-hooks.sh - bash port of akto-hooks.py + run_observability_hook()
# Usage: akto-hooks.sh <HookName>
# Fire-and-forget: read stdin, ingest as observability event, print "{}".
#
# Disabled by default in hooks.json (GitHub Copilot does not yet fire these
# events) but shipped so they can be re-enabled. Connector comes from
# AKTO_CONNECTOR (default vscode), NOT auto-detected, matching the python path
# where akto-hooks.py runs run_observability_hook with the env connector.
set -u
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$SCRIPT_DIR/akto_common.sh"

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

CONNECTOR="${AKTO_CONNECTOR:-vscode}"
TAG_NAME="$(connector_tag "$CONNECTOR")"
HOOK_HEADER="x-${TAG_NAME}-hook"
DEVICE_ID="${DEVICE_ID:-$(get_machine_id)}"

# LOG_DIR default matches akto-hooks.py: ~/akto/.github/akto/vscode/logs
AKTO_LOG_DIR="${LOG_DIR:-$HOME/akto/.github/akto/vscode/logs}"
case "$AKTO_LOG_DIR" in "~"/*) AKTO_LOG_DIR="$HOME/${AKTO_LOG_DIR#"~/"}";; esac
mkdir -p "$AKTO_LOG_DIR" 2>/dev/null || true
AKTO_LOG_FILE="$AKTO_LOG_DIR/hook-executions.log"

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
  # requestPayload = {"body": <input object>} ; responsePayload = {"body": {}}
  local request_payload; request_payload="$(json_str "{\"body\":$input}")"
  local response_payload; response_payload="$(json_str '{"body":{}}')"
  local tag_str; tag_str="$(json_str "$tags")"

  local body
  body="$(printf '{"path":"/v1/hooks/%s","requestHeaders":%s,"responseHeaders":%s,"method":"POST","requestPayload":%s,"responsePayload":%s,"ip":%s,"destIp":"127.0.0.1","time":"%s","statusCode":"200","type":"HTTP/1.1","status":"200","akto_account_id":"1000000","akto_vxlan_id":%s,"is_pending":"false","source":"MIRRORING","direction":null,"process_id":null,"socket_id":null,"daemonset_id":null,"enabled_graph":null,"tag":%s,"metadata":%s,"contextSource":%s}' \
    "$HOOK_NAME" "$(json_str "$req_hdr")" "$(json_str "$resp_hdr")" "$request_payload" "$response_payload" \
    "$(json_str "$(get_username)")" "$(date +%s)000" "$(json_str "$DEVICE_ID")" "$tag_str" "$tag_str" "$(json_str "$CONTEXT_SOURCE")")"

  local url="${AKTO_DATA_INGESTION_URL}/api/http-proxy?akto_connector=${CONNECTOR}&ingest_data=true&client_hook=${HOOK_NAME}"
  post_payload_json "$url" "$body" >/dev/null 2>&1 || true
  log_info "=== $HOOK_NAME hook completed ==="
  printf '{}\n'
  exit 0
}
main
