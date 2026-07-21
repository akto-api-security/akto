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
# installed app regardless of install path via HKLM/HKCU Uninstall keys.
function Get-InstalledAppsFromRegistry {
    $paths = @(
        "HKLM:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*",
        "HKLM:\SOFTWARE\WOW6432Node\Microsoft\Windows\CurrentVersion\Uninstall\*",
        "HKCU:\SOFTWARE\Microsoft\Windows\CurrentVersion\Uninstall\*"
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

$localAppData = $env:LOCALAPPDATA
$programFiles = $env:PROGRAMFILES
$userProfile  = $env:USERPROFILE

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
    if ($foundViaRegistry[$agent]) { continue }
    Test-BinaryPaths -Agent $agent -Paths $pathChecks[$agent] | Out-Null
}

# Claude CLI
$claudeCLIPaths = @(
    "$localAppData\Programs\Claude\claude.exe",
    "$programFiles\Claude\claude.exe",
    "$userProfile\.local\bin\claude.exe"
)
if (-not (Test-BinaryPaths -Agent "claude-cli-user" -Paths $claudeCLIPaths)) {
    Test-PathLookup -Agent "claude-cli-user" -Bin "claude.exe" | Out-Null
}

# GitHub Copilot config dir
Test-DirExists -Agent "copilot" -Dir "$userProfile\.copilot" | Out-Null

# Codex CLI
$codexCLIPaths = @(
    "$localAppData\Programs\Codex\codex.exe",
    "$programFiles\Codex\codex.exe",
    "$userProfile\.local\bin\codex.exe"
)
if (-not (Test-BinaryPaths -Agent "codex" -Paths $codexCLIPaths)) {
    Test-PathLookup -Agent "codex" -Bin "codex.exe" | Out-Null
}

# Ollama
$ollamaPaths = @(
    "$localAppData\Programs\Ollama\ollama.exe",
    "$programFiles\Ollama\ollama.exe",
    "$userProfile\.local\bin\ollama.exe"
)
if (-not (Test-BinaryPaths -Agent "ollama" -Paths $ollamaPaths)) {
    Test-PathLookup -Agent "ollama" -Bin "ollama.exe" | Out-Null
}

# Kiro CLI (binary, PATH, or on-disk footprint)
$kiroCLIPaths = @(
    "$localAppData\Programs\Kiro\kiro-cli.exe",
    "$programFiles\Kiro\kiro-cli.exe",
    "$userProfile\.local\bin\kiro-cli.exe",
    "$localAppData\Programs\Kiro\kiro.exe",
    "$programFiles\Kiro\kiro.exe",
    "$userProfile\.local\bin\kiro.exe"
)
$kiroFound = Test-BinaryPaths -Agent "kirocli" -Paths $kiroCLIPaths
if (-not $kiroFound) { $kiroFound = Test-PathLookup -Agent "kirocli" -Bin "kiro-cli.exe" }
if (-not $kiroFound) { $kiroFound = Test-PathLookup -Agent "kirocli" -Bin "kiro.exe" }
if (-not $kiroFound) {
    $footprint = @("$userProfile\.kiro\settings\cli.json", "$userProfile\.kiro\sessions\cli")
    foreach ($fp in $footprint) {
        if (Test-Path -LiteralPath $fp) {
            Add-App -Agent "kirocli" -Path "$userProfile\.kiro" -Method "footprint"
            break
        }
    }
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
