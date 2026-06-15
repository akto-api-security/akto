# AktoCommon.psm1 - shared helpers for Akto claude-cli hooks (PowerShell port).
#
# Native PowerShell only - no python, no jq, no modules to install.
# Targets PowerShell 7+ (cross-platform: Windows/macOS/Linux) with a
# fallback for Windows PowerShell 5.1.

$script:LogDir   = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".claude/akto/logs" }
$script:LogLevel = $(if ($env:LOG_LEVEL) { $env:LOG_LEVEL } else { "INFO" }).ToUpper()
$script:LogPayloads = (($env:LOG_PAYLOADS) -as [string]).ToLower() -eq "true"

$script:Mode      = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$script:IngestUrl = (($env:AKTO_DATA_INGESTION_URL) -as [string]).TrimEnd("/")
$script:Timeout   = if ($env:AKTO_TIMEOUT) { [double]$env:AKTO_TIMEOUT } else { 5 }
$script:SyncMode  = $(if ($env:AKTO_SYNC_MODE) { $env:AKTO_SYNC_MODE } else { "true" } ).ToLower() -eq "true"
$script:Connector = if ($env:AKTO_CONNECTOR) { $env:AKTO_CONNECTOR } else { "claude_code_cli" }
$script:ConnectorValue = if ($env:AKTO_CONNECTOR_VALUE) { $env:AKTO_CONNECTOR_VALUE } else { "claudecli" }
$script:ApiToken  = ($env:AKTO_API_TOKEN) -as [string]
$script:ContextSource = if ($env:CONTEXT_SOURCE) { $env:CONTEXT_SOURCE } else { "ENDPOINT" }
$script:IngestNonMcp = $(if ($env:AKTO_INGEST_NON_MCP_TOOLS) { $env:AKTO_INGEST_NON_MCP_TOOLS } else { "false" } ).ToLower() -eq "true"
$script:McpIngestPath = if ($env:MCP_INGEST_PATH) { $env:MCP_INGEST_PATH } else { "/mcp" }
$script:NonMcpPrefix = if ($env:NON_MCP_TOOL_PATH_PREFIX) { $env:NON_MCP_TOOL_PATH_PREFIX } else { "/tool" }

New-Item -ItemType Directory -Force -Path $script:LogDir -ErrorAction SilentlyContinue | Out-Null
$script:LogFile = $null   # set by each hook via Set-AktoLogFile

function Set-AktoLogFile { param([string]$Name) $script:LogFile = Join-Path $script:LogDir $Name }

function Get-AktoConfig { $script:Mode } # exposed accessor not strictly needed
function Test-AktoSync { $script:SyncMode }
function Get-AktoIngestUrl { $script:IngestUrl }
function Get-AktoContextSource { $script:ContextSource }
function Test-AktoIngestNonMcp { $script:IngestNonMcp }

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

# Compact JSON for a PS object (string fields containing JSON are produced by
# calling ConvertTo-Json on the inner object first).
function ConvertTo-AktoJson { param($Obj) ($Obj | ConvertTo-Json -Depth 30 -Compress) }

function Get-AktoSha256Hex {
    param([string]$Text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text)
        ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join ""
    } finally { $sha.Dispose() }
}

# ---- machine id / username (mirrors akto_machine_id.py) --------------------
$script:_MachineId = $null
$script:_Username = $null

function Test-IsWindows { if ($null -ne (Get-Variable -Name IsWindows -ErrorAction SilentlyContinue)) { $IsWindows } else { $true } }
function Test-IsMac     { if ($null -ne (Get-Variable -Name IsMacOS -ErrorAction SilentlyContinue))   { $IsMacOS }   else { $false } }
function Test-IsLinux   { if ($null -ne (Get-Variable -Name IsLinux -ErrorAction SilentlyContinue))   { $IsLinux }   else { $false } }

function Get-AktoMachineRaw {
    if (Test-IsMac) {
        try { $n = (& scutil --get ComputerName 2>$null); if ($n) { return $n.Trim() } } catch {}
    }
    if (Test-IsWindows) {
        try {
            $g = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Cryptography' -Name MachineGuid -ErrorAction Stop).MachineGuid
            if ($g) { return $g }
        } catch {}
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
function Get-AktoProxyUrl {
    param([switch]$Guardrails, [switch]$ResponseGuardrails, [switch]$IngestData)
    $params = @()
    if ($Guardrails) { $params += "guardrails=true" }
    if ($ResponseGuardrails) { $params += "response_guardrails=true" }
    $params += "akto_connector=$($script:Connector)"
    if ($IngestData) { $params += "ingest_data=true" }
    "$($script:IngestUrl)/api/http-proxy?$([string]::Join('&',$params))"
}

# POST a JSON string body; returns parsed object (or $null on error / non-JSON).
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
            # Windows PowerShell 5.1: disable cert validation globally (process scope)
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

# ---- guardrails result parsing ---------------------------------------------
function Get-AktoGuardrailsResult {
    param($Resp)
    $r = [ordered]@{ Allowed=$true; Reason=""; Behaviour=""; Modified=$false; ModifiedPayload=$null }
    if ($null -eq $Resp) { return $r }
    $gr = $null
    try { $gr = $Resp.data.guardrailsResult } catch {}
    if ($null -eq $gr) { return $r }
    if ($null -ne $gr.Allowed) { $r.Allowed = [bool]$gr.Allowed }
    if ($null -ne $gr.Reason)  { $r.Reason  = [string]$gr.Reason }
    $beh = $gr.behaviour; if (-not $beh) { $beh = $gr.Behaviour }
    if ($beh) { $r.Behaviour = [string]$beh }
    if ($null -ne $gr.Modified) { $r.Modified = [bool]$gr.Modified }
    if ($null -ne $gr.ModifiedPayload) { $r.ModifiedPayload = $gr.ModifiedPayload }
    return $r
}

function Test-WarnBehaviour  { param([string]$b) $b.Trim().ToLower() -eq "warn" }
function Test-AlertBehaviour { param([string]$b) $b.Trim().ToLower() -eq "alert" }

# ---- warn-pending state -----------------------------------------------------
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

# Returns @{ Allowed=[bool]; Reason=[string] }
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
        Write-AktoInfo "Warn flow: allowing resubmit; removed fingerprint from map"
        return @{ Allowed=$true; Reason="" }
    }
    $pending.Add($Fingerprint)
    Save-WarnPending $WarnFile $pending.ToArray()
    return @{ Allowed=$false; Reason=$Reason }
}

Export-ModuleMember -Function * -Variable *
