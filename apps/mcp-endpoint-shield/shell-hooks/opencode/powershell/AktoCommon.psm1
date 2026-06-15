# AktoCommon.psm1 - shared helpers for Akto opencode hooks (PowerShell port).
# Native PowerShell only - no python, no jq, no modules to install.

$script:LogDir   = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".config/opencode/akto/logs" }
$script:LogLevel = $(if ($env:LOG_LEVEL) { $env:LOG_LEVEL } else { "INFO" }).ToUpper()
$script:LogPayloads = (($env:LOG_PAYLOADS) -as [string]).ToLower() -eq "true"
$script:Mode      = $(if ($env:MODE) { $env:MODE } else { "atlas" }).ToLower()
$script:IngestUrl = (($env:AKTO_DATA_INGESTION_URL) -as [string]).TrimEnd("/")
$script:Timeout   = if ($env:AKTO_TIMEOUT) { [double]$env:AKTO_TIMEOUT } else { 5 }
$script:SyncMode  = $(if ($env:AKTO_SYNC_MODE) { $env:AKTO_SYNC_MODE } else { "true" } ).ToLower() -eq "true"
$script:Connector = if ($env:AKTO_CONNECTOR) { $env:AKTO_CONNECTOR } else { "opencode" }
$script:ApiToken  = ($env:AKTO_API_TOKEN) -as [string]
$script:ContextSource = if ($env:CONTEXT_SOURCE) { $env:CONTEXT_SOURCE } else { "ENDPOINT" }

New-Item -ItemType Directory -Force -Path $script:LogDir -ErrorAction SilentlyContinue | Out-Null
$script:LogFile = $null

function Set-AktoLogFile { param([string]$Name) $script:LogFile = Join-Path $script:LogDir $Name }
function Test-AktoSync { $script:SyncMode }
function Get-AktoIngestUrl { $script:IngestUrl }
function Get-AktoContextSource { $script:ContextSource }
function Get-AktoLogDir { $script:LogDir }

$script:Levels = @{ DEBUG=10; INFO=20; WARNING=30; ERROR=40 }
function Write-AktoLog {
    param([string]$Level, [string]$Message)
    if ($script:Levels[$Level] -lt $script:Levels[$script:LogLevel]) { return }
    $ts = (Get-Date).ToString("yyyy-MM-dd HH:mm:ss")
    if ($script:LogFile) { try { Add-Content -Path $script:LogFile -Value "$ts - $Level - $Message" -ErrorAction SilentlyContinue } catch {} }
    if ($Level -eq "ERROR") { [Console]::Error.WriteLine($Message) }
}
function Write-AktoInfo  { param([string]$m) Write-AktoLog INFO $m }
function Write-AktoWarn  { param([string]$m) Write-AktoLog WARNING $m }
function Write-AktoError { param([string]$m) Write-AktoLog ERROR $m }

function ConvertTo-AktoJson { param($Obj) ($Obj | ConvertTo-Json -Depth 30 -Compress) }

