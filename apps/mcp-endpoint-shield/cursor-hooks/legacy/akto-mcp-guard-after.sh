#!/bin/bash

# MCP After Hook - Response Validation via HTTP API
# Calls the mcp-endpoint-shield validation API for response inspection
# NOTE: afterMCPExecution hooks CANNOT modify or block responses (Cursor limitation)

# Config
SERVICE_URL="${MCP_SHIELD_URL:-http://localhost:57294}"
TIMEOUT=5
INGEST_ENABLED="${MCP_SHIELD_INGEST:-false}"
LOG_DIR="${MCP_SHIELD_LOG_DIR:-$HOME/.cursor/mcp-logs}"

# Read input from stdin
INPUT=$(cat)

# Log raw response
mkdir -p "$LOG_DIR"
{
    echo "=== $(date -u +%Y-%m-%dT%H:%M:%SZ) ==="
    echo "$INPUT" | jq . 2>/dev/null || echo "$INPUT"
    echo ""
} >> "$LOG_DIR/responses.log"

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

# Extract ONLY result content for validation (not metadata like user_email)
# Note: result_json is a JSON-encoded string that needs to be parsed
RESULT=$(echo "$INPUT" | jq -c '.result_json // "{}"')

# Build API request body using jq to ensure proper JSON encoding
# The result_json is already a JSON string, so we pass it directly
API_REQUEST=$(echo "$INPUT" | jq -c \
  --arg server "$SERVER_NAME" \
  '{payload: (.result_json // "{}"), context: {mcp_server_name: $server}}')

# Call validation API (for logging and alerting only)
RESPONSE=$(curl -s -m $TIMEOUT -X POST "$SERVICE_URL/api/validate/response" \
  -H "Content-Type: application/json" \
  -d "$API_REQUEST" 2>/dev/null)

# Check if service responded
if [ $? -ne 0 ] || [ -z "$RESPONSE" ]; then
    # Service unavailable - just log and continue
    echo "Warning: MCP shield service unavailable" >&2
    echo "{}"
    exit 0
fi

# Extract action from response
ACTION=$(echo "$RESPONSE" | jq -r '.action // "allow"')
REASON=$(echo "$RESPONSE" | jq -r '.reason // ""')
THREAT_TYPE=$(echo "$RESPONSE" | jq -r '.threat_type // ""')

# Log violations (cannot block, only alert)
if [ "$ACTION" = "deny" ] || [ "$ACTION" = "block" ]; then
    echo "ALERT: Response violation detected - $REASON (type: $THREAT_TYPE)" >&2
fi

# Optional: Ingest request-response pair
if [ "$INGEST_ENABLED" = "true" ]; then
    # Extract request from input if available
    REQUEST=$(echo "$INPUT" | jq -r '.request // "{}"')

    # Build ingest request body using jq
    INGEST_REQUEST=$(jq -n \
      --arg req "$REQUEST" \
      --arg resp "$INPUT" \
      --arg server "$SERVER_NAME" \
      '{request_payload: $req, response_payload: $resp, mcp_server_name: $server}')

    # Send to ingest API (async, don't wait)
    curl -s -m 2 -X POST "$SERVICE_URL/api/ingest" \
      -H "Content-Type: application/json" \
      -d "$INGEST_REQUEST" \
      >/dev/null 2>&1 &
fi

# After hooks must return empty JSON
echo "{}"
