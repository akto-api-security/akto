# ========================================================================================
# MCP Endpoint Shield - Cursor IDE Hook Installer (Windows PowerShell)
# ========================================================================================
# Automatically installs Akto guardrails hooks for Cursor IDE if detected on Windows
# Downloads latest hooks from GitHub and configures Cursor hooks.json
# Mirrors: mcp-endpoint-shield/misc/windows/install_cursor_hooks.ps1 (master branch, full hook set)
# ========================================================================================

param(
    [string]$TargetUserHome = "",
    [string]$AktoDataIngestionUrl = "",
    [string]$AktoApiToken = ""
)

$ErrorActionPreference = "Stop"

$GITHUB_RAW_BASE    = "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/cursor-hooks"
$GITHUB_SHARED_BASE = "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/shared"

function Write-Log {
    param([string]$Message)
    Write-Host "[Cursor Hooks] $Message" -ForegroundColor Green
}

function Write-LogError {
    param([string]$Message)
    Write-Host "[Cursor Hooks] ERROR: $Message" -ForegroundColor Red
}

function Test-CursorInstalled {
    param([string]$UserHome)

    $cursorDir = Join-Path $UserHome ".cursor"
    $cursorProgramFiles = "C:\Program Files\Cursor"
    $cursorLocalAppData = Join-Path $UserHome "AppData\Local\Programs\Cursor"

    return (Test-Path $cursorDir) -or (Test-Path $cursorProgramFiles) -or (Test-Path $cursorLocalAppData)
}

function Get-IngestionUrl {
    param(
        [string]$UserHome,
        [string]$ProvidedUrl
    )

    if ($ProvidedUrl) {
        if ($ProvidedUrl -match "^[A-Z_]+=(.+)$") {
            return $Matches[1].Trim()
        }
        return $ProvidedUrl
    }

    $configFile = Join-Path $UserHome ".akto-mcp-endpoint-shield\config\config.env"
    if (Test-Path $configFile) {
        $content = Get-Content $configFile -ErrorAction SilentlyContinue
        $line = $content | Where-Object { $_ -match "^AKTO_API_BASE_URL=" } | Select-Object -First 1
        if ($line) { return ($line -split "=", 2)[1].Trim() }
        $line = $content | Where-Object { $_ -match "^AKTO_DATA_INGESTION_URL=" } | Select-Object -First 1
        if ($line) { return ($line -split "=", 2)[1].Trim() }
    }

    return "https://guardrails.akto.io"
}

function Get-ApiToken {
    param(
        [string]$UserHome,
        [string]$ProvidedToken
    )

    if ($ProvidedToken) { return $ProvidedToken }

    $configFile = Join-Path $UserHome ".akto-mcp-endpoint-shield\config\config.env"
    if (Test-Path $configFile) {
        $line = Get-Content $configFile -ErrorAction SilentlyContinue | Where-Object { $_ -match "^AKTO_API_TOKEN=" } | Select-Object -First 1
        if ($line) { return ($line -split "=", 2)[1].Trim() }
    }

    return ""
}

function Get-FlagValue {
    param([string]$UserHome, [string]$Key)
    $configFile = Join-Path $UserHome ".akto-mcp-endpoint-shield\config\config.env"
    if (Test-Path $configFile) {
        $line = (Get-Content $configFile -ErrorAction SilentlyContinue) |
                Where-Object { $_ -match "^$Key=" } | Select-Object -First 1
        if ($line) { return ($line -split "=", 2)[1].Trim() }
    }
    return "true"  # default: enabled (backward compat)
}

