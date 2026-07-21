#!/bin/bash
# Skill File Discovery Script (common — used by SentinelOne and Microsoft Defender)
# Mirrors: mcp-endpoint-shield/mcp/skill_detector.go
#
# What this approximates:
#   - Scans hardcoded agent directories (depth 5): ~/.cursor, ~/.claude, ~/.codeium/windsurf,
#     ~/.antigravity, ~/.copilot, ~/.vscode, ~/.codex, ~/.kiro
#   - Scans home directory (depth 6) with skip logic for:
#     * Hardcoded paths (already scanned)
#     * macOS protected dirs (Desktop, Documents, Downloads, Pictures, Movies, Music, Library)
#     * Junk dirs (node_modules, .git, dist, build, cache, etc.)
#   - Finds files with exact basename match (case-insensitive):
#     SKILL.md, skill.md, skills.md, SKILLS.MD, PROMPT.md, prompt.md
#
# Known gaps vs full parity:
#   - Cannot determine agent name from directory context as precisely as Go code
#   - Does not validate skill content or extract metadata
#   - macOS TCC protection: some directories may be inaccessible even with this skip logic
#
# Requirements: bash, find. Deliberately NO external commands at all — not stat/sed/tr/basename/
# dirname/date, and not even python3 — inside any loop that processes `find` results. CrowdStrike
# RTR's execution shell was confirmed (via direct POC testing against a real device) to make
# external-command execution inside such loops unreliable: sometimes a command resolves and
# succeeds, sometimes the identical command in the identical loop fails as "command not found",
# with no discernible pattern. Everything inside a loop below uses only bash builtins and
# parameter expansion. `date` calls in log_debug are harmless if they fail — see cleanup().

# Enable debug logging to stderr
log_debug() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*" >&2
}

log_debug "=== Skills Discovery Script Started ==="
log_debug "Hostname: $(hostname)"
log_debug "OS: $(uname -s)"
log_debug "Current User: $(whoami)"

# Marker file tracking whether the first skill record has been printed yet — see add_skill()
# for why a file is used instead of a shell variable.
FIRST_MARKER="/tmp/.akto_skills_first_$$"
: > "$FIRST_MARKER"

# Ensure JSON output even on error
cleanup() {
    if [ "$JSON_STARTED" = true ] && [ "$JSON_CLOSED" = false ]; then
        echo ""
        echo "  ]"
        echo "}"
    fi
    rm -f "$FIRST_MARKER"
    log_debug "=== Skills Discovery Script Ended ==="
}
trap cleanup EXIT

JSON_STARTED=false
JSON_CLOSED=false

echo "{"
echo "  \"scan_time\": \"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\","
echo "  \"hostname\": \"$(hostname)\","
echo "  \"os\": \"$(uname -s)\","
echo "  \"user\": \"$(whoami)\","
echo "  \"skills_found\": ["
JSON_STARTED=true

# Derives a [a-z0-9-] slug from a file's parent directory name — e.g.
# ~/.claude/skills/mcp-gateway-dev/SKILL.md -> mcp-gateway-dev. Pure parameter expansion + a
# character-by-character `case` loop, no basename/dirname/tr/sed: those (like stat/date/sed)
# reliably fail as "command not found" specifically inside the pipe-fed loops below on
# CrowdStrike RTR's execution shell, even though the identical binaries work fine outside any
# loop (confirmed via a 20-call `date` probe placed before the loop, which succeeded every time
# on the real device — this is a loop/subshell-specific restriction, not a missing-binary or
# PATH problem).
parent_dir_slug() {
    local path="$1"
    local dir="${path%/*}"
    local parent="${dir##*/}"
    local slug=""
    local len=${#parent}
    local i=0
    while [ "$i" -lt "$len" ]; do
        local ch="${parent:$i:1}"
        case "$ch" in
            A) ch=a ;; B) ch=b ;; C) ch=c ;; D) ch=d ;; E) ch=e ;;
            F) ch=f ;; G) ch=g ;; H) ch=h ;; I) ch=i ;; J) ch=j ;;
            K) ch=k ;; L) ch=l ;; M) ch=m ;; N) ch=n ;; O) ch=o ;;
            P) ch=p ;; Q) ch=q ;; R) ch=r ;; S) ch=s ;; T) ch=t ;;
            U) ch=u ;; V) ch=v ;; W) ch=w ;; X) ch=x ;; Y) ch=y ;; Z) ch=z ;;
            [a-z0-9-]) ;;
            *) ch=- ;;
        esac
        slug="${slug}${ch}"
        i=$((i + 1))
    done
    printf '%s' "$slug"
}

