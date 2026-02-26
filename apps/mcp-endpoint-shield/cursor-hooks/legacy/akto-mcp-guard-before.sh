#!/bin/bash

# MCP Before Hook - Request Validation via HTTP API
# Calls the mcp-endpoint-shield validation API

# Config
SERVICE_URL="${MCP_SHIELD_URL:-http://localhost:57294}"
TIMEOUT=5
FAIL_OPEN="${MCP_SHIELD_FAIL_OPEN:-true}"
LOG_DIR="${MCP_SHIELD_LOG_DIR:-$HOME/.cursor/mcp-logs}"

# Read input from stdin
INPUT=$(cat)

# Log raw request
mkdir -p "$LOG_DIR"
{
    echo "=== $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
    echo "$INPUT" | jq . 2>/dev/null || echo "$INPUT"
    echo ""
} >> "$LOG_DIR/requests.log"

# Extract MCP server identifier with fallback strategy
# Priority: server > url (extract domain) > command > tool_name prefix > default
SERVER_NAME=$(echo "$INPUT" | jq -r '
  if .server != null and .server != "" then
    .server
  elif .url != null and .url != "" then
    .url | gsub("https?://"; "") | split("/")[0]
  elif .command != null and .command != "" then
    .command
  elif .tool_name != null and (.tool_name | startswith("mcp__")) then
    .tool_name | split("__")[1]
  else
    "cursor-unknown"
  end
')

# Extract ONLY tool_input content for validation (not metadata like user_email)
TOOL_INPUT=$(echo "$INPUT" | jq -r '.tool_input // "{}"')

# Build API request body using jq to ensure proper JSON encoding
# Only send tool_input (the actual MCP tool parameters), not the full metadata
API_REQUEST=$(jq -n \
  --arg payload "$TOOL_INPUT" \
  --arg server "$SERVER_NAME" \
  '{payload: $payload, context: {mcp_server_name: $server}}')

# Call validation API
RESPONSE=$(curl -s -m $TIMEOUT -X POST "$SERVICE_URL/api/validate/request" \
  -H "Content-Type: application/json" \
  -d "$API_REQUEST" 2>/dev/null)

# Check if service responded
if [ $? -ne 0 ] || [ -z "$RESPONSE" ]; then
    # Service unavailable - apply fail-open or fail-closed based on config
    if [ "$FAIL_OPEN" = "true" ]; then
        # Fail-open: allow with warning
        echo '{"permission": "allow"}' >&2
        echo "Warning: MCP shield service unavailable (failing open)" >&2
    else
        # Fail-closed: block with error
        cat <<EOF
{
  "permission": "deny",
  "user_message": "Security service unavailable",
  "agent_message": "MCP shield service is not responding"
}
EOF
    fi
    exit 0
fi

# Extract action from response
ACTION=$(echo "$RESPONSE" | jq -r '.action // "allow"')
REASON=$(echo "$RESPONSE" | jq -r '.reason // "Security policy violation"')
THREAT_TYPE=$(echo "$RESPONSE" | jq -r '.threat_type // "unknown"')

# Map action to Cursor permission format
case "$ACTION" in
    "allow")
        cat <<EOF
{
  "permission": "allow"
}
EOF
        ;;

    "deny"|"block")
        cat <<EOF
{
  "permission": "deny",
  "user_message": "$REASON",
  "agent_message": "Request blocked by security policy: $THREAT_TYPE"
}
EOF
        ;;

    "ask")
        cat <<EOF
{
  "permission": "ask",
  "user_message": "$REASON",
  "agent_message": "User approval required for this operation"
}
EOF
        ;;

    *)
        # Unknown action - default to allow
        cat <<EOF
{
  "permission": "allow"
}
EOF
        ;;
esac
