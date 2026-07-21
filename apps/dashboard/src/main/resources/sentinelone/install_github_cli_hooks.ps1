# ========================================================================================
# MCP Endpoint Shield - GitHub Copilot CLI Hook Installer (Windows PowerShell)
# ========================================================================================
# Installs Akto guardrails hooks for GitHub Copilot CLI.
# Scripts go to %USERPROFILE%\.github\hooks\; hook commands are merged into
# %USERPROFILE%\.copilot\config.json (global user-level -- see Akto Copilot CLI hooks doc).
#
# Scripts are downloaded from:
#   https://github.com/akto-api-security/akto/tree/master/apps/mcp-endpoint-shield/github-cli-hooks
#
# Controlled by flags in config.env:
#   ENABLE_HOOKS_COPILOT_CLI=true  -- installs all three hooks
#                                    (userPromptSubmitted, preToolUse, postToolUse)
#
# Mirrors: mcp-endpoint-shield/misc/windows/install_github_cli_hooks.ps1 (master branch, full hook set)
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
    Write-Host "[Copilot CLI Hooks] $Message" -ForegroundColor Green
}

function Write-LogError {
    param([string]$Message)
    Write-Host "[Copilot CLI Hooks] ERROR: $Message" -ForegroundColor Red
}

# Copilot CLI detection: check for standalone copilot.exe (winget install GitHub.Copilot)
# or gh CLI with gh copilot extension
function Test-CopilotCliInstalled {
    param([string]$UserHome)

    # Check for standalone GitHub Copilot CLI (winget install GitHub.Copilot)
    $copilotPaths = @(
        (Join-Path $UserHome "AppData\Local\Programs\GitHub Copilot\copilot.exe"),
        (Join-Path $UserHome "AppData\Local\Microsoft\WinGet\Packages\GitHub.Copilot_Microsoft.Winget.Source_8wekyb3d8bbwe\copilot.exe"),
        "C:\Program Files\GitHub Copilot\copilot.exe",
        "C:\Program Files (x86)\GitHub Copilot\copilot.exe"
    )
    $copilotFound = ($copilotPaths | Where-Object { Test-Path $_ }).Count -gt 0
    if (-not $copilotFound) {
        try { $copilotFound = $null -ne (Get-Command copilot -ErrorAction SilentlyContinue) } catch {}
    }
    if ($copilotFound) { return $true }

    # Check for gh CLI binary
    $ghPaths = @(
        (Join-Path $UserHome "AppData\Local\Programs\GitHub CLI\gh.exe"),
        "C:\Program Files\GitHub CLI\gh.exe",
        "C:\Program Files (x86)\GitHub CLI\gh.exe"
    )
    $ghFound = ($ghPaths | Where-Object { Test-Path $_ }).Count -gt 0
    if (-not $ghFound) {
        try { $ghFound = $null -ne (Get-Command gh -ErrorAction SilentlyContinue) } catch {}
    }

    if (-not $ghFound) { return $false }

    # Check for gh copilot extension data dir
    $copilotExtDir = Join-Path $UserHome "AppData\Roaming\GitHub CLI\extensions\gh-copilot"
    if (Test-Path $copilotExtDir) { return $true }

    # Fallback: try running gh copilot --version silently
    try {
        $null = & gh copilot --version 2>&1
        return $LASTEXITCODE -eq 0
    } catch {}

    return $false
}

function Get-ConfigValue {
    param([string]$ConfigFile, [string]$Key)

    if (-not (Test-Path $ConfigFile)) { return $null }
    $line = Get-Content $ConfigFile -ErrorAction SilentlyContinue | Where-Object { $_ -match "^${Key}=" }
    if ($line) { return ($line -split "=", 2)[1].Trim() }
    return $null
}

