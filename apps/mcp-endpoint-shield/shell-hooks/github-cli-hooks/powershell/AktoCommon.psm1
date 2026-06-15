# AktoCommon.psm1 - shared helpers for Akto github-cli / copilot hooks (PowerShell port).
#
# Native PowerShell only - no python, no jq, no modules to install.
# Targets PowerShell 7+ (cross-platform) with a fallback for Windows PowerShell 5.1.
# Connector auto-detected from hook payload (vscode vs copilot_cli).

$script:LogLevel  = $(if ($env:LOG_LEVEL) { $env:LOG_LEVEL } else { "INFO" }).ToUpper()
$script:LogPayloads = (($env:LOG_PAYLOADS) -as [string]).ToLower() -eq "true"
$script:Mode      = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$script:IngestUrl = (($env:AKTO_DATA_INGESTION_URL) -as [string]).TrimEnd("/")
$script:Timeout   = if ($env:AKTO_TIMEOUT) { [double]$env:AKTO_TIMEOUT } else { 5 }
$script:SyncMode  = $(if ($env:AKTO_SYNC_MODE) { $env:AKTO_SYNC_MODE } else { "true" } ).ToLower() -eq "true"
$script:ApiToken  = ($env:AKTO_API_TOKEN) -as [string]
$script:ContextSource = if ($env:CONTEXT_SOURCE) { $env:CONTEXT_SOURCE } else { "ENDPOINT" }
$script:McpIngestPath = if ($env:MCP_INGEST_PATH) { $env:MCP_INGEST_PATH } else { "/mcp" }
$script:EnvConnector  = if ($env:AKTO_CONNECTOR) { $env:AKTO_CONNECTOR } else { "copilot_cli" }

# Heartbeat
$script:HbModuleType = "MCP_ENDPOINT_SHIELD"
$script:HbVersion    = "1.0.0"
$script:HbIntervalS  = 30
$script:HbTimeout    = 3
$script:DbAbstractorUrl = ($(if ($env:DATABASE_ABSTRACTOR_SERVICE_URL) { $env:DATABASE_ABSTRACTOR_SERVICE_URL } else { "https://cyborg.akto.io" })).TrimEnd("/")

# Per-connector config (populated by Invoke-ConnectorSetup)
$script:Cfg = @{}
$script:LogDir  = ""
$script:LogFile = $null

function Set-AktoLogFile { param([string]$Name) $script:LogFile = Join-Path $script:LogDir $Name }

# ---- connector auto-detection -----------------------------------------------
function Get-Connector {
    param($InputObj)
    if ($null -ne $InputObj.hookEventName) { return "vscode" }
    return $script:EnvConnector
}

function Invoke-ConnectorSetup {
    param([string]$Connector, [string]$DeviceId)
    if ($Connector -eq "vscode") {
        $script:Cfg = @{
            Connector       = "vscode"
            IsVscode        = $true
            ApiUrl          = if ($env:VSCODE_API_URL) { $env:VSCODE_API_URL } else { "https://vscode.dev" }
            AiAgentTag      = "vscode"
            HookHeader      = "x-vscode-hook"
            AtlasDomain     = "ai-agent.vscode"
            LogDirDefault   = (Join-Path $HOME ".github/akto/vscode/logs")
            BlockedExitCode = 2
        }
    } else {
        $script:Cfg = @{
            Connector       = $Connector
            IsVscode        = $false
            ApiUrl          = if ($env:GITHUB_COPILOT_API_URL) { $env:GITHUB_COPILOT_API_URL } else { "https://api.github.com" }
            AiAgentTag      = "copilotcli"
            HookHeader      = "x-copilot-hook"
            AtlasDomain     = "ai-agent.copilot"
            LogDirDefault   = (Join-Path $HOME ".github/akto/copilot/logs")
            BlockedExitCode = 0
        }
    }
    if ($script:Mode -eq "atlas" -and $DeviceId) {
        $script:Cfg.ApiUrl = "https://$DeviceId.$($script:Cfg.AtlasDomain)"
    }
    $script:LogDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { $script:Cfg.LogDirDefault }
    New-Item -ItemType Directory -Force -Path $script:LogDir -ErrorAction SilentlyContinue | Out-Null
}

