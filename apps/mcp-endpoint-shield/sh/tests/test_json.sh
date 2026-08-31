#!/bin/bash
# Unit tests for lib/json.awk. Run: bash sh/tests/test_json.sh
cd "$(dirname "$0")/.." || exit 1
J=lib/json.awk
pass=0
fail=0

t() { # t <desc> <expected> <actual>
    if [ "$2" = "$3" ]; then
        pass=$((pass + 1))
    else
        fail=$((fail + 1))
        printf 'FAIL %s\n  want: [%s]\n  got : [%s]\n' "$1" "$2" "$3"
    fi
}

IN='{"prompt":"hello \"world\"","session_id":"s1","tool_input":{"a":1,"b":{"c":[1,2,{"d":"}"}]}},"n":42,"ok":true,"nil":null,"nested":{"deep":{"key":"v"}}}'
g() { printf '%s' "$IN" | awk -v mode=get -v path="$1" -f $J; }

t "string keeps escapes"  '"hello \"world\""'                     "$(g prompt)"
t "plain string"          '"s1"'                                  "$(g session_id)"
t "object subtree raw"    '{"a":1,"b":{"c":[1,2,{"d":"}"}]}}'     "$(g tool_input)"
t "brace inside string"   '{"c":[1,2,{"d":"}"}]}'                 "$(g tool_input.b)"
t "array subtree"         '[1,2,{"d":"}"}]'                       "$(g tool_input.b.c)"
t "number"                '42'                                    "$(g n)"
t "boolean"               'true'                                  "$(g ok)"
t "null"                  'null'                                  "$(g nil)"
t "deep path"             '"v"'                                   "$(g nested.deep.key)"
t "type string"           'string'  "$(printf '%s' "$IN" | awk -v mode=type -v path=prompt -f $J)"
t "type object"           'object'  "$(printf '%s' "$IN" | awk -v mode=type -v path=tool_input -f $J)"
t "type array"            'array'   "$(printf '%s' "$IN" | awk -v mode=type -v path=tool_input.b.c -f $J)"
t "type number"           'number'  "$(printf '%s' "$IN" | awk -v mode=type -v path=n -f $J)"

g missing >/dev/null 2>&1
t "missing key exits 1"   "1" "$?"
printf '%s' "$IN" | awk -v mode=get -v path=n.deeper -f $J >/dev/null 2>&1
t "descend non-object -> 1" "1" "$?"

t "unquote keeps escapes" 'hello \"world\"' "$(printf '%s' '"hello \"world\""' | awk -v mode=unquote -f $J)"

t "escape quote"     'a\"b'  "$(printf 'a"b'    | awk -v mode=escape -f $J)"
t "escape backslash" 'a\\b'  "$(printf 'a\\b'   | awk -v mode=escape -f $J)"
t "escape newline"   'a\nb'  "$(printf 'a\nb'   | awk -v mode=escape -f $J)"
t "escape tab"       'a\tb'  "$(printf 'a\tb'   | awk -v mode=escape -f $J)"
t "escape ctrl-A"    "$(printf '\\u0001')"  "$(printf '\001'  | awk -v mode=escape -f $J)"
t "escape empty"     ''      "$(printf ''       | awk -v mode=escape -f $J)"

# Values move around still-encoded, so multi-line and non-ASCII content is a
# byte-for-byte pass-through with no decode/re-encode step.
ML='{"prompt":"line1\nline2 é café \\\\ end"}'
t "encoded passthrough" '"line1\nline2 é café \\\\ end"' \
    "$(printf '%s' "$ML" | awk -v mode=get -v path=prompt -f $J)"

# Real (decoded) UTF-8 bytes in the input must survive too.
U8='{"prompt":"café ☕ 日本語"}'
t "utf8 passthrough" '"café ☕ 日本語"' "$(printf '%s' "$U8" | awk -v mode=get -v path=prompt -f $J)"

# Literal newline inside the JSON document (pretty-printed hook payloads).
PP='{
  "prompt" : "hi",
  "tool_input" : { "x" : [ 1 , 2 ] }
}'
t "pretty-printed scalar"  '"hi"'                "$(printf '%s' "$PP" | awk -v mode=get -v path=prompt -f $J)"
t "pretty-printed subtree" '{ "x" : [ 1 , 2 ] }' "$(printf '%s' "$PP" | awk -v mode=get -v path=tool_input -f $J)"

# Adversarial: JSON syntax embedded in string values must not confuse the scanner.
ADV='{"a":"{\"fake\":1}","b":"has \\\" quote","c":"trailing\\\\","d":"real"}'
t "fake object in string" '"{\"fake\":1}"' "$(printf '%s' "$ADV" | awk -v mode=get -v path=a -f $J)"
t "escaped quote in val"  '"has \\\" quote"' "$(printf '%s' "$ADV" | awk -v mode=get -v path=b -f $J)"
t "value after tricky"    '"real"'         "$(printf '%s' "$ADV" | awk -v mode=get -v path=d -f $J)"

# Key that is a prefix of another key must not match early.
PFX='{"tool":"a","tool_name":"b"}'
t "exact key match" '"b"' "$(printf '%s' "$PFX" | awk -v mode=get -v path=tool_name -f $J)"

# Empty object / empty string edge cases.
t "empty object"  '{}'  "$(printf '%s' '{"x":{}}' | awk -v mode=get -v path=x -f $J)"
t "empty string"  '""'  "$(printf '%s' '{"x":""}' | awk -v mode=get -v path=x -f $J)"
printf '%s' '{}' | awk -v mode=get -v path=x -f $J >/dev/null 2>&1
t "key in empty obj -> 1" "1" "$?"

echo "----"
echo "json.awk: pass=$pass fail=$fail"
[ $fail -eq 0 ]
