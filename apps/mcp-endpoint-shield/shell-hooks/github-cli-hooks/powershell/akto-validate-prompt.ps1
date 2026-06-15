#!/usr/bin/env pwsh
# akto-validate-prompt.ps1 - PowerShell port of akto-validate-prompt.py (userPromptSubmitted)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force

$raw = [Console]::In.ReadToEnd()
try { $in = $raw | ConvertFrom-Json } catch { [Console]::Error.WriteLine("Invalid JSON input"); exit 0 }

$connector = Get-Connector $in
$deviceId  = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
Invoke-ConnectorSetup -Connector $connector -DeviceId $deviceId
$cfg = Get-AktoCfg

Set-AktoLogFile "validate-prompt.log"
$warnState = Join-Path (Get-AktoLogDir) "akto_prompt_warn_pending.json"
Send-AktoHeartbeat (Get-AktoLogDir)

Write-AktoInfo "=== UserPromptSubmitted Hook - Connector: $connector, Mode: $(if ($env:MODE) { $env:MODE } else { 'argus' }), Sync: $(Test-AktoSync) ==="

$prompt = [string]$in.prompt
if ([string]::IsNullOrWhiteSpace($prompt)) { Write-AktoInfo "Empty prompt, skipping"; exit 0 }

$rawTs = $in.timestamp
$timestamp = if ($rawTs -match '^\d+$') { [string]$rawTs } else { [string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) }

function Build-ValidationRequest {
    param([string]$Prompt, [string]$Timestamp)
    $host_ = $cfg.ApiUrl -replace '^https?://',''
    $tags = if ($script:Mode -eq "atlas") { [ordered]@{ "gen-ai"="Gen AI"; "ai-agent"=$cfg.AiAgentTag; source=(Get-AktoContextSource) } } else { [ordered]@{ "gen-ai"="Gen AI" } }

    $reqHdr  = [ordered]@{ host=$host_; ($cfg.HookHeader)="UserPromptSubmitted"; "content-type"="application/json" }
    $respHdr = [ordered]@{ ($cfg.HookHeader)="UserPromptSubmitted" }

    [ordered]@{
        path            = "/copilot/chat"
        requestHeaders  = (ConvertTo-AktoJson $reqHdr)
        responseHeaders = (ConvertTo-AktoJson $respHdr)
        method          = "POST"
        requestPayload  = (ConvertTo-AktoJson ([ordered]@{ body = $Prompt.Trim() }))
        responsePayload = "{}"
        ip              = (Get-AktoUsername)
        destIp          = "127.0.0.1"
        time            = $Timestamp
        statusCode      = "200"; type = "HTTP/1.1"; status = "200"
        akto_account_id = "1000000"
        akto_vxlan_id   = $deviceId
        is_pending      = "false"; source = "MIRRORING"
        direction = $null; process_id = $null; socket_id = $null; daemonset_id = $null; enabled_graph = $null
        tag             = (ConvertTo-AktoJson $tags)
        metadata        = (ConvertTo-AktoJson $tags)
        contextSource   = "ENDPOINT"
    }
}

if ((Test-AktoSync) -and (Get-AktoIngestUrl)) {
    $body = ConvertTo-AktoJson (Build-ValidationRequest -Prompt $prompt -Timestamp $timestamp)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl "prompt_guard") -Body $body
    $gr   = Get-AktoGuardrailsResult $resp
    $fp   = Get-AktoSha256Hex ('{"p":' + (ConvertTo-Json $prompt -Compress) + '}')
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $warnState

    if (-not $flow.Allowed) {
        if (Test-WarnBehaviour $gr.Behaviour) {
            $blockReason = "Warning!!, prompt blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)"
        } else {
            $blockReason = "Prompt blocked: $($gr.Reason)"
        }
        Write-AktoWarn "BLOCKING prompt - Reason: $blockReason"

        # Ingest blocked
        $blockedBody = ConvertTo-AktoJson (Build-ValidationRequest -Prompt $prompt -Timestamp $timestamp)
        $null = Invoke-AktoPost -Url (Get-AktoProxyUrl "ingest") -Body $blockedBody

        [Console]::Error.WriteLine("Warning:  Akto Guardrails flagged prompt: $(if ($gr.Reason) { $gr.Reason } else { 'Policy violation' })")
        (@{ continue=$false; stopReason=$blockReason } | ConvertTo-Json -Compress)
        exit $cfg.BlockedExitCode
    }
}
Write-AktoInfo "Hook completed"
exit 0