function Get-AktoCfg { $script:Cfg }
function Test-AktoSync { $script:SyncMode }
function Get-AktoIngestUrl { $script:IngestUrl }
function Get-AktoContextSource { $script:ContextSource }
function Get-AktoMcpIngestPath { $script:McpIngestPath }
function Get-AktoLogDir { $script:LogDir }

$script:Levels = @{ DEBUG=10; INFO=20; WARNING=30; ERROR=40 }
function Write-AktoLog {
    param([string]$Level, [string]$Message)
    if ($script:Levels[$Level] -lt $script:Levels[$script:LogLevel]) { return }
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    if ($script:LogFile) {
        try { Add-Content -Path $script:LogFile -Value "$ts - $Level - $Message" -ErrorAction SilentlyContinue } catch {}
    }
    if ($Level -eq "ERROR") { [Console]::Error.WriteLine($Message) }
}
function Write-AktoInfo  { param([string]$m) Write-AktoLog INFO $m }
function Write-AktoWarn  { param([string]$m) Write-AktoLog WARNING $m }
function Write-AktoError { param([string]$m) Write-AktoLog ERROR $m }

function ConvertTo-AktoJson { param($Obj) ($Obj | ConvertTo-Json -Depth 30 -Compress) }

function Get-AktoSha256Hex {
    param([string]$Text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
    } finally { $sha.Dispose() }
}

# ---- machine id / username --------------------------------------------------
$script:_MachineId = $null
$script:_Username  = $null

function Test-IsWindows { if ($null -ne (Get-Variable -Name IsWindows -ErrorAction SilentlyContinue)) { $IsWindows } else { $true } }
function Test-IsMac     { if ($null -ne (Get-Variable -Name IsMacOS -ErrorAction SilentlyContinue))   { $IsMacOS }   else { $false } }
function Test-IsLinux   { if ($null -ne (Get-Variable -Name IsLinux -ErrorAction SilentlyContinue))   { $IsLinux }   else { $false } }

