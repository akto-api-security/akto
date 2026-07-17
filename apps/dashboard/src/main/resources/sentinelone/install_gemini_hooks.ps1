# ========================================================================================
# MCP Endpoint Shield - Gemini CLI Hook Installer (PowerShell)
# ========================================================================================
# Automatically installs Akto guardrails hooks for Gemini CLI if detected
# Downloads latest hooks from GitHub and configures Gemini CLI settings.json
# Mirrors: mcp-endpoint-shield/misc/windows/install_gemini_hooks.ps1 (master branch, full hook set)
#
# Controlled by flags in config.env:
#   ENABLE_PROMPT_HOOKS_GEMINI=true  -- installs BeforeModel/AfterModel hooks
#   ENABLE_MCP_HOOKS_GEMINI=true     -- installs BeforeTool/AfterTool hooks
# ========================================================================================

param(
    [string]$TargetUserHome = "",
    [string]$AktoDataIngestionUrl = "",
    [string]$AktoApiToken = ""
)

$ErrorActionPreference = "Stop"

$GITHUB_RAW_BASE    = "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/gemini-cli-hooks"
$GITHUB_SHARED_BASE = "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/shared"

function Write-Log {
    param([string]$Message)
    Write-Host "[Gemini Hooks] $Message"
}

function Write-ErrorLog {
    param([string]$Message)
    Write-Host "[Gemini Hooks] ERROR: $Message" -ForegroundColor Red
}

function Test-GeminiInstalled {
    param([string]$UserHome)

    # .gemini dir exists after first run
    if (Test-Path (Join-Path $UserHome ".gemini")) {
        return $true
    }

    # npm global install paths
    $npmPaths = @(
        (Join-Path $UserHome "AppData\Roaming\npm\gemini"),
        (Join-Path $UserHome "AppData\Roaming\npm\gemini.cmd"),
        (Join-Path $UserHome "AppData\Local\npm\gemini"),
        (Join-Path $UserHome "AppData\Local\npm\gemini.cmd")
    )
    foreach ($p in $npmPaths) {
        if (Test-Path $p) { return $true }
    }

    if (Get-Command gemini -ErrorAction SilentlyContinue) {
        return $true
    }

    return $false
}

function Get-FlagValue {
    param([string]$ConfigFile, [string]$Key)
    if (Test-Path $ConfigFile) {
        $line = (Get-Content $ConfigFile -ErrorAction SilentlyContinue) |
                Where-Object { $_ -match "^$Key=" } | Select-Object -First 1
        if ($line) { return ($line -split "=", 2)[1].Trim() }
    }
    return "false"  # default: disabled (opt-in model; dashboard enables)
}

function Get-GuardrailsUrl {
    param([string]$ConfigFile, [string]$EnvVar)

    if ($EnvVar) { return $EnvVar }

    if (Test-Path $ConfigFile) {
        $content = Get-Content $ConfigFile -Raw
        if ($content -match '(?m)^AKTO_API_BASE_URL=(.+)')       { return $Matches[1].Trim() }
        if ($content -match '(?m)^AKTO_DATA_INGESTION_URL=(.+)') { return $Matches[1].Trim() }
    }

    return "https://guardrails.akto.io"
}

function Get-ApiToken {
    param([string]$ConfigFile, [string]$EnvVar)

    if ($EnvVar) { return $EnvVar }

    if (Test-Path $ConfigFile) {
        $content = Get-Content $ConfigFile -Raw
        if ($content -match '(?m)^AKTO_API_TOKEN=(.+)') { return $Matches[1].Trim() }
    }

    return ""
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
        Write-Log "Invoke-WebRequest failed, trying curl.exe fallback..."
    }

    try {
        $result = & curl.exe -fsSL -H "Cache-Control: no-cache" -H "Pragma: no-cache" $Url -o $Destination 2>&1
        if ($LASTEXITCODE -eq 0) { return $true }
        Write-ErrorLog "curl.exe failed (exit $LASTEXITCODE): $result"
        return $false
    } catch {
        Write-ErrorLog "Failed to download from $Url : $_"
        return $false
    }
}

