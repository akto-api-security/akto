#!/bin/bash
# Skill File Discovery Script for SentinelOne RemoteOps
# Mirrors: mcp-endpoint-shield/mcp/skill_detector.go
#
# What this approximates:
#   - Scans hardcoded agent directories (depth 5): ~/.cursor, ~/.claude, ~/.codeium/windsurf,
#     ~/.antigravity, ~/.copilot, ~/.vscode
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
# Requirements: POSIX shell (bash/zsh), find
# Optional: None (uses shell builtins only)

# Ensure JSON output even on error
cleanup() {
    if [ "$JSON_STARTED" = true ] && [ "$JSON_CLOSED" = false ]; then
        echo ""
        echo "  ]"
        echo "}"
    fi
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

FIRST=true

add_skill() {
    local path="$1"
    local agent="$2"
    
    if [ -f "$path" ]; then
        if [ "$FIRST" = true ]; then
            FIRST=false
        else
            echo ","
        fi
        
        local size=$(stat -f%z "$path" 2>/dev/null || stat -c%s "$path" 2>/dev/null || echo "0")
        local mtime=$(stat -f%m "$path" 2>/dev/null || stat -c%Y "$path" 2>/dev/null || echo "0")
        
        echo "    {\"path\":\"$path\",\"agent\":\"$agent\",\"size\":$size,\"modified\":$mtime}"
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
    local max_depth=5
    
    if [ ! -d "$base_path" ]; then
        return
    fi
    
    # Find skill files matching exact patterns (case-insensitive)
    while IFS= read -r file; do
        # Skip if in junk directory
        skip=false
        IFS='/' read -ra PARTS <<< "$file"
        for part in "${PARTS[@]}"; do
            if should_skip_dir "$part"; then
                skip=true
                break
            fi
        done
        
        if [ "$skip" = false ]; then
            add_skill "$file" "$agent_name"
        fi
    done < <(find "$base_path" -maxdepth "$max_depth" -type f \( \
        -iname "SKILL.md" -o \
        -iname "skill.md" -o \
        -iname "skills.md" -o \
        -iname "SKILLS.MD" -o \
        -iname "PROMPT.md" -o \
        -iname "prompt.md" \
    \) 2>/dev/null)
}

# Get all user home directories
USER_HOMES=""
if [ "$(uname -s)" = "Darwin" ]; then
    USER_HOMES=$(ls -d /Users/* 2>/dev/null | grep -v "/Users/Shared" | grep -v "/Users/Guest")
elif [ "$(uname -s)" = "Linux" ]; then
    USER_HOMES=$(ls -d /home/* /root 2>/dev/null)
fi

# Phase 1: Scan hardcoded agent directories (depth 5)
for user_home in $USER_HOMES; do
    scan_agent_dir "$user_home/.cursor" "cursor"
    scan_agent_dir "$user_home/.claude" "claude"
    scan_agent_dir "$user_home/.codeium/windsurf" "windsurf"
    scan_agent_dir "$user_home/.antigravity" "antigravity"
    scan_agent_dir "$user_home/.copilot" "copilot"
    scan_agent_dir "$user_home/.vscode" "vscode"
    
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
    for agent_dir in .cursor .claude .codeium .antigravity .copilot .vscode .gemini; do
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
    
    # Find skill files in home (depth 6)
    while IFS= read -r file; do
        # Determine agent from parent directory name
        dir_name=$(basename "$(dirname "$file")" | tr '[:upper:]' '[:lower:]' | tr ' _' '--')
        add_skill "$file" "$dir_name"
    done < <(eval "find \"$user_home\" -maxdepth 6 $EXCLUDE_PATHS -type f \( \
        -iname \"SKILL.md\" -o \
        -iname \"skill.md\" -o \
        -iname \"skills.md\" -o \
        -iname \"SKILLS.MD\" -o \
        -iname \"PROMPT.md\" -o \
        -iname \"prompt.md\" \
    \) -print 2>/dev/null")
done

# Phase 3: Scan common Docker/container paths (depth 6)
for container_path in /app /workspace /opt /srv; do
    if [ -d "$container_path" ]; then
        while IFS= read -r file; do
            dir_name=$(basename "$(dirname "$file")" | tr '[:upper:]' '[:lower:]' | tr ' _' '--')
            add_skill "$file" "$dir_name"
        done < <(find "$container_path" -maxdepth 6 -type f \( \
            -iname "SKILL.md" -o \
            -iname "skill.md" -o \
            -iname "skills.md" -o \
            -iname "SKILLS.MD" -o \
            -iname "PROMPT.md" -o \
            -iname "prompt.md" \
        \) 2>/dev/null)
    fi
done

echo ""
echo "  ]"
echo "}"
JSON_CLOSED=true