function Get-AktoMachineRaw {
    if (Test-IsMac) {
        try { $n = (& scutil --get ComputerName 2>$null); if ($n) { return $n.Trim() } } catch {}
    }
    if (Test-IsWindows) {
        try { $g = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Cryptography' -Name MachineGuid -ErrorAction Stop).MachineGuid; if ($g) { return $g } } catch {}
        if ($env:COMPUTERNAME) { return $env:COMPUTERNAME }
    }
    if (Test-IsLinux) {
        foreach ($p in @('/etc/machine-id','/var/lib/dbus/machine-id')) {
            if (Test-Path $p) { $c = (Get-Content $p -Raw).Trim(); if ($c) { return $c } }
        }
    }
    try { $h = [System.Net.Dns]::GetHostName(); if ($h) { return ($h -replace '\.local$','') } } catch {}
    return ""
}

function Get-AktoMachineId {
    if ($script:_MachineId) { return $script:_MachineId }
    $raw = Get-AktoMachineRaw
    if (-not $raw) { $raw = "" }
    $raw = ($raw.ToLower() -replace '[^a-z0-9]','-')
    $script:_MachineId = $raw
    return $raw
}

function Get-AktoUsername {
    if ($script:_Username) { return $script:_Username }
    $u = ""
    if (Test-IsWindows -and $env:USERNAME) { $u = $env:USERNAME }
    if (-not $u -and $env:SUDO_USER -and $env:SUDO_USER -ne "root") { $u = $env:SUDO_USER }
    if (-not $u) { try { $u = [Environment]::UserName } catch {} }
    if (-not $u) { $u = "unknown" }
    $script:_Username = $u
    return $u
}

# ---- HTTP -------------------------------------------------------------------
# build_proxy_url KIND (matches Python query-string orderings)
function Get-AktoProxyUrl {
    param([string]$Kind)
    $base = "$($script:IngestUrl)/api/http-proxy"
    $c = $script:Cfg.Connector
    switch ($Kind) {
        "prompt_guard"     { return "$base?akto_connector=$c&guardrails=true" }
        "ingest"           { return "$base?akto_connector=$c&ingest_data=true" }
        "resp_guard"       { return "$base?response_guardrails=true&akto_connector=$c" }
        "resp_ingest"      { return "$base?akto_connector=$c&ingest_data=true" }
        "resp_guard_ingest"{ return "$base?response_guardrails=true&akto_connector=$c&ingest_data=true" }
    }
    return "$base?akto_connector=$c"
}

function Invoke-AktoPost {
    param([string]$Url, [string]$Body)
    Write-AktoInfo "API CALL: POST $Url"
    if ($script:LogPayloads) { Write-AktoInfo "Request payload: $Body" }
    $headers = @{ "Content-Type" = "application/json" }
    if ($script:ApiToken) { $headers["Authorization"] = $script:ApiToken }
    try {
        $common = @{ Uri=$Url; Method="POST"; Body=$Body; Headers=$headers; TimeoutSec=$script:Timeout }
        if ($PSVersionTable.PSVersion.Major -ge 6) {
            $resp = Invoke-RestMethod @common -SkipCertificateCheck
        } else {
            [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }
            $resp = Invoke-RestMethod @common
        }
        if ($script:LogPayloads) { Write-AktoInfo "Response body: $($resp | ConvertTo-Json -Depth 10 -Compress)" }
        return $resp
    } catch {
        Write-AktoError "API CALL FAILED: $($_.Exception.Message)"
        return $null
    }
}

# ---- guardrails result parsing ----------------------------------------------
function Get-AktoGuardrailsResult {
    param($Resp)
    $r = [ordered]@{ Allowed=$true; Reason=""; Behaviour="" }
    if ($null -eq $Resp) { return $r }
    $gr = $null
    try { $gr = $Resp.data.guardrailsResult } catch {}
    if ($null -eq $gr) { return $r }
    if ($null -ne $gr.Allowed) { $r.Allowed = [bool]$gr.Allowed }
    if ($null -ne $gr.Reason)  { $r.Reason  = [string]$gr.Reason }
    $beh = $gr.behaviour; if (-not $beh) { $beh = $gr.Behaviour }
    if ($beh) { $r.Behaviour = [string]$beh }
    return $r
}

function Test-WarnBehaviour  { param([string]$b) $b.Trim().ToLower() -eq "warn" }
function Test-AlertBehaviour { param([string]$b) $b.Trim().ToLower() -eq "alert" }

# ---- warn-pending state file -----------------------------------------------
function Get-WarnPending {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return @() }
    try { $d = Get-Content $Path -Raw | ConvertFrom-Json; return @($d.warn_pending) } catch { return @() }
}
function Save-WarnPending {
    param([string]$Path, [string[]]$Hashes)
    $tmp = "$Path.tmp"
    try {
        (@{ warn_pending = @($Hashes | Sort-Object -Unique) } | ConvertTo-Json -Compress) | Set-Content -Path $tmp -NoNewline
        Move-Item -Force $tmp $Path
    } catch { if (Test-Path $tmp) { Remove-Item $tmp -ErrorAction SilentlyContinue } }
}
function Invoke-WarnResubmitFlow {
    param([bool]$GrAllowed, [string]$Reason, [string]$Behaviour, [string]$Fingerprint, [string]$WarnFile)
    if ($GrAllowed) { return @{ Allowed=$true; Reason="" } }
    if (Test-AlertBehaviour $Behaviour) {
        Write-AktoInfo "Alert behaviour: allowing despite violation (server-side alert only)"
        return @{ Allowed=$true; Reason="" }
    }
    if (-not (Test-WarnBehaviour $Behaviour)) { return @{ Allowed=$false; Reason=$Reason } }
    $pending = New-Object System.Collections.Generic.List[string]
    $existing = Get-WarnPending $WarnFile
    if ($existing) { foreach ($h in $existing) { if ($h) { $pending.Add([string]$h) } } }
    if ($pending -contains $Fingerprint) {
        $pending.Remove($Fingerprint) | Out-Null
        Save-WarnPending $WarnFile $pending.ToArray()
        Write-AktoInfo "Warn flow: allowing resubmit; removed fingerprint"
        return @{ Allowed=$true; Reason="" }
    }
    $pending.Add($Fingerprint)
    Save-WarnPending $WarnFile $pending.ToArray()
    return @{ Allowed=$false; Reason=$Reason }
}