# Reads a file's content and JSON-escapes it, with NO external commands at all (not even
# python3). Verified via direct RTR POC test against the real device: python3/date are
# UNRELIABLE inside this loop (sometimes resolve, sometimes don't, no discernible pattern),
# while this pure-bash read+escape succeeds every time, including on a realistic ~14KB file.
# A file that ends in a trailing newline may lose that single trailing newline in the round
# trip (bash's `read` line loop can't distinguish "last line has no newline" from "file ends
# exactly after one" without stat/tail, neither reliable here) — harmless for how skill content
# is consumed downstream (display/LLM validation, not byte-exact diffing).
# JSON-escapes a single-line string (backslash, double-quote only — file paths don't contain
# newlines/tabs/CR). No external commands.
escape_string() {
    local input="$1"
    local out=""
    local len=${#input}
    local i=0
    while [ "$i" -lt "$len" ]; do
        local c="${input:$i:1}"
        case "$c" in
            '\') c='\\' ;;
            '"') c='\"' ;;
        esac
        out="${out}${c}"
        i=$((i + 1))
    done
    printf '%s' "$out"
}

# SLOW fallback (~2-3s per 14KB file — bash's own string ops are effectively O(n^2) at this
# scale; confirmed no way to make pure-bash escaping fast). Reliable every time on the real
# device, unlike python3, so this is the correctness-guaranteed path when the fast path fails.
json_escape_file_slow() {
    local file="$1"
    local content=""
    local first=true
    while IFS= read -r line || [ -n "$line" ]; do
        if [ "$first" = true ]; then
            content="$line"
            first=false
        else
            content="${content}
${line}"
        fi
    done < "$file"

    local escaped=""
    local len=${#content}
    local i=0
    local nl=$(printf '\nX'); nl="${nl%X}"
    local tb=$(printf '\tX'); tb="${tb%X}"
    local cr=$(printf '\rX'); cr="${cr%X}"
    while [ "$i" -lt "$len" ]; do
        local c="${content:$i:1}"
        case "$c" in
            '\') c='\\' ;;
            '"') c='\"' ;;
            "$tb") c='\t' ;;
            "$cr") c='\r' ;;
            "$nl") c='\n' ;;
            *) ;;
        esac
        escaped="${escaped}${c}"
        i=$((i + 1))
    done
    printf '%s' "$escaped"
}

