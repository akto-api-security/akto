# ========================================================================================
# MCP Endpoint Shield - Claude CLI Hook Installer (PowerShell)
# ========================================================================================
# Automatically installs Akto guardrails hooks for Claude CLI if detected
# Downloads latest hooks from GitHub and configures Claude CLI settings
# ========================================================================================

param(
    [string]$TargetUserHome = "",
    [string]$AktoGuardrailsUrl = ""
)

$ErrorActionPreference = "Stop"

$GITHUB_RAW_BASE = "https://raw.githubusercontent.com/akto-api-security/akto/agent-hooks/apps/mcp-endpoint-shield/claude-cli-hooks"

foreach ($arg in $args) {
    switch -Wildcard ($arg) {
        "TARGET_USER_HOME=*" { $TargetUserHome = $arg -replace "TARGET_USER_HOME=", "" }
        "AKTO_GUARDRAILS_URL=*" { $AktoGuardrailsUrl = $arg -replace "AKTO_GUARDRAILS_URL=", "" }
    }
}

if ($AktoGuardrailsUrl) {
    $env:AKTO_GUARDRAILS_URL = $AktoGuardrailsUrl
}

function Write-Log {
    param([string]$Message)
    Write-Host "[Claude CLI Hooks] $Message"
}

function Write-ErrorLog {
    param([string]$Message)
    Write-Host "[Claude CLI Hooks] ERROR: $Message" -ForegroundColor Red
}

function Test-ClaudeCliInstalled {
    param([string]$UserHome)
    
    $claudeExists = Get-Command claude -ErrorAction SilentlyContinue
    if ($claudeExists) {
        return $true
    }
    
    $claudeDir = Join-Path $UserHome ".claude"
    if (Test-Path $claudeDir) {
        return $true
    }
    
    return $false
}

function Get-GuardrailsUrl {
    param(
        [string]$ConfigFile,
        [string]$EnvVar
    )
    
    if ($EnvVar) {
        return $EnvVar
    }
    
    if (Test-Path $ConfigFile) {
        $content = Get-Content $ConfigFile -Raw
        if ($content -match 'AKTO_GUARDRAILS_URL=(.+)') {
            return $Matches[1].Trim()
        }
    }
    
    return "https://guardrails.akto.io"
}

function Get-DeviceId {
    try {
        $uuid = (Get-WmiObject -Class Win32_ComputerSystemProduct).UUID
        if ($uuid) {
            return $uuid.Replace("-", "").ToLower()
        }
    } catch {
        # Fallback
    }
    
    try {
        $machineGuid = (Get-ItemProperty -Path "HKLM:\SOFTWARE\Microsoft\Cryptography" -Name MachineGuid).MachineGuid
        if ($machineGuid) {
            return $machineGuid
        }
    } catch {
        # Fallback
    }
    
    return "unknown-device-$(Get-Date -Format 'yyyyMMddHHmmss')"
}

function Download-File {
    param(
        [string]$Url,
        [string]$Destination
    )
    
    Write-Log "Downloading $(Split-Path $Destination -Leaf)..."
    
    try {
        Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing
        return $true
    } catch {
        Write-ErrorLog "Failed to download from $Url : $_"
        return $false
    }
}

