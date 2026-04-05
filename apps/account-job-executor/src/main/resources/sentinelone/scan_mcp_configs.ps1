# MCP Configuration Discovery Script for SentinelOne RemoteOps (Windows PowerShell)
# Mirrors: mcp-endpoint-shield/mcp/**/discovery.go
#
# What this does:
#   - Discovers MCP config files across all user profiles
#   - Reads and parses mcpServers section from each config
#   - Extracts server names, commands, URLs for collection creation
#   - Outputs structured JSON with file metadata AND server details
#
# Requirements: PowerShell 5.1+

$ErrorActionPreference = 'SilentlyContinue'

$results = @{
    scan_time = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    hostname = $env:COMPUTERNAME
    os = "Windows"
    user = $env:USERNAME
    configs_found = @()
}

function Parse-MCPServers {
    param([string]$FilePath)
    
    try {
        # Check if file is empty
        $item = Get-Item -Path $FilePath -Force
        if ($item.Length -lt 2) {
            return @()
        }
        
        $content = Get-Content -Path $FilePath -Raw -ErrorAction Stop
        if ([string]::IsNullOrWhiteSpace($content)) {
            return @()
        }
        
        $json = $content | ConvertFrom-Json -ErrorAction Stop
        
        if (-not $json.mcpServers) {
            return @()
        }
        
        $servers = @()
        foreach ($serverName in $json.mcpServers.PSObject.Properties.Name) {
            $serverConfig = $json.mcpServers.$serverName
            
            # Infer type if not specified
            $serverType = if ($serverConfig.type) { $serverConfig.type } else { "" }
            if (-not $serverType) {
                if ($serverConfig.command) {
                    $serverType = "stdio"
                } elseif ($serverConfig.url) {
                    $serverType = "http"
                } else {
                    $serverType = "unknown"
                }
            }
            
            $serverInfo = @{
                name = $serverName
                type = $serverType
                command = if ($serverConfig.command) { $serverConfig.command } else { "" }
                args = if ($serverConfig.args) { $serverConfig.args } else { @() }
                url = if ($serverConfig.url) { $serverConfig.url } else { "" }
                env = if ($serverConfig.env) { $serverConfig.env } else { @{} }
            }
            
            $servers += $serverInfo
        }
        
        return $servers
    }
    catch {
        return @()
    }
}

function Add-File {
    param(
        [string]$Path,
        [string]$ClientType
    )
    
    if (Test-Path -Path $Path -PathType Leaf) {
        $item = Get-Item -Path $Path -Force
        
        # Skip backup files
        if ($item.Name -match '\.backup|backup|\.akto-backup-|\.shield_') {
            return
        }
        
        # Parse mcpServers from the config file
        $servers = Parse-MCPServers -FilePath $Path
        
        $results.configs_found += @{
            path = $Path
            client = $ClientType
            size = $item.Length
            modified = [int][double]::Parse((Get-Date $item.LastWriteTime -UFormat %s))
            permissions = $item.Attributes.ToString()
            servers = $servers
        }
    }
}

function Find-Files {
    param(
        [string]$BasePath,
        [string]$Pattern,
        [string]$ClientType,
        [int]$MaxDepth = 5
    )
    
    if (-not (Test-Path -Path $BasePath -PathType Container)) {
        return
    }
    
    Get-ChildItem -Path $BasePath -Filter $Pattern -Recurse -Depth $MaxDepth -File -Force -ErrorAction SilentlyContinue | ForEach-Object {
        # Skip backup files
        if ($_.Name -notmatch '\.backup|backup|\.akto-backup-|\.shield_') {
            Add-File -Path $_.FullName -ClientType $ClientType
        }
    }
}

# Get all user profile directories
$userProfiles = @()
if (Test-Path "C:\Users") {
    $userProfiles = Get-ChildItem "C:\Users" -Directory -Force -ErrorAction SilentlyContinue | 
        Where-Object { $_.Name -notin @('Public', 'Default', 'Default User', 'All Users') } |
        Select-Object -ExpandProperty FullName
}

