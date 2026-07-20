# ========================================================================================
# MCP Endpoint Shield - Codex CLI Hook Installer (PowerShell)
# ========================================================================================
# Automatically installs Akto guardrails hooks for Codex CLI if detected.
# Downloads latest hooks from GitHub and configures ~/.codex/hooks.json.
#
# Codex also requires [features] codex_hooks = true in ~/.codex/config.toml --
# this script writes that entry if absent (same as the Mac installer).
#
# Controlled by flags in config.env:
#   ENABLE_PROMPT_HOOKS_CODEX=true  -- installs UserPromptSubmit/Stop hooks
#   ENABLE_MCP_HOOKS_CODEX=true     -- installs PreToolUse/PostToolUse MCP hooks
#
# Mirrors: mcp-endpoint-shield/misc/windows/install_codex_hooks.ps1 (master branch, full hook set)
# ========================================================================================

param(
    [string]$TargetUserHome = "",
    [string]$AktoDataIngestionUrl = "",
    [string]$AktoApiToken = ""
)

$ErrorActionPreference = "Stop"

$GITHUB_RAW_BASE    = "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/codex-cli-hooks"
$GITHUB_SHARED_BASE = "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/shared"

function Write-Log {
    param([string]$Message)
    Write-Host "[Codex Hooks] $Message"
}

function Write-ErrorLog {
    param([string]$Message)
    Write-Host "[Codex Hooks] ERROR: $Message" -ForegroundColor Red
}