# FAST path: python3 reads + JSON-encodes in one shot (effectively instant vs. 2-3s/file for the
# pure-bash fallback). Per direct RTR POC testing, python3 resolves and runs successfully most of
# the time inside these loops, but unreliably — no discernible pattern for when it fails. Try it
# first; only pay the slow-but-guaranteed pure-bash cost for the files where it doesn't work.
json_escape_file() {
    local file="$1"
    local py_out
    local py_status
    py_out=$(python3 -c "
import json, sys
try:
    with open(sys.argv[1], 'r', encoding='utf-8', errors='replace') as f:
        content = f.read()
    # Emit just the escaped inner string (strip the outer quotes json.dumps adds for a bare str)
    print(json.dumps(content)[1:-1], end='')
except Exception:
    sys.exit(1)
" "$file" 2>/dev/null)
    py_status=$?
    if [ "$py_status" -eq 0 ]; then
        printf '%s' "$py_out"
    else
        json_escape_file_slow "$file"
    fi
}

# add_skill() below uses $FIRST_MARKER (set up above, near cleanup()) instead of a shell variable
# to track whether the first record was printed — `find ... | while read; do add_skill; done`
# runs the loop body in a subshell (plain pipe, not `< <(...)` process substitution — the latter
# fails on RTR's execution shell, see scan_agent_dir), so a variable set inside add_skill() during
# one loop iteration is invisible to the next call, and to calls from a *different*
# scan_agent_dir invocation. A file survives across all of those subshell boundaries.
add_skill() {
    local path="$1"
    local agent="$2"

    if [ -f "$path" ]; then
        log_debug "Found skill file: $path (agent: $agent)"

        if [ -s "$FIRST_MARKER" ]; then
            echo ","
        else
            printf 'x' >> "$FIRST_MARKER"
        fi

        # size/mtime are cosmetic only (never read by the Java ingestion side) — hardcode rather
        # than call stat, which fails as "command not found" inside this loop (see parent_dir_slug
        # comment above).
        local size=0
        local mtime=0

        local skill_name
        skill_name=$(parent_dir_slug "$path")
        log_debug "Skill name: $skill_name"

        local escaped_content
        escaped_content=$(json_escape_file "$path")
        local escaped_path
        escaped_path=$(escape_string "$path")

        printf '    {"path":"%s","agent":"%s","size":%s,"modified":%s,"skill_name":"%s","skill_content":"%s"}' \
            "$escaped_path" "$agent" "$size" "$mtime" "$skill_name" "$escaped_content"
    fi
}

should_skip_dir() {
    local dir_name="$1"

    case "$dir_name" in
        node_modules|.git|.svn|.hg|dist|build|cache|.cache|logs|tmp|.venv|.next|target|vendor|__pycache__|.pytest_cache|coverage) return 0 ;;
        *) return 1 ;;
    esac
}

scan_agent_dir() {
    local base_path="$1"
    local agent_name="$2"
    # NOTE: Codex skill coverage at depth 5 is incomplete (~8 files vs ~71-1289 depending on
    # scope). Tried depth 8/10 + .tmp exclusion (see git history) — worked locally but caused a
    # 60x file-duplication bug on the real CrowdStrike RTR device at depth 10, and depth 8's
    # generic "cache" skip-entry incorrectly excludes Codex's own plugins/cache/<runtime>/...
    # directory structure (genuine versioned plugin skills, not build junk). Reverted to depth 5
    # until both issues are resolved — do not raise this without re-testing directly against a
    # real device first.
    local max_depth=5

    if [ ! -d "$base_path" ]; then
        log_debug "Directory not found: $base_path"
        return
    fi

    log_debug "Scanning agent directory: $base_path (agent: $agent_name, depth: $max_depth)"

    # Find skill files matching exact patterns (case-insensitive)
    local found_count=0
    # Plain pipe, not `< <(...)` process substitution — RTR's execution shell appears to run
    # process-substitution loop bodies in a child context with a stripped PATH/environment
    # (confirmed via RTR stderr: date/dirname/tr/basename/sed all "command not found" the
    # instant execution enters a `< <(...)`-fed loop, despite $PATH being correct moments
    # earlier outside it). A plain pipe avoids that mechanism. found_count becomes a
    # best-effort count (loop body runs in a subshell with a pipe too) — cosmetic only, not
    # read by the Java ingestion side.
    find "$base_path" -maxdepth "$max_depth" -type f \( \
        -iname "SKILL.md" -o \
        -iname "skill.md" -o \
        -iname "skills.md" -o \
        -iname "SKILLS.MD" -o \
        -iname "PROMPT.md" -o \
        -iname "prompt.md" \
    \) 2>/dev/null | while IFS= read -r file; do
        # Skip if in junk directory — POSIX-safe path scan (no bash-only `read -ra`/here-string,
        # which fails under RTR's execution shell with "bad option: -a").
        skip=false
        remainder="$file"
        while [ -n "$remainder" ]; do
            part="${remainder%%/*}"
            if should_skip_dir "$part"; then
                skip=true
                break
            fi
            case "$remainder" in
                */*) remainder="${remainder#*/}" ;;
                *) remainder="" ;;
            esac
        done

        if [ "$skip" = false ]; then
            add_skill "$file" "$agent_name"
            found_count=$((found_count + 1))
        fi
    done

    log_debug "Scan complete for $base_path: found $found_count skill files"
}

