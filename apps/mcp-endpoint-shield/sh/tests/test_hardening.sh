#!/bin/bash
# Hardening tests: hostile content, scale, and state-file correctness.
# Run: bash sh/tests/test_hardening.sh
cd "$(dirname "$0")/.." || exit 1

PORT=${PORT:-19097}
CAPTURE=/tmp/akto_hard_capture.jsonl
VERDICT=/tmp/akto_hard_verdict.json
export LOG_DIR=/tmp/aktosh_hard
export AKTO_DATA_INGESTION_URL="http://127.0.0.1:$PORT"
export MODE=atlas
export DEVICE_ID=testbox-abcd1234
export AKTO_SYNC_MODE=true
export AKTO_TIMEOUT=10

rm -rf "$LOG_DIR"; mkdir -p "$LOG_DIR"
echo '{"Allowed": true}' >"$VERDICT"
: >"$CAPTURE"

CAPTURE=$CAPTURE VERDICT=$VERDICT python3 tests/mock_server.py "$PORT" &
SERVER=$!
trap 'kill $SERVER 2>/dev/null; rm -f /tmp/akto_canary' EXIT
for _ in $(seq 1 40); do
    curl -s -o /dev/null "http://127.0.0.1:$PORT" -X POST -d '{}' && break
    sleep 0.1
done

pass=0; fail=0
t()  { if [ "$2" = "$3" ]; then pass=$((pass+1)); else fail=$((fail+1)); printf 'FAIL %s\n  want: [%s]\n  got : [%s]\n' "$1" "$2" "$3"; fi; }
tc() { case "$3" in *"$2"*) pass=$((pass+1));; *) fail=$((fail+1)); printf 'FAIL %s\n  want substring: [%s]\n  got: [%s]\n' "$1" "$2" "$3";; esac; }
allow() { echo '{"Allowed": true}' >"$VERDICT"; }
deny()  { printf '{"Allowed": false, "Reason": "%s", "behaviour": "%s"}\n' "${1:-PII}" "${2:-block}" >"$VERDICT"; }

# The payload the mock last received, with its double-encoded requestPayload
# decoded back to a real object so assertions can look inside it.
last_req() {
    tail -1 "$CAPTURE" | python3 -c '
import json,sys
outer = json.loads(json.load(sys.stdin)["body"])
print(json.dumps(json.loads(outer["requestPayload"]), ensure_ascii=False))'
}

# ── Content that must never be executed or mangled ────────────────────────────
allow
rm -f /tmp/akto_canary

# Command substitution and backticks inside a prompt must reach the wire as text.
printf '%s' '{"prompt":"$(touch /tmp/akto_canary) `touch /tmp/akto_canary` ${HOME}","session_id":"s1"}' |
    bash akto-hook.sh claude_code_cli UserPromptSubmit >/dev/null 2>&1
t  "no command substitution executed" "absent" "$([ -e /tmp/akto_canary ] && echo present || echo absent)"
tc "substitution text preserved" '$(touch /tmp/akto_canary)' "$(last_req)"
tc "backtick text preserved"     '`touch /tmp/akto_canary`' "$(last_req)"
tc "shell var text preserved"    '${HOME}'                  "$(last_req)"

# Quotes, backslashes and newlines must survive the double-encoding intact.
printf '%s' '{"prompt":"he said \"hi\"\nback\\\\slash\ttab","session_id":"s1"}' |
    bash akto-hook.sh claude_code_cli UserPromptSubmit >/dev/null 2>&1
t "quotes/backslash/newline round-trip" \
  '{"body": "he said \"hi\"\nback\\\\slash\ttab"}' "$(last_req)"

# Non-ASCII must arrive byte-identical.
printf '%s' '{"prompt":"café ☕ 日本語 — em-dash","session_id":"s1"}' |
    bash akto-hook.sh claude_code_cli UserPromptSubmit >/dev/null 2>&1
t "utf8 round-trip" '{"body": "café ☕ 日本語 — em-dash"}' "$(last_req)"

# JSON that looks like structure but is string content.
printf '%s' '{"prompt":"{\"not\":\"an object\"}","session_id":"s1"}' |
    bash akto-hook.sh claude_code_cli UserPromptSubmit >/dev/null 2>&1
t "json-in-string stays a string" '{"body": "{\"not\":\"an object\"}"}' "$(last_req)"

# ── Scale ─────────────────────────────────────────────────────────────────────
BIG="$(python3 -c 'print("A"*200000)')"
printf '{"prompt":"%s","session_id":"s1"}' "$BIG" |
    bash akto-hook.sh claude_code_cli UserPromptSubmit >/dev/null 2>&1
t "200KB prompt survives" "200000" \
  "$(last_req | python3 -c 'import json,sys; print(len(json.load(sys.stdin)["body"]))')"

DEEP="$(python3 -c 'print(json.dumps({"a":{"b":{"c":{"d":{"e":{"f":{"g":1}}}}}}}) if False else "")' 2>/dev/null)"
printf '%s' '{"tool_name":"X","tool_input":{"a":{"b":{"c":{"d":{"e":{"f":{"g":1}}}}}}},"session_id":"s1"}' |
    bash akto-hook.sh claude_code_cli PreToolUse >/dev/null 2>&1
tc "deep nesting preserved" '"g": 1' "$(last_req)"

# ── Warn state file with several fingerprints ─────────────────────────────────
rm -f "$LOG_DIR"/akto_prompt_warn_pending.json
deny "maybe" "warn"
for p in one two three; do
    printf '{"prompt":"%s","session_id":"s1"}' "$p" | bash akto-hook.sh claude_code_cli UserPromptSubmit >/dev/null
