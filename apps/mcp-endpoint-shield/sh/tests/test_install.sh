#!/bin/bash
# Installer tests: agent detection, non-destructive config merge, the installed
# handler actually running, idempotency, and uninstall.
# Run: bash sh/tests/test_install.sh
cd "$(dirname "$0")/.." || exit 1
SRC="$PWD"

FAKE=/tmp/akto_install_test
rm -rf "$FAKE"; mkdir -p "$FAKE"/.claude "$FAKE"/.cursor "$FAKE"/.gemini
export HOME="$FAKE"

pass=0; fail=0
t()  { if [ "$2" = "$3" ]; then pass=$((pass+1)); else fail=$((fail+1)); printf 'FAIL %s\n  want: [%s]\n  got : [%s]\n' "$1" "$2" "$3"; fi; }
tc() { case "$3" in *"$2"*) pass=$((pass+1));; *) fail=$((fail+1)); printf 'FAIL %s\n  want substring: [%s]\n  got: [%s]\n' "$1" "$2" "$3";; esac; }

# A settings file with unrelated user settings that must survive.
printf '%s' '{"theme":"dark","model":"opus","permissions":{"allow":["Bash(ls)"]},"hooks":{"Existing":[{"x":1}]}}' \
    >"$FAKE/.claude/settings.json"

bash "$SRC/install.sh" AKTO_DATA_INGESTION_URL=https://akto.example DEVICE_ID=box-1234abcd >/dev/null 2>&1

# ── Files landed ──────────────────────────────────────────────────────────────
for f in akto-hook.sh lib/json.awk lib/setkey.awk lib/akto_core.sh lib/akto_adapters.sh ps/akto-hook.ps1; do
    [ -f "$FAKE/.akto/hooks/$f" ] && pass=$((pass+1)) || { fail=$((fail+1)); echo "FAIL missing $f"; }
done
t "handler is executable" "yes" "$([ -x "$FAKE/.akto/hooks/akto-hook.sh" ] && echo yes || echo no)"
t "env file written"      "yes" "$([ -f "$FAKE/.akto/hooks.env" ] && echo yes || echo no)"
t "env file is private"   "600" "$(stat -f '%Lp' "$FAKE/.akto/hooks.env" 2>/dev/null || stat -c '%a' "$FAKE/.akto/hooks.env" 2>/dev/null)"
tc "env carries the url"  "AKTO_DATA_INGESTION_URL=https://akto.example" "$(cat "$FAKE/.akto/hooks.env")"

# ── The merge must not lose unrelated settings ────────────────────────────────
claude_keys() { python3 -c 'import json;print(" ".join(sorted(json.load(open("'"$FAKE"'/.claude/settings.json"))))) '; }
t "other top-level keys kept" "hooks model permissions theme" "$(claude_keys)"
t "nested user setting kept" '["Bash(ls)"]' \
  "$(python3 -c 'import json;print(json.dumps(json.load(open("'"$FAKE"'/.claude/settings.json"))["permissions"]["allow"]))')"
t "backup was written" "yes" \
  "$(ls "$FAKE"/.claude/settings.json.akto-backup.* >/dev/null 2>&1 && echo yes || echo no)"
tc "hook command points at the installed handler" "$FAKE/.akto/hooks/akto-hook.sh" \
  "$(cat "$FAKE/.claude/settings.json")"
t "every wired config is valid json" "ok" \
  "$(python3 -c '
import json,glob,sys
for f in glob.glob("'"$FAKE"'/.*/settings.json")+glob.glob("'"$FAKE"'/.*/hooks.json"):
    json.load(open(f))
print("ok")' 2>&1 | tail -1)"

# Agents that were not present must not be configured.
t "absent agent not configured" "no" "$([ -f "$FAKE/.codex/hooks.json" ] && echo yes || echo no)"

# ── The installed handler runs from its installed location ────────────────────
# stderr carries the unreachable-backend notice; the contract is exit 0 with no
# decision on stdout (fail-open).
OUT="$(printf '%s' '{"prompt":"hi","session_id":"s1"}' | \
       "$FAKE/.akto/hooks/akto-hook.sh" claude_code_cli UserPromptSubmit 2>/dev/null)"; RC=$?
t "installed handler exits 0" "0" "$RC"
t "installed handler allows"  ""  "$OUT"

# ── Idempotency ───────────────────────────────────────────────────────────────
before="$(python3 -c 'import json;print(json.dumps(json.load(open("'"$FAKE"'/.claude/settings.json")),sort_keys=True))')"
bash "$SRC/install.sh" AKTO_DATA_INGESTION_URL=https://akto.example DEVICE_ID=box-1234abcd >/dev/null 2>&1
after="$(python3 -c 'import json;print(json.dumps(json.load(open("'"$FAKE"'/.claude/settings.json")),sort_keys=True))')"
t "second install is a no-op" "$before" "$after"

# ── A config that is not valid JSON must be left alone ────────────────────────
printf '%s' 'this is not json' >"$FAKE/.gemini/settings.json"
bash "$SRC/install.sh" AKTO_DATA_INGESTION_URL=https://akto.example >/dev/null 2>&1
t "unparseable config untouched" "this is not json" "$(cat "$FAKE/.gemini/settings.json")"

# ── Uninstall ─────────────────────────────────────────────────────────────────
bash "$SRC/install.sh" --uninstall >/dev/null 2>&1
t "handler removed"      "no" "$([ -d "$FAKE/.akto/hooks" ] && echo yes || echo no)"
t "user settings survive uninstall" "opus" \
  "$(python3 -c 'import json;print(json.load(open("'"$FAKE"'/.claude/settings.json"))["model"])')"
t "hooks cleared on uninstall" "{}" \
  "$(python3 -c 'import json;print(json.dumps(json.load(open("'"$FAKE"'/.claude/settings.json"))["hooks"]))')"

echo "----"
echo "install.sh: pass=$pass fail=$fail"
[ $fail -eq 0 ]