# Get all user home directories
USER_HOMES=""
if [ "$(uname -s)" = "Darwin" ]; then
    USER_HOMES=$(ls -d /Users/* 2>/dev/null | grep -v "/Users/Shared" | grep -v "/Users/Guest")
elif [ "$(uname -s)" = "Linux" ]; then
    USER_HOMES=$(ls -d /home/* /root 2>/dev/null)
fi

log_debug "Found user homes: $USER_HOMES"

# Phase 1: Scan hardcoded agent directories (depth 5)
log_debug "=== Phase 1: Scanning hardcoded agent directories ==="
for user_home in $USER_HOMES; do
    log_debug "Processing user home: $user_home"
    scan_agent_dir "$user_home/.cursor" "cursor"
    scan_agent_dir "$user_home/.claude" "claude"
    scan_agent_dir "$user_home/.codeium/windsurf" "windsurf"
    scan_agent_dir "$user_home/.antigravity" "antigravity"
    scan_agent_dir "$user_home/.copilot" "copilot"
    scan_agent_dir "$user_home/.vscode" "vscode"
    scan_agent_dir "$user_home/.codex" "codex"
    scan_agent_dir "$user_home/.kiro" "kiro"

    # Also check .gemini/antigravity
    scan_agent_dir "$user_home/.gemini/antigravity" "antigravity"
done

# Phase 2: Scan home directory (depth 6) with skip logic
for user_home in $USER_HOMES; do
    if [ ! -d "$user_home" ]; then
        continue
    fi

    # Build exclusion patterns for find
    EXCLUDE_PATHS=""

    # Skip hardcoded agent paths (already scanned)
    for agent_dir in .cursor .claude .codeium .antigravity .copilot .vscode .gemini .codex .kiro; do
        EXCLUDE_PATHS="$EXCLUDE_PATHS -path $user_home/$agent_dir -prune -o"
    done

    # Skip macOS protected directories (TCC prompts)
    if [ "$(uname -s)" = "Darwin" ]; then
        for protected in Desktop Documents Downloads Pictures Movies Music Public Library Applications .Trash; do
            EXCLUDE_PATHS="$EXCLUDE_PATHS -path $user_home/$protected -prune -o"
        done
    fi

    # Skip junk directories
    for junk in node_modules .git .svn .hg dist build cache .cache logs tmp .venv .next target vendor __pycache__ .pytest_cache coverage; do
        EXCLUDE_PATHS="$EXCLUDE_PATHS -name $junk -prune -o"
    done

    # Find skill files in home (depth 6) — plain pipe, not process substitution (see comment
    # in scan_agent_dir above for why).
    eval "find \"$user_home\" -maxdepth 6 $EXCLUDE_PATHS -type f \( \
        -iname \"SKILL.md\" -o \
        -iname \"skill.md\" -o \
        -iname \"skills.md\" -o \
        -iname \"SKILLS.MD\" -o \
        -iname \"PROMPT.md\" -o \
        -iname \"prompt.md\" \
    \) -print 2>/dev/null" | while IFS= read -r file; do
        # Determine agent from parent directory name
        dir_name=$(parent_dir_slug "$file")
        add_skill "$file" "$dir_name"
    done
done

# Phase 3: Scan common Docker/container paths (depth 6)
for container_path in /app /workspace /opt /srv; do
    if [ -d "$container_path" ]; then
        find "$container_path" -maxdepth 6 -type f \( \
            -iname "SKILL.md" -o \
            -iname "skill.md" -o \
            -iname "skills.md" -o \
            -iname "SKILLS.MD" -o \
            -iname "PROMPT.md" -o \
            -iname "prompt.md" \
        \) 2>/dev/null | while IFS= read -r file; do
            dir_name=$(parent_dir_slug "$file")
            add_skill "$file" "$dir_name"
        done
    fi
done

echo ""
echo "  ]"
echo "}"
JSON_CLOSED=true
