#!/usr/bin/env pwsh
# akto-validate-response.ps1 - PowerShell port of codex akto-validate-response.py (Stop)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "validate-response.log"

$logDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".codex/akto/logs" }
$WarnState = Join-Path $logDir "akto_response_warn_pending.json"
$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()

function Resolve-CodexApi {
    if ($env:OPENAI_BASE_URL) { return @{ Host=($env:OPENAI_BASE_URL).TrimEnd("/"); Path="/v1/responses" } }
    if ($env:OPENAI_API_KEY)  { return @{ Host="https://api.openai.com"; Path="/v1/responses" } }
    return @{ Host="https://chatgpt.com"; Path="/backend-api/codex/responses" }
}
$api = Resolve-CodexApi
$CodexApiPath = $api.Path
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
if ($Mode -eq "atlas") {
    $CodexApiHost = if ($DeviceId) { "https://$DeviceId.ai-agent.codexcli" } else { $api.Host }
} else { $CodexApiHost = $api.Host }

function Get-PayloadText {
    param($Content)
    if ($Content -is [string]) { return $Content.Trim() }
    if ($Content -is [System.Collections.IEnumerable]) {
        $parts = foreach ($b in $Content) {
            if (($b.type -eq "input_text" -or $b.type -eq "output_text" -or $b.type -eq "text") -and $b.text) { [string]$b.text }
        }
        return (($parts -join "").Trim())
    }
    return ""
}
function Get-LastUserPrompt {
    param([string]$Path)
    if (-not $Path -or -not (Test-Path $Path)) { return "" }
    $last = ""
    foreach ($line in (Get-Content $Path)) {
        if (-not $line) { continue }
        try { $e = $line | ConvertFrom-Json } catch { continue }
        if ($e.type -ne "response_item") { continue }
        $p = $e.payload
        if ($null -eq $p) { continue }
        if ($p.type -eq "message" -and $p.role -eq "user") {
            $t = Get-PayloadText $p.content
            if ($t) { $last = $t }
        }
    }
    return $last
}

function Build-IngestionPayload {
    param([string]$UserPrompt, [string]$ResponseText, [hashtable]$Session)
    $host_ = $CodexApiHost -replace '^https?://',''
    $tags = [ordered]@{ "gen-ai"="Gen AI" }
    if ($Mode -eq "atlas") { $tags["ai-agent"]="codexcli"; $tags["source"]=(Get-AktoContextSource) }
    $reqHdr = [ordered]@{ host=$host_; "x-codex-hook"="Stop"; "content-type"="application/json" }
    foreach ($k in $Session.Keys) { if ($null -ne $Session[$k]) { $reqHdr["x-akto-installer-$k"]=[string]$Session[$k] } }
    [ordered]@{
        path=$CodexApiPath
        requestHeaders=(ConvertTo-AktoJson $reqHdr)
        responseHeaders=(ConvertTo-AktoJson ([ordered]@{ "x-codex-hook"="Stop"; "content-type"="application/json" }))
        method="POST"
        requestPayload=(ConvertTo-AktoJson ([ordered]@{ body=$UserPrompt }))
        responsePayload=(ConvertTo-AktoJson ([ordered]@{ body=$ResponseText }))
        ip=(Get-AktoUsername); destIp="127.0.0.1"
        time=([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
        statusCode="200"; type="HTTP/1.1"; status="200"
        akto_account_id="1000000"; akto_vxlan_id=0
        is_pending="false"; source="MIRRORING"
        direction=$null; process_id=$null; socket_id=$null; daemonset_id=$null; enabled_graph=$null
        tag=(ConvertTo-AktoJson $tags); metadata=(ConvertTo-AktoJson $tags)
        contextSource=(Get-AktoContextSource)
    }
}

$raw = [Console]::In.ReadToEnd()
try { $input_ = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; exit 0 }
$session = @{}
foreach ($f in "session_id","transcript_path","cwd","hook_event_name","model","turn_id") {
    if ($null -ne $input_.$f) { $session[$f] = $input_.$f }
}
$transcript = [string]$input_.transcript_path
if (-not $transcript) { Write-AktoInfo "No transcript path provided"; exit 0 }
if ($transcript.StartsWith("~/")) { $transcript = Join-Path $HOME $transcript.Substring(2) }
$responseText = ([string]$input_.last_assistant_message).Trim()
$stopActive = [bool]$input_.stop_hook_active
$userPrompt = Get-LastUserPrompt $transcript
if (-not $userPrompt -or -not $responseText) { Write-AktoInfo "No complete interaction found"; exit 0 }

if ($stopActive) { Write-AktoInfo "stop_hook_active=true: skipping guardrails block to avoid Stop hook loops" }

if ((Test-AktoSync) -and -not $stopActive) {
    if (-not (Get-AktoIngestUrl)) { Write-AktoWarn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; exit 0 }
    $body = ConvertTo-AktoJson (Build-IngestionPayload -UserPrompt $userPrompt -ResponseText $responseText -Session $session)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails) -Body $body
    $gr = Get-AktoGuardrailsResult $resp
    $fp = Get-AktoSha256Hex ([ordered]@{ p=$userPrompt; r=$responseText } | ConvertTo-Json -Compress)
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState
    if (-not $flow.Allowed) {
        Write-AktoWarn "BLOCKING Stop - Reason: $($gr.Reason)"
        if (Test-WarnBehaviour $gr.Behaviour) {
            $reason = "Warning!!, response blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)"
            (@{ decision="block"; reason=$reason } | ConvertTo-Json -Compress)
        } else {
            $reason = "Response blocked: $($gr.Reason)"
            $stopReason = $(if ($gr.Reason) { $gr.Reason } else { "Policy violation" })
            ([ordered]@{ continue=$false; stopReason=$stopReason; systemMessage=$reason } | ConvertTo-Json -Compress)
        }
        exit 0
    }
}
if (Get-AktoIngestUrl) {
    $body = ConvertTo-AktoJson (Build-IngestionPayload -UserPrompt $userPrompt -ResponseText $responseText -Session $session)
    if (Test-AktoSync) { $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -IngestData) -Body $body }
    else { $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails -IngestData) -Body $body }
}
Write-AktoInfo "Hook execution completed"
exit 0
