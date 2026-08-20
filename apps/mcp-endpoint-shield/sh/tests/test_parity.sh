#!/bin/bash
# Parity tests: the PowerShell hook must make the same decisions as the bash hook
# for identical input. Requires pwsh; skips cleanly when it is absent.
# Run: bash sh/tests/test_parity.sh
cd "$(dirname "$0")/.." || exit 1

if ! command -v pwsh >/dev/null 2>&1; then
    echo "pwsh not installed — skipping parity tests"
    exit 0
fi

PORT=${PORT:-19098}
CAPTURE=/tmp/akto_parity_capture.jsonl
VERDICT=/tmp/akto_parity_verdict.json
export LOG_DIR=/tmp/aktosh_parity
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
deny() { printf '{"Allowed": false, "Reason": "%s", "behaviour": "%s"}\n' "${1:-PII detected}" "${2:-block}" >"$VERDICT"; }
allow() { echo '{"Allowed": true}' >"$VERDICT"; }

# Compare bash vs pwsh for one (connector, event, stdin) triple. Both sides get a
# clean state dir so warn-flow bookkeeping cannot leak between them.
cmp_case() { # cmp_case <desc> <connector> <event> <stdin>
    rm -f "$LOG_DIR"/akto_*_warn_pending.json
    local b_out b_rc p_out p_rc
    b_out="$(printf '%s' "$4" | bash akto-hook.sh "$2" "$3" 2>/dev/null)"; b_rc=$?
    rm -f "$LOG_DIR"/akto_*_warn_pending.json
    p_out="$(printf '%s' "$4" | pwsh -NoProfile -File ps/akto-hook.ps1 "$2" "$3" 2>/dev/null)"; p_rc=$?

    # Compare decisions structurally, not byte-for-byte: PowerShell's ConvertTo-Json
    # orders and spaces differently from the hand-built bash JSON.
    local b_norm p_norm
    b_norm="$(printf '%s' "$b_out" | python3 -c 'import json,sys
s=sys.stdin.read().strip()
print(json.dumps(json.loads(s),sort_keys=True) if s.startswith("{") else s)' 2>/dev/null)"
    p_norm="$(printf '%s' "$p_out" | python3 -c 'import json,sys
s=sys.stdin.read().strip()
print(json.dumps(json.loads(s),sort_keys=True) if s.startswith("{") else s)' 2>/dev/null)"

    if [ "$b_norm" = "$p_norm" ] && [ "$b_rc" = "$p_rc" ]; then
        pass=$((pass + 1))
    else
        fail=$((fail + 1))
        printf 'PARITY FAIL %s\n  bash (rc=%s): %s\n  pwsh (rc=%s): %s\n' "$1" "$b_rc" "$b_norm" "$p_rc" "$p_norm"
    fi
}

allow
cmp_case "claude prompt allow"  claude_code_cli UserPromptSubmit '{"prompt":"hello","session_id":"s1"}'
cmp_case "claude tool allow"    claude_code_cli PreToolUse       '{"tool_name":"Bash","tool_input":{"command":"ls"},"session_id":"s1"}'
cmp_case "observe event"        claude_code_cli SessionStart     '{"session_id":"s1"}'

deny "SSN found"
cmp_case "claude prompt deny"   claude_code_cli UserPromptSubmit '{"prompt":"x","session_id":"s1"}'
cmp_case "claude mcp tool deny" claude_code_cli PreToolUse       '{"tool_name":"mcp__github__create_issue","tool_input":{"title":"x"},"session_id":"s1"}'
cmp_case "claude stop deny"     claude_code_cli Stop             '{"last_assistant_message":"leaked","session_id":"s1"}'
cmp_case "codex prompt deny"    codex_cli       UserPromptSubmit '{"prompt":"x","session_id":"s1"}'
cmp_case "cursor prompt deny"   cursor          beforeSubmitPrompt '{"prompt":"x","conversation_id":"c1"}'
cmp_case "cursor mcp deny"      cursor          beforeMCPExecution '{"tool_name":"create_issue","server_name":"github","tool_input":{"t":1},"conversation_id":"c1"}'
cmp_case "github prompt deny"   github          userPromptSubmitted '{"prompt":"x","session_id":"s1"}'
cmp_case "vscode tool deny"     vscode          preToolUse       '{"tool_name":"edit","tool_input":{"f":"a"},"session_id":"s1"}'
cmp_case "gemini prompt deny"   gemini_cli      BeforeAgent      '{"prompt":"x","session_id":"s1"}'
cmp_case "kiro tool deny"       kiro_cli        preToolUse       '{"tool_name":"Bash","tool_input":{"command":"ls"},"session_id":"s1"}'
cmp_case "kiro prompt deny"     kiro_cli        userPromptSubmit '{"prompt":"x","session_id":"s1"}'

deny "policy note" "alert"
cmp_case "alert never blocks"   claude_code_cli UserPromptSubmit '{"prompt":"x","session_id":"s1"}'

deny "maybe secret" "warn"
cmp_case "warn blocks first"    claude_code_cli UserPromptSubmit '{"prompt":"warnme","session_id":"s1"}'

# Tricky content must survive both encoders identically.
allow
cmp_case "quotes in prompt"     claude_code_cli UserPromptSubmit '{"prompt":"say \"hi\" now","session_id":"s1"}'
cmp_case "newlines in prompt"   claude_code_cli UserPromptSubmit '{"prompt":"line1\nline2","session_id":"s1"}'
cmp_case "utf8 in prompt"       claude_code_cli UserPromptSubmit '{"prompt":"café ☕ 日本語","session_id":"s1"}'

echo "----"
echo "bash/pwsh parity: pass=$pass fail=$fail"
[ $fail -eq 0 ]