# Scan each user profile
foreach ($userProfile in $userProfiles) {
    # 1. Cursor
    Add-File -Path "$userProfile\.cursor\mcp.json" -ClientType "cursor"
    
    # 2. Claude Desktop
    $appData = Join-Path $userProfile "AppData\Roaming"
    if (Test-Path $appData) {
        Add-File -Path "$appData\Claude\claude_desktop_config.json" -ClientType "claude-desktop"
    }
    
    # 3. Windsurf native
    Add-File -Path "$userProfile\.codeium\windsurf\mcp_config.json" -ClientType "windsurf"
    
    # 4. Windsurf globalStorage
    if (Test-Path $appData) {
        Find-Files -BasePath "$appData\Windsurf\User\globalStorage" -Pattern "cline_mcp_settings.json" -ClientType "windsurf" -MaxDepth 3
        Find-Files -BasePath "$appData\Windsurf\User\globalStorage" -Pattern "roo_mcp_settings.json" -ClientType "windsurf" -MaxDepth 3
    }
    
    # 5. VSCode global mcp.json
    if (Test-Path $appData) {
        Add-File -Path "$appData\Code\User\mcp.json" -ClientType "vscode"
        Add-File -Path "$appData\Code - Insiders\User\mcp.json" -ClientType "vscode"
    }
    
    # 6. VSCode globalStorage
    if (Test-Path $appData) {
        Find-Files -BasePath "$appData\Code\User\globalStorage" -Pattern "cline_mcp_settings.json" -ClientType "vscode" -MaxDepth 3
        Find-Files -BasePath "$appData\Code\User\globalStorage" -Pattern "roo_mcp_settings.json" -ClientType "vscode" -MaxDepth 3
        Find-Files -BasePath "$appData\Code - Insiders\User\globalStorage" -Pattern "cline_mcp_settings.json" -ClientType "vscode" -MaxDepth 3
    }
    
    # 7. GitHub CLI
    if (Test-Path $appData) {
        Find-Files -BasePath "$appData\GitHub CLI" -Pattern "mcp.json" -ClientType "github-cli" -MaxDepth 4
        Find-Files -BasePath "$appData\GitHub CLI" -Pattern "mcp.yaml" -ClientType "github-cli" -MaxDepth 4
    }
    
    # 8. Claude CLI user configs
    Add-File -Path "$userProfile\.claude.json" -ClientType "claude-cli-user"
    Add-File -Path "$userProfile\.claude\settings.json" -ClientType "claude-cli-user"
    Add-File -Path "$userProfile\.claude\config.json" -ClientType "claude-cli-user"
    
    # 9. Claude CLI plugins
    Find-Files -BasePath "$userProfile\.claude\plugins" -Pattern "mcp.json" -ClientType "claude-plugin" -MaxDepth 3
    
    # 10. Antigravity
    Add-File -Path "$userProfile\.gemini\antigravity\mcp_config.json" -ClientType "antigravity"
    if (Test-Path $appData) {
        Add-File -Path "$appData\Antigravity\mcp_config.json" -ClientType "antigravity"
        Find-Files -BasePath "$appData\Antigravity\User\globalStorage" -Pattern "mcp_config.json" -ClientType "antigravity" -MaxDepth 3
    }
}

# Project-level Claude CLI configs (scan current working directory)
$cwd = Get-Location
if ($cwd) {
    Find-Files -BasePath $cwd.Path -Pattern "settings.json" -ClientType "claude-cli-project" -MaxDepth 6
    Find-Files -BasePath $cwd.Path -Pattern "settings.local.json" -ClientType "claude-cli-local" -MaxDepth 6
}

# Enterprise configs (system-wide)
$programData = $env:ProgramData
if ($programData) {
    Add-File -Path "$programData\ClaudeCode\managed-mcp.json" -ClientType "claude-cli-enterprise"
}

# Container/cloud environments
foreach ($containerPath in @("C:\app", "C:\workspace")) {
    if (Test-Path $containerPath) {
        Find-Files -BasePath $containerPath -Pattern "mcp*.json" -ClientType "container" -MaxDepth 4
    }
}

# Output JSON
$results | ConvertTo-Json -Depth 10
