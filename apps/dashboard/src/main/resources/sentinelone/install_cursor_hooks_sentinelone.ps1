# ========================================================================================
# MCP Endpoint Shield - Cursor IDE Hook Installer (Windows PowerShell)
# ========================================================================================
# Automatically installs Akto guardrails hooks for Cursor IDE if detected on Windows
# Downloads latest hooks from GitHub and configures Cursor hooks.json
# ========================================================================================

param(
    [string]$TargetUserHome = "",
    [string]$AktoDataIngestionUrl = ""
)

$ErrorActionPreference = "Stop"

$GITHUB_RAW_BASE = "https://raw.githubusercontent.com/akto-api-security/akto/agent-hooks/apps/mcp-endpoint-shield/cursor-hooks"

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
        # Strip KEY=VALUE prefix if caller passed the full env var line (e.g. "AKTO_DATA_INGESTION_URL=https://...")
        if ($ProvidedUrl -match "^[A-Z_]+=(.+)$") {
            return $Matches[1].Trim()
        }
        return $ProvidedUrl
    }
    
    $configFile = Join-Path $UserHome ".akto-mcp-endpoint-shield\config\config.env"
    if (Test-Path $configFile) {
        $content = Get-Content $configFile -ErrorAction SilentlyContinue
        $urlLine = $content | Where-Object { $_ -match "^AKTO_DATA_INGESTION_URL=" }
        if ($urlLine) {
            return ($urlLine -split "=", 2)[1].Trim()
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
    } catch {}
    
    try {
        $mac = (Get-NetAdapter | Where-Object {$_.Status -eq "Up"} | Select-Object -First 1).MacAddress
        if ($mac) {
            return $mac.Replace("-", "").Replace(":", "").ToLower()
        }
    } catch {}
    
    return ""
}

function Get-FileFromUrl {
    param(
        [string]$Url,
        [string]$Destination
    )

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
        Write-LogError "curl.exe failed (exit $LASTEXITCODE): $result"
        return $false
    } catch {
        Write-LogError "Failed to download from $Url : $_"
        return $false
    }
}

function New-WrapperScript {
    param(
        [string]$HookType,
        [string]$IngestionUrl,
        [string]$DeviceId,
        [string]$HooksDir,
        [string]$Connector
    )
    
    $wrapperFile = Join-Path $HooksDir "akto-validate-chat-$HookType-wrapper.ps1"
    $templateUrl = "$GITHUB_RAW_BASE/akto-validate-chat-$HookType-wrapper.ps1"
    $pythonScript = Join-Path $HooksDir "akto-validate-chat-$HookType.py"
    
    $tempFile = "$wrapperFile.tmp"
    if (-not (Get-FileFromUrl $templateUrl $tempFile)) {
        Write-LogError "Failed to download wrapper template for $HookType, creating locally..."
        Write-Log "Creating wrapper with URL: $IngestionUrl"
        
        $wrapperContent = @"
# Auto-generated wrapper for Akto guardrails hook
# Data Ingestion URL: $IngestionUrl

`$env:MODE = "atlas"
`$env:AKTO_DATA_INGESTION_URL = "$IngestionUrl"
`$env:AKTO_SYNC_MODE = "true"
`$env:AKTO_TIMEOUT = "5"
`$env:AKTO_CONNECTOR = "$Connector"
`$env:CONTEXT_SOURCE = "ENDPOINT"
`$env:DEVICE_ID = "$DeviceId"

[Console]::Error.WriteLine("[Cursor Hook] Data Ingestion URL: `$env:AKTO_DATA_INGESTION_URL")
[Console]::Error.WriteLine("[Cursor Hook] Device ID: `$env:DEVICE_ID")

# Read stdin from Cursor, strip preamble bytes before JSON, write to temp file
# then use Python -c to override sys.stdin with the file (avoids all Windows pipe issues)
`$stdinContent = [Console]::In.ReadToEnd()
`$jsonStart = `$stdinContent.IndexOf('{')
if (`$jsonStart -gt 0) { `$stdinContent = `$stdinContent.Substring(`$jsonStart) }
`$tempIn = [System.IO.Path]::GetTempFileName()
[System.IO.File]::WriteAllText(`$tempIn, `$stdinContent, [System.Text.Encoding]::UTF8)
`$hooksDir = Split-Path -Parent "$pythonScript"
`$pyCode = "import sys; sys.path.insert(0, r'`$hooksDir'); sys.stdin = open(r'`$tempIn', encoding='utf-8-sig'); exec(open(r'$pythonScript', encoding='utf-8').read())"
`$output = & python -c `$pyCode 2>&1
Remove-Item `$tempIn -ErrorAction SilentlyContinue
foreach (`$line in `$output) {
    if (`$line -match '^\{') { Write-Output `$line } else { [Console]::Error.WriteLine(`$line) }
}
"@
        [System.IO.File]::WriteAllText($wrapperFile, $wrapperContent, [System.Text.Encoding]::UTF8)
        return
    }

    $content = Get-Content $tempFile -Raw
    $content = $content -replace '\{\{AKTO_DATA_INGESTION_URL\}\}', $IngestionUrl
    $content = $content -replace '\{\{DEVICE_ID \(optional\)\}\}', $DeviceId
    # Ensure Write-Host lines go to stderr so Cursor can parse stdout as JSON
    $content = $content -replace 'Write-Host "\[Cursor Hook\][^"]*"[^\n]*\n?', ''
    [System.IO.File]::WriteAllText($wrapperFile, $content, [System.Text.Encoding]::UTF8)
    Remove-Item $tempFile -ErrorAction SilentlyContinue
    
    Write-Log "Wrapper configured with URL: $IngestionUrl"
}

