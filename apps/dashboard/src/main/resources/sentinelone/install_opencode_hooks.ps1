# ========================================================================================
# Akto Endpoint Shield - OpenCode Plugin Installer (PowerShell)
# ========================================================================================
# Automatically installs the Akto guardrails plugin for OpenCode if detected.
# Downloads the latest plugin + Python handlers from GitHub and drops them into
# OpenCode's global plugin directory (~/.config/opencode/plugin).
#
# Unlike the CLI hook integrations (Claude/Gemini/Cursor/Codex) that register command
# hooks in settings.json, OpenCode loads a JavaScript plugin *in-process* and reads its
# configuration from environment variables. Because OpenCode is launched interactively
# (no wrapper to export env vars), this installer bakes the config into a prelude that is
# prepended to the plugin. The prelude also re-reads config.env on every OpenCode start,
# so URL / token / enable-flag changes take effect live.
#
# Controlled by flags in config.env:
#   ENABLE_PROMPT_HOOKS_OPENCODE=false  AND  ENABLE_MCP_HOOKS_OPENCODE=false
#     -> plugin is uninstalled / disabled. Default (flag absent) = enabled.
#
# Mirrors: mcp-endpoint-shield/misc/windows/install_opencode_hooks.ps1 (master branch, full hook set)
# ========================================================================================

param(
    [string]$TargetUserHome = "",
    [string]$AktoDataIngestionUrl = "",
    [string]$AktoApiToken = ""
)

$ErrorActionPreference = "Stop"

$GITHUB_RAW_BASE    = "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/opencode"
$GITHUB_SHARED_BASE = "https://raw.githubusercontent.com/akto-api-security/akto/master/apps/mcp-endpoint-shield/shared"

function Write-Log {
    param([string]$Message)
    Write-Host "[OpenCode Hooks] $Message"
}

function Write-ErrorLog {
    param([string]$Message)
    Write-Host "[OpenCode Hooks] ERROR: $Message" -ForegroundColor Red
}

function Test-OpenCodeInstalled {
    param([string]$UserHome)

    if (Test-Path (Join-Path $UserHome ".config\opencode")) { return $true }
    if (Test-Path (Join-Path $UserHome ".opencode"))        { return $true }

    $npmPaths = @(
        (Join-Path $UserHome "AppData\Roaming\npm\opencode"),
        (Join-Path $UserHome "AppData\Roaming\npm\opencode.cmd"),
        (Join-Path $UserHome "AppData\Local\npm\opencode"),
        (Join-Path $UserHome "AppData\Local\npm\opencode.cmd")
    )
    foreach ($p in $npmPaths) {
        if (Test-Path $p) { return $true }
    }

    if (Get-Command opencode -ErrorAction SilentlyContinue) { return $true }

    return $false
}

