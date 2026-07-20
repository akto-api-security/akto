# ========================================================================================
# MCP Endpoint Shield - GitHub Copilot VSCode Hook Installer (Windows PowerShell)
# ========================================================================================
# Installs Akto guardrails hooks for GitHub Copilot in VSCode at user level.
# Hook config is written to %USERPROFILE%\.copilot\hooks\akto-hooks.json
# which VSCode reads automatically for ALL workspaces -- no project-level config needed.
#
# Scripts are downloaded from:
#   https://github.com/akto-api-security/akto/tree/master/apps/mcp-endpoint-shield/github-cli-hooks
#
# Controlled by flags in config.env:
#   ENABLE_PROMPT_HOOKS_VSCODE_COPILOT=true  -- installs UserPromptSubmit/Stop hooks
#   ENABLE_MCP_HOOKS_VSCODE_COPILOT=true     -- installs PreToolUse/PostToolUse hooks
#
# Mirrors: mcp-endpoint-shield/misc/windows/install_vscode_copilot_hooks.ps1 (master branch, full hook set)
# ========================================================================================

param(
    [string]$TargetUserHome = "",
    [string]$AktoDataIngestionUrl = "",
    [string]$AktoApiToken = ""
)

$ErrorActionPreference = "Stop"

$GITHUB_RAW_BASE = "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/github-cli-hooks"

function Write-Log {
    param([string]$Message)
    Write-Host "[Copilot VSCode Hooks] $Message" -ForegroundColor Cyan
}

function Write-LogError {
    param([string]$Message)
    Write-Host "[Copilot VSCode Hooks] ERROR: $Message" -ForegroundColor Red
}

# VSCode detection: check %APPDATA%\Code -- always present once VSCode has been run,
# regardless of where the binary is installed (custom path, system-wide, portable, etc.)
function Test-VSCodeInstalled {
    param([string]$UserHome)

    $appData = Join-Path $UserHome "AppData\Roaming"
    return (Test-Path (Join-Path $appData "Code")) -or (Test-Path (Join-Path $appData "Code - Insiders"))
}

# Returns the VSCode data dir (%APPDATA%\Code or %APPDATA%\Code - Insiders)
function Get-VSCodeDataDir {
    param([string]$UserHome)

    $appData = Join-Path $UserHome "AppData\Roaming"
    $codeDir = Join-Path $appData "Code"
    if (Test-Path $codeDir) { return $codeDir }
    return Join-Path $appData "Code - Insiders"
}

function Get-ConfigFlag {
    param([string]$ConfigFile, [string]$FlagName)

    if (-not (Test-Path $ConfigFile)) { return $null }
    $line = Get-Content $ConfigFile -ErrorAction SilentlyContinue | Where-Object { $_ -match "^${FlagName}=" }
    if ($line) { return ($line -split "=", 2)[1].Trim() }
    return $null
}

function Test-PromptHooksEnabled {
    param([string]$ConfigFile)
    $val = Get-ConfigFlag $ConfigFile "ENABLE_PROMPT_HOOKS_VSCODE_COPILOT"
    return $val -eq "true"  # default: disabled (opt-in model)
}

function Test-McpHooksEnabled {
    param([string]$ConfigFile)
    $val = Get-ConfigFlag $ConfigFile "ENABLE_MCP_HOOKS_VSCODE_COPILOT"
    return $val -eq "true"  # default: disabled (opt-in model)
}

function Get-IngestionUrl {
    param([string]$ConfigFile, [string]$ProvidedUrl)

    if ($ProvidedUrl) { return $ProvidedUrl }
    if (Test-Path $ConfigFile) {
        $content = Get-Content $ConfigFile -Raw
        if ($content -match '(?m)^AKTO_API_BASE_URL=(.+)')        { return $Matches[1].Trim() }
        if ($content -match '(?m)^AKTO_DATA_INGESTION_URL=(.+)') { return $Matches[1].Trim() }
    }
    return "https://guardrails.akto.io"
}

function Get-ApiToken {
    param([string]$ConfigFile, [string]$ProvidedToken)

    if ($ProvidedToken) { return $ProvidedToken }
    if (Test-Path $ConfigFile) {
        $content = Get-Content $ConfigFile -Raw
        if ($content -match '(?m)^AKTO_API_TOKEN=(.+)') { return $Matches[1].Trim() }
    }
    return ""
}