function New-WrapperScript {
    param(
        [string]$HookType,
        [string]$IngestionUrl,
        [string]$DeviceId,
        [string]$HooksDir
    )
    
    $wrapperFile = Join-Path $HooksDir "akto-validate-$HookType-wrapper.sh"
    $templateUrl = "$GITHUB_RAW_BASE/akto-validate-$HookType-wrapper.sh"
    $pythonScript = Join-Path $HooksDir "akto-validate-$HookType.py"
    
    $tempFile = "$wrapperFile.tmp"
    if (-not (Download-File -Url $templateUrl -Destination $tempFile)) {
        Write-ErrorLog "Failed to download wrapper template for $HookType, creating locally..."
        Write-Log "Creating wrapper with URL: $IngestionUrl"
        
        $wrapperContent = @"
#!/bin/bash
# Auto-generated wrapper for Akto guardrails hook
# Guardrails URL: $IngestionUrl

export MODE="atlas"
export AKTO_GUARDRAILS_URL="$IngestionUrl"
export AKTO_SYNC_MODE="true"
export AKTO_TIMEOUT="5"
export AKTO_CONNECTOR="claude_code_cli"
export CONTEXT_SOURCE="ENDPOINT"
export DEVICE_ID="$DeviceId"

# Log configuration for debugging
echo "[Claude Hook] Guardrails URL: `$AKTO_GUARDRAILS_URL" >&2
echo "[Claude Hook] Device ID: `$DEVICE_ID" >&2

exec python3 "$pythonScript" "`$@"
"@
        Set-Content -Path $wrapperFile -Value $wrapperContent -NoNewline
        return
    }
    
    $content = Get-Content $tempFile -Raw
    $content = $content -replace '\{\{AKTO_GUARDRAILS_URL\}\}', $IngestionUrl
    $content = $content -replace '\{\{AKTO_DATA_INGESTION_URL\}\}', $IngestionUrl
    $content = $content -replace '\{\{DEVICE_ID \(optional\)\}\}', $DeviceId
    Set-Content -Path $wrapperFile -Value $content -NoNewline
    Remove-Item $tempFile -ErrorAction SilentlyContinue
    
    Write-Log "Wrapper configured with URL: $IngestionUrl"
}

function Update-ClaudeSettings {
    param(
        [string]$SettingsFile,
        [string]$HooksDir
    )
    
    $promptWrapper = Join-Path $HooksDir "akto-validate-prompt-wrapper.sh"
    $responseWrapper = Join-Path $HooksDir "akto-validate-response-wrapper.sh"
    
    $newHooksConfig = @{
        hooks = @{
            UserPromptSubmit = @(
                @{
                    hooks = @(
                        @{
                            type = "command"
                            command = $promptWrapper
                            timeout = 10
                        }
                    )
                }
            )
            Stop = @(
                @{
                    hooks = @(
                        @{
                            type = "command"
                            command = $responseWrapper
                            timeout = 10
                        }
                    )
                }
            )
        }
    }
    
    $settingsDir = Split-Path $SettingsFile -Parent
    if (-not (Test-Path $settingsDir)) {
        New-Item -ItemType Directory -Path $settingsDir -Force | Out-Null
    }
    
    if (Test-Path $SettingsFile) {
        Write-Log "Existing settings.json found, backing up..."
        Copy-Item $SettingsFile "$SettingsFile.backup" -Force
        
        try {
            $settings = Get-Content $SettingsFile -Raw | ConvertFrom-Json -AsHashtable
            $settings["hooks"] = $newHooksConfig["hooks"]
            $settings | ConvertTo-Json -Depth 10 | Set-Content $SettingsFile
            Write-Log "Merged hooks into existing settings.json"
        } catch {
            $newHooksConfig | ConvertTo-Json -Depth 10 | Set-Content $SettingsFile
            Write-Log "Created new settings.json (merge failed)"
        }
    } else {
        $newHooksConfig | ConvertTo-Json -Depth 10 | Set-Content $SettingsFile
        Write-Log "Created new settings.json with hooks"
    }
}