function Get-DeviceId {
    $hostname = $env:COMPUTERNAME
    if (-not $hostname) {
        try { $hostname = [System.Net.Dns]::GetHostName() } catch {}
    }
    $deviceName = ""
    if ($hostname) {
        $deviceName = $hostname.ToLower() -replace '[^a-z0-9]', '-'
    }

    $machineId = ""
    try {
        $guid = (Get-ItemProperty -Path "HKLM:\SOFTWARE\Microsoft\Cryptography" -Name MachineGuid -ErrorAction Stop).MachineGuid
        if ($guid) {
            $machineId = $guid.Replace("-", "").ToLower()
        }
    } catch {}

    if (-not $machineId) {
        try {
            $mac = (Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object { $_.Status -eq "Up" } | Select-Object -First 1).MacAddress
            if ($mac) { $machineId = $mac.Replace("-", "").Replace(":", "").ToLower() }
        } catch {}
    }

    $shortId = if ($machineId.Length -ge 8) { $machineId.Substring(0, 8) } else { $machineId }

    if ($deviceName -and $shortId) { return "$deviceName-$shortId" }
    if ($deviceName)               { return $deviceName }
    if ($machineId)                { return $machineId }
    return "unknown-device"
}

function Get-FileFromUrl {
    param(
        [string]$Url,
        [string]$Destination
    )

    try {
        Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing -Headers @{"Cache-Control"="no-cache";"Pragma"="no-cache"}
        return $true
    } catch {
        Write-Log "Invoke-WebRequest failed, trying curl.exe fallback..."
    }

    try {
        $result = & curl.exe -fsSL -H "Cache-Control: no-cache" -H "Pragma: no-cache" $Url -o $Destination 2>&1
        if ($LASTEXITCODE -eq 0) {
            return $true
        }
        Write-LogError "curl.exe failed (exit $LASTEXITCODE): $result"
        return $false
    } catch {
        Write-LogError "Failed to download from $Url : $_"
        return $false
    }
}