function Get-DeviceId {
    # Matches Go's GetDeviceLabel(): "{hostname}-{first8ofMachineID}"

    $hostname = $env:COMPUTERNAME
    if (-not $hostname) { try { $hostname = [System.Net.Dns]::GetHostName() } catch {} }
    $deviceName = if ($hostname) { $hostname.ToLower() -replace '[^a-z0-9]', '-' } else { "" }

    $machineId = ""
    try {
        $guid = (Get-ItemProperty -Path "HKLM:\SOFTWARE\Microsoft\Cryptography" -Name MachineGuid -ErrorAction Stop).MachineGuid
        if ($guid) { $machineId = $guid.Replace("-", "").ToLower() }
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
    param([string]$Url, [string]$Destination)

    try {
        Invoke-WebRequest -Uri $Url -OutFile $Destination -UseBasicParsing -Headers @{"Cache-Control"="no-cache";"Pragma"="no-cache"}
        return $true
    } catch {
        Write-Log "Invoke-WebRequest failed, trying curl.exe..."
    }

    try {
        $result = & curl.exe -fsSL -H "Cache-Control: no-cache" -H "Pragma: no-cache" $Url -o $Destination 2>&1
        if ($LASTEXITCODE -eq 0) { return $true }
        Write-LogError "curl.exe failed (exit $LASTEXITCODE): $result"
        return $false
    } catch {
        Write-LogError "Failed to download $Url : $_"
        return $false
    }
}

# Generate wrapper .ps1 with absolute path to Python script.
# The Python scripts auto-detect connector type via AKTO_CONNECTOR env var.
function New-WrapperScript {
    param(
        [string]$WrapperFile,
        [string]$PythonScript,
        [string]$IngestionUrl,
        [string]$ApiToken,
        [string]$DeviceId,
        [string]$GuardFlag   # e.g. "ENABLE_PROMPT_HOOKS_VSCODE_COPILOT" or "ENABLE_MCP_HOOKS_VSCODE_COPILOT"
    )

    $pythonScriptFwd = $PythonScript.Replace("\", "/")

    $wrapperContent = @"
# Auto-generated wrapper for Akto Copilot VSCode guardrails hook

# Live flag guard -- re-read config.env on every invocation
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
`$env:AKTO_CONNECTOR = "vscode"
`$env:CONTEXT_SOURCE = "ENDPOINT"
`$env:DEVICE_ID = "$DeviceId"
`$env:PYTHONIOENCODING = "utf-8"
`$env:PYTHONUTF8 = "1"

`$stdinContent = [Console]::In.ReadToEnd()
`$jsonStart = `$stdinContent.IndexOf('{')
`$jsonContent = if (`$jsonStart -ge 0) { `$stdinContent.Substring(`$jsonStart) } else { '{}' }
`$tempFile = [System.IO.Path]::GetTempFileName()
try {
    [System.IO.File]::WriteAllText(`$tempFile, `$jsonContent, (New-Object System.Text.UTF8Encoding(`$false)))
    Get-Content -Raw `$tempFile | & python "$pythonScriptFwd"
} finally {
    Remove-Item `$tempFile -ErrorAction SilentlyContinue
}
"@

    [System.IO.File]::WriteAllText($WrapperFile, $wrapperContent, (New-Object System.Text.UTF8Encoding($false)))
}

function Repair-MachineIdScript {
    param([string]$FilePath)
    if (-not (Test-Path $FilePath)) { return }
    $content = Get-Content $FilePath -Raw
    if ($content -match '(?m)^import pwd\s*$') {
        $patched = $content -replace '(?m)^import pwd\s*$', "try:`n    import pwd`nexcept ImportError:`n    pwd = None  # Not available on Windows"
        [System.IO.File]::WriteAllText($FilePath, $patched, (New-Object System.Text.UTF8Encoding($false)))
        Write-Log "Patched akto_machine_id.py for Windows compatibility"
    }
}

