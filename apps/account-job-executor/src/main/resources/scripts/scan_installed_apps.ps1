# Installed AI-Agent Application Discovery Script (Windows)
# Mirrors: mcp-endpoint-shield/mcp/agent_detector.go (detectWindowsAgents)
#
# Replaces the CrowdStrike Falcon Discover "software inventory" API path when the
# Discover module/Assets scope isn't available on the API client.

$ErrorActionPreference = 'SilentlyContinue'

function Write-Log {
    param([string]$Message)
    [Console]::Error.WriteLine("[APPS-SCAN] $Message")
}

Write-Log "Script started on $env:COMPUTERNAME"

$results = [ordered]@{
    scan_time  = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    hostname   = $env:COMPUTERNAME
    os         = "Windows"
    apps_found = New-Object System.Collections.ArrayList
}

function Add-App {
    param([string]$Agent, [string]$Path, [string]$Method)
    Write-Log "Found app: agent=$Agent path=$Path method=$Method"
    [void]$results.apps_found.Add([ordered]@{
        agent            = $Agent
        path             = $Path
        detection_method = $Method
    })
}

function Test-BinaryPaths {
    param([string]$Agent, [string[]]$Paths)
    foreach ($p in $Paths) {
        if ($p -and (Test-Path -LiteralPath $p -PathType Leaf)) {
            Add-App -Agent $Agent -Path $p -Method "path"
            return $true
        }
    }
    return $false
}

function Test-PathLookup {
    param([string]$Agent, [string]$Bin)
    $cmd = Get-Command $Bin -ErrorAction SilentlyContinue
    if ($cmd) {
        Add-App -Agent $Agent -Path $cmd.Source -Method "PATH"
        return $true
    }
    return $false
}

function Test-DirExists {
    param([string]$Agent, [string]$Dir)
    if ($Dir -and (Test-Path -LiteralPath $Dir -PathType Container)) {
        Add-App -Agent $Agent -Path $Dir -Method "config-dir"
        return $true
    }
    return $false
}

# Registry scan — mirrors detectWindowsAgents' Layer 1: catches any properly
# installed app regardless of install path via HKLM Uninstall keys (machine-wide installs).
function Get-InstalledAppsFromRegistry {
    $paths = @(
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*"
    )
    $names = @()
    foreach ($p in $paths) {
        Get-ItemProperty -Path $p -ErrorAction SilentlyContinue | ForEach-Object {
            if ($_.DisplayName) { $names += $_.DisplayName }
        }
    }
    return $names
}

$registryApps = Get-InstalledAppsFromRegistry
$foundViaRegistry = @{}

$registryNameMap = [ordered]@{
    "cursor"         = "Cursor"
    "vscode"         = "Visual Studio Code"
    "windsurf"       = "Windsurf"
    "claude-desktop" = "Claude"
    "antigravity"    = "Antigravity"
    "codex"          = "Codex"
    "kiroide"        = "Kiro"
}

foreach ($agent in $registryNameMap.Keys) {
    $needle = $registryNameMap[$agent]
    $match = $registryApps | Where-Object { $_ -like "*$needle*" } | Select-Object -First 1
    if ($match) {
        Add-App -Agent $agent -Path $match -Method "registry"
        $foundViaRegistry[$agent] = $true
    }
}

$programFiles = $env:PROGRAMFILES


$userProfiles = @()
try {
    if (Test-Path "C:\Users") {
        $userProfiles = Get-ChildItem "C:\Users" -Directory -Force -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -notin @('Public', 'Default', 'Default User', 'All Users') } |
            Select-Object -ExpandProperty FullName
        Write-Log "Found $($userProfiles.Count) user profile(s)"
    }
} catch {
    Write-Log "ERROR: Failed to enumerate user profiles: $_"
}

$foundPerAgent = $foundViaRegistry.Clone()