function Update-CursorHooks {
    param(
        [string]$UserHome,
        [string]$HooksDir
    )
    
    $hooksFile = Join-Path $UserHome ".cursor\hooks.json"
    $promptWrapper = Join-Path $HooksDir "akto-validate-chat-prompt-wrapper.ps1"
    $responseWrapper = Join-Path $HooksDir "akto-validate-chat-response-wrapper.ps1"
    
    # Convert Windows paths to forward slashes for JSON
    $promptWrapperJson = $promptWrapper.Replace("\", "/")
    $responseWrapperJson = $responseWrapper.Replace("\", "/")
    
    $newHooksConfig = @{
        version = 1
        hooks = @{
            beforeSubmitPrompt = @(
                @{
                    command = "powershell.exe -ExecutionPolicy Bypass -File `"$promptWrapperJson`""
                    type = "command"
                    timeout = 10
                }
            )
            afterAgentResponse = @(
                @{
                    command = "powershell.exe -ExecutionPolicy Bypass -File `"$responseWrapperJson`""
                    type = "command"
                    timeout = 10
                }
            )
        }
    }
    
    if (Test-Path $hooksFile) {
        Write-Log "Existing hooks.json found, backing up..."
        Copy-Item $hooksFile "$hooksFile.backup" -Force
        
        try {
            $existingConfig = Get-Content $hooksFile -Raw | ConvertFrom-Json

            if (-not $existingConfig.hooks) {
                $existingConfig | Add-Member -NotePropertyName "hooks" -NotePropertyValue @{} -Force
            }
            if (-not $existingConfig.hooks.beforeSubmitPrompt) {
                $existingConfig.hooks | Add-Member -NotePropertyName "beforeSubmitPrompt" -NotePropertyValue @() -Force
            }
            if (-not $existingConfig.hooks.afterAgentResponse) {
                $existingConfig.hooks | Add-Member -NotePropertyName "afterAgentResponse" -NotePropertyValue @() -Force
            }

            $existingConfig.version = $newHooksConfig.version
            $existingConfig.hooks.beforeSubmitPrompt += $newHooksConfig.hooks.beforeSubmitPrompt
            $existingConfig.hooks.afterAgentResponse += $newHooksConfig.hooks.afterAgentResponse

            $merged = $existingConfig | ConvertTo-Json -Depth 10
            [System.IO.File]::WriteAllText($hooksFile, $merged, [System.Text.Encoding]::UTF8)
            Write-Log "Merged hooks into existing hooks.json"
        } catch {
            $json = $newHooksConfig | ConvertTo-Json -Depth 10
            [System.IO.File]::WriteAllText($hooksFile, $json, [System.Text.Encoding]::UTF8)
            Write-Log "Created new hooks.json (merge failed)"
        }
    } else {
        $json = $newHooksConfig | ConvertTo-Json -Depth 10
        [System.IO.File]::WriteAllText($hooksFile, $json, [System.Text.Encoding]::UTF8)
        Write-Log "Created new hooks.json"
    }
}

function Install-ForUser {
    param([string]$UserHome)
    
    # Skip system/non-real-user profiles (no AppData = not a real user)
    if (-not (Test-Path (Join-Path $UserHome "AppData"))) { return $true }

    try {
        Write-Log "Starting Cursor IDE hook installation..."
        Write-Log "Target user home: $UserHome"

        if (-not (Test-CursorInstalled $UserHome)) {
            Write-Log "Cursor IDE not detected - skipping hook installation"
            return $true
        }

        Write-Log "Cursor IDE detected"

        $ingestionUrl = Get-IngestionUrl $UserHome $AktoDataIngestionUrl
        if (-not $ingestionUrl) {
            Write-Log "Warning: AKTO_GUARDRAILS_URL not configured"
            $ingestionUrl = "https://your-ingestion-url.akto.io"
        }

        $deviceId = Get-DeviceId
        if (-not $deviceId) {
            Write-Log "Warning: Could not generate device ID"
            $deviceId = "unknown-device"
        }

        Write-Log "Device ID: $deviceId"

        $hooksDir = Join-Path $UserHome ".cursor\hooks\akto"
        New-Item -ItemType Directory -Force -Path $hooksDir | Out-Null
        Write-Log "Created hooks directory: $hooksDir"

        # Grant Users read/execute on the .cursor folder so hooks run under the real user
        $cursorDir = Join-Path $UserHome ".cursor"
        icacls "$cursorDir" /grant "Users:(OI)(CI)RX" /T /C /Q | Out-Null
        Write-Log "Set permissions on $cursorDir"
        
        Write-Log "Downloading hook scripts from GitHub..."
        
        $files = @(
            "akto-validate-chat-prompt.py",
            "akto-validate-chat-response.py"
        )

        foreach ($file in $files) {
            $url = "$GITHUB_RAW_BASE/$file"
            $dest = Join-Path $hooksDir $file
            if (-not (Get-FileFromUrl $url $dest)) {
                Write-LogError "Failed to download $file"
                return $false
            }
            Write-Log "Downloaded $file"
        }

        # akto_machine_id.py is fetched from master branch (has Windows-compatible import pwd fix)
        $machineIdUrl = "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/cursor-hooks/akto_machine_id.py"
        $machineIdDest = Join-Path $hooksDir "akto_machine_id.py"
        if (-not (Get-FileFromUrl $machineIdUrl $machineIdDest)) {
            Write-LogError "Failed to download akto_machine_id.py"
            return $false
        }
        Write-Log "Downloaded akto_machine_id.py"
        
        Write-Log "Creating wrapper scripts with environment variables..."
        
        $connector = "cursor"
        New-WrapperScript "prompt" $ingestionUrl $deviceId $hooksDir $connector
        Write-Log "Created akto-validate-chat-prompt-wrapper.ps1"
        
        New-WrapperScript "response" $ingestionUrl $deviceId $hooksDir $connector
        Write-Log "Created akto-validate-chat-response-wrapper.ps1"
        
        Write-Log "Updating Cursor IDE hooks.json..."
        Update-CursorHooks $UserHome $hooksDir
        Write-Log "Updated hooks.json"
        
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
# If an explicit TARGET_USER_HOME was passed, use it directly.
# Otherwise scan C:\Users\* - this is the correct path when running as SYSTEM
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