function Install-ForUser {
    param([string]$UserHome)
    
    $env:HOME = $UserHome
    $script:TargetUserHome = $UserHome
    $ClaudeHooksDir = Join-Path $UserHome ".claude\hooks"
    $ClaudeSettingsFile = Join-Path $UserHome ".claude\settings.json"
    $ConfigFile = Join-Path $UserHome ".akto-mcp-endpoint-shield\config\config.env"
    
    Write-Log "Starting Claude CLI hook installation..."
    Write-Log "Target user home: $UserHome"
    
    if (-not (Test-ClaudeCliInstalled -UserHome $UserHome)) {
        Write-Log "Claude CLI not detected - skipping hook installation"
        return $true
    }
    
    Write-Log "✓ Claude CLI detected"
    
    $GuardrailsUrl = Get-GuardrailsUrl -ConfigFile $ConfigFile -EnvVar $AktoGuardrailsUrl
    if (-not $GuardrailsUrl) {
        Write-Log "⚠ Warning: AKTO_GUARDRAILS_URL not configured"
        $GuardrailsUrl = "https://guardrails.akto.io"
    }
    
    $DeviceId = Get-DeviceId
    if (-not $DeviceId) {
        Write-Log "⚠ Warning: Could not generate device ID"
        $DeviceId = "unknown-device"
    }
    
    Write-Log "Device ID: $DeviceId"
    Write-Log "Guardrails URL: $GuardrailsUrl"
    
    if (-not (Test-Path $ClaudeHooksDir)) {
        New-Item -ItemType Directory -Path $ClaudeHooksDir -Force | Out-Null
    }
    Write-Log "✓ Created hooks directory: $ClaudeHooksDir"
    
    Write-Log "Downloading hook scripts from GitHub..."
    
    $promptPy = Join-Path $ClaudeHooksDir "akto-validate-prompt.py"
    if (-not (Download-File -Url "$GITHUB_RAW_BASE/akto-validate-prompt.py" -Destination $promptPy)) {
        Write-ErrorLog "Failed to download akto-validate-prompt.py"
        return $false
    }
    Write-Log "✓ Downloaded akto-validate-prompt.py"
    
    $responsePy = Join-Path $ClaudeHooksDir "akto-validate-response.py"
    if (-not (Download-File -Url "$GITHUB_RAW_BASE/akto-validate-response.py" -Destination $responsePy)) {
        Write-ErrorLog "Failed to download akto-validate-response.py"
        return $false
    }
    Write-Log "✓ Downloaded akto-validate-response.py"
    
    $machineIdPy = Join-Path $ClaudeHooksDir "akto_machine_id.py"
    if (-not (Download-File -Url "$GITHUB_RAW_BASE/akto_machine_id.py" -Destination $machineIdPy)) {
        Write-ErrorLog "Failed to download akto_machine_id.py"
        return $false
    }
    Write-Log "✓ Downloaded akto_machine_id.py"
    
    Write-Log "Creating wrapper scripts with environment variables..."
    New-WrapperScript -HookType "prompt" -IngestionUrl $GuardrailsUrl -DeviceId $DeviceId -HooksDir $ClaudeHooksDir
    Write-Log "✓ Created akto-validate-prompt-wrapper.sh"
    
    New-WrapperScript -HookType "response" -IngestionUrl $GuardrailsUrl -DeviceId $DeviceId -HooksDir $ClaudeHooksDir
    Write-Log "✓ Created akto-validate-response-wrapper.sh"
    
    Write-Log "Updating Claude CLI settings..."
    Update-ClaudeSettings -SettingsFile $ClaudeSettingsFile -HooksDir $ClaudeHooksDir
    Write-Log "✓ Updated settings.json"
    
    Write-Log ""
    Write-Log "=========================================="
    Write-Log "✅ Claude CLI hooks installed successfully!"
    Write-Log "=========================================="
    Write-Log ""
    Write-Log "Hooks location: $ClaudeHooksDir"
    Write-Log "Settings: $ClaudeSettingsFile"
    Write-Log "Guardrails URL: $GuardrailsUrl"
    
    return $true
}

# Main execution
if ($TargetUserHome) {
    Install-ForUser -UserHome $TargetUserHome | Out-Null
} else {
    Write-Log "Scanning for user home directories..."
    
    $userDirs = Get-ChildItem "C:\Users" -Directory -ErrorAction SilentlyContinue
    foreach ($userDir in $userDirs) {
        if ($userDir.Name -in @("Public", "Default", "Default User", "All Users")) {
            continue
        }
        
        try {
            Install-ForUser -UserHome $userDir.FullName | Out-Null
        } catch {
            Write-ErrorLog "Failed for $($userDir.FullName): $_"
        }
    }
    
    Write-Log "Scan complete"
}