function Test-CodexInstalled {
    param([string]$UserHome)

    if (Test-Path (Join-Path $UserHome ".codex")) {
        return $true
    }

    $npmPaths = @(
        (Join-Path $UserHome "AppData\Roaming\npm\codex"),
        (Join-Path $UserHome "AppData\Roaming\npm\codex.cmd"),
        (Join-Path $UserHome "AppData\Local\npm\codex"),
        (Join-Path $UserHome "AppData\Local\npm\codex.cmd"),
        (Join-Path $UserHome "AppData\Local\Programs\codex\Codex.exe"),
        (Join-Path $UserHome "AppData\Local\codex\Codex.exe")
    )
    foreach ($p in $npmPaths) {
        if (Test-Path $p) { return $true }
    }

    if (Get-Command codex -ErrorAction SilentlyContinue) {
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

# Codex requires [features] codex_hooks = true in ~/.codex/config.toml.
# Mirrors the Mac ensure_codex_hooks_feature_flag() function.
function Ensure-CodexHooksFeatureFlag {
    param([string]$CodexDir)

    $configToml = Join-Path $CodexDir "config.toml"

    if (-not (Test-Path $configToml)) {
        [System.IO.File]::WriteAllText($configToml, "[features]`ncodex_hooks = true`n", (New-Object System.Text.UTF8Encoding($false)))
        Write-Log "Created config.toml with [features] codex_hooks = true"
        return
    }

    $content = Get-Content $configToml -Raw -ErrorAction SilentlyContinue

    # Already set correctly -- nothing to do
    if ($content -match '(?m)^\s*codex_hooks\s*=\s*true') {
        Write-Log "config.toml already has codex_hooks = true"
        return
    }

    # Key exists but set to false -- flip it
    if ($content -match '(?m)^\s*codex_hooks\s*=') {
        $content = $content -replace '(?m)^\s*codex_hooks\s*=.*', 'codex_hooks = true'
        [System.IO.File]::WriteAllText($configToml, $content, (New-Object System.Text.UTF8Encoding($false)))
        Write-Log "Updated config.toml: codex_hooks = true"
        return
    }

    # Key absent -- append under [features] section if it exists, else append the section
    if ($content -match '(?m)^\[features\]') {
        $content = $content -replace '(?m)(^\[features\])', "`$1`ncodex_hooks = true"
        [System.IO.File]::WriteAllText($configToml, $content, (New-Object System.Text.UTF8Encoding($false)))
    } else {
        $content = $content.TrimEnd() + "`n`n[features]`ncodex_hooks = true`n"
        [System.IO.File]::WriteAllText($configToml, $content, (New-Object System.Text.UTF8Encoding($false)))
    }
    Write-Log "Appended codex_hooks = true to config.toml"
}

# Creates a .ps1 wrapper with baked-in env vars and a live flag guard.
# The guard re-reads config.env on every invocation and exits 0 when the
# named flag is "false" -- mirrors Mac's _inject_flag_guard.
function New-WrapperScript {
    param(
        [string]$HookType,      # "prompt", "response", "pre-tool", "post-tool"
        [string]$IngestionUrl,
        [string]$ApiToken,
        [string]$DeviceId,
        [string]$HooksDir,
        [string]$GuardFlag      # "ENABLE_PROMPT_HOOKS_CODEX" or "ENABLE_MCP_HOOKS_CODEX"
    )

    $wrapperFile  = Join-Path $HooksDir "akto-validate-$HookType-wrapper.ps1"
    $pythonScript = (Join-Path $HooksDir "akto-validate-$HookType.py").Replace("\", "/")

    $wrapperContent = @"
# Auto-generated wrapper for Akto Codex guardrails hook
# Data Ingestion URL: $IngestionUrl

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
`$env:AKTO_CONNECTOR = "codex_cli"
`$env:CONTEXT_SOURCE = "ENDPOINT"
`$env:DEVICE_ID = "$DeviceId"
`$env:PYTHONIOENCODING = "utf-8"

[Console]::Error.WriteLine("[Codex Hook] Data Ingestion URL: `$env:AKTO_DATA_INGESTION_URL")
[Console]::Error.WriteLine("[Codex Hook] Device ID: `$env:DEVICE_ID")

& python "$pythonScript" `$args
exit `$LASTEXITCODE
"@

    [System.IO.File]::WriteAllText($wrapperFile, $wrapperContent, (New-Object System.Text.UTF8Encoding($false)))
    Write-Log "Created akto-validate-$HookType-wrapper.ps1"
}

# Merge hook entries into ~/.codex/hooks.json.
# Codex hook format mirrors Claude CLI: UserPromptSubmit, Stop, PreToolUse, PostToolUse.
function Merge-CodexHooksJson {
    param(
        [string]$HooksFile,
        [hashtable]$HooksFragment   # keys = event names, values = hook entry arrays
    )

    $hooksDir = Split-Path $HooksFile -Parent
    if (-not (Test-Path $hooksDir)) {
        New-Item -ItemType Directory -Path $hooksDir -Force | Out-Null
    }

    $settings = $null
    if (Test-Path $HooksFile) {
        Write-Log "Existing hooks.json found, backing up..."
        Copy-Item $HooksFile "$HooksFile.backup" -Force
        try {
            $settings = Get-Content $HooksFile -Raw | ConvertFrom-Json -AsHashtable
        } catch {
            Write-Log "Could not parse hooks.json; writing hooks section only"
            $settings = @{}
        }
    } else {
        $settings = @{}
    }

    if (-not $settings.ContainsKey("hooks")) {
        $settings["hooks"] = @{}
    }

    foreach ($key in $HooksFragment.Keys) {
        $settings["hooks"][$key] = $HooksFragment[$key]
    }

    [System.IO.File]::WriteAllText($HooksFile, ($settings | ConvertTo-Json -Depth 20), (New-Object System.Text.UTF8Encoding($false)))
    Write-Log "Merged hooks into hooks.json"
}

function Install-ForUser {
    param([string]$UserHome)

    # Skip system/non-real-user profiles
    if (-not (Test-Path (Join-Path $UserHome "AppData"))) { return $true }

    Write-Log "Starting Codex CLI hook installation..."
    Write-Log "Target user home: $UserHome"

    if (-not (Test-CodexInstalled -UserHome $UserHome)) {
        Write-Log "Codex CLI not detected - skipping hook installation"
        return $true
    }

    Write-Log "Codex CLI detected"

    $ConfigFile    = Join-Path $UserHome ".akto-mcp-endpoint-shield\config\config.env"
    $promptEnabled = Get-FlagValue $ConfigFile "ENABLE_PROMPT_HOOKS_CODEX"
    $mcpEnabled    = Get-FlagValue $ConfigFile "ENABLE_MCP_HOOKS_CODEX"

    if ($promptEnabled -eq "false" -and $mcpEnabled -eq "false") {
        Write-Log "Both ENABLE_PROMPT_HOOKS_CODEX and ENABLE_MCP_HOOKS_CODEX are false -- skipping"
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

    $codexDir  = Join-Path $UserHome ".codex"
    $hooksDir  = Join-Path $codexDir "hooks"
    $hooksFile = Join-Path $codexDir "hooks.json"

    New-Item -ItemType Directory -Force -Path $codexDir | Out-Null
    New-Item -ItemType Directory -Force -Path $hooksDir | Out-Null
    icacls "$codexDir" /grant "Users:(OI)(CI)F" /T /C /Q | Out-Null

    # Codex requires codex_hooks = true in config.toml to honour hooks.json
    Ensure-CodexHooksFeatureFlag -CodexDir $codexDir

    $hooksFragment = @{}

    # Shared utility — required by all hook scripts
    $ingestionUtilPy = Join-Path $hooksDir "akto_ingestion_utility.py"
    if (-not (Get-FileFromUrl "$GITHUB_SHARED_BASE/akto_ingestion_utility.py" $ingestionUtilPy)) {
        Write-ErrorLog "Failed to download akto_ingestion_utility.py"
        return $false
    }
    Write-Log "Downloaded akto_ingestion_utility.py"

    # -----------------------------------------------------------------------
    # PROMPT HOOKS -- UserPromptSubmit / Stop
    # -----------------------------------------------------------------------
    if ($promptEnabled -eq "true") {
        Write-Log "Installing prompt hooks (UserPromptSubmit/Stop)..."

        foreach ($f in @("akto-validate-prompt.py", "akto-validate-response.py", "akto_machine_id.py")) {
            $dest = Join-Path $hooksDir $f
            if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/$f" $dest)) {
                Write-ErrorLog "Failed to download $f"
                return $false
            }
            Write-Log "Downloaded $f"
        }

        # Patch akto_machine_id.py: 'pwd' is Unix-only
        $machineIdPy = Join-Path $hooksDir "akto_machine_id.py"
        if (Test-Path $machineIdPy) {
            $content = Get-Content $machineIdPy -Raw
            if ($content -match '(?m)^import pwd\s*$') {
                $patched = $content -replace '(?m)^import pwd\s*$', "try:`n    import pwd`nexcept ImportError:`n    pwd = None  # Not available on Windows"
                [System.IO.File]::WriteAllText($machineIdPy, $patched, (New-Object System.Text.UTF8Encoding($false)))
                Write-Log "Patched akto_machine_id.py for Windows compatibility"
            }
        }

        New-WrapperScript -HookType "prompt"   -IngestionUrl $GuardrailsUrl -ApiToken $ApiToken -DeviceId $DeviceId -HooksDir $hooksDir -GuardFlag "ENABLE_PROMPT_HOOKS_CODEX"
        New-WrapperScript -HookType "response" -IngestionUrl $GuardrailsUrl -ApiToken $ApiToken -DeviceId $DeviceId -HooksDir $hooksDir -GuardFlag "ENABLE_PROMPT_HOOKS_CODEX"

        $promptCmd   = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $hooksDir 'akto-validate-prompt-wrapper.ps1')`""
        $responseCmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $hooksDir 'akto-validate-response-wrapper.ps1')`""

        $hooksFragment["UserPromptSubmit"] = @(
            @{ hooks = @(@{ type = "command"; command = $promptCmd; timeout = 10 }) }
        )
        $hooksFragment["Stop"] = @(
            @{ hooks = @(@{ type = "command"; command = $responseCmd; timeout = 10 }) }
        )

        Write-Log "Prompt hooks ready"
    } else {
        Write-Log "ENABLE_PROMPT_HOOKS_CODEX is false -- skipping prompt hooks"
    }

    # -----------------------------------------------------------------------
    # MCP HOOKS -- PreToolUse / PostToolUse
    # -----------------------------------------------------------------------
    if ($mcpEnabled -eq "true") {
        Write-Log "Installing MCP hooks (PreToolUse/PostToolUse)..."

        foreach ($f in @("akto-validate-pre-tool.py", "akto-validate-post-tool.py")) {
            $dest = Join-Path $hooksDir $f
            if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/$f" $dest)) {
                Write-ErrorLog "Failed to download $f"
                return $false
            }
            Write-Log "Downloaded $f"
        }

        New-WrapperScript -HookType "pre-tool"  -IngestionUrl $GuardrailsUrl -ApiToken $ApiToken -DeviceId $DeviceId -HooksDir $hooksDir -GuardFlag "ENABLE_MCP_HOOKS_CODEX"
        New-WrapperScript -HookType "post-tool" -IngestionUrl $GuardrailsUrl -ApiToken $ApiToken -DeviceId $DeviceId -HooksDir $hooksDir -GuardFlag "ENABLE_MCP_HOOKS_CODEX"

        $preToolCmd  = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $hooksDir 'akto-validate-pre-tool-wrapper.ps1')`""
        $postToolCmd = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $hooksDir 'akto-validate-post-tool-wrapper.ps1')`""

        $hooksFragment["PreToolUse"] = @(
            @{ matcher = "Bash"; hooks = @(@{ type = "command"; command = $preToolCmd; timeout = 10 }) }
        )
        $hooksFragment["PostToolUse"] = @(
            @{ matcher = "Bash"; hooks = @(@{ type = "command"; command = $postToolCmd; timeout = 10 }) }
        )

        Write-Log "MCP hooks ready"
    } else {
        Write-Log "ENABLE_MCP_HOOKS_CODEX is false -- skipping MCP hooks"
    }

    if ($hooksFragment.Count -gt 0) {
        Merge-CodexHooksJson -HooksFile $hooksFile -HooksFragment $hooksFragment
    }

    Write-Log ""
    Write-Log "=========================================="
    Write-Log "Codex CLI hooks installed successfully!"
    Write-Log "=========================================="
    Write-Log ""
    Write-Log "Hooks location : $hooksDir"
    Write-Log "Hooks config   : $hooksFile"
    Write-Log "Feature flag   : $(Join-Path $codexDir 'config.toml')"
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
