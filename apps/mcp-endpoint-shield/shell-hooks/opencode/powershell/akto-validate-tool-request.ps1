#!/usr/bin/env pwsh
# akto-validate-tool-request.ps1 - PowerShell port of akto-validate-tool-request.py (tool.execute.before)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "validate-tool-request.log"

$deviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
$apiUrl = Get-OpenCodeApiUrl -DeviceId $deviceId
$mode = $(if ($env:MODE) { $env:MODE } else { "atlas" }).ToLower()

Write-AktoInfo "=== PreToolUse hook started - Mode: $mode, Sync: $(Test-AktoSync) ==="

$raw = [Console]::In.ReadToEnd()
try { $in = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; exit 0 }

$toolName  = [string]$in.tool_name
$toolInput = if ($null -ne $in.tool_input) { $in.tool_input } else { @{} }
Write-AktoInfo "Processing tool request: $toolName"

function Build-ValidationRequest {
    $host_ = $apiUrl -replace '^https?://',''
    $tags = [ordered]@{ "gen-ai" = "Gen AI"; "tool_server_name" = "opencode" }
    if ($mode -eq "atlas") { $tags["ai-agent"] = "opencode"; $tags["source"] = Get-AktoContextSource }

    [ordered]@{
        path            = "/v1/messages"
        requestHeaders  = (ConvertTo-AktoJson ([ordered]@{ host=$host_; "x-opencode-hook"="PreToolUse"; "content-type"="application/json" }))
        responseHeaders = (ConvertTo-AktoJson ([ordered]@{ "x-opencode-hook"="PreToolUse" }))
        method          = "POST"
        requestPayload  = (ConvertTo-AktoJson ([ordered]@{ body = $toolInput; toolName = $toolName }))
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
    $inputIsEmpty = ($null -eq $toolInput -or ($toolInput -is [hashtable] -and $toolInput.Count -eq 0) -or ($toolInput -is [pscustomobject] -and ($toolInput | Get-Member -MemberType NoteProperty).Count -eq 0))
    if ($inputIsEmpty) { Write-AktoInfo "Empty tool input, allowing"; exit 0 }

    $body = ConvertTo-AktoJson (Build-ValidationRequest)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -Guardrails $true -IngestData $false) -Body $body
    $gr = Get-AktoGuardrailsResult $resp

    if (-not $gr.Allowed) {
        $blockReason = if ($gr.Reason) { $gr.Reason } else { "Policy violation" }
        Write-AktoWarn "BLOCKING tool request - Tool: $toolName, Reason: $blockReason"
        $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -Guardrails $false -IngestData $true) -Body $body
        (@{ decision = "block"; reason = "Blocked by Akto Guardrails: $blockReason" } | ConvertTo-Json -Compress)
        exit 0
    }
    Write-AktoInfo "Tool request allowed for $toolName"
}
exit 0
