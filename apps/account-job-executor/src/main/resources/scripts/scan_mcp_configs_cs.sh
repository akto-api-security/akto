#!/bin/bash
# MCP Configuration Discovery Script (common — used by SentinelOne and Microsoft Defender)
# Mirrors: mcp-endpoint-shield/mcp/**/discovery.go
#
# What this does:
#   - Discovers MCP config files across all user home directories
#   - Reads and parses mcpServers section from each config
#   - Extracts server names, commands, URLs for collection creation
#   - Outputs structured JSON with file metadata AND server details
#
# Requirements: POSIX shell (bash/zsh), find, stat, python3 (for JSON parsing)

# Enable debug logging to stderr
DEBUG_LOG="/tmp/akto-scan-mcp-configs.log"
log_debug() {
    echo "[$(date +'%Y-%m-%d %H:%M:%S')] $*" >&2
}

log_debug "=== MCP Config Discovery Script Started ==="
log_debug "Hostname: $(hostname)"
log_debug "OS: $(uname -s)"
log_debug "Current User: $(whoami)"
log_debug "Working Directory: $(pwd)"

# Marker file tracking whether the first config record has been printed yet — a plain shell
# variable doesn't work here because find_files() feeds add_file() through a pipe (`find ... |
# while read; do add_file; done`), which runs the loop body in a subshell; a variable set inside
# add_file() during one loop iteration is invisible to the next, and to add_file() calls from a
# *different* find_files() invocation. (We use a plain pipe, not `< <(...)` process substitution,
# because the latter fails on CrowdStrike RTR's execution shell — confirmed via RTR stderr
# capture: date/stat/etc. all "command not found" the instant execution enters a
# process-substitution-fed loop, despite $PATH being correct moments earlier outside it.)
FIRST_MARKER="/tmp/.akto_mcp_first_$$"
: > "$FIRST_MARKER"

# Ensure JSON is always closed even if the script exits early
JSON_STARTED=false
JSON_CLOSED=false
cleanup() {
    if [ "$JSON_STARTED" = true ] && [ "$JSON_CLOSED" = false ]; then
        echo ""
        echo "  ]"
        echo "}"
    fi
    rm -f "$FIRST_MARKER"
    log_debug "=== MCP Config Discovery Script Ended ==="
}
trap cleanup EXIT

echo "{"
echo "  \"scan_time\": \"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\","
echo "  \"hostname\": \"$(hostname)\","
echo "  \"os\": \"$(uname -s)\","
echo "  \"user\": \"$(whoami)\","
echo "  \"configs_found\": ["
JSON_STARTED=true

