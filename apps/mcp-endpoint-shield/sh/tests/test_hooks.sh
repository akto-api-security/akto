#!/bin/bash
# Integration tests for akto-hook.sh against a mock ingestion endpoint.
# Run: bash sh/tests/test_hooks.sh
cd "$(dirname "$0")/.." || exit 1

PORT=${PORT:-19099}
CAPTURE=/tmp/akto_capture.jsonl
VERDICT=/tmp/akto_verdict.json
export LOG_DIR=/tmp/aktosh_test
export AKTO_DATA_INGESTION_URL="http://127.0.0.1:$PORT"
export MODE=atlas
export DEVICE_ID=testbox-abcd1234
export AKTO_SYNC_MODE=true
export AKTO_TIMEOUT=5

rm -rf "$LOG_DIR"; mkdir -p "$LOG_DIR"
echo '{"Allowed": true}' >"$VERDICT"
: >"$CAPTURE"

CAPTURE=$CAPTURE VERDICT=$VERDICT python3 tests/mock_server.py "$PORT" &
SERVER=$!
trap 'kill $SERVER 2>/dev/null' EXIT
for _ in $(seq 1 40); do
    curl -s -o /dev/null "http://127.0.0.1:$PORT" -X POST -d '{}' && break
    sleep 0.1
done

