#!/usr/bin/env pwsh
# akto-hooks.ps1 - PowerShell port of akto-hooks.py (observability dispatcher).
# Usage: akto-hooks.ps1 <HookName>
[CmdletBinding()] param([Parameter(Mandatory)][string]$HookName)
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "hook-executions.log"

$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
$Connector = if ($env:AKTO_CONNECTOR) { $env:AKTO_CONNECTOR } else { "gemini_cli" }
$ContextSource = Get-AktoContextSource

# connector -> short tag (mirrors _CONNECTOR_TAG in akto_ingestion_utility.py)
$tagMap = @{ claude_code_cli="claudecli"; cursor="cursor"; vscode="vscode"; gemini_cli="geminicli"; github="github"; codex_cli="codexcli" }
$TagName = if ($tagMap.ContainsKey($Connector)) { $tagMap[$Connector] } else { $Connector }
$HookHeader = "x-$TagName-hook"

if ($Mode -eq "atlas") {
    $AiAgentUrl = if ($DeviceId) { "https://$DeviceId.ai-agent.$TagName" } else { $(if ($env:AKTO_API_URL) { $env:AKTO_API_URL } else { "" }) }
} else {
    $AiAgentUrl = if ($env:AKTO_API_URL) { $env:AKTO_API_URL } else { "" }
}

Write-AktoInfo "=== $HookName hook started ==="

$raw = [Console]::In.ReadToEnd()
if ([string]::IsNullOrEmpty($raw)) { $raw = "{}" }
try { $inputObj = $raw | ConvertFrom-Json } catch { $inputObj = $null }

if (-not (Get-AktoIngestUrl)) {
    Write-AktoInfo "AKTO_DATA_INGESTION_URL not set, skipping ingestion"
    '{}'
    exit 0
}

$host_ = $AiAgentUrl -replace '^https?://',''

$tags = [ordered]@{ "gen-ai" = "Gen AI"; hook = $HookName }
if ($Mode -eq "atlas") { $tags["ai-agent"] = $TagName; $tags["source"] = $ContextSource }

$reqHdr  = [ordered]@{ host = $host_; $HookHeader = $HookName; "content-type" = "application/json" }
$respHdr = [ordered]@{ $HookHeader = $HookName; "content-type" = "application/json" }

$reqPayload  = ConvertTo-AktoJson ([ordered]@{ body = if ($null -ne $inputObj) { $inputObj } else { @{} } })
$respPayload = ConvertTo-AktoJson ([ordered]@{ body = @{} })
$tagStr = ConvertTo-AktoJson $tags

$body = ConvertTo-AktoJson ([ordered]@{
    path            = "/v1/hooks/$HookName"
    requestHeaders  = (ConvertTo-AktoJson $reqHdr)
    responseHeaders = (ConvertTo-AktoJson $respHdr)
    method          = "POST"
    requestPayload  = $reqPayload
    responsePayload = $respPayload
    ip              = (Get-AktoUsername)
    destIp          = "127.0.0.1"
    time            = ([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
    statusCode      = "200"; type = "HTTP/1.1"; status = "200"
    akto_account_id = "1000000"
    akto_vxlan_id   = $DeviceId
    is_pending      = "false"; source = "MIRRORING"
    direction = $null; process_id = $null; socket_id = $null; daemonset_id = $null; enabled_graph = $null
    tag             = $tagStr
    metadata        = $tagStr
    contextSource   = $ContextSource
})

$ingestUrl = Get-AktoIngestUrl
$url = "$ingestUrl/api/http-proxy?akto_connector=$Connector&ingest_data=true&client_hook=$HookName"
try { $null = Invoke-AktoPost -Url $url -Body $body } catch {}

Write-AktoInfo "=== $HookName hook completed ==="
'{}'
exit 0
