#!/usr/bin/env pwsh
# akto-validate-chat-response.ps1 - PowerShell port of akto-validate-chat-response.py
# Cursor afterAgentResponse hook. Observe-only: ingest response with response_guardrails.
# Output: {} (cannot block).
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "akto-validate-chat-response.log"

$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
if ($Mode -eq "atlas") {
    $ApiUrl = if ($DeviceId) { "https://$DeviceId.ai-agent.cursor" } else { "https://api.anthropic.com" }
} else { $ApiUrl = if ($env:API_URL) { $env:API_URL } else { "https://api.anthropic.com" } }

function Build-IngestionPayload {
    param([string]$ResponseText)
    $host_ = $ApiUrl -replace '^https?://',''
    $tags = [ordered]@{ "gen-ai"="Gen AI" }
    if ($Mode -eq "atlas") { $tags["ai-agent"]="cursor"; $tags["source"]=(Get-AktoContextSource) }
    [ordered]@{
        path="/v1/messages"
        requestHeaders=(ConvertTo-AktoJson ([ordered]@{ host=$host_; "x-cursor-hook"="afterAgentResponse"; "content-type"="application/json" }))
        responseHeaders=(ConvertTo-AktoJson ([ordered]@{ "x-cursor-hook"="afterAgentResponse"; "content-type"="application/json" }))
        method="POST"
        requestPayload="{}"
        responsePayload=(ConvertTo-AktoJson ([ordered]@{ body=$ResponseText }))
        ip=(Get-AktoUsername); destIp="127.0.0.1"
        time=([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
        statusCode="200"; type=$null; status="200"
        akto_account_id="1000000"; akto_vxlan_id=$DeviceId
        is_pending="false"; source="MIRRORING"
        direction=$null; process_id=$null; socket_id=$null; daemonset_id=$null; enabled_graph=$null
        tag=(ConvertTo-AktoJson $tags); metadata=(ConvertTo-AktoJson $tags)
        contextSource=(Get-AktoContextSource)
    }
}

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { '{}'; exit 0 }
try { $input_ = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; '{}'; exit 0 }

$responseText = [string]$input_.text
if ([string]::IsNullOrWhiteSpace($responseText)) { Write-AktoWarn "Empty response received"; '{}'; exit 0 }

if (Get-AktoIngestUrl) {
    Write-AktoInfo "Ingesting chat response (length: $($responseText.Length))"
    $body = ConvertTo-AktoJson (Build-IngestionPayload -ResponseText $responseText)
    $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails -IngestData) -Body $body
} else { Write-AktoWarn "AKTO_DATA_INGESTION_URL not set, skipping ingestion" }

Write-AktoInfo "Hook execution completed"
'{}'
exit 0
