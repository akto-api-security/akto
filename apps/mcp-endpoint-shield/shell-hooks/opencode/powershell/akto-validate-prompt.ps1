#!/usr/bin/env pwsh
# akto-validate-prompt.ps1 - PowerShell port of akto-validate-prompt.py
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "validate-prompt.log"

$warnState = Join-Path (Get-AktoLogDir) "akto_prompt_warn_pending.json"
$deviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
$apiUrl = Get-OpenCodeApiUrl -DeviceId $deviceId
$mode = $(if ($env:MODE) { $env:MODE } else { "atlas" }).ToLower()

Write-AktoInfo "=== Prompt hook started - Mode: $mode, Sync: $(Test-AktoSync) ==="

$raw = [Console]::In.ReadToEnd()
try { $in = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; exit 0 }

# Extract prompt
$prompt = [string]$in.prompt
if ([string]::IsNullOrWhiteSpace($prompt) -and $null -ne $in.messages) {
    $msgs = @($in.messages)
    for ($i = $msgs.Count - 1; $i -ge 0; $i--) {
        if ($msgs[$i].role -eq "user") {
            $c = $msgs[$i].content
            if ($c -is [string]) { $prompt = $c; break }
            if ($c -is [System.Collections.IEnumerable]) {
                $parts = foreach ($p in $c) { if ($null -ne $p.text) { [string]$p.text } }
                $prompt = $parts -join ""; break
            }
        }
    }
}
if ([string]::IsNullOrWhiteSpace($prompt)) { Write-AktoInfo "Empty prompt, skipping"; exit 0 }

function Build-ValidationRequest {
    $host_ = $apiUrl -replace '^https?://',''
    $tags = [ordered]@{ "gen-ai" = "Gen AI" }
    if ($mode -eq "atlas") { $tags["ai-agent"] = "opencode"; $tags["source"] = Get-AktoContextSource }

    [ordered]@{
        path            = "/v1/messages"
        requestHeaders  = (ConvertTo-AktoJson ([ordered]@{ host=$host_; "x-claude-hook"="UserPromptSubmit"; "content-type"="application/json" }))
        responseHeaders = (ConvertTo-AktoJson ([ordered]@{ "x-claude-hook"="UserPromptSubmit" }))
        method          = "POST"
        requestPayload  = (ConvertTo-AktoJson ([ordered]@{ body = $prompt.Trim() }))
        responsePayload = "{}"
        ip              = (Get-AktoUsername)
        destIp          = "127.0.0.1"
        time            = ([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
        statusCode      = "200"; type = "HTTP/1.1"; status = "200"
        akto_account_id = "1000000"
        akto_vxlan_id   = $deviceId
        is_pending      = "false"; source = "MIRRORING"
        direction = $null; process_id = $null; socket_id = $null; daemonset_id = $null; enabled_graph = $null
        tag             = (ConvertTo-AktoJson $tags)
        metadata        = (ConvertTo-AktoJson $tags)
        contextSource   = (Get-AktoContextSource)
    }
}

if ((Test-AktoSync) -and (Get-AktoIngestUrl)) {
    $body = ConvertTo-AktoJson (Build-ValidationRequest)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -Guardrails $true -IngestData $true) -Body $body
    $gr = Get-AktoGuardrailsResult $resp
    # fingerprint: json.dumps({"p": prompt, "a": []}, sort_keys=True) → key order: a, p
    $fp = Get-AktoSha256Hex ('{"a": [], "p": ' + (ConvertTo-Json $prompt -Compress) + '}')
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $warnState

    if (-not $flow.Allowed) {
        if (Test-WarnBehaviour $gr.Behaviour) {
            $blockReason = "Warning!!, prompt blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)"
        } else {
            $blockReason = "Prompt blocked: $($gr.Reason)"
        }
        Write-AktoWarn "BLOCKING prompt - Reason: $blockReason"
        $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -Guardrails $false -IngestData $true) -Body $body
        (@{ decision = "block"; reason = $blockReason } | ConvertTo-Json -Compress)
        exit 0
    }
    Write-AktoInfo "Prompt ALLOWED"
}
exit 0