# ---- MCP tool parsing (mcp_<server>_<tool> OR <server>-<tool>) -------------
function Resolve-GithubTool {
    param([string]$ToolName)
    $result = @{ IsMcp=$false; Server=""; Tool="" }
    if ($ToolName.StartsWith("mcp_")) {
        $rest = $ToolName.Substring(4)
        $idx = $rest.IndexOf('_')
        if ($idx -gt 0 -and $idx -lt $rest.Length - 1) {
            $s = $rest.Substring(0, $idx); $t = $rest.Substring($idx + 1)
            if ($s -and $t) { $result.IsMcp=$true; $result.Server=$s; $result.Tool=$t; return $result }
        }
    }
    $idx = $ToolName.LastIndexOf('-')
    if ($idx -gt 0 -and $idx -lt $ToolName.Length - 1) {
        $s = $ToolName.Substring(0, $idx); $t = $ToolName.Substring($idx + 1)
        if ($s -and $t) { $result.IsMcp=$true; $result.Server=$s; $result.Tool=$t }
    }
    return $result
}

function Get-JsonRpcArguments {
    param($ti)
    if ($ti -is [pscustomobject] -or $ti -is [hashtable]) { return $ti }
    if ($null -eq $ti) { return @{} }
    return @{ input = $ti }
}

# ---- heartbeat (mirrors akto_heartbeat.py) ---------------------------------
function Send-AktoHeartbeat {
    param([string]$LogDir)
    $hbFile = Join-Path $LogDir "last_heartbeat"
    $agentFile = Join-Path $LogDir "agent_id"
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()

    if (Test-Path $hbFile) {
        try {
            $last = [long](Get-Content $hbFile -Raw).Trim()
            if (($now - $last) -lt $script:HbIntervalS) { Write-AktoInfo "Heartbeat skipped (rate limit)"; return }
        } catch {}
    }

    $agentId = ""
    if (Test-Path $agentFile) {
        try { $agentId = (Get-Content $agentFile -Raw).Trim() } catch {}
    }
    if (-not $agentId) {
        $agentId = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() * 1000000).ToString()
        try { $agentId | Set-Content $agentFile -NoNewline } catch {}
    }

    $deviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
    $username = Get-AktoUsername
    $body = ConvertTo-AktoJson ([ordered]@{
        moduleInfo = [ordered]@{
            id = $agentId; name = $deviceId
            moduleType = $script:HbModuleType; currentVersion = $script:HbVersion
            startedTs = $now; lastHeartbeatReceived = $now
            additionalData = [ordered]@{ username = $username; mcpServers = @{} }
        }
    })

    $url = "$($script:DbAbstractorUrl)/api/updateModuleInfoForHeartbeat"
    $headers = @{ "Content-Type" = "application/json" }
    if ($script:ApiToken) { $headers["Authorization"] = $script:ApiToken }
    try {
        $common = @{ Uri=$url; Method="POST"; Body=$body; Headers=$headers; TimeoutSec=$script:HbTimeout }
        if ($PSVersionTable.PSVersion.Major -ge 6) { Invoke-RestMethod @common -SkipCertificateCheck | Out-Null }
        else { [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }; Invoke-RestMethod @common | Out-Null }
        [string]$now | Set-Content $hbFile -NoNewline
        Write-AktoInfo "Heartbeat sent: agentId=$agentId, deviceId=$deviceId"
    } catch { Write-AktoInfo "Heartbeat failed (non-fatal): $($_.Exception.Message)" }
}

Export-ModuleMember -Function * -Variable *