# Write akto-hooks.json into %USERPROFILE%\.copilot\hooks\
# VSCode reads ALL .json files in this folder at user level automatically.
# Hook event names are Copilot CLI format (camelCase: userPromptSubmitted, preToolUse, postToolUse).
# VSCode Copilot Chat reads both project-level and user-level hooks in this same format.
function Update-VSCodeCopilotHooks {
    param(
        [string]$HooksConfigDir,
        [string]$ScriptsDir,
        [bool]$PromptHooksEnabled,
        [bool]$McpHooksEnabled
    )

    $hooksFile = Join-Path $HooksConfigDir "akto-hooks.json"
    $hooks = @{}

    if ($PromptHooksEnabled) {
        $promptWrapper   = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $ScriptsDir 'akto-validate-prompt-wrapper.ps1')`""
        $responseWrapper = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $ScriptsDir 'akto-validate-response-wrapper.ps1')`""

        $hooks["userPromptSubmitted"] = @(@{ type = "command"; windows = $promptWrapper;   timeout = 30 })
        $hooks["stop"]                = @(@{ type = "command"; windows = $responseWrapper; timeout = 30 })
    }

    if ($McpHooksEnabled) {
        $preToolWrapper  = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $ScriptsDir 'akto-validate-pre-tool-wrapper.ps1')`""
        $postToolWrapper = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $ScriptsDir 'akto-validate-post-tool-wrapper.ps1')`""

        $hooks["preToolUse"]  = @(@{ type = "command"; windows = $preToolWrapper;  timeout = 30 })
        $hooks["postToolUse"] = @(@{ type = "command"; windows = $postToolWrapper; timeout = 30 })
    }

    $newConfig = [ordered]@{ version = 1; hooks = $hooks }

    if (Test-Path $hooksFile) {
        Write-Log "Existing akto-hooks.json found, backing up..."
        Copy-Item $hooksFile "$hooksFile.backup" -Force

        try {
            $existing = Get-Content $hooksFile -Raw | ConvertFrom-Json -AsHashtable
            if (-not $existing.ContainsKey("hooks")) { $existing["hooks"] = @{} }

            foreach ($key in $hooks.Keys) {
                # Remove old Akto entries, add new -- prevents duplicates on reinstall
                if ($existing["hooks"].ContainsKey($key)) {
                    $existing["hooks"][$key] = @($existing["hooks"][$key] | Where-Object { $_.powershell -notlike "*akto*" }) + $hooks[$key]
                } else {
                    $existing["hooks"][$key] = $hooks[$key]
                }
            }

            [System.IO.File]::WriteAllText($hooksFile, ($existing | ConvertTo-Json -Depth 10), (New-Object System.Text.UTF8Encoding($false)))
            Write-Log "Merged into existing akto-hooks.json"
        } catch {
            [System.IO.File]::WriteAllText($hooksFile, ($newConfig | ConvertTo-Json -Depth 10), (New-Object System.Text.UTF8Encoding($false)))
            Write-Log "Created new akto-hooks.json (merge failed)"
        }
    } else {
        [System.IO.File]::WriteAllText($hooksFile, ($newConfig | ConvertTo-Json -Depth 10), (New-Object System.Text.UTF8Encoding($false)))
        Write-Log "Created akto-hooks.json"
    }
}