# Reads a flag from config.env. Returns "" when absent so callers can apply defaults.
function Get-FlagValue {
    param([string]$ConfigFile, [string]$Key)
    if (Test-Path $ConfigFile) {
        $line = (Get-Content $ConfigFile -ErrorAction SilentlyContinue) |
                Where-Object { $_ -match "^$Key=" } | Select-Object -First 1
        if ($line) { return ($line -split "=", 2)[1].Trim() }
    }
    return ""
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

# Device label matching Go's GetDeviceLabel(): "{hostname}-{first8ofMachineID}"
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

# Build the injected config prelude and prepend it to the upstream plugin.
# See install_opencode_hooks.sh for the rationale; this mirrors it on Windows and
# additionally sets HOME from USERPROFILE (the plugin uses process.env.HOME for its
# log path, which is unset on Windows by default).
function Write-PluginWithPrelude {
    param(
        [string]$UpstreamFile,
        [string]$IngestionUrl,
        [string]$ApiToken,
        [string]$DeviceId,
        [string]$DestFile
    )

    $prelude = @"
// === Akto Endpoint Shield — injected configuration (generated by installer) ===
// Do not edit by hand; regenerated on every install / config poll.
;(() => {
  const _os = require('os')
  const _fs = require('fs')
  const _path = require('path')
  if (!process.env.HOME && process.env.USERPROFILE) process.env.HOME = process.env.USERPROFILE

  const _defaults = {
    AKTO_DATA_INGESTION_URL: "$IngestionUrl",
    AKTO_API_TOKEN: "$ApiToken",
    AKTO_SYNC_MODE: "true",
    AKTO_TIMEOUT: "5",
    AKTO_CONNECTOR: "opencode",
    MODE: "atlas",
    DEVICE_ID: "$DeviceId",
    CONTEXT_SOURCE: "ENDPOINT",
  }

  const _cfg = {}
  try {
    const _p = _path.join(_os.homedir(), ".akto-mcp-endpoint-shield", "config", "config.env")
    if (_fs.existsSync(_p)) {
      for (const _line of _fs.readFileSync(_p, "utf8").split(/\r?\n/)) {
        const _m = _line.match(/^\s*([A-Z_][A-Z0-9_]*)=(.*)`$/)
        if (_m) _cfg[_m[1]] = _m[2].replace(/^["']|["']`$/g, "")
      }
    }
  } catch (_e) { /* fail open — use baked defaults */ }

  const _url = _cfg.AKTO_API_BASE_URL || _cfg.AKTO_DATA_INGESTION_URL || _defaults.AKTO_DATA_INGESTION_URL
  const _apply = (k, v) => { if (v !== undefined && v !== "" && !process.env[k]) process.env[k] = v }
  _apply("AKTO_DATA_INGESTION_URL", _url)
  _apply("AKTO_API_TOKEN", _cfg.AKTO_API_TOKEN || _defaults.AKTO_API_TOKEN)
  for (const _k of ["AKTO_SYNC_MODE", "AKTO_TIMEOUT", "AKTO_CONNECTOR", "MODE", "DEVICE_ID", "CONTEXT_SOURCE"]) {
    _apply(_k, _cfg[_k] || _defaults[_k])
  }

  if (_cfg.ENABLE_PROMPT_HOOKS_OPENCODE === "false" && _cfg.ENABLE_MCP_HOOKS_OPENCODE === "false") {
    process.env.AKTO_DATA_INGESTION_URL = ""
  }
})()
// === end injected configuration ===

"@

    $upstream = Get-Content $UpstreamFile -Raw
    # The upstream plugin hardcodes spawn('python3', ...). On Windows the launcher is
    # typically 'python', so rewrite it to keep the Python handlers working.
    $upstream = $upstream -replace "spawn\('python3'", "spawn('python'"

    [System.IO.File]::WriteAllText($DestFile, ($prelude + $upstream), (New-Object System.Text.UTF8Encoding($false)))
    Write-Log "Installed akto-guardrails-plugin.js"
}

function Install-ForUser {
    param([string]$UserHome)

    # Skip system/non-real-user profiles
    if (-not (Test-Path (Join-Path $UserHome "AppData"))) { return $true }

    Write-Log "Starting OpenCode plugin installation..."
    Write-Log "Target user home: $UserHome"

    if (-not (Test-OpenCodeInstalled -UserHome $UserHome)) {
        Write-Log "OpenCode not detected - skipping plugin installation"
        return $true
    }

    Write-Log "OpenCode detected"

    $ConfigFile = Join-Path $UserHome ".akto-mcp-endpoint-shield\config\config.env"

    # Bail only when BOTH flags are explicitly false.
    $promptFlag = Get-FlagValue $ConfigFile "ENABLE_PROMPT_HOOKS_OPENCODE"
    $mcpFlag    = Get-FlagValue $ConfigFile "ENABLE_MCP_HOOKS_OPENCODE"
    if ($promptFlag -eq "false" -and $mcpFlag -eq "false") {
        Write-Log "Both ENABLE_PROMPT_HOOKS_OPENCODE and ENABLE_MCP_HOOKS_OPENCODE are false -- skipping"
        return $true
    }

    $GuardrailsUrl = Get-GuardrailsUrl -ConfigFile $ConfigFile -EnvVar $AktoDataIngestionUrl
    if (-not $GuardrailsUrl) {
        Write-ErrorLog "AKTO_API_BASE_URL / AKTO_DATA_INGESTION_URL not set in config.env -- cannot install plugin"
        return $false
    }

    $ApiToken = Get-ApiToken -ConfigFile $ConfigFile -EnvVar $AktoApiToken
    if (-not $ApiToken) {
        Write-ErrorLog "AKTO_API_TOKEN not set in config.env -- cannot install plugin"
        return $false
    }

    $DeviceId = Get-DeviceId
    if (-not $DeviceId) { $DeviceId = "unknown-device" }

    Write-Log "Device ID: $DeviceId"
    Write-Log "Guardrails URL: $GuardrailsUrl"

    $configDir  = Join-Path $UserHome ".config\opencode"
    $pluginDir  = Join-Path $configDir "plugin"
    $pluginFile = Join-Path $pluginDir "akto-guardrails-plugin.js"

    New-Item -ItemType Directory -Force -Path $pluginDir | Out-Null
    New-Item -ItemType Directory -Force -Path (Join-Path $configDir "akto\logs") | Out-Null
    Write-Log "Plugin directory: $pluginDir"

    Write-Log "Downloading plugin files from GitHub..."

    # Shared utility — required by the Python handlers.
    $ingestionUtilPy = Join-Path $pluginDir "akto_ingestion_utility.py"
    if (-not (Get-FileFromUrl "$GITHUB_SHARED_BASE/akto_ingestion_utility.py" $ingestionUtilPy)) {
        Write-ErrorLog "Failed to download akto_ingestion_utility.py"
        return $false
    }
    Write-Log "Downloaded akto_ingestion_utility.py"

    $pyFiles = @(
        "akto_machine_id.py",
        "akto-validate-prompt.py",
        "akto-validate-tool-request.py",
        "akto-validate-tool-response.py",
        "akto-mcp-request.py",
        "akto-mcp-response.py"
    )
    foreach ($f in $pyFiles) {
        $dest = Join-Path $pluginDir $f
        if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/$f" $dest)) {
            Write-ErrorLog "Failed to download $f"
            return $false
        }
        Write-Log "Downloaded $f"
    }

    # Patch akto_machine_id.py: 'pwd' module is Unix-only, causes ModuleNotFoundError on Windows.
    $machineIdPy = Join-Path $pluginDir "akto_machine_id.py"
    if (Test-Path $machineIdPy) {
        $content = Get-Content $machineIdPy -Raw
        if ($content -match '(?m)^import pwd\s*$') {
            $patched = $content -replace '(?m)^import pwd\s*$', "try:`n    import pwd`nexcept ImportError:`n    pwd = None  # Not available on Windows"
            [System.IO.File]::WriteAllText($machineIdPy, $patched, (New-Object System.Text.UTF8Encoding($false)))
            Write-Log "Patched akto_machine_id.py for Windows compatibility"
        }
    }

    # Download the plugin, then write it with the injected config prelude.
    $upstreamTmp = Join-Path $pluginDir "akto-guardrails-plugin.js.tmp"
    if (-not (Get-FileFromUrl "$GITHUB_RAW_BASE/akto-guardrails-plugin.js" $upstreamTmp)) {
        Write-ErrorLog "Failed to download akto-guardrails-plugin.js"
        return $false
    }
    Write-PluginWithPrelude -UpstreamFile $upstreamTmp -IngestionUrl $GuardrailsUrl -ApiToken $ApiToken -DeviceId $DeviceId -DestFile $pluginFile
    Remove-Item $upstreamTmp -Force -ErrorAction SilentlyContinue

    Write-Log ""
    Write-Log "=========================================="
    Write-Log "OpenCode plugin installed successfully!"
    Write-Log "=========================================="
    Write-Log ""
    Write-Log "Plugin location : $pluginFile"
    Write-Log "Logs            : $(Join-Path $configDir 'akto\logs')"
    Write-Log "Guardrails URL  : $GuardrailsUrl"
    Write-Log "Restart OpenCode to load the plugin."

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
