#!/usr/bin/env bash

set -euo pipefail

BASE_URL=${BASE_URL:-"http://localhost:9000"}
ENDPOINT=${ENDPOINT:-"/api/insertTestingRunResults"}

post_result() {
  local NAME="$1"; shift
  local RESPONSE
  local HTTP_CODE
  
  echo "\n--- Posting ${NAME} sample ---"
  
  # Capture both response and HTTP status code
  RESPONSE=$(curl -w "\nHTTP_CODE:%{http_code}" -sS -X POST "${BASE_URL}${ENDPOINT}" \
    -H "Content-Type: application/json" \
    -H "Accept: application/json" \
    --data-binary @-)
  
  HTTP_CODE=$(echo "$RESPONSE" | grep "HTTP_CODE:" | cut -d: -f2)
  RESPONSE_BODY=$(echo "$RESPONSE" | sed '/HTTP_CODE:/d')
  
  echo "HTTP Status: ${HTTP_CODE}"
  echo "Response: ${RESPONSE_BODY}"
  
  if [ "$HTTP_CODE" -ne 200 ] && [ "$HTTP_CODE" -ne 201 ]; then
    echo "❌ ERROR: Request failed with status $HTTP_CODE"
    return 1
  else
    echo "✅ SUCCESS: Request completed"
  fi
}

# Fixed payloads with correct field names
payload_429='{
  "message": "{\"response\":{\"statusCode\":429},\"request\":{}}",
  "originalMessage": "{\"statusCode\":429,\"body\":\"rate limit\"}",
  "errors": ["rate limit encountered"],
  "percentageMatch": 0.9,
  "requiresConfig": false,
  "vulnerable": false,
  "confidence": "LOW"
}'

payload_5xx='{
  "message": "{\"response\":{\"statusCode\":500},\"request\":{}}",
  "originalMessage": "{\"statusCode\":500,\"body\":\"server error\"}",
  "errors": ["server error"],
  "percentageMatch": 0.9,
  "requiresConfig": false,
  "vulnerable": false,
  "confidence": "LOW"
}'

payload_cf='{
  "message": "Cloudflare: Error 1020 Ray ID blocked. ddos protection",
  "originalMessage": "Cloudflare WAF under attack mode",
  "errors": ["cloudflare blocked"],
  "percentageMatch": 0.9,
  "requiresConfig": false,
  "vulnerable": false,
  "confidence": "LOW"
}'

payload_clean='{
  "message": "{\"response\":{\"statusCode\":200},\"request\":{}}",
  "originalMessage": "{\"statusCode\":200}",
  "errors": [],
  "percentageMatch": 1.0,
  "requiresConfig": false,
  "vulnerable": false,
  "confidence": "LOW"
}'

render_and_send() {
  local label="$1" payload="$2"
  echo "📤 Sending payload for $label:"
  cat <<EOF | tee /tmp/payload_${label}.json | post_result "$label"
{
  "testingRunResult": {
    "testRunHexId": "66f0e0000000000000000001",
    "testRunResultSummaryHexId": "66f0e0000000000000000002",
    "apiInfoKey": {"apiCollectionId": 1, "url": "/test/api", "method": "GET"},
    "testSuperType": "RUNTIME",
    "testSubType": "DUMMY_TEST",
    "singleTestResults": [
      ${payload}
    ],
    "startTimestamp": $(date +%s),
    "endTimestamp": $(($(date +%s) + 1))
  }
}
EOF
}

main() {
  echo "🚀 Testing API Error Insertion"
  echo "Target: ${BASE_URL}${ENDPOINT}"
  
  render_and_send "CASE_429" "$payload_429"
  render_and_send "CASE_5XX" "$payload_5xx" 
  render_and_send "CLOUDFLARE" "$payload_cf"
  render_and_send "CLEAN" "$payload_clean"
  
  echo "\n📊 Check your database/logs for inserted records"
  echo "💾 Payloads saved to /tmp/payload_*.json for inspection"
}

main "$@"