foreach ($userProfile in $userProfiles) {
    $localAppData = Join-Path $userProfile "AppData\Local"

    # Layer 2: hardcoded path fallback for portable installs / apps without registry entries.
    $pathChecks = [ordered]@{
        "cursor"         = @("$localAppData\Programs\cursor\Cursor.exe", "$localAppData\cursor\Cursor.exe")
        "vscode"         = @("$programFiles\Microsoft VS Code\Code.exe", "$localAppData\Programs\Microsoft VS Code\Code.exe")
        "windsurf"       = @("$localAppData\Programs\windsurf\Windsurf.exe", "$localAppData\windsurf\Windsurf.exe")
        "claude-desktop" = @("$localAppData\AnthropicClaude\claude.exe", "$localAppData\Programs\claude\Claude.exe", "$localAppData\claude\Claude.exe")
        "antigravity"    = @("$localAppData\Programs\antigravity\Antigravity.exe")
        "codex"          = @("$localAppData\Programs\codex\Codex.exe", "$localAppData\codex\Codex.exe")
        "kiroide"        = @("$localAppData\Programs\kiro\Kiro.exe", "$localAppData\kiro\Kiro.exe")
    }
    foreach ($agent in $pathChecks.Keys) {
        if ($foundPerAgent[$agent]) { continue }
        if (Test-BinaryPaths -Agent $agent -Paths $pathChecks[$agent]) { $foundPerAgent[$agent] = $true }
    }

    # Claude CLI
    if (-not $foundPerAgent["claude-cli-user"]) {
        $claudeCLIPaths = @("$localAppData\Programs\Claude\claude.exe", "$programFiles\Claude\claude.exe", "$userProfile\.local\bin\claude.exe")
        if (Test-BinaryPaths -Agent "claude-cli-user" -Paths $claudeCLIPaths) { $foundPerAgent["claude-cli-user"] = $true }
    }

    # GitHub Copilot config dir
    if (-not $foundPerAgent["copilot"]) {
        if (Test-DirExists -Agent "copilot" -Dir "$userProfile\.copilot") { $foundPerAgent["copilot"] = $true }
    }

    # Codex CLI
    if (-not $foundPerAgent["codex"]) {
        $codexCLIPaths = @("$localAppData\Programs\Codex\codex.exe", "$programFiles\Codex\codex.exe", "$userProfile\.local\bin\codex.exe")
        if (Test-BinaryPaths -Agent "codex" -Paths $codexCLIPaths) { $foundPerAgent["codex"] = $true }
    }

    # Ollama
    if (-not $foundPerAgent["ollama"]) {
        $ollamaPaths = @("$localAppData\Programs\Ollama\ollama.exe", "$programFiles\Ollama\ollama.exe", "$userProfile\.local\bin\ollama.exe")
        if (Test-BinaryPaths -Agent "ollama" -Paths $ollamaPaths) { $foundPerAgent["ollama"] = $true }
    }

    # Kiro CLI (binary or on-disk footprint)
    if (-not $foundPerAgent["kirocli"]) {
        $kiroCLIPaths = @(
            "$localAppData\Programs\Kiro\kiro-cli.exe", "$programFiles\Kiro\kiro-cli.exe", "$userProfile\.local\bin\kiro-cli.exe",
            "$localAppData\Programs\Kiro\kiro.exe", "$programFiles\Kiro\kiro.exe", "$userProfile\.local\bin\kiro.exe"
        )
        if (Test-BinaryPaths -Agent "kirocli" -Paths $kiroCLIPaths) {
            $foundPerAgent["kirocli"] = $true
        } else {
            $footprint = @("$userProfile\.kiro\settings\cli.json", "$userProfile\.kiro\sessions\cli")
            foreach ($fp in $footprint) {
                if (Test-Path -LiteralPath $fp) {
                    Add-App -Agent "kirocli" -Path "$userProfile\.kiro" -Method "footprint"
                    $foundPerAgent["kirocli"] = $true
                    break
                }
            }
        }
    }
}

# PATH lookups still make sense once, globally — a PATH entry for SYSTEM's own session (e.g. a
# machine-wide install added to the system PATH) is valid regardless of user context.
if (-not $foundPerAgent["claude-cli-user"]) { Test-PathLookup -Agent "claude-cli-user" -Bin "claude.exe" | Out-Null }
if (-not $foundPerAgent["codex"])           { Test-PathLookup -Agent "codex" -Bin "codex.exe" | Out-Null }
if (-not $foundPerAgent["ollama"])          { Test-PathLookup -Agent "ollama" -Bin "ollama.exe" | Out-Null }
if (-not $foundPerAgent["kirocli"]) {
    if (-not (Test-PathLookup -Agent "kirocli" -Bin "kiro-cli.exe")) { Test-PathLookup -Agent "kirocli" -Bin "kiro.exe" | Out-Null }
}

Write-Log "Scan complete. Found $($results.apps_found.Count) app(s)"

$plainApps = @($results.apps_found | ForEach-Object { [PSCustomObject]$_ })
$output = [PSCustomObject]@{
    scan_time  = $results.scan_time
    hostname   = $results.hostname
    os         = $results.os
    apps_found = $plainApps
}

$json = $output | ConvertTo-Json -Depth 5 -Compress
Write-Output $json

Write-Log "Total runtime complete"