function Install-ForUser {
    param([string]$UserHome)

    # Skip system/non-real-user profiles
    if (-not (Test-Path (Join-Path $UserHome "AppData"))) { return $true }

    try {
        Write-Log "Starting VSCode Copilot hook installation..."
        Write-Log "Target user home: $UserHome"

        if (-not (Test-VSCodeInstalled $UserHome)) {
            Write-Log "VSCode not detected - skipping"
            return $true
        }
        Write-Log "VSCode detected"

        $configFile         = Join-Path $UserHome ".akto-mcp-endpoint-shield\config\config.env"
        $promptHooksEnabled = Test-PromptHooksEnabled $configFile
        $mcpHooksEnabled    = Test-McpHooksEnabled $configFile

        if (-not $promptHooksEnabled -and -not $mcpHooksEnabled) {
            Write-Log "Both ENABLE_PROMPT_HOOKS_VSCODE_COPILOT and ENABLE_MCP_HOOKS_VSCODE_COPILOT are false - skipping"
            return $true
        }

        $ingestionUrl = Get-IngestionUrl $configFile $AktoDataIngestionUrl
        $apiToken     = Get-ApiToken $configFile $AktoApiToken
        $deviceId     = Get-DeviceId
        if (-not $deviceId) { $deviceId = "unknown-device" }

        Write-Log "Device ID: $deviceId"
        Write-Log "Ingestion URL: $ingestionUrl"

        # Python scripts live here (outside VSCode dirs -- survives VSCode updates)
        $scriptsDir = Join-Path $UserHome ".akto-endpoint-shield\vscode-hooks"
        New-Item -ItemType Directory -Force -Path $scriptsDir | Out-Null
        Write-Log "Scripts directory: $scriptsDir"

        # VSCode reads all .json files from %USERPROFILE%\.copilot\hooks\ at user level
        $hooksConfigDir = Join-Path $UserHome ".copilot\hooks"
        New-Item -ItemType Directory -Force -Path $hooksConfigDir | Out-Null
        Write-Log "Hooks config directory: $hooksConfigDir"

        # Always download shared utilities
        $machineIdPy = Join-Path $scriptsDir "akto_machine_id.py"
        if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto_machine_id.py" $machineIdPy)) {
            Write-LogError "Failed to download akto_machine_id.py"
            return $false
        }
        Write-Log "Downloaded akto_machine_id.py"
        Repair-MachineIdScript $machineIdPy

        $heartbeatPy = Join-Path $scriptsDir "akto_heartbeat.py"
        if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto_heartbeat.py" $heartbeatPy)) {
            Write-LogError "Failed to download akto_heartbeat.py"
            return $false
        }
        Write-Log "Downloaded akto_heartbeat.py"

        # -----------------------------------------------------------------------
        # PROMPT HOOKS (UserPromptSubmit / Stop)
        # -----------------------------------------------------------------------
        if ($promptHooksEnabled) {
            Write-Log "Installing prompt hooks (UserPromptSubmit/Stop)..."

            $promptPy = Join-Path $scriptsDir "akto-validate-prompt.py"
            if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto-validate-prompt.py" $promptPy)) {
                Write-LogError "Failed to download akto-validate-prompt.py"
                return $false
            }
            Write-Log "Downloaded akto-validate-prompt.py"

            # akto-validate-post-tool.py handles Stop (response ingestion)
            $postToolPy = Join-Path $scriptsDir "akto-validate-post-tool.py"
            if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto-validate-post-tool.py" $postToolPy)) {
                Write-LogError "Failed to download akto-validate-post-tool.py"
                return $false
            }
            Write-Log "Downloaded akto-validate-post-tool.py"

            New-WrapperScript (Join-Path $scriptsDir "akto-validate-prompt-wrapper.ps1")   $promptPy   $ingestionUrl $apiToken $deviceId "ENABLE_PROMPT_HOOKS_VSCODE_COPILOT"
            New-WrapperScript (Join-Path $scriptsDir "akto-validate-response-wrapper.ps1") $postToolPy $ingestionUrl $apiToken $deviceId "ENABLE_PROMPT_HOOKS_VSCODE_COPILOT"
            Write-Log "Created prompt wrapper scripts"
        }

        # -----------------------------------------------------------------------
        # MCP HOOKS (PreToolUse / PostToolUse)
        # -----------------------------------------------------------------------
        if ($mcpHooksEnabled) {
            Write-Log "Installing MCP hooks (PreToolUse/PostToolUse)..."

            $preToolPy = Join-Path $scriptsDir "akto-validate-pre-tool.py"
            if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto-validate-pre-tool.py" $preToolPy)) {
                Write-LogError "Failed to download akto-validate-pre-tool.py"
                return $false
            }
            Write-Log "Downloaded akto-validate-pre-tool.py"

            $postToolPy = Join-Path $scriptsDir "akto-validate-post-tool.py"
            if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto-validate-post-tool.py" $postToolPy)) {
                Write-LogError "Failed to download akto-validate-post-tool.py"
                return $false
            }
            Write-Log "Downloaded akto-validate-post-tool.py"

            New-WrapperScript (Join-Path $scriptsDir "akto-validate-pre-tool-wrapper.ps1")  $preToolPy  $ingestionUrl $apiToken $deviceId "ENABLE_MCP_HOOKS_VSCODE_COPILOT"
            New-WrapperScript (Join-Path $scriptsDir "akto-validate-post-tool-wrapper.ps1") $postToolPy $ingestionUrl $apiToken $deviceId "ENABLE_MCP_HOOKS_VSCODE_COPILOT"
            Write-Log "Created MCP wrapper scripts"
        }

        # Unblock all downloaded files -- Windows blocks scripts downloaded from the internet
        Get-ChildItem $scriptsDir -ErrorAction SilentlyContinue | Unblock-File -ErrorAction SilentlyContinue
        Write-Log "Unblocked downloaded scripts"

        # Write akto-hooks.json into VSCode user-level hooks dir
        Update-VSCodeCopilotHooks $hooksConfigDir $scriptsDir $promptHooksEnabled $mcpHooksEnabled
        Write-Log "Updated akto-hooks.json"

        Write-Log ""
        Write-Log "=========================================="
        Write-Log "VSCode Copilot hooks installed successfully!"
        Write-Log "=========================================="
        Write-Log "Scripts location : $scriptsDir"
        Write-Log "Hooks config     : $(Join-Path $hooksConfigDir 'akto-hooks.json')"

        return $true
    } catch {
        Write-LogError "Installation failed for $UserHome : $_"
        return $false
    }
}

# Main execution
if ($TargetUserHome -and (Test-Path $TargetUserHome)) {
    $success = Install-ForUser $TargetUserHome
    exit $(if ($success) { 0 } else { 1 })
} else {
    $exitCode = 0
    $userProfiles = Get-ChildItem "C:\Users" -Directory -ErrorAction SilentlyContinue

    foreach ($userDir in $userProfiles) {
        $userName = $userDir.Name
        if ($userName -in @("Public", "Default", "Default User", "All Users")) { continue }
        if ($userName.StartsWith(".")) { continue }

        Write-Log "=== Processing user: $($userDir.FullName) ==="
        try {
            $success = Install-ForUser $userDir.FullName
            if (-not $success) { $exitCode = 1 }
        } catch {
            Write-LogError "Failed for $($profile.FullName): $_"
            $exitCode = 1
        }
    }

    exit $exitCode
}