pass=0; fail=0
t() {
    if [ "$2" = "$3" ]; then pass=$((pass + 1)); else
        fail=$((fail + 1)); printf 'FAIL %s\n  want: [%s]\n  got : [%s]\n' "$1" "$2" "$3"
    fi
}
tc() { # contains
    case "$3" in *"$2"*) pass=$((pass + 1)) ;;
    *) fail=$((fail + 1)); printf 'FAIL %s\n  want substring: [%s]\n  got: [%s]\n' "$1" "$2" "$3" ;;
    esac
}
allow() { echo '{"Allowed": true}' >"$VERDICT"; }
deny()  { printf '{"Allowed": false, "Reason": "%s", "behaviour": "%s"}\n' "${1:-PII detected}" "${2:-block}" >"$VERDICT"; }
last_body() { tail -1 "$CAPTURE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["body"])'; }
last_url()  { tail -1 "$CAPTURE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["url"])'; }
# ── allow path ────────────────────────────────────────────────────────────────
allow
OUT=$(printf '{"prompt":"hello","session_id":"s1"}' | bash akto-hook.sh claude_code_cli UserPromptSubmit); RC=$?
t "claude prompt allow: exit"   "0"  "$RC"
t "claude prompt allow: stdout" ""   "$OUT"
tc "prompt hits guardrails url" "guardrails=true" "$(last_url)"
tc "prompt path is /v1/messages" '"path":"/v1/messages"' "$(last_body)"

# ── deny path, per connector ──────────────────────────────────────────────────
deny "SSN found"
OUT=$(printf '{"prompt":"x","session_id":"s1"}' | bash akto-hook.sh claude_code_cli UserPromptSubmit)
tc "claude prompt deny json" '"decision":"block"' "$OUT"
tc "claude prompt deny reason" 'Prompt blocked: SSN found' "$OUT"

OUT=$(printf '{"tool_name":"mcp__github__create_issue","tool_input":{"title":"x"},"session_id":"s1"}' | bash akto-hook.sh claude_code_cli PreToolUse)
tc "claude tool deny shape" '"permissionDecision":"deny"' "$OUT"
tc "claude tool deny event" '"hookEventName":"PreToolUse"' "$OUT"
tc "mcp path used" '"path":"/mcp"' "$(last_body)"
tc "mcp jsonrpc built" 'tools/call' "$(last_body)"

OUT=$(printf '{"tool_name":"Bash","tool_input":{"command":"ls"},"session_id":"s1"}' | bash akto-hook.sh claude_code_cli PreToolUse)
tc "non-mcp path" '"path":"/tool/Bash"' "$(last_body)"

OUT=$(printf '{"prompt":"x","conversation_id":"c1","generation_id":"g1"}' | bash akto-hook.sh cursor beforeSubmitPrompt)
tc "cursor deny shape" '"permission":"deny"' "$OUT"
tc "cursor agent_message" 'Blocked by Akto Guardrails' "$OUT"

OUT=$(printf '{"prompt":"x","session_id":"s1"}' | bash akto-hook.sh github userPromptSubmitted)
tc "github deny shape" '"permissionDecision":"deny"' "$OUT"

OUT=$(printf '{"prompt":"x","session_id":"s1"}' | bash akto-hook.sh gemini_cli BeforeAgent)
tc "gemini deny shape" '"decision":"block"' "$OUT"

printf '{"tool_name":"Bash","tool_input":{"command":"ls"},"session_id":"s1"}' | bash akto-hook.sh kiro_cli preToolUse >/tmp/o 2>/tmp/e; RC=$?
t  "kiro tool deny exit 2" "2" "$RC"
tc "kiro tool deny stderr" "Blocked by Akto Guardrails" "$(cat /tmp/e)"

printf '{"prompt":"x","session_id":"s1"}' | bash akto-hook.sh kiro_cli userPromptSubmit >/tmp/o 2>/tmp/e; RC=$?
t  "kiro prompt cannot block: exit 0" "0" "$RC"
tc "kiro prompt injects context" "[AKTO GUARDRAILS]" "$(cat /tmp/o)"

# ── warn behaviour: block once, allow the identical retry ─────────────────────
rm -f "$LOG_DIR"/akto_prompt_warn_pending.json
deny "maybe secret" "warn"
OUT1=$(printf '{"prompt":"warnme","session_id":"s1"}' | bash akto-hook.sh claude_code_cli UserPromptSubmit)
OUT2=$(printf '{"prompt":"warnme","session_id":"s1"}' | bash akto-hook.sh claude_code_cli UserPromptSubmit)
tc "warn blocks first attempt" '"decision":"block"' "$OUT1"
tc "warn message wording" 'Send again to bypass' "$OUT1"
t  "warn allows resubmit" "" "$OUT2"

# ── alert behaviour: never blocks ─────────────────────────────────────────────
deny "policy note" "alert"
OUT=$(printf '{"prompt":"alertme","session_id":"s1"}' | bash akto-hook.sh claude_code_cli UserPromptSubmit)
t "alert never blocks" "" "$OUT"

# ── fail-open guarantees ──────────────────────────────────────────────────────
kill $SERVER 2>/dev/null; wait $SERVER 2>/dev/null
OUT=$(printf '{"prompt":"x","session_id":"s1"}' | bash akto-hook.sh claude_code_cli UserPromptSubmit); RC=$?
t "backend down: exit 0" "0" "$RC"
t "backend down: allows" "" "$OUT"

OUT=$(printf 'not json at all' | bash akto-hook.sh claude_code_cli UserPromptSubmit 2>/dev/null); RC=$?
t "bad stdin: exit 0"  "0" "$RC"
t "bad stdin: allows"  ""  "$OUT"

OUT=$(printf '' | bash akto-hook.sh claude_code_cli UserPromptSubmit 2>/dev/null); RC=$?
t "empty stdin: exit 0" "0" "$RC"

# ── ENABLE_* kill switch ──────────────────────────────────────────────────────
CAPTURE=$CAPTURE VERDICT=$VERDICT python3 tests/mock_server.py "$PORT" &
SERVER=$!
for _ in $(seq 1 40); do curl -s -o /dev/null "http://127.0.0.1:$PORT" -X POST -d '{}' && break; sleep 0.1; done
deny "SSN found"
OUT=$(ENABLE_PROMPT_HOOKS_CLAUDE=false printf '%s' '{"prompt":"x","session_id":"s1"}' | ENABLE_PROMPT_HOOKS_CLAUDE=false bash akto-hook.sh claude_code_cli UserPromptSubmit); RC=$?
t "prompt kill switch: exit 0" "0" "$RC"
t "prompt kill switch: no block" "" "$OUT"
OUT=$(ENABLE_MCP_HOOKS_CLAUDE=false bash -c 'printf %s "{\"tool_name\":\"Bash\",\"tool_input\":{},\"session_id\":\"s1\"}" | bash akto-hook.sh claude_code_cli PreToolUse')
t "tool kill switch: no block" "" "$OUT"
OUT=$(printf '%s' '{"prompt":"x","session_id":"s1"}' | bash akto-hook.sh claude_code_cli UserPromptSubmit)
tc "still blocks when flag unset" '"decision":"block"' "$OUT"
kill $SERVER 2>/dev/null; wait $SERVER 2>/dev/null

# ── config file supplies defaults, real env wins ──────────────────────────────
CFG=/tmp/akto_hooks_test.env
printf 'AKTO_CONNECTOR_VALUE=fromfile\nLOG_PAYLOADS=false\n' >"$CFG"
V=$(AKTO_CONFIG_FILE=$CFG bash -c '. lib/akto_core.sh; printf %s "$AKTO_CONNECTOR_VALUE"')
t "config file default applied" "fromfile" "$V"
V=$(AKTO_CONFIG_FILE=$CFG AKTO_CONNECTOR_VALUE=fromenv bash -c '. lib/akto_core.sh; printf %s "$AKTO_CONNECTOR_VALUE"')
t "real env overrides file" "fromenv" "$V"

echo "----"
echo "akto-hook.sh: pass=$pass fail=$fail"
[ $fail -eq 0 ]
