#!/usr/bin/env pwsh
# akto-validate-response.ps1 - PowerShell port of akto-validate-response.py (Stop)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "validate-response.log"

$logDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".claude/akto/logs" }
$WarnState = Join-Path $logDir "akto_response_warn_pending.json"
$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$ConnectorValue = if ($env:AKTO_CONNECTOR_VALUE) { $env:AKTO_CONNECTOR_VALUE } else { "claudecli" }
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
if ($Mode -eq "atlas") {
    $ClaudeApiUrl = if ($DeviceId) { "https://$DeviceId.ai-agent.$ConnectorValue" } else { "https://api.anthropic.com" }
} else { $ClaudeApiUrl = if ($env:CLAUDE_API_URL) { $env:CLAUDE_API_URL } else { "https://api.anthropic.com" } }

function Get-EntryText {
    param($Entry)
    $content = $Entry.message.content
    if ($content -is [string]) { return $content.Trim() }
    if ($content -is [System.Collections.IEnumerable]) {
        $parts = foreach ($b in $content) { if ($b.type -eq "text" -and $b.text) { [string]$b.text } }
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
        if ($e.type -eq "user") { $t = Get-EntryText $e; if ($t) { $last = $t } }
    }
    return $last
}

function Build-IngestionPayload {
    param([string]$UserPrompt, [string]$ResponseText, [hashtable]$Session)
    $host_ = $ClaudeApiUrl -replace '^https?://',''
    $tags = [ordered]@{ "gen-ai"="Gen AI" }
    if ($Mode -eq "atlas") { $tags["ai-agent"]=$ConnectorValue; $tags["source"]=(Get-AktoContextSource) }
    $reqHdr = [ordered]@{ host=$host_; "x-claude-hook"="Stop"; "content-type"="application/json" }
    foreach ($k in $Session.Keys) { if ($null -ne $Session[$k]) { $reqHdr["x-akto-installer-$k"]=[string]$Session[$k] } }
    [ordered]@{
        path="/v1/messages"
        requestHeaders=(ConvertTo-AktoJson $reqHdr)
        responseHeaders=(ConvertTo-AktoJson ([ordered]@{ "x-claude-hook"="Stop"; "content-type"="application/json" }))
        method="POST"
        requestPayload=(ConvertTo-AktoJson ([ordered]@{ body=$UserPrompt }))
        responsePayload=(ConvertTo-AktoJson ([ordered]@{ body=$ResponseText }))
        ip=(Get-AktoUsername); destIp="127.0.0.1"
        time=([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
        statusCode="200"; type="HTTP/1.1"; status="200"
        akto_account_id="1000000"; akto_vxlan_id=$DeviceId
        is_pending="false"; source="MIRRORING"
        direction=$null; process_id=$null; socket_id=$null; daemonset_id=$null; enabled_graph=$null
        tag=(ConvertTo-AktoJson $tags); metadata=(ConvertTo-AktoJson $tags)
        contextSource=(Get-AktoContextSource)
    }
}

$raw = [Console]::In.ReadToEnd()
try { $input_ = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; exit 0 }
$session = @{}
foreach ($f in "session_id","transcript_path","cwd","permission_mode","hook_event_name") {
    if ($null -ne $input_.$f) { $session[$f] = $input_.$f }
}
$transcript = [string]$input_.transcript_path
if (-not $transcript) { Write-AktoInfo "No transcript path provided"; exit 0 }
if ($transcript.StartsWith("~/")) { $transcript = Join-Path $HOME $transcript.Substring(2) }
$responseText = ([string]$input_.last_assistant_message).Trim()
$stopActive = [bool]$input_.stop_hook_active
$userPrompt = Get-LastUserPrompt $transcript
if (-not $userPrompt -or -not $responseText) { Write-AktoInfo "No complete interaction found"; exit 0 }

if ((Test-AktoSync) -and -not $stopActive) {
    if (-not (Get-AktoIngestUrl)) { Write-AktoWarn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; exit 0 }
    $body = ConvertTo-AktoJson (Build-IngestionPayload -UserPrompt $userPrompt -ResponseText $responseText -Session $session)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails) -Body $body
    $gr = Get-AktoGuardrailsResult $resp
    $fp = Get-AktoSha256Hex ([ordered]@{ p=$userPrompt; r=$responseText } | ConvertTo-Json -Compress)
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState
    if (-not $flow.Allowed) {
        if (Test-WarnBehaviour $gr.Behaviour) {
            $reason = "Warning!!, response blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)"
        } else { $reason = "Response blocked: $($gr.Reason)" }
        Write-AktoWarn "BLOCKING Stop - Reason: $($gr.Reason)"
        (@{ decision="block"; reason=$reason } | ConvertTo-Json -Compress)
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
