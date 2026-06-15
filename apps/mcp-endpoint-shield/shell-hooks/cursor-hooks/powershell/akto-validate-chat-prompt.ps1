#!/usr/bin/env pwsh
# akto-validate-chat-prompt.ps1 - PowerShell port of akto-validate-chat-prompt.py
# Cursor beforeSubmitPrompt hook. Output: {"continue":<bool>[,"user_message":<str>]}.
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "akto-validate-chat-prompt.log"

$logDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".cursor/akto/chat-logs" }
$WarnState = Join-Path $logDir "akto_chat_prompt_warn_pending.json"
$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
if ($Mode -eq "atlas") {
    $ApiUrl = if ($DeviceId) { "https://$DeviceId.ai-agent.cursor" } else { "https://api.anthropic.com" }
} else { $ApiUrl = if ($env:API_URL) { $env:API_URL } else { "https://api.anthropic.com" } }

function Build-ValidationRequest {
    param([string]$Prompt, $Attachments, [hashtable]$Session)
    $host_ = $ApiUrl -replace '^https?://',''
    $tags = [ordered]@{ "gen-ai" = "Gen AI" }
    if ($Mode -eq "atlas") { $tags["ai-agent"] = "cursor"; $tags["source"] = (Get-AktoContextSource) }
    if ($Attachments -and @($Attachments).Count -gt 0) {
        $tags["attachments_count"] = @($Attachments).Count
        $types = foreach ($a in $Attachments) { if ($a.type) { [string]$a.type } else { "unknown" } }
        $tags["attachment_types"] = ($types -join ",")
    }
    $reqHdr = [ordered]@{ host = $host_; "x-cursor-hook" = "beforeSubmitPrompt"; "content-type" = "application/json" }
    foreach ($k in $Session.Keys) { if ($null -ne $Session[$k]) { $reqHdr["x-akto-installer-$k"] = [string]$Session[$k] } }
    [ordered]@{
        path            = "/v1/messages"
        requestHeaders  = (ConvertTo-AktoJson $reqHdr)
        responseHeaders = (ConvertTo-AktoJson ([ordered]@{ "x-cursor-hook" = "beforeSubmitPrompt" }))
        method          = "POST"
        requestPayload  = (ConvertTo-AktoJson ([ordered]@{ body = $Prompt }))
        responsePayload = "{}"
        ip              = (Get-AktoUsername); destIp = "127.0.0.1"
        time            = ([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
        statusCode      = "200"; type = $null; status = "200"
        akto_account_id = "1000000"; akto_vxlan_id = $DeviceId
        is_pending      = "false"; source = "MIRRORING"
        direction = $null; process_id = $null; socket_id = $null; daemonset_id = $null; enabled_graph = $null
        tag             = (ConvertTo-AktoJson $tags); metadata = (ConvertTo-AktoJson $tags)
        contextSource   = (Get-AktoContextSource)
    }
}

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { '{"continue":true}'; exit 0 }
try { $input_ = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; '{"continue":true}'; exit 0 }

$prompt = [string]$input_.prompt
$attachments = if ($null -ne $input_.attachments) { @($input_.attachments) } else { @() }
$session = @{}
foreach ($f in "conversation_id","generation_id","model","transcript_path","user_email") {
    if ($null -ne $input_.$f) { $session[$f] = $input_.$f }
}

if ([string]::IsNullOrWhiteSpace($prompt)) { Write-AktoWarn "Empty prompt received, allowing"; '{"continue":true}'; exit 0 }
Write-AktoInfo "Processing prompt (length: $($prompt.Length) chars)"

$gr = [ordered]@{ Allowed=$true; Reason=""; Behaviour="" }
if (Get-AktoIngestUrl) {
    $body = ConvertTo-AktoJson (Build-ValidationRequest -Prompt $prompt -Attachments $attachments -Session $session)
    if (Test-AktoSync) { $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -Guardrails -IngestData) -Body $body }
    else { $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -IngestData) -Body $body }
    $gr = Get-AktoGuardrailsResult $resp
} else { Write-AktoWarn "AKTO_DATA_INGESTION_URL not set, allowing prompt" }

# fingerprint: json.dumps({"p":prompt,"a":attachments}, sort_keys=True) -> {"a":..,"p":..}
$fpObj = [ordered]@{ a = $attachments; p = $prompt }
$fp = Get-AktoSha256Hex ($fpObj | ConvertTo-Json -Depth 30 -Compress)
$flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState

if (-not $flow.Allowed) {
    if (Test-WarnBehaviour $gr.Behaviour) {
        $msg = "Warning!!, prompt blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)"
    } else { $msg = "Prompt blocked: $($gr.Reason)" }
    Write-AktoWarn "BLOCKING prompt - Reason: $($gr.Reason)"
    (@{ continue = $false; user_message = $msg } | ConvertTo-Json -Compress)
    exit 0
}
Write-AktoInfo "Prompt allowed"
'{"continue":true}'
exit 0
