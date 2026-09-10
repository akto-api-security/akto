#!/bin/bash
# Unit tests for lib/setkey.awk. Run: bash sh/tests/test_setkey.sh
cd "$(dirname "$0")/.." || exit 1
A=lib/setkey.awk
V=/tmp/akto_setkey_val.json
pass=0; fail=0

t() { if [ "$2" = "$3" ]; then pass=$((pass+1)); else fail=$((fail+1)); printf 'FAIL %s\n  want: [%s]\n  got : [%s]\n' "$1" "$2" "$3"; fi; }
set_hooks() { printf '%s' "$1" | awk -v key=hooks -v valfile="$V" -f $A; }
# Compare structurally so key order is not asserted.
norm() { python3 -c 'import json,sys; print(json.dumps(json.load(sys.stdin),sort_keys=True,ensure_ascii=False))' 2>/dev/null; }

echo '{"PreToolUse":[1]}' >"$V"

t "replaces existing key" \
  '{"hooks": {"PreToolUse": [1]}, "model": "x", "theme": "dark"}' \
  "$(set_hooks '{"theme":"dark","hooks":{"old":1},"model":"x"}' | norm)"

t "appends when absent" \
  '{"hooks": {"PreToolUse": [1]}, "model": "x", "theme": "dark"}' \
  "$(set_hooks '{"theme":"dark","model":"x"}' | norm)"

t "empty object" '{"hooks": {"PreToolUse": [1]}}' "$(set_hooks '{}' | norm)"
t "empty file"   '{"hooks": {"PreToolUse": [1]}}' "$(printf '' | awk -v key=hooks -v valfile="$V" -f $A | norm)"

t "preserves nested values and commas in strings" \
  '{"a": {"nested": {"deep": [1, 2]}}, "b": "keep, me", "hooks": {"PreToolUse": [1]}}' \
  "$(set_hooks '{"a":{"nested":{"deep":[1,2]}},"b":"keep, me"}' | norm)"

t "preserves a key whose value contains braces" \
  '{"hooks": {"PreToolUse": [1]}, "note": "{\"looks\":\"like json\"}"}' \
  "$(set_hooks '{"note":"{\"looks\":\"like json\"}"}' | norm)"

t "preserves unicode and escapes" \
  '{"hooks": {"PreToolUse": [1]}, "msg": "café \"q\"\n"}' \
  "$(set_hooks '{"msg":"café \"q\"\n"}' | norm)"

t "pretty-printed input" \
  '{"hooks": {"PreToolUse": [1]}, "theme": "dark"}' \
  "$(set_hooks '{
     "theme" : "dark",
     "hooks" : { "old" : 1 }
   }' | norm)"

# Refuses to emit anything for input it cannot parse, so the caller keeps the
# original file rather than writing a truncated one.
printf '%s' '{"broken": ' | awk -v key=hooks -v valfile="$V" -f $A >/tmp/akto_sk_out 2>/dev/null
t "malformed input exits nonzero" "1" "$?"

# Content that is not an object at all must be refused, not replaced.
printf '%s' 'this is not json' | awk -v key=hooks -v valfile="$V" -f $A >/tmp/akto_sk_out2 2>/dev/null
t "non-object input exits nonzero" "1" "$?"
t "non-object input emits nothing" "0" "$(wc -c </tmp/akto_sk_out2 | tr -d ' ')"
t "malformed input emits nothing" "0" "$(wc -c </tmp/akto_sk_out | tr -d ' ')"

printf '%s' '{"a":1}' | awk -v key=hooks -v valfile=/nonexistent -f $A >/dev/null 2>&1
t "missing valfile exits nonzero" "1" "$?"

echo "----"
echo "setkey.awk: pass=$pass fail=$fail"
[ $fail -eq 0 ]