function New-WrapperScript {
    param(
        [string]$ScriptBaseName,
        [string]$IngestionUrl,
        [string]$ApiToken,
        [string]$DeviceId,
        [string]$HooksDir,
        [string]$Connector,
        [string]$GuardFlag
    )

    $wrapperFile = Join-Path $HooksDir "$ScriptBaseName-wrapper.ps1"
    $pythonScript = (Join-Path $HooksDir "$ScriptBaseName.py").Replace("\", "/")

    $wrapperContent = @"
# Auto-generated wrapper for Akto guardrails hook

`$_akto_cfg = Join-Path `$env:USERPROFILE ".akto-mcp-endpoint-shield\config\config.env"
if (Test-Path `$_akto_cfg) {
    Get-Content `$_akto_cfg | ForEach-Object {
        if (`$_ -match '^([A-Z_][A-Z0-9_]*)=(.*)$') {
            [System.Environment]::SetEnvironmentVariable(`$Matches[1], `$Matches[2], 'Process')
        }
    }
}
if (`$env:$GuardFlag -eq "false") { exit 0 }

`$env:MODE = "atlas"
$(if ($IngestionUrl) { "`$env:AKTO_DATA_INGESTION_URL = `"$IngestionUrl`"" })
`$env:AKTO_API_TOKEN = "$ApiToken"
`$env:AKTO_SYNC_MODE = "true"
`$env:AKTO_TIMEOUT = "15"
`$env:AKTO_CONNECTOR = "$Connector"
`$env:CONTEXT_SOURCE = "ENDPOINT"
`$env:DEVICE_ID = "$DeviceId"
`$env:PYTHONIOENCODING = "utf-8"

[Console]::Error.WriteLine("[Cursor Hook] Data Ingestion URL: `$env:AKTO_DATA_INGESTION_URL")
[Console]::Error.WriteLine("[Cursor Hook] Device ID: `$env:DEVICE_ID")

`$stdinContent = [Console]::In.ReadToEnd()
`$jsonStart = `$stdinContent.IndexOf('{')
`$jsonContent = if (`$jsonStart -ge 0) { `$stdinContent.Substring(`$jsonStart) } else { '{}' }
`$tempFile = [System.IO.Path]::GetTempFileName()
try {
    [System.IO.File]::WriteAllText(`$tempFile, `$jsonContent, (New-Object System.Text.UTF8Encoding(`$false)))
    cmd /c "python ""$pythonScript"" < ""`$tempFile"""
} finally {
    Remove-Item `$tempFile -ErrorAction SilentlyContinue
}
"@
    [System.IO.File]::WriteAllText($wrapperFile, $wrapperContent, (New-Object System.Text.UTF8Encoding($false)))
    Write-Log "Created wrapper: $ScriptBaseName-wrapper.ps1"
}

function New-HookWrapperScript {
    param(
        [string]$IngestionUrl,
        [string]$ApiToken,
        [string]$DeviceId,
        [string]$HooksDir,
        [string]$Connector
    )

    $wrapperFile  = Join-Path $HooksDir "akto-hook-wrapper.ps1"
    $hooksScript  = (Join-Path $HooksDir "akto-hooks.py").Replace("\", "/")

    $wrapperContent = @"
param([string]`$EventType)

`$_akto_cfg = Join-Path `$env:USERPROFILE ".akto-mcp-endpoint-shield\config\config.env"
if (Test-Path `$_akto_cfg) {
    Get-Content `$_akto_cfg | ForEach-Object {
        if (`$_ -match '^([A-Z_][A-Z0-9_]*)=(.*)$') {
            [System.Environment]::SetEnvironmentVariable(`$Matches[1], `$Matches[2], 'Process')
        }
    }
}
if (`$env:ENABLE_MCP_HOOKS_CURSOR -eq "false") { exit 0 }

`$env:MODE = "atlas"
$(if ($IngestionUrl) { "`$env:AKTO_DATA_INGESTION_URL = `"$IngestionUrl`"" })
`$env:AKTO_API_TOKEN = "$ApiToken"
`$env:AKTO_SYNC_MODE = "true"
`$env:AKTO_TIMEOUT = "15"
`$env:AKTO_CONNECTOR = "$Connector"
`$env:CONTEXT_SOURCE = "ENDPOINT"
`$env:DEVICE_ID = "$DeviceId"
`$env:PYTHONIOENCODING = "utf-8"

`$stdinContent = [Console]::In.ReadToEnd()
`$jsonStart = `$stdinContent.IndexOf('{')
`$jsonContent = if (`$jsonStart -ge 0) { `$stdinContent.Substring(`$jsonStart) } else { '{}' }
`$tempFile = [System.IO.Path]::GetTempFileName()
try {
    [System.IO.File]::WriteAllText(`$tempFile, `$jsonContent, (New-Object System.Text.UTF8Encoding(`$false)))
    cmd /c "python ""$hooksScript"" `$EventType < ""`$tempFile"""
} finally {
    Remove-Item `$tempFile -ErrorAction SilentlyContinue
}
"@
    [System.IO.File]::WriteAllText($wrapperFile, $wrapperContent, (New-Object System.Text.UTF8Encoding($false)))
    Write-Log "Created akto-hook-wrapper.ps1"
}

function Update-CursorHooks {
    param(
        [string]$UserHome,
        [string]$HooksDir
    )

    $hooksFile = Join-Path $UserHome ".cursor\hooks.json"

    $makeEntry = {
        param([string]$WrapperName)
        $path = (Join-Path $HooksDir $WrapperName).Replace("\", "/")
        @{ command = "powershell.exe -ExecutionPolicy Bypass -File `"$path`""; type = "command"; timeout = 10 }
    }

    $wrapperPath = (Join-Path $HooksDir "akto-hook-wrapper.ps1").Replace("\", "/")
    $makeLifecycleEntry = {
        param([string]$EventType)
        @{ command = "powershell.exe -ExecutionPolicy Bypass -File `"$wrapperPath`" $EventType"; type = "command"; timeout = 10 }
    }

    $newHooksConfig = @{
        version = 1
        hooks = @{
            beforeSubmitPrompt  = @(& $makeEntry "akto-validate-chat-prompt-wrapper.ps1")
            afterAgentResponse  = @(& $makeEntry "akto-validate-chat-response-wrapper.ps1")
            beforeMCPExecution  = @(& $makeEntry "akto-validate-mcp-request-wrapper.ps1")
            afterMCPExecution   = @(& $makeEntry "akto-validate-mcp-response-wrapper.ps1")
            sessionStart        = @(& $makeLifecycleEntry "sessionStart")
            sessionEnd          = @(& $makeLifecycleEntry "sessionEnd")
            preToolUse          = @(& $makeLifecycleEntry "preToolUse")
            postToolUse         = @(& $makeLifecycleEntry "postToolUse")
            postToolUseFailure  = @(& $makeLifecycleEntry "postToolUseFailure")
            subagentStart       = @(& $makeLifecycleEntry "subagentStart")
            subagentStop        = @(& $makeLifecycleEntry "subagentStop")
            beforeShellExecution = @(& $makeLifecycleEntry "beforeShellExecution")
            afterShellExecution = @(& $makeLifecycleEntry "afterShellExecution")
            beforeReadFile      = @(& $makeLifecycleEntry "beforeReadFile")
            afterFileEdit       = @(& $makeLifecycleEntry "afterFileEdit")
            afterAgentThought   = @(& $makeLifecycleEntry "afterAgentThought")
            stop                = @(& $makeLifecycleEntry "stop")
            preCompact          = @(& $makeLifecycleEntry "preCompact")
            beforeTabFileRead   = @(& $makeLifecycleEntry "beforeTabFileRead")
            afterTabFileEdit    = @(& $makeLifecycleEntry "afterTabFileEdit")
        }
    }

    if (Test-Path $hooksFile) {
        Write-Log "Existing hooks.json found, backing up..."
        Copy-Item $hooksFile "$hooksFile.backup" -Force
    }
    [System.IO.File]::WriteAllText($hooksFile, ($newHooksConfig | ConvertTo-Json -Depth 10), (New-Object System.Text.UTF8Encoding($false)))
    Write-Log "Created hooks.json with 20 hooks"
}

function Install-ForUser {
    param([string]$UserHome)

    if (-not (Test-Path (Join-Path $UserHome "AppData"))) { return $true }

    try {
        Write-Log "Starting Cursor IDE hook installation..."
        Write-Log "Target user home: $UserHome"

        if (-not (Test-CursorInstalled $UserHome)) {
            Write-Log "Cursor IDE not detected - skipping hook installation"
            return $true
        }

        Write-Log "Cursor IDE detected"

        $promptEnabled = Get-FlagValue $UserHome "ENABLE_PROMPT_HOOKS_CURSOR"
        $mcpEnabled    = Get-FlagValue $UserHome "ENABLE_MCP_HOOKS_CURSOR"
        if ($promptEnabled -eq "false" -and $mcpEnabled -eq "false") {
            Write-Log "Both ENABLE_PROMPT_HOOKS_CURSOR and ENABLE_MCP_HOOKS_CURSOR are false -- skipping"
            return $true
        }

        $ingestionUrl = Get-IngestionUrl $UserHome $AktoDataIngestionUrl
        if (-not $ingestionUrl) {
            Write-Log "Warning: ingestion URL not configured"
            $ingestionUrl = "https://your-ingestion-url.akto.io"
        }

        $apiToken = Get-ApiToken $UserHome $AktoApiToken

        $deviceId = Get-DeviceId
        if (-not $deviceId) {
            Write-Log "Warning: Could not generate device ID"
            $deviceId = "unknown-device"
        }

        Write-Log "Device ID: $deviceId"

        $hooksDir = Join-Path $UserHome ".cursor\hooks\akto"
        New-Item -ItemType Directory -Force -Path $hooksDir | Out-Null
        Write-Log "Created hooks directory: $hooksDir"

        $cursorDir = Join-Path $UserHome ".cursor"
        icacls "$cursorDir" /grant "Users:(OI)(CI)RX" /T /C /Q | Out-Null
        Write-Log "Set permissions on $cursorDir"

        Write-Log "Downloading hook scripts from GitHub..."

        $ingestionUtilPy = Join-Path $hooksDir "akto_ingestion_utility.py"
        if (-not (Get-FileFromUrl "$GITHUB_SHARED_BASE/akto_ingestion_utility.py" $ingestionUtilPy)) {
            Write-LogError "Failed to download akto_ingestion_utility.py"
            return $false
        }
        Write-Log "Downloaded akto_ingestion_utility.py"

        $pyFiles = @(
            "akto-validate-chat-prompt.py",
            "akto-validate-chat-response.py",
            "akto-validate-mcp-request.py",
            "akto-validate-mcp-response.py",
            "akto-hooks.py",
            "akto_machine_id.py"
        )

        foreach ($file in $pyFiles) {
            $url = "$GITHUB_RAW_BASE/$file"
            $dest = Join-Path $hooksDir $file
            if (-not (Get-FileFromUrl $url $dest)) {
                Write-LogError "Failed to download $file"
                return $false
            }
            Write-Log "Downloaded $file"
        }

        # akto_machine_id.py is Unix-only ("import pwd") — patch for Windows compatibility
        $machineIdFile = Join-Path $hooksDir "akto_machine_id.py"
        if (Test-Path $machineIdFile) {
            $content = Get-Content $machineIdFile -Raw
            if ($content -match '(?m)^import pwd\s*$') {
                $patched = $content -replace '(?m)^import pwd\s*$', "try:`n    import pwd`nexcept ImportError:`n    pwd = None  # Not available on Windows"
                [System.IO.File]::WriteAllText($machineIdFile, $patched, (New-Object System.Text.UTF8Encoding($false)))
                Write-Log "Patched akto_machine_id.py for Windows compatibility"
            }
        }

        Write-Log "Generating PS1 wrapper scripts..."
        $connector = "cursor"
        $scriptGuardMap = [ordered]@{
            "akto-validate-chat-prompt"   = "ENABLE_PROMPT_HOOKS_CURSOR"
            "akto-validate-chat-response" = "ENABLE_PROMPT_HOOKS_CURSOR"
            "akto-validate-mcp-request"   = "ENABLE_MCP_HOOKS_CURSOR"
            "akto-validate-mcp-response"  = "ENABLE_MCP_HOOKS_CURSOR"
        }
        foreach ($script in $scriptGuardMap.Keys) {
            New-WrapperScript $script $ingestionUrl $apiToken $deviceId $hooksDir $connector $scriptGuardMap[$script]
        }
        New-HookWrapperScript $ingestionUrl $apiToken $deviceId $hooksDir $connector

        Write-Log "Generating hooks.json..."
        Update-CursorHooks $UserHome $hooksDir
        Write-Log "Generated hooks.json"

        Write-Log ""
        Write-Log "=========================================="
        Write-Log "Cursor IDE hooks installed successfully!"
        Write-Log "=========================================="
        Write-Log ""
        Write-Log "Hooks location: $hooksDir"
        Write-Log "Configuration: $(Join-Path $UserHome '.cursor\hooks.json')"

        return $true
    } catch {
        Write-LogError "Installation failed for $UserHome : $_"
        return $false
    }
}

# Main execution
# If an explicit TARGET_USER_HOME was passed, use it directly (RTR dispatch).
# Otherwise scan C:\Users\* -- this is the correct path when running as SYSTEM
# since $env:USERPROFILE under SYSTEM resolves to the system profile, not a real user.
if ($TargetUserHome -and (Test-Path $TargetUserHome)) {
    $success = Install-ForUser $TargetUserHome
    exit $(if ($success) { 0 } else { 1 })
} else {
    $exitCode = 0
    $userProfiles = Get-ChildItem "C:\Users" -Directory -ErrorAction SilentlyContinue

    foreach ($profile in $userProfiles) {
        $userName = $profile.Name
        if ($userName -in @("Public", "Default", "Default User", "All Users")) {
            continue
        }
        if ($userName.StartsWith(".")) {
            continue
        }

        $userHome = $profile.FullName
        Write-Log "=== Processing user: $userHome ==="
        $success = Install-ForUser $userHome
        if (-not $success) {
            $exitCode = 1
        }
    }

    exit $exitCode
}
