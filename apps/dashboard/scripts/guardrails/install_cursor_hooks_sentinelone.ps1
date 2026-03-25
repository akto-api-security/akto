# ========================================================================================
# MCP Endpoint Shield - Cursor IDE Hook Installer (Windows PowerShell)
# ========================================================================================
# Automatically installs Akto guardrails hooks for Cursor IDE if detected on Windows
# Downloads latest hooks from GitHub and configures Cursor hooks.json
# ========================================================================================

param(
    [string]$TargetUserHome = $env:USERPROFILE,
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
    $cursorLocalAppData = Join-Path $env:LOCALAPPDATA "Programs\Cursor"
    
    return (Test-Path $cursorDir) -or (Test-Path $cursorProgramFiles) -or (Test-Path $cursorLocalAppData)
}

function Get-IngestionUrl {
    param(
        [string]$UserHome,
        [string]$ProvidedUrl
    )
    
    if ($ProvidedUrl) {
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
    
    return "https://1726615470-guardrails.akto.io"
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
    
    try {
        $webClient = New-Object System.Net.WebClient
        $webClient.Headers.Add("Cache-Control", "no-cache")
        $webClient.Headers.Add("Pragma", "no-cache")
        $webClient.DownloadFile($Url, $Destination)
        return $true
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
        
        $wrapperContent = @"
`$env:MODE = "atlas"
`$env:AKTO_DATA_INGESTION_URL = "$IngestionUrl"
`$env:AKTO_SYNC_MODE = "true"
`$env:AKTO_TIMEOUT = "5"
`$env:AKTO_CONNECTOR = "$Connector"
`$env:CONTEXT_SOURCE = "ENDPOINT"
`$env:DEVICE_ID = "$DeviceId"
& python "$pythonScript" `$args
"@
        Set-Content -Path $wrapperFile -Value $wrapperContent -Encoding UTF8
        return
    }
    
    $content = Get-Content $tempFile -Raw
    $content = $content -replace '\{\{AKTO_DATA_INGESTION_URL\}\}', $IngestionUrl
    $content = $content -replace '\{\{DEVICE_ID \(optional\)\}\}', $DeviceId
    Set-Content -Path $wrapperFile -Value $content -Encoding UTF8
    Remove-Item $tempFile -ErrorAction SilentlyContinue
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
            
            # Merge hooks
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
            
            $existingConfig | ConvertTo-Json -Depth 10 | Set-Content $hooksFile -Encoding UTF8
            Write-Log "Merged hooks into existing hooks.json"
        } catch {
            $newHooksConfig | ConvertTo-Json -Depth 10 | Set-Content $hooksFile -Encoding UTF8
            Write-Log "Created new hooks.json (merge failed)"
        }
    } else {
        $newHooksConfig | ConvertTo-Json -Depth 10 | Set-Content $hooksFile -Encoding UTF8
        Write-Log "Created new hooks.json"
    }
}

function Install-ForUser {
    param([string]$UserHome)
    
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
            Write-Log "Warning: AKTO_DATA_INGESTION_URL not configured"
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
        
        Write-Log "Downloading hook scripts from GitHub..."
        
        $files = @(
            "akto-validate-chat-prompt.py",
            "akto-validate-chat-response.py",
            "akto_machine_id.py"
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
if ($TargetUserHome -and $TargetUserHome -ne "") {
    if (-not (Test-Path $TargetUserHome)) {
        Write-LogError "TARGET_USER_HOME is not a valid directory: $TargetUserHome"
        exit 1
    }
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