function Get-AktoSha256Hex {
    param([string]$Text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try { $bytes = [System.Text.Encoding]::UTF8.GetBytes($Text); ($sha.ComputeHash($bytes) | ForEach-Object { $_.ToString("x2") }) -join "" } finally { $sha.Dispose() }
}

$script:_MachineId = $null; $script:_Username = $null
function Test-IsWindows { if ($null -ne (Get-Variable -Name IsWindows -ErrorAction SilentlyContinue)) { $IsWindows } else { $true } }
function Test-IsMac     { if ($null -ne (Get-Variable -Name IsMacOS  -ErrorAction SilentlyContinue)) { $IsMacOS  } else { $false } }
function Test-IsLinux   { if ($null -ne (Get-Variable -Name IsLinux  -ErrorAction SilentlyContinue)) { $IsLinux  } else { $false } }

function Get-AktoMachineId {
    if ($script:_MachineId) { return $script:_MachineId }
    $raw = ""
    if (Test-IsMac) { try { $n = (& scutil --get ComputerName 2>$null); if ($n) { $raw = $n.Trim() } } catch {} }
    if (-not $raw -and Test-IsWindows) {
        try { $raw = (Get-ItemProperty 'HKLM:\SOFTWARE\Microsoft\Cryptography' -Name MachineGuid -ErrorAction Stop).MachineGuid } catch {}
        if (-not $raw -and $env:COMPUTERNAME) { $raw = $env:COMPUTERNAME }
    }
    if (-not $raw -and Test-IsLinux) {
        foreach ($p in @('/etc/machine-id','/var/lib/dbus/machine-id')) { if (Test-Path $p) { $c = (Get-Content $p -Raw).Trim(); if ($c) { $raw = $c; break } } }
    }
    if (-not $raw) { try { $raw = [System.Net.Dns]::GetHostName() -replace '\.local$','' } catch {} }
    if (-not $raw) { $raw = "" }
    $raw = ($raw.ToLower() -replace '[^a-z0-9]','-')
    $script:_MachineId = $raw; return $raw
}
function Get-AktoUsername {
    if ($script:_Username) { return $script:_Username }
    $u = ""
    if (Test-IsWindows -and $env:USERNAME) { $u = $env:USERNAME }
    if (-not $u -and $env:SUDO_USER -and $env:SUDO_USER -ne "root") { $u = $env:SUDO_USER }
    if (-not $u) { try { $u = [Environment]::UserName } catch {} }
    if (-not $u) { $u = "unknown" }
    $script:_Username = $u; return $u
}

# Resolve OpenCode API URL
function Get-OpenCodeApiUrl {
    param([string]$DeviceId)
    if ($script:Mode -eq "atlas") {
        if ($DeviceId) { return "https://$DeviceId.opencode.local" }
        return if ($env:OPENCODE_API_URL) { $env:OPENCODE_API_URL } else { "https://api.opencode.ai" }
    }
    return if ($env:OPENCODE_API_URL) { $env:OPENCODE_API_URL } else { "https://api.opencode.ai" }
}

# build_http_proxy_url GUARDRAILS(bool) INGEST(bool)
function Get-AktoProxyUrl {
    param([bool]$Guardrails, [bool]$IngestData)
    $params = @()
    if ($Guardrails) { $params += "guardrails=true" }
    $params += "akto_connector=$($script:Connector)"
    if ($IngestData) { $params += "ingest_data=true" }
    "$($script:IngestUrl)/api/http-proxy?$([string]::Join('&',$params))"
}

function Invoke-AktoPost {
    param([string]$Url, [string]$Body)
    Write-AktoInfo "API CALL: POST $Url"
    if ($script:LogPayloads) { Write-AktoInfo "Request payload: $Body" }
    $headers = @{ "Content-Type" = "application/json" }
    if ($script:ApiToken) { $headers["Authorization"] = $script:ApiToken }
    try {
        $common = @{ Uri=$Url; Method="POST"; Body=$Body; Headers=$headers; TimeoutSec=$script:Timeout }
        if ($PSVersionTable.PSVersion.Major -ge 6) { $resp = Invoke-RestMethod @common -SkipCertificateCheck }
        else { [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }; $resp = Invoke-RestMethod @common }
        return $resp
    } catch { Write-AktoError "API CALL FAILED: $($_.Exception.Message)"; return $null }
}

function Get-AktoGuardrailsResult {
    param($Resp)
    $r = [ordered]@{ Allowed=$true; Reason=""; Behaviour="" }
    if ($null -eq $Resp) { return $r }
    $gr = $null; try { $gr = $Resp.data.guardrailsResult } catch {}
    if ($null -eq $gr) { return $r }
    if ($null -ne $gr.Allowed)  { $r.Allowed = [bool]$gr.Allowed }
    if ($null -ne $gr.Reason)   { $r.Reason  = [string]$gr.Reason }
    $beh = $gr.behaviour; if (-not $beh) { $beh = $gr.Behaviour }; if ($beh) { $r.Behaviour = [string]$beh }
    return $r
}

function Test-WarnBehaviour  { param([string]$b) $b.Trim().ToLower() -eq "warn" }
function Test-AlertBehaviour { param([string]$b) $b.Trim().ToLower() -eq "alert" }

function Get-WarnPending {
    param([string]$Path)
    if (-not (Test-Path $Path)) { return @() }
    try { $d = Get-Content $Path -Raw | ConvertFrom-Json; return @($d.warn_pending) } catch { return @() }
}
function Save-WarnPending {
    param([string]$Path, [string[]]$Hashes)
    $tmp = "$Path.tmp"
    try { (@{ warn_pending = @($Hashes | Sort-Object -Unique) } | ConvertTo-Json -Compress) | Set-Content -Path $tmp -NoNewline; Move-Item -Force $tmp $Path }
    catch { if (Test-Path $tmp) { Remove-Item $tmp -ErrorAction SilentlyContinue } }
}
function Invoke-WarnResubmitFlow {
    param([bool]$GrAllowed, [string]$Reason, [string]$Behaviour, [string]$Fingerprint, [string]$WarnFile)
    if ($GrAllowed) { return @{ Allowed=$true; Reason="" } }
    if (Test-AlertBehaviour $Behaviour) { Write-AktoInfo "Alert: allowing"; return @{ Allowed=$true; Reason="" } }
    if (-not (Test-WarnBehaviour $Behaviour)) { return @{ Allowed=$false; Reason=$Reason } }
    $pending = New-Object System.Collections.Generic.List[string]
    $existing = Get-WarnPending $WarnFile
    if ($existing) { foreach ($h in $existing) { if ($h) { $pending.Add([string]$h) } } }
    if ($pending -contains $Fingerprint) {
        $pending.Remove($Fingerprint) | Out-Null; Save-WarnPending $WarnFile $pending.ToArray()
        Write-AktoInfo "Warn: allowing resubmit"; return @{ Allowed=$true; Reason="" }
    }
    $pending.Add($Fingerprint); Save-WarnPending $WarnFile $pending.ToArray()
    return @{ Allowed=$false; Reason=$Reason }
}

Export-ModuleMember -Function * -Variable *
