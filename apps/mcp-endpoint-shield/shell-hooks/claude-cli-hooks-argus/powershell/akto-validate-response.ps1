#!/usr/bin/env pwsh
# akto-validate-response.ps1 - PowerShell port of claude-cli-hooks-argus/akto-validate-response.py (Stop)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "validate-response.log"
$logDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".claude/akto/logs" }
$WarnState = Join-Path $logDir "akto_response_warn_pending.json"
$AktoHost = if ($env:AKTO_HOST) { $env:AKTO_HOST } else { "https://api.anthropic.com" }
$ContextSource = if ($env:CONTEXT_SOURCE) { $env:CONTEXT_SOURCE } else { "AGENTIC" }
$HostHeader = $AktoHost -replace '^https?://',''
$DeviceIp = Get-AktoDeviceIp

function Get-EntryText { param($Entry)
    $c = $Entry.message.content
    if ($c -is [string]) { return $c.Trim() }
    if ($c -is [System.Collections.IEnumerable]) { $p = foreach ($b in $c) { if ($b.type -eq "text" -and $b.text) { [string]$b.text } }; return (($p -join "").Trim()) }
    return ""
}
function Get-LastUserPrompt { param([string]$Path)
    if (-not $Path -or -not (Test-Path $Path)) { return "" }
    $last=""; foreach ($line in (Get-Content $Path)) { if (-not $line) { continue }; try { $e=$line|ConvertFrom-Json } catch { continue }; if ($e.type -eq "user") { $t=Get-EntryText $e; if ($t){$last=$t} } }
    return $last
}
function Build-IngestionPayload { param([string]$UserPrompt,[string]$ResponseText,[hashtable]$Session)
    $tags=[ordered]@{ "gen-ai"="Gen AI"; "source"=$ContextSource }
    $reqHdr=[ordered]@{ host=$HostHeader; "x-claude-hook"="Stop"; "content-type"="application/json" }
    foreach ($k in $Session.Keys) { if ($null -ne $Session[$k]) { $reqHdr["x-akto-installer-$k"]=[string]$Session[$k] } }
    [ordered]@{
        path="/v1/messages"; requestHeaders=(ConvertTo-AktoJson $reqHdr)
        responseHeaders=(ConvertTo-AktoJson ([ordered]@{ "x-claude-hook"="Stop"; "content-type"="application/json" }))
        method="POST"; requestPayload=(ConvertTo-AktoJson ([ordered]@{ body=$UserPrompt })); responsePayload=(ConvertTo-AktoJson ([ordered]@{ body=$ResponseText }))
        ip=$DeviceIp; destIp="127.0.0.1"; time=([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
        statusCode="200"; type="HTTP/1.1"; status="200"; akto_account_id="1000000"; akto_vxlan_id=0
        is_pending="false"; source="MIRRORING"; direction=$null; process_id=$null; socket_id=$null; daemonset_id=$null; enabled_graph=$null
        tag=(ConvertTo-AktoJson $tags); metadata=(ConvertTo-AktoJson $tags); contextSource=$ContextSource
    }
}
$raw = [Console]::In.ReadToEnd()
try { $in = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; exit 0 }
$session=@{}; foreach ($f in "session_id","transcript_path","cwd","permission_mode","hook_event_name") { if ($null -ne $in.$f) { $session[$f]=$in.$f } }
$transcript=[string]$in.transcript_path
if (-not $transcript) { Write-AktoInfo "No transcript path"; exit 0 }
if ($transcript.StartsWith("~/")) { $transcript = Join-Path $HOME $transcript.Substring(2) }
$responseText=([string]$in.last_assistant_message).Trim()
$stopActive=[bool]$in.stop_hook_active
$userPrompt=Get-LastUserPrompt $transcript
if (-not $userPrompt -or -not $responseText) { Write-AktoInfo "No complete interaction"; exit 0 }
if ((Test-AktoSync) -and -not $stopActive) {
    if (-not (Get-AktoIngestUrl)) { Write-AktoWarn "no URL, fail-open"; exit 0 }
    $body = ConvertTo-AktoJson (Build-IngestionPayload -UserPrompt $userPrompt -ResponseText $responseText -Session $session)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails) -Body $body
    $gr = Get-AktoGuardrailsResult $resp
    $fp = Get-AktoSha256Hex ([ordered]@{ p=$userPrompt; r=$responseText } | ConvertTo-Json -Compress)
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState
    if (-not $flow.Allowed) {
        if (Test-WarnBehaviour $gr.Behaviour) { $reason="Warning!!, response blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)" } else { $reason="Response blocked: $($gr.Reason)" }
        Write-AktoWarn "BLOCKING Stop - Reason: $($gr.Reason)"
        (@{ decision="block"; reason=$reason } | ConvertTo-Json -Compress); exit 0
    }
}
if (Get-AktoIngestUrl) {
    $body = ConvertTo-AktoJson (Build-IngestionPayload -UserPrompt $userPrompt -ResponseText $responseText -Session $session)
    if (Test-AktoSync) { $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -IngestData) -Body $body } else { $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails -IngestData) -Body $body }
}
exit 0