done
t "three fingerprints parked" "3" \
  "$(python3 -c 'import json;print(len(json.load(open("'"$LOG_DIR"'/akto_prompt_warn_pending.json"))["warn_pending"]))' 2>/dev/null)"

# Re-sending the middle one must clear only that entry.
OUT=$(printf '{"prompt":"two","session_id":"s1"}' | bash akto-hook.sh claude_code_cli UserPromptSubmit)
t "middle resubmit allowed" "" "$OUT"
t "two fingerprints remain" "2" \
  "$(python3 -c 'import json;print(len(json.load(open("'"$LOG_DIR"'/akto_prompt_warn_pending.json"))["warn_pending"]))' 2>/dev/null)"
t "state file still valid json" "ok" \
  "$(python3 -c 'import json;json.load(open("'"$LOG_DIR"'/akto_prompt_warn_pending.json"));print("ok")' 2>/dev/null)"

# Re-sending a parked fingerprint is allowed by design (that is what warn means),
# so what must still block is a prompt this flow has never seen.
OUT=$(printf '{"prompt":"never-seen-before","session_id":"s1"}' | bash akto-hook.sh claude_code_cli UserPromptSubmit)
tc "unseen prompt still blocks" '"decision":"block"' "$OUT"
OUT=$(printf '{"prompt":"one","session_id":"s1"}' | bash akto-hook.sh claude_code_cli UserPromptSubmit)
t  "parked prompt allowed on resubmit" "" "$OUT"

# ── Concurrency: parallel hooks must not corrupt shared state ─────────────────
rm -f "$LOG_DIR"/akto_prompt_warn_pending.json
pids=""
for i in 1 2 3 4 5 6 7 8; do
    printf '{"prompt":"c%s","session_id":"s1"}' "$i" | bash akto-hook.sh claude_code_cli UserPromptSubmit >/dev/null &
    pids="$pids $!"
done
# Wait only on the hook PIDs — a bare `wait` would also block on the mock server,
# which is backgrounded and never exits.
for pid in $pids; do wait "$pid"; done
t "state valid after 8 parallel writers" "ok" \
  "$(python3 -c 'import json;json.load(open("'"$LOG_DIR"'/akto_prompt_warn_pending.json"));print("ok")' 2>/dev/null)"

# ── Missing / odd fields must not crash ───────────────────────────────────────
allow
for payload in '{}' '{"prompt":null}' '{"prompt":123}' '{"tool_name":null,"tool_input":null}' '[1,2,3]' '"just a string"'; do
    printf '%s' "$payload" | bash akto-hook.sh claude_code_cli PreToolUse >/dev/null 2>&1
    rc=$?
    [ "$rc" = "0" ] || { fail=$((fail+1)); printf 'FAIL odd payload %s -> rc=%s\n' "$payload" "$rc"; }
done
pass=$((pass+1))

# Tool name with characters that must not escape the mirrored URL path.
printf '%s' '{"tool_name":"we ird/../tool!","tool_input":{},"session_id":"s1"}' |
    bash akto-hook.sh claude_code_cli PreToolUse >/dev/null 2>&1
t "tool name sanitised in path" '"/tool/we-ird-..-tool"' \
  "$(tail -1 "$CAPTURE" | python3 -c 'import json,sys; print(json.dumps(json.loads(json.load(sys.stdin)["body"])["path"]))')"

# Path normalisation must match normalize_tool_name_for_url_path() in the Python
# hooks exactly, edge cases included.
. lib/akto_core.sh >/dev/null 2>&1; . lib/akto_adapters.sh
t "path norm: spaces"      "/tool/a-b"            "$(adapter_non_mcp_path 'a  b')"
t "path norm: dots kept"   "/tool/we-ird-..-tool" "$(adapter_non_mcp_path 'we ird/../tool!')"
t "path norm: plain"       "/tool/Bash"           "$(adapter_non_mcp_path 'Bash')"
t "path norm: underscores" "/tool/mcp__x"         "$(adapter_non_mcp_path 'mcp__x')"
t "path norm: all junk"    "/tool/unknown"        "$(adapter_non_mcp_path '!!!')"
t "path norm: empty"       "/tool/unknown"        "$(adapter_non_mcp_path '')"

# Regression: a JSON literal written inline inside a nested "$( ... )" is subject
# to brace expansion once the outer quotes strip its \" escapes, so {"a":1,"b":2}
# splits on the comma into two arguments. Every JSON literal must be hoisted into a
# variable first. This asserts the observe path (whose payload always has commas)
# and the 403 block record both survive intact.
: >"$CAPTURE"
printf '%s' '{"session_id":"s1","cwd":"/x","a":1,"b":2,"c":3}' | bash akto-hook.sh claude_code_cli SessionStart >/dev/null
t "observe payload not brace-split" '{"body":{"session_id":"s1","cwd":"/x","a":1,"b":2,"c":3}}' \
  "$(tail -1 "$CAPTURE" | python3 -c 'import json,sys; print(json.loads(json.load(sys.stdin)["body"])["requestPayload"])')"

deny "bad"
: >"$CAPTURE"
printf '%s' '{"tool_name":"mcp__gh__x","tool_input":{},"session_id":"s1"}' | bash akto-hook.sh claude_code_cli PreToolUse >/dev/null
t "block record reaches the backend" "2" "$(wc -l <"$CAPTURE" | tr -d ' ')"
t "block record headers intact" '{"x-claudecli-hook":"PreToolUse","x-blocked-by":"Akto Proxy"}' \
  "$(tail -1 "$CAPTURE" | python3 -c 'import json,sys; print(json.loads(json.load(sys.stdin)["body"])["responseHeaders"])')"
allow

echo "----"
echo "hardening: pass=$pass fail=$fail"
[ $fail -eq 0 ]
