#!/bin/bash
# MCP Configuration Discovery Script for SentinelOne RemoteOps
# Mirrors: mcp-endpoint-shield/mcp/**/discovery.go
#
# What this does:
#   - Discovers MCP config files across all user home directories
#   - Reads and parses mcpServers section from each config
#   - Extracts server names, commands, URLs for collection creation
#   - Outputs structured JSON with file metadata AND server details
#
# Requirements: POSIX shell (bash/zsh), find, stat, python3 (for JSON parsing)

set -e

# Check if python3 is available for JSON parsing
if ! command -v python3 &> /dev/null; then
    echo "{\"error\":\"python3 not found - required for JSON parsing\"}" >&2
    exit 1
fi

# Output JSON format
echo "{"
echo "  \"scan_time\": \"$(date -u +"%Y-%m-%dT%H:%M:%SZ")\","
echo "  \"hostname\": \"$(hostname)\","
echo "  \"os\": \"$(uname -s)\","
echo "  \"user\": \"$(whoami)\","
echo "  \"configs_found\": ["

FIRST=true

# Function to parse mcpServers from JSON file
parse_mcp_servers() {
    local file_path="$1"
    
    # Check if file is empty or too small
    local file_size=$(stat -f%z "$file_path" 2>/dev/null || stat -c%s "$file_path" 2>/dev/null || echo "0")
    if [ "$file_size" -lt 2 ]; then
        echo "[]"
        return
    fi
    
    python3 -c "
import json
import sys

try:
    with open('$file_path', 'r') as f:
        content = f.read().strip()
        if not content:
            print('[]')
            sys.exit(0)
        data = json.loads(content)
    
    servers = data.get('mcpServers', {})
    if not servers:
        print('[]')
        sys.exit(0)
    
    result = []
    for server_name, server_config in servers.items():
        if not isinstance(server_config, dict):
            continue
        
        # Infer type if not specified
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
except Exception as e:
    print('[]')
    sys.exit(0)
" 2>/dev/null || echo "[]"
}

add_file() {
    local path="$1"
    local client_type="$2"
    
    if [ -f "$path" ]; then
        local size=$(stat -f%z "$path" 2>/dev/null || stat -c%s "$path" 2>/dev/null || echo "0")
        local mtime=$(stat -f%m "$path" 2>/dev/null || stat -c%Y "$path" 2>/dev/null || echo "0")
        local perms=$(stat -f%Sp "$path" 2>/dev/null || stat -c%A "$path" 2>/dev/null || echo "unknown")
        
        # Parse mcpServers from the config file
        local servers=$(parse_mcp_servers "$path")
        
        if [ "$FIRST" = false ]; then
            echo ","
        fi
        FIRST=false
        
        # Escape path for JSON
        local escaped_path=$(echo "$path" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g')
        
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
    
    while IFS= read -r file; do
        # Skip backup files
        case "$file" in
            *.backup|*.backup.json|*backup*|*.akto-backup-*|*.shield_*) continue ;;
        esac

        add_file "$file" "$client_type"
    done < <(find "$base_path" -maxdepth "$max_depth" -name "$pattern" -type f 2>/dev/null)
}

# Get all user home directories (scan ALL users, not just $HOME)
USER_HOMES=""
if [ "$(uname -s)" = "Darwin" ]; then
    USER_HOMES=$(ls -d /Users/* 2>/dev/null | grep -v "/Users/Shared" | grep -v "/Users/Guest")
elif [ "$(uname -s)" = "Linux" ]; then
    USER_HOMES=$(ls -d /home/* /root 2>/dev/null)
fi

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
