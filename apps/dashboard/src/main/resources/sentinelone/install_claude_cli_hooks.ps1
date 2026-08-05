# ========================================================================================
# MCP Endpoint Shield - Claude CLI Hook Installer (PowerShell)
# ========================================================================================
# Automatically installs Akto guardrails hooks for Claude CLI if detected
# Downloads latest hooks from GitHub and configures Claude CLI settings
# ========================================================================================

param(
    [string]$TargetUserHome = "",
    [string]$AktoDataIngestionUrl = ""
)

$ErrorActionPreference = "Stop"

$GITHUB_RAW_BASE = "https://raw.githubusercontent.com/akto-api-security/akto/agent-hooks/apps/mcp-endpoint-shield/claude-cli-hooks"

foreach ($arg in $args) {
    switch -Wildcard ($arg) {
        "TARGET_USER_HOME=*" { $TargetUserHome = $arg -replace "TARGET_USER_HOME=", "" }
        "AKTO_DATA_INGESTION_URL=*" { $AktoDataIngestionUrl = $arg -replace "AKTO_DATA_INGESTION_URL=", "" }
    }
}

if ($AktoDataIngestionUrl) {
    # Strip KEY=VALUE prefix if caller passed the full env var line (e.g. "AKTO_DATA_INGESTION_URL=https://...")
    if ($AktoDataIngestionUrl -match "^[A-Z_]+=(.+)$") {
        $AktoDataIngestionUrl = $Matches[1].Trim()
    }
    $env:AKTO_DATA_INGESTION_URL = $AktoDataIngestionUrl
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

    # .claude dir exists after first run
    if (Test-Path (Join-Path $UserHome ".claude")) {
        return $true
    }

    # npm global install paths (SYSTEM account has no %APPDATA%, so derive from UserHome)
    $npmPaths = @(
        (Join-Path $UserHome "AppData\Roaming\npm\claude"),
        (Join-Path $UserHome "AppData\Roaming\npm\claude.cmd"),
        (Join-Path $UserHome "AppData\Local\npm\claude"),
        (Join-Path $UserHome "AppData\Local\npm\claude.cmd")
    )
    foreach ($p in $npmPaths) {
        if (Test-Path $p) { return $true }
    }

    # Last resort: Get-Command works if run interactively (non-SYSTEM)
    if (Get-Command claude -ErrorAction SilentlyContinue) {
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
        if ($content -match 'AKTO_DATA_INGESTION_URL=(.+)') {
            return $Matches[1].Trim()
        }
    }

    return "https://1764882677-guardrails.akto.io"
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

function Get-FileFromUrl {
    param(
        [string]$Url,
        [string]$Destination
    )

    Write-Log "Downloading $(Split-Path $Destination -Leaf)..."

    # Try Invoke-WebRequest first
    try {
        Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing -Headers @{"Cache-Control"="no-cache";"Pragma"="no-cache"}
        return $true
    } catch {
        Write-Log "Invoke-WebRequest failed, trying curl.exe fallback..."
    }

    # Fallback: curl.exe (ships with Windows 10+ and handles TLS correctly under SYSTEM)
    try {
        $result = & curl.exe -fsSL -H "Cache-Control: no-cache" -H "Pragma: no-cache" $Url -o $Destination 2>&1
        if ($LASTEXITCODE -eq 0) {
            return $true
        }
        Write-ErrorLog "curl.exe failed (exit $LASTEXITCODE): $result"
        return $false
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

    $wrapperFile = Join-Path $HooksDir "akto-validate-$HookType-wrapper.ps1"
    $pythonScript = (Join-Path $HooksDir "akto-validate-$HookType.py").Replace("\", "/")

    $wrapperContent = @"
# Auto-generated wrapper for Akto guardrails hook
# Data Ingestion URL: $IngestionUrl

`$env:MODE = "atlas"
`$env:AKTO_DATA_INGESTION_URL = "$IngestionUrl"
`$env:AKTO_SYNC_MODE = "true"
`$env:AKTO_TIMEOUT = "5"
`$env:AKTO_CONNECTOR = "claude_code_cli"
`$env:CONTEXT_SOURCE = "ENDPOINT"
`$env:DEVICE_ID = "$DeviceId"

Write-Host "[Claude Hook] Data Ingestion URL: `$env:AKTO_DATA_INGESTION_URL" -ForegroundColor Gray
Write-Host "[Claude Hook] Device ID: `$env:DEVICE_ID" -ForegroundColor Gray

& python "$pythonScript" `$args
"@

    [System.IO.File]::WriteAllText($wrapperFile, $wrapperContent, [System.Text.Encoding]::UTF8)
    Write-Log "Wrapper configured with URL: $IngestionUrl"
}

function Update-ClaudeSettings {
    param(
        [string]$SettingsFile,
        [string]$HooksDir
    )

    $promptWrapper = (Join-Path $HooksDir "akto-validate-prompt-wrapper.ps1").Replace("\", "/")
    $responseWrapper = (Join-Path $HooksDir "akto-validate-response-wrapper.ps1").Replace("\", "/")

    $newHooksConfig = @{
        hooks = @{
            UserPromptSubmit = @(
                @{
                    hooks = @(
                        @{
                            type    = "command"
                            command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$promptWrapper`""
                            timeout = 10
                        }
                    )
                }
            )
            Stop = @(
                @{
                    hooks = @(
                        @{
                            type    = "command"
                            command = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$responseWrapper`""
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
            [System.IO.File]::WriteAllText($SettingsFile, ($settings | ConvertTo-Json -Depth 10), [System.Text.Encoding]::UTF8)
            Write-Log "Merged hooks into existing settings.json"
        } catch {
            [System.IO.File]::WriteAllText($SettingsFile, ($newHooksConfig | ConvertTo-Json -Depth 10), [System.Text.Encoding]::UTF8)
            Write-Log "Created new settings.json (merge failed)"
        }
    } else {
        [System.IO.File]::WriteAllText($SettingsFile, ($newHooksConfig | ConvertTo-Json -Depth 10), [System.Text.Encoding]::UTF8)
        Write-Log "Created new settings.json with hooks"
    }
}

function Install-ForUser {
    param([string]$UserHome)

    # Skip system/non-real-user profiles (no AppData = not a real user)
    if (-not (Test-Path (Join-Path $UserHome "AppData"))) { return $true }

    Write-Log "Starting Claude CLI hook installation..."
    Write-Log "Target user home: $UserHome"

    if (-not (Test-ClaudeCliInstalled -UserHome $UserHome)) {
        Write-Log "Claude CLI not detected - skipping hook installation"
        return $true
    }

    Write-Log "Claude CLI detected"

    $ConfigFile = Join-Path $UserHome ".akto-mcp-endpoint-shield\config\config.env"
    $GuardrailsUrl = Get-GuardrailsUrl -ConfigFile $ConfigFile -EnvVar $AktoDataIngestionUrl
    if (-not $GuardrailsUrl) {
        Write-Log "Warning: AKTO_DATA_INGESTION_URL not configured"
        $GuardrailsUrl = "https://guardrails.akto.io"
    }

    $DeviceId = Get-DeviceId
    if (-not $DeviceId) {
        Write-Log "Warning: Could not generate device ID"
        $DeviceId = "unknown-device"
    }

    Write-Log "Device ID: $DeviceId"
    Write-Log "Guardrails URL: $GuardrailsUrl"

    $claudeDir = Join-Path $UserHome ".claude"
    $ClaudeHooksDir = Join-Path $claudeDir "hooks"
    $ClaudeSettingsFile = Join-Path $claudeDir "settings.json"

    New-Item -ItemType Directory -Force -Path $claudeDir | Out-Null
    New-Item -ItemType Directory -Force -Path $ClaudeHooksDir | Out-Null
    icacls "$claudeDir" /grant "Users:(OI)(CI)F" /T /C /Q | Out-Null
    Write-Log "Created hooks directory: $ClaudeHooksDir"

    Write-Log "Downloading hook scripts from GitHub..."

    $promptPy = Join-Path $ClaudeHooksDir "akto-validate-prompt.py"
    if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto-validate-prompt.py" $promptPy)) {
        Write-ErrorLog "Failed to download akto-validate-prompt.py"
        return $false
    }
    Write-Log "Downloaded akto-validate-prompt.py"

    $responsePy = Join-Path $ClaudeHooksDir "akto-validate-response.py"
    if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto-validate-response.py" $responsePy)) {
        Write-ErrorLog "Failed to download akto-validate-response.py"
        return $false
    }
    Write-Log "Downloaded akto-validate-response.py"

    # akto_machine_id.py is fetched from master branch (has Windows-compatible import pwd fix)
    $machineIdPy = Join-Path $ClaudeHooksDir "akto_machine_id.py"
    $machineIdUrl = "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/claude-cli-hooks/akto_machine_id.py"
    if (-not (Get-FileFromUrl $machineIdUrl $machineIdPy)) {
        Write-ErrorLog "Failed to download akto_machine_id.py"
        return $false
    }
    Write-Log "Downloaded akto_machine_id.py"

    Write-Log "Creating wrapper scripts with environment variables..."
    New-WrapperScript -HookType "prompt" -IngestionUrl $GuardrailsUrl -DeviceId $DeviceId -HooksDir $ClaudeHooksDir
    Write-Log "Created akto-validate-prompt-wrapper.ps1"

    New-WrapperScript -HookType "response" -IngestionUrl $GuardrailsUrl -DeviceId $DeviceId -HooksDir $ClaudeHooksDir
    Write-Log "Created akto-validate-response-wrapper.ps1"

    Write-Log "Updating Claude CLI settings..."
    Update-ClaudeSettings -SettingsFile $ClaudeSettingsFile -HooksDir $ClaudeHooksDir
    Write-Log "Updated settings.json"

    Write-Log ""
    Write-Log "=========================================="
    Write-Log "Claude CLI hooks installed successfully!"
    Write-Log "=========================================="
    Write-Log ""
    Write-Log "Hooks location: $ClaudeHooksDir"
    Write-Log "Settings: $ClaudeSettingsFile"
    Write-Log "Guardrails URL: $GuardrailsUrl"

    return $true
}

# Main execution
if ($TargetUserHome -and (Test-Path $TargetUserHome)) {
    $success = Install-ForUser -UserHome $TargetUserHome
    exit $(if ($success) { 0 } else { 1 })
} else {
    $exitCode = 0
    $userDirs = Get-ChildItem "C:\Users" -Directory -ErrorAction SilentlyContinue

    foreach ($userDir in $userDirs) {
        $userName = $userDir.Name
        if ($userName -in @("Public", "Default", "Default User", "All Users")) { continue }
        if ($userName.StartsWith(".")) { continue }

        Write-Log "=== Processing user: $($userDir.FullName) ==="
        try {
            $success = Install-ForUser -UserHome $userDir.FullName
            if (-not $success) { $exitCode = 1 }
        } catch {
            Write-ErrorLog "Failed for $($userDir.FullName): $_"
            $exitCode = 1
        }
    }

    exit $exitCode
}