function Test-CopilotCliHooksEnabled {
    param([string]$ConfigFile)
    $prompt = Get-ConfigValue $ConfigFile "ENABLE_PROMPT_HOOKS_GITHUB_CLI"
    $mcp    = Get-ConfigValue $ConfigFile "ENABLE_MCP_HOOKS_GITHUB_CLI"
    # disabled only when BOTH are explicitly false
    if ($prompt -eq "false" -and $mcp -eq "false") { return $false }
    return $true
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
    $val = Get-ConfigValue $ConfigFile "AKTO_API_TOKEN"
    if ($val) { return $val }
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

# Generate wrapper .ps1 with absolute paths and baked-in env vars.
# The downloaded wrappers use relative paths (.github/hooks/...) which only work
# when run from the project root. We generate new ones with absolute paths instead.
function New-WrapperScript {
    param(
        [string]$WrapperFile,
        [string]$PythonScript,
        [string]$IngestionUrl,
        [string]$ApiToken,
        [string]$DeviceId,
        [string]$GuardFlag   # e.g. "ENABLE_PROMPT_HOOKS_GITHUB_CLI" or "ENABLE_MCP_HOOKS_GITHUB_CLI"
    )

    $pythonScriptFwd = $PythonScript.Replace("\", "/")

    $wrapperContent = @"
# Auto-generated wrapper for Akto Copilot CLI guardrails hook

# Live flag guard -- re-read config.env on every invocation (mirrors Mac _inject_flag_guard)
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
`$env:AKTO_TIMEOUT = "5"
`$env:AKTO_CONNECTOR = "github_cli"
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

# Merge hooks into %USERPROFILE%\.copilot\config.json (PS 5.1: no -AsHashtable on ConvertFrom-Json).
function Write-HooksJson {
    param(
        [string]$HooksDir,
        [string]$PromptWrapper,
        [string]$PreToolWrapper,
        [string]$PostToolWrapper
    )

    $hooksJsonPath = Join-Path $HooksDir "hooks.json"

    $promptCmd   = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$PromptWrapper`""
    $preToolCmd  = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$PreToolWrapper`""
    $postToolCmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$PostToolWrapper`""

    $newConfig = @{ hooks = @{
        userPromptSubmitted = @(@{ type = "command"; powershell = $promptCmd; comment = "Akto Guardrails (userPromptSubmitted)"; timeoutSec = 30 })
        preToolUse          = @(@{ type = "command"; powershell = $preToolCmd; comment = "Akto Guardrails (preToolUse)"; timeoutSec = 30 })
        postToolUse         = @(@{ type = "command"; powershell = $postToolCmd; comment = "Akto Guardrails (postToolUse)"; timeoutSec = 30 })
    } }

    if (Test-Path $hooksJsonPath) {
        Write-Log "Backing up existing hooks.json..."
        Copy-Item $hooksJsonPath "$hooksJsonPath.backup" -Force
        try {
            if ($PSVersionTable.PSVersion.Major -ge 6) {
                $raw = Get-Content $hooksJsonPath -Raw
                $existing = ConvertFrom-Json @{ Content = $raw; AsHashtable = $true }
            } else {
                Add-Type -AssemblyName System.Web.Extensions
                $ser = New-Object System.Web.Script.Serialization.JavaScriptSerializer
                $ser.MaxJsonLength = 2147483647
                $existing = $ser.Deserialize((Get-Content $hooksJsonPath -Raw), [System.Collections.Hashtable])
            }
            if (-not $existing.ContainsKey("hooks")) { $existing["hooks"] = @{} }

            foreach ($key in $newConfig.hooks.Keys) {
                if ($existing["hooks"].ContainsKey($key)) {
                    $existing["hooks"][$key] = @($existing["hooks"][$key] | Where-Object { $_.powershell -notlike "*akto*" }) + $newConfig.hooks[$key]
                } else {
                    $existing["hooks"][$key] = $newConfig.hooks[$key]
                }
            }

            [System.IO.File]::WriteAllText($hooksJsonPath, ($existing | ConvertTo-Json -Depth 20), (New-Object System.Text.UTF8Encoding($false)))
            Write-Log "Merged into hooks.json"
        } catch {
            Write-LogError "Could not merge hooks.json: $_"
            if (Test-Path "$hooksJsonPath.backup") {
                Copy-Item "$hooksJsonPath.backup" $hooksJsonPath -Force
            }
            return $false
        }
    } else {
        [System.IO.File]::WriteAllText($hooksJsonPath, ($newConfig | ConvertTo-Json -Depth 20), (New-Object System.Text.UTF8Encoding($false)))
        Write-Log "Created hooks.json"
    }
    return $true
}

function Install-ForUser {
    param([string]$UserHome)

    # Skip system/non-real-user profiles
    if (-not (Test-Path (Join-Path $UserHome "AppData"))) { return $true }

    try {
        Write-Log "Starting Copilot CLI hook installation..."
        Write-Log "Target user home: $UserHome"

        if (-not (Test-CopilotCliInstalled $UserHome)) {
            Write-Log "GitHub Copilot CLI not detected - skipping"
            return $true
        }
        Write-Log "GitHub Copilot CLI detected"

        $configFile = Join-Path $UserHome ".akto-mcp-endpoint-shield\config\config.env"

        if (-not (Test-CopilotCliHooksEnabled $configFile)) {
            Write-Log "ENABLE_HOOKS_COPILOT_CLI is false - skipping"
            return $true
        }

        $ingestionUrl = Get-IngestionUrl $configFile $AktoDataIngestionUrl
        $apiToken     = Get-ApiToken $configFile $AktoApiToken
        $deviceId     = Get-DeviceId
        if (-not $deviceId) { $deviceId = "unknown-device" }

        Write-Log "Device ID: $deviceId"
        Write-Log "Ingestion URL: $ingestionUrl"

        $hooksDir = Join-Path $UserHome ".github\hooks"
        New-Item -ItemType Directory -Force -Path $hooksDir | Out-Null
        Write-Log "Hooks directory: $hooksDir"

        # Download Python scripts
        $files = @(
            "akto-validate-prompt.py",
            "akto-validate-pre-tool.py",
            "akto-validate-post-tool.py",
            "akto_machine_id.py",
            "akto_heartbeat.py"
        )

        foreach ($file in $files) {
            $dest = Join-Path $hooksDir $file
            if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/$file" $dest)) {
                Write-LogError "Failed to download $file"
                return $false
            }
            Write-Log "Downloaded $file"
        }

        Repair-MachineIdScript (Join-Path $hooksDir "akto_machine_id.py")

        # Generate wrapper .ps1 scripts with absolute paths
        $promptPy   = Join-Path $hooksDir "akto-validate-prompt.py"
        $preToolPy  = Join-Path $hooksDir "akto-validate-pre-tool.py"
        $postToolPy = Join-Path $hooksDir "akto-validate-post-tool.py"

        $promptWrapper   = Join-Path $hooksDir "akto-validate-prompt-wrapper.ps1"
        $preToolWrapper  = Join-Path $hooksDir "akto-validate-pre-tool-wrapper.ps1"
        $postToolWrapper = Join-Path $hooksDir "akto-validate-post-tool-wrapper.ps1"

        New-WrapperScript $promptWrapper   $promptPy   $ingestionUrl $apiToken $deviceId "ENABLE_PROMPT_HOOKS_GITHUB_CLI"
        New-WrapperScript $preToolWrapper  $preToolPy  $ingestionUrl $apiToken $deviceId "ENABLE_MCP_HOOKS_GITHUB_CLI"
        New-WrapperScript $postToolWrapper $postToolPy $ingestionUrl $apiToken $deviceId "ENABLE_MCP_HOOKS_GITHUB_CLI"
        Write-Log "Created wrapper scripts"

        # Unblock all downloaded files -- Windows blocks scripts downloaded from the internet
        Get-ChildItem $hooksDir -ErrorAction SilentlyContinue | Unblock-File -ErrorAction SilentlyContinue
        Write-Log "Unblocked downloaded scripts"

        if (-not (Write-HooksJson $hooksDir $promptWrapper $preToolWrapper $postToolWrapper)) {
            return $false
        }
        Write-Log "Updated hooks.json"

        Write-Log ""
        Write-Log "=========================================="
        Write-Log "Copilot CLI hooks installed successfully!"
        Write-Log "=========================================="
        Write-Log "Hooks directory : $hooksDir"
        Write-Log "Hooks config    : $(Join-Path $hooksDir 'hooks.json')"
        Write-Log ""
        Write-Log "NOTE: userPromptSubmitted hook is observe-only (cannot block per GitHub design)."
        Write-Log "      Only preToolUse can block tool execution."

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
            Write-LogError "Failed for $($userDir.FullName): $_"
            $exitCode = 1
        }
    }

    exit $exitCode
}