# SLOW-but-reliable fallback: line-oriented pure-bash parser for the pretty-printed
# `"mcpServers": { "<name>": { "command": ..., "args": [...], "url": ... } }` shape (Cursor, Claude
# CLI) or the `"servers": { ... }` shape (VS Code's native mcp.json) — every real MCP client
# pretty-prints one key per line. Does not handle a hand-minified single-line JSON blob, which
# none of these tools produce in practice.
#
# KNOWN GAP vs the python3 fast path above: does NOT search nested projects["<path>"].mcpServers
# (Claude CLI's ~/.claude.json stores its real per-project MCP servers there, not at the top
# level — confirmed directly). Only hit when python3 is unavailable for this specific file, which
# per direct RTR testing is rare but not impossible. Extending this fallback to track project-key
# nesting depth is a bigger change than the servers/mcpServers rename above — not done yet.
parse_mcp_servers_slow() {
    local file_path="$1"
    local depth=0
    local in_servers=false
    local servers_depth=-1
    local cur_name="" cur_cmd="" cur_url="" cur_type="" cur_args="[]"
    local out="" out_count=0
    local in_args=false args_buf=""

    while IFS= read -r raw_line || [ -n "$raw_line" ]; do
        local line="${raw_line#"${raw_line%%[![:space:]]*}"}"   # ltrim
        line="${line%"${line##*[![:space:]]}"}"                 # rtrim

        local opens="${line//[^\{]/}"
        local closes="${line//[^\}]/}"

        if [ "$in_servers" = false ]; then
            # Cursor/Claude CLI use "mcpServers"; VS Code's native mcp.json uses plain "servers" —
            # confirmed directly against a real VS Code config with 7 entries under "servers" and
            # none under "mcpServers" (the old check silently reported 0 for it).
            case "$line" in
                '"mcpServers"'*'{'*|'"mcp_servers"'*'{'*|'"servers"'*'{'*) in_servers=true; servers_depth=$((depth + 1)) ;;
            esac
            depth=$((depth + ${#opens} - ${#closes}))
            continue
        fi

        if [ "$in_args" = true ]; then
            case "$line" in
                ']'*)
                    in_args=false
                    cur_args="[${args_buf}]"
                    ;;
                *)
                    local v="$line"
                    v="${v#\"}"; v="${v%,}"; v="${v%\"}"
                    if [ -n "$args_buf" ]; then args_buf="${args_buf},"; fi
                    args_buf="${args_buf}\"${v}\""
                    ;;
            esac
            depth=$((depth + ${#opens} - ${#closes}))
            continue
        fi

        if [ "$servers_depth" -ge 0 ] && [ "$depth" -eq "$servers_depth" ]; then
            case "$line" in
                '"'*'": {'|'"'*'":{')
                    if [ -n "$cur_name" ]; then
                        local t="$cur_type"
                        if [ -z "$t" ]; then
                            if [ -n "$cur_cmd" ]; then t="stdio"; elif [ -n "$cur_url" ]; then t="http"; else t="unknown"; fi
                        fi
                        if [ "$out_count" -gt 0 ]; then out="${out},"; fi
                        out="${out}{\"name\":\"${cur_name}\",\"type\":\"${t}\",\"command\":\"${cur_cmd}\",\"args\":${cur_args},\"url\":\"${cur_url}\",\"env\":{}}"
                        out_count=$((out_count + 1))
                    fi
                    cur_name="${line%%\":*}"
                    cur_name="${cur_name#\"}"
                    cur_cmd=""; cur_url=""; cur_type=""; cur_args="[]"
                    ;;
            esac
        fi

        if [ -n "$cur_name" ]; then
            case "$line" in
                '"command"'*)
                    cur_cmd="${line#*: \"}"; cur_cmd="${cur_cmd%\"*}"
                    ;;
                '"url"'*)
                    cur_url="${line#*: \"}"; cur_url="${cur_url%\"*}"
                    ;;
                '"type"'*)
                    cur_type="${line#*: \"}"; cur_type="${cur_type%\"*}"
                    ;;
                '"args"'*'['*']'*)
                    local inner="${line#*[}"; inner="${inner%]*}"
                    cur_args="[${inner}]"
                    ;;
                '"args"'*'['*)
                    in_args=true; args_buf=""
                    ;;
            esac
        fi

        depth=$((depth + ${#opens} - ${#closes}))
        if [ "$in_servers" = true ] && [ "$depth" -lt "$servers_depth" ]; then
            if [ -n "$cur_name" ]; then
                local t="$cur_type"
                if [ -z "$t" ]; then
                    if [ -n "$cur_cmd" ]; then t="stdio"; elif [ -n "$cur_url" ]; then t="http"; else t="unknown"; fi
                fi
                if [ "$out_count" -gt 0 ]; then out="${out},"; fi
                out="${out}{\"name\":\"${cur_name}\",\"type\":\"${t}\",\"command\":\"${cur_cmd}\",\"args\":${cur_args},\"url\":\"${cur_url}\",\"env\":{}}"
                out_count=$((out_count + 1))
            fi
            in_servers=false
        fi
    done < "$file_path"

    printf '[%s]' "$out"
}

# FAST path: python3 does the actual JSON parsing (handles arbitrary nesting/escaping correctly,
# unlike the line-oriented fallback above). Per direct RTR POC testing, python3 resolves and
# succeeds most of the time inside these loops, but unreliably — no discernible pattern for when
# it doesn't. Try it first; only pay the pure-bash parser's shape assumptions when it fails.
parse_mcp_servers() {
    local file_path="$1"
    local py_out
    local py_status
    py_out=$(python3 -c "
import json
import sys

try:
    with open('$file_path', 'r') as f:
        content = f.read().strip()
        if not content:
            print('[]')
            sys.exit(0)
        data = json.loads(content)

    # Cursor/VS Code globalStorage/Claude CLI's own top-level config use "mcpServers"; VS Code's
    # native mcp.json and Antigravity use plain "servers" (confirmed directly against real configs
    # for both — VS Code has 7 real entries under "servers" and none under "mcpServers"; the old
    # mcpServers-only check silently reported 0 for it and for Antigravity's identically-shaped
    # config). Claude CLI's ~/.claude.json ALSO nests real per-project MCP servers under
    # projects[\"<path>\"].mcpServers — confirmed directly: this user's top-level mcpServers key
    # doesn't exist at all, but projects[\"/Users/harshithb\"].mcpServers has 2 real entries. Collect
    # from both the top level and every project entry, since a config can have either or both.
    server_sources = []
    top_level = data.get('mcpServers') or data.get('servers') or {}
    if top_level:
        server_sources.append(top_level)
    projects = data.get('projects', {})
    if isinstance(projects, dict):
        for proj_data in projects.values():
            if isinstance(proj_data, dict):
                proj_servers = proj_data.get('mcpServers') or {}
                if proj_servers:
                    server_sources.append(proj_servers)

    if not server_sources:
        print('[]')
        sys.exit(0)

    result = []
    seen_names = set()
    for servers in server_sources:
        for server_name, server_config in servers.items():
            if not isinstance(server_config, dict):
                continue
            if server_name in seen_names:
                continue
            seen_names.add(server_name)

            server_type = server_config.get('type', '')
            if not server_type:
                if server_config.get('command'):
                    server_type = 'stdio'
                elif server_config.get('url'):
                    server_type = 'http'
                else:
                    server_type = 'unknown'

            server_info = {
                'name': server_name,
                'type': server_type,
                'command': server_config.get('command', ''),
                'args': server_config.get('args', []),
                'url': server_config.get('url', ''),
                'env': server_config.get('env', {})
            }
            result.append(server_info)

    print(json.dumps(result))
except Exception:
    sys.exit(1)
" 2>/dev/null)
    py_status=$?
    if [ "$py_status" -eq 0 ]; then
        printf '%s' "$py_out"
    else
        parse_mcp_servers_slow "$file_path"
    fi
}

# JSON-escapes backslash/double-quote in a single-line string. No sed dependency — sed fails
# as "command not found" inside the pipe-fed loops in find_files() on CrowdStrike RTR's
# execution shell (same restriction as stat — see parse_mcp_servers comment above).
escape_json_string() {
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

add_file() {
    local path="$1"
    local client_type="$2"

    if [ -f "$path" ]; then
        log_debug "Found config file: $path ($client_type)"
        # size/modified/permissions are best-effort — stat fails inside this function when called
        # from find_files()'s pipe-fed loop (see parse_mcp_servers comment above), so these
        # fall back to 0/"unknown" rather than breaking the whole record. `modified` feeds a
        # collection timestamp on the Java side (cosmetic — not correctness-critical); size/perms
        # aren't read at all downstream.
        local size=0
        local mtime=0
        local perms="unknown"

        # Parse mcpServers from the config file
        local servers=$(parse_mcp_servers "$path")
        log_debug "Parsed servers from $path: $servers"

        if [ -s "$FIRST_MARKER" ]; then
            echo ","
        else
            printf 'x' >> "$FIRST_MARKER"
        fi

        local escaped_path
        escaped_path=$(escape_json_string "$path")

        echo -n "    {\"path\":\"$escaped_path\",\"client\":\"$client_type\",\"size\":$size,\"modified\":$mtime,\"permissions\":\"$perms\",\"servers\":$servers}"
    fi
}

find_files() {
    local base_path="$1"
    local pattern="$2"
    local client_type="$3"
    local max_depth="${4:-5}"

    if [ ! -d "$base_path" ]; then
        return
    fi

    # Plain pipe, not `< <(...)` process substitution — see FIRST_MARKER comment above for why.
    find "$base_path" -maxdepth "$max_depth" -name "$pattern" -type f 2>/dev/null | while IFS= read -r file; do
        # Skip backup files
        case "$file" in
            *.backup|*.backup.json|*backup*|*.akto-backup-*|*.shield_*) continue ;;
        esac

        add_file "$file" "$client_type"
    done
}

# Get all user home directories (scan ALL users, not just $HOME)
USER_HOMES=""
if [ "$(uname -s)" = "Darwin" ]; then
    USER_HOMES=$(ls -d /Users/* 2>/dev/null | grep -v "/Users/Shared" | grep -v "/Users/Guest")
elif [ "$(uname -s)" = "Linux" ]; then
    USER_HOMES=$(ls -d /home/* /root 2>/dev/null)
fi

log_debug "Found user homes: $USER_HOMES"

# Scan each user's home directory
for user_home in $USER_HOMES; do
    # 1. Cursor: ~/.cursor/mcp.json
    add_file "$user_home/.cursor/mcp.json" "cursor"

    # 2. Claude Desktop (OS-specific)
    if [ "$(uname -s)" = "Darwin" ]; then
        add_file "$user_home/Library/Application Support/Claude/claude_desktop_config.json" "claude-desktop"
    elif [ "$(uname -s)" = "Linux" ]; then
        add_file "$user_home/.config/Claude/claude_desktop_config.json" "claude-desktop"
    fi

    # 3. Windsurf native
    add_file "$user_home/.codeium/windsurf/mcp_config.json" "windsurf"

    # 4. Windsurf globalStorage (Cline, Roo Cline, etc.)
    if [ "$(uname -s)" = "Darwin" ]; then
        find_files "$user_home/Library/Application Support/Windsurf/User/globalStorage" "cline_mcp_settings.json" "windsurf" 3
        find_files "$user_home/Library/Application Support/Windsurf/User/globalStorage" "roo_mcp_settings.json" "windsurf" 3
    elif [ "$(uname -s)" = "Linux" ]; then
        find_files "$user_home/.config/Windsurf/User/globalStorage" "cline_mcp_settings.json" "windsurf" 3
        find_files "$user_home/.config/Windsurf/User/globalStorage" "roo_mcp_settings.json" "windsurf" 3
    fi

    # 5. VSCode global mcp.json
    if [ "$(uname -s)" = "Darwin" ]; then
        add_file "$user_home/Library/Application Support/Code/User/mcp.json" "vscode"
        add_file "$user_home/Library/Application Support/Code - Insiders/User/mcp.json" "vscode"
    elif [ "$(uname -s)" = "Linux" ]; then
        add_file "$user_home/.config/Code/User/mcp.json" "vscode"
        add_file "$user_home/.config/Code - Insiders/User/mcp.json" "vscode"
    fi

    # 6. VSCode globalStorage (Cline, Roo Cline, Continue, etc.)
    if [ "$(uname -s)" = "Darwin" ]; then
        find_files "$user_home/Library/Application Support/Code/User/globalStorage" "cline_mcp_settings.json" "vscode" 3
        find_files "$user_home/Library/Application Support/Code/User/globalStorage" "roo_mcp_settings.json" "vscode" 3
        find_files "$user_home/Library/Application Support/Code - Insiders/User/globalStorage" "cline_mcp_settings.json" "vscode" 3
    elif [ "$(uname -s)" = "Linux" ]; then
        find_files "$user_home/.config/Code/User/globalStorage" "cline_mcp_settings.json" "vscode" 3
        find_files "$user_home/.config/Code/User/globalStorage" "roo_mcp_settings.json" "vscode" 3
        find_files "$user_home/.config/Code - Insiders/User/globalStorage" "cline_mcp_settings.json" "vscode" 3
    fi

    # 7. GitHub CLI
    find_files "$user_home/.config/gh" "mcp.json" "github-cli" 4
    find_files "$user_home/.config/gh" "mcp.yaml" "github-cli" 4

    # 8. Claude CLI user configs
    add_file "$user_home/.claude.json" "claude-cli-user"
    add_file "$user_home/.claude/settings.json" "claude-cli-user"
    add_file "$user_home/.claude/config.json" "claude-cli-user"

    # 9. Claude CLI plugins
    find_files "$user_home/.claude/plugins" "mcp.json" "claude-plugin" 3

    # 10. Antigravity (OS-specific)
    add_file "$user_home/.gemini/antigravity/mcp_config.json" "antigravity"
    if [ "$(uname -s)" = "Darwin" ]; then
        add_file "$user_home/Library/Application Support/Antigravity/mcp_config.json" "antigravity"
        find_files "$user_home/Library/Application Support/Antigravity/User/globalStorage" "mcp_config.json" "antigravity" 3
    elif [ "$(uname -s)" = "Linux" ]; then
        # Check XDG_CONFIG_HOME
        if [ -n "$XDG_CONFIG_HOME" ]; then
            add_file "$XDG_CONFIG_HOME/antigravity/mcp_config.json" "antigravity"
        else
            add_file "$user_home/.config/antigravity/mcp_config.json" "antigravity"
        fi
        find_files "$user_home/.config/Antigravity/User/globalStorage" "mcp_config.json" "antigravity" 3
    fi

    # 11. Kiro (IDE + CLI share the same config)
    add_file "$user_home/.kiro/settings/mcp.json" "kiro"
done

# Project-level Claude CLI configs (scan current working directory tree if accessible)
CWD=$(pwd 2>/dev/null || echo "")
if [ -n "$CWD" ] && [ -d "$CWD" ]; then
    find_files "$CWD" "settings.json" "claude-cli-project" 6
    find_files "$CWD" "settings.local.json" "claude-cli-local" 6
fi

# Enterprise configs (system-wide)
if [ "$(uname -s)" = "Darwin" ]; then
    add_file "/Library/Application Support/ClaudeCode/managed-mcp.json" "claude-cli-enterprise"
elif [ "$(uname -s)" = "Linux" ]; then
    add_file "/etc/claude-code/managed-mcp.json" "claude-cli-enterprise"
fi

# Container/cloud environments
for container_path in /app /workspace; do
    if [ -d "$container_path" ]; then
        find_files "$container_path" "mcp*.json" "container" 4
    fi
done

echo ""
echo "  ]"
echo "}"
JSON_CLOSED=true