# Creates a wrapper .ps1 with baked-in env vars and a live flag guard.
# The guard re-reads config.env on every invocation -- mirrors Mac's _inject_flag_guard.
function New-WrapperScript {
    param(
        [string]$HookType,      # "prompt", "response", "pre-tool", or "post-tool"
        [string]$IngestionUrl,
        [string]$ApiToken,
        [string]$DeviceId,
        [string]$HooksDir,
        [string]$GuardFlag      # e.g. "ENABLE_PROMPT_HOOKS_GEMINI" or "ENABLE_MCP_HOOKS_GEMINI"
    )

    $wrapperFile  = Join-Path $HooksDir "akto-validate-$HookType-wrapper.ps1"
    $pythonScript = (Join-Path $HooksDir "akto-validate-$HookType.py").Replace("\", "/")

    $wrapperContent = @"
# Auto-generated wrapper for Akto Gemini guardrails hook
# Data Ingestion URL: $IngestionUrl

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
`$env:AKTO_CONNECTOR = "gemini_cli"
`$env:CONTEXT_SOURCE = "ENDPOINT"
`$env:DEVICE_ID = "$DeviceId"
`$env:PYTHONIOENCODING = "utf-8"

[Console]::Error.WriteLine("[Gemini Hook] Data Ingestion URL: `$env:AKTO_DATA_INGESTION_URL")
[Console]::Error.WriteLine("[Gemini Hook] Device ID: `$env:DEVICE_ID")

& python "$pythonScript" `$args
exit `$LASTEXITCODE
"@

    [System.IO.File]::WriteAllText($wrapperFile, $wrapperContent, (New-Object System.Text.UTF8Encoding($false)))
    Write-Log "Created akto-validate-$HookType-wrapper.ps1"
}

# Merge hook entries into ~/.gemini/settings.json.
# Creates the file if it doesn't exist; merges into existing hooks otherwise.
function Merge-GeminiSettings {
    param(
        [string]$SettingsFile,
        [string]$PromptWrapper,
        [string]$ResponseWrapper,
        [string]$PreToolWrapper  = "",
        [string]$PostToolWrapper = ""
    )

    $settingsDir = Split-Path $SettingsFile -Parent
    if (-not (Test-Path $settingsDir)) {
        New-Item -ItemType Directory -Path $settingsDir -Force | Out-Null
    }

    $settings = $null
    if (Test-Path $SettingsFile) {
        Write-Log "Existing settings.json found, backing up..."
        Copy-Item $SettingsFile "$SettingsFile.backup" -Force
        try {
            $settings = Get-Content $SettingsFile -Raw | ConvertFrom-Json -AsHashtable
        } catch {
            Write-Log "Could not parse settings.json; writing hooks section only"
            $settings = @{}
        }
    } else {
        $settings = @{}
    }

    if (-not $settings.ContainsKey("hooks")) {
        $settings["hooks"] = @{}
    }

    if ($PromptWrapper -and $ResponseWrapper) {
        $promptCmd   = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$PromptWrapper`""
        $responseCmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$ResponseWrapper`""
        $settings["hooks"]["BeforeModel"] = @(
            @{ matcher = "*"; hooks = @(@{ name = "akto-validate-prompt"; type = "command"; command = $promptCmd; timeout = 10000 }) }
        )
        $settings["hooks"]["AfterModel"] = @(
            @{ matcher = "*"; hooks = @(@{ name = "akto-validate-response"; type = "command"; command = $responseCmd; timeout = 10000 }) }
        )
    }

    if ($PreToolWrapper -and $PostToolWrapper) {
        $preToolCmd  = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$PreToolWrapper`""
        $postToolCmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$PostToolWrapper`""

        $settings["hooks"]["BeforeTool"] = @(
            @{ matcher = ".*"; sequential = $true; hooks = @(@{ name = "akto-validate-pre-tool"; type = "command"; command = $preToolCmd; timeout = 10000 }) }
        )
        $settings["hooks"]["AfterTool"] = @(
            @{ matcher = ".*"; sequential = $true; hooks = @(@{ name = "akto-validate-post-tool"; type = "command"; command = $postToolCmd; timeout = 10000 }) }
        )
    }

    [System.IO.File]::WriteAllText($SettingsFile, ($settings | ConvertTo-Json -Depth 20), (New-Object System.Text.UTF8Encoding($false)))
    Write-Log "Merged hooks into settings.json"
}

function Install-ForUser {
    param([string]$UserHome)

    # Skip system/non-real-user profiles
    if (-not (Test-Path (Join-Path $UserHome "AppData"))) { return $true }

    Write-Log "Starting Gemini CLI hook installation..."
    Write-Log "Target user home: $UserHome"

    if (-not (Test-GeminiInstalled -UserHome $UserHome)) {
        Write-Log "Gemini CLI not detected - skipping hook installation"
        return $true
    }

    Write-Log "Gemini CLI detected"

    $ConfigFile = Join-Path $UserHome ".akto-mcp-endpoint-shield\config\config.env"

    $promptEnabled = Get-FlagValue $ConfigFile "ENABLE_PROMPT_HOOKS_GEMINI"
    $mcpEnabled    = Get-FlagValue $ConfigFile "ENABLE_MCP_HOOKS_GEMINI"
    if ($promptEnabled -eq "false" -and $mcpEnabled -eq "false") {
        Write-Log "Both ENABLE_PROMPT_HOOKS_GEMINI and ENABLE_MCP_HOOKS_GEMINI are false -- skipping"
        return $true
    }

    $GuardrailsUrl = Get-GuardrailsUrl -ConfigFile $ConfigFile -EnvVar $AktoDataIngestionUrl
    if (-not $GuardrailsUrl) {
        Write-ErrorLog "AKTO_API_BASE_URL / AKTO_DATA_INGESTION_URL not set in config.env -- cannot install hooks"
        return $false
    }

    $ApiToken = Get-ApiToken -ConfigFile $ConfigFile -EnvVar $AktoApiToken
    if (-not $ApiToken) {
        Write-ErrorLog "AKTO_API_TOKEN not set in config.env -- cannot install hooks"
        return $false
    }

    $DeviceId = Get-DeviceId
    if (-not $DeviceId) { $DeviceId = "unknown-device" }

    Write-Log "Device ID: $DeviceId"
    Write-Log "Guardrails URL: $GuardrailsUrl"

    $geminiDir  = Join-Path $UserHome ".gemini"
    $hooksDir   = Join-Path $geminiDir "hooks"
    $settingsFile = Join-Path $geminiDir "settings.json"

    New-Item -ItemType Directory -Force -Path $geminiDir  | Out-Null
    New-Item -ItemType Directory -Force -Path $hooksDir   | Out-Null
    icacls "$geminiDir" /grant "Users:(OI)(CI)F" /T /C /Q | Out-Null
    Write-Log "Created hooks directory: $hooksDir"

    Write-Log "Downloading hook scripts from GitHub..."

    # Shared utility — required by pre-tool/post-tool scripts
    $ingestionUtilPy = Join-Path $hooksDir "akto_ingestion_utility.py"
    if (-not (Get-FileFromUrl "$GITHUB_SHARED_BASE/akto_ingestion_utility.py" $ingestionUtilPy)) {
        Write-ErrorLog "Failed to download akto_ingestion_utility.py"
        return $false
    }
    Write-Log "Downloaded akto_ingestion_utility.py"

    $machineIdPy = Join-Path $hooksDir "akto_machine_id.py"
    if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto_machine_id.py" $machineIdPy)) {
        Write-ErrorLog "Failed to download akto_machine_id.py"
        return $false
    }
    Write-Log "Downloaded akto_machine_id.py"

    # Patch akto_machine_id.py: 'pwd' module is Unix-only, causes ModuleNotFoundError on Windows.
    if (Test-Path $machineIdPy) {
        $content = Get-Content $machineIdPy -Raw
        if ($content -match '(?m)^import pwd\s*$') {
            $patched = $content -replace '(?m)^import pwd\s*$', "try:`n    import pwd`nexcept ImportError:`n    pwd = None  # Not available on Windows"
            [System.IO.File]::WriteAllText($machineIdPy, $patched, (New-Object System.Text.UTF8Encoding($false)))
            Write-Log "Patched akto_machine_id.py for Windows compatibility"
        }
    }

    $promptWrapper   = ""
    $responseWrapper = ""
    $preToolWrapper  = ""
    $postToolWrapper = ""

    if ($promptEnabled -ne "false") {
        $promptPy = Join-Path $hooksDir "akto-validate-prompt.py"
        if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto-validate-prompt.py" $promptPy)) {
            Write-ErrorLog "Failed to download akto-validate-prompt.py"
            return $false
        }
        Write-Log "Downloaded akto-validate-prompt.py"

        $responsePy = Join-Path $hooksDir "akto-validate-response.py"
        if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto-validate-response.py" $responsePy)) {
            Write-ErrorLog "Failed to download akto-validate-response.py"
            return $false
        }
        Write-Log "Downloaded akto-validate-response.py"

        New-WrapperScript -HookType "prompt"   -IngestionUrl $GuardrailsUrl -ApiToken $ApiToken -DeviceId $DeviceId -HooksDir $hooksDir -GuardFlag "ENABLE_PROMPT_HOOKS_GEMINI"
        New-WrapperScript -HookType "response" -IngestionUrl $GuardrailsUrl -ApiToken $ApiToken -DeviceId $DeviceId -HooksDir $hooksDir -GuardFlag "ENABLE_PROMPT_HOOKS_GEMINI"

        $promptWrapper   = Join-Path $hooksDir "akto-validate-prompt-wrapper.ps1"
        $responseWrapper = Join-Path $hooksDir "akto-validate-response-wrapper.ps1"
    }

    if ($mcpEnabled -ne "false") {
        $preToolPy = Join-Path $hooksDir "akto-validate-pre-tool.py"
        if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto-validate-pre-tool.py" $preToolPy)) {
            Write-ErrorLog "Failed to download akto-validate-pre-tool.py"
            return $false
        }
        Write-Log "Downloaded akto-validate-pre-tool.py"

        $postToolPy = Join-Path $hooksDir "akto-validate-post-tool.py"
        if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto-validate-post-tool.py" $postToolPy)) {
            Write-ErrorLog "Failed to download akto-validate-post-tool.py"
            return $false
        }
        Write-Log "Downloaded akto-validate-post-tool.py"

        New-WrapperScript -HookType "pre-tool"  -IngestionUrl $GuardrailsUrl -ApiToken $ApiToken -DeviceId $DeviceId -HooksDir $hooksDir -GuardFlag "ENABLE_MCP_HOOKS_GEMINI"
        New-WrapperScript -HookType "post-tool" -IngestionUrl $GuardrailsUrl -ApiToken $ApiToken -DeviceId $DeviceId -HooksDir $hooksDir -GuardFlag "ENABLE_MCP_HOOKS_GEMINI"

        $preToolWrapper  = Join-Path $hooksDir "akto-validate-pre-tool-wrapper.ps1"
        $postToolWrapper = Join-Path $hooksDir "akto-validate-post-tool-wrapper.ps1"
    }

    Write-Log "Updating Gemini settings.json..."
    Merge-GeminiSettings -SettingsFile $settingsFile -PromptWrapper $promptWrapper -ResponseWrapper $responseWrapper -PreToolWrapper $preToolWrapper -PostToolWrapper $postToolWrapper

    Write-Log ""
    Write-Log "=========================================="
    Write-Log "Gemini CLI hooks installed successfully!"
    Write-Log "=========================================="
    Write-Log ""
    Write-Log "Hooks location : $hooksDir"
    Write-Log "Settings       : $settingsFile"
    Write-Log "Guardrails URL : $GuardrailsUrl"

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
