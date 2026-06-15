#!/usr/bin/env pwsh
# akto-hooks.ps1 - PowerShell port of akto-hooks.py (observability dispatcher).
# Usage: akto-hooks.ps1 <HookName>
[CmdletBinding()] param([Parameter(Mandatory)][string]$HookName)
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force

$mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$deviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
$connector = if ($env:AKTO_CONNECTOR) { $env:AKTO_CONNECTOR } else { "vscode" }
$contextSource = if ($env:CONTEXT_SOURCE) { $env:CONTEXT_SOURCE } else { "ENDPOINT" }

$tagMap = @{ claude_code_cli="claudecli"; cursor="cursor"; vscode="vscode"; gemini_cli="geminicli"; github="github"; codex_cli="codexcli" }
$tagName = if ($tagMap.ContainsKey($connector)) { $tagMap[$connector] } else { $connector }
$hookHeader = "x-$tagName-hook"

if ($mode -eq "atlas") {
    $aiAgentUrl = if ($deviceId) { "https://$deviceId.ai-agent.$tagName" } else { $(if ($env:AKTO_API_URL) { $env:AKTO_API_URL } else { "" }) }
} else {
    $aiAgentUrl = if ($env:AKTO_API_URL) { $env:AKTO_API_URL } else { "" }
}

$logDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".github/akto/vscode/logs" }
New-Item -ItemType Directory -Force -Path $logDir -ErrorAction SilentlyContinue | Out-Null
Invoke-ConnectorSetup -Connector $connector -DeviceId $deviceId
Set-AktoLogFile "hook-executions.log"
Write-AktoInfo "=== $HookName hook started ==="

$raw = [Console]::In.ReadToEnd()
if ([string]::IsNullOrEmpty($raw)) { $raw = "{}" }
try { $inputObj = $raw | ConvertFrom-Json } catch { $inputObj = $null }

$ingestUrl = (($env:AKTO_DATA_INGESTION_URL) -as [string]).TrimEnd("/")
if (-not $ingestUrl) {
    Write-AktoInfo "AKTO_DATA_INGESTION_URL not set, skipping ingestion"
    '{}'
    exit 0
}

$host_ = $aiAgentUrl -replace '^https?://',''

$tags = [ordered]@{ "gen-ai" = "Gen AI"; hook = $HookName }
if ($mode -eq "atlas") { $tags["ai-agent"] = $tagName; $tags["source"] = $contextSource }

$reqHdr  = [ordered]@{ host = $host_; $hookHeader = $HookName; "content-type" = "application/json" }
$respHdr = [ordered]@{ $hookHeader = $HookName; "content-type" = "application/json" }
$tagStr = ConvertTo-AktoJson $tags

$body = ConvertTo-AktoJson ([ordered]@{
    path            = "/v1/hooks/$HookName"
    requestHeaders  = (ConvertTo-AktoJson $reqHdr)
    responseHeaders = (ConvertTo-AktoJson $respHdr)
    method          = "POST"
    requestPayload  = (ConvertTo-AktoJson ([ordered]@{ body = if ($null -ne $inputObj) { $inputObj } else { @{} } }))
    responsePayload = (ConvertTo-AktoJson ([ordered]@{ body = @{} }))
    ip              = (Get-AktoUsername)
    destIp          = "127.0.0.1"
    time            = ([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
    statusCode      = "200"; type = "HTTP/1.1"; status = "200"
    akto_account_id = "1000000"
    akto_vxlan_id   = $deviceId
    is_pending      = "false"; source = "MIRRORING"
    direction = $null; process_id = $null; socket_id = $null; daemonset_id = $null; enabled_graph = $null
    tag             = $tagStr
    metadata        = $tagStr
    contextSource   = $contextSource
})

$url = "$ingestUrl/api/http-proxy?akto_connector=$connector&ingest_data=true&client_hook=$HookName"
$apiToken = ($env:AKTO_API_TOKEN) -as [string]
$timeout  = if ($env:AKTO_TIMEOUT) { [double]$env:AKTO_TIMEOUT } else { 5 }
$headers  = @{ "Content-Type" = "application/json" }
if ($apiToken) { $headers["Authorization"] = $apiToken }
try {
    $common = @{ Uri=$url; Method="POST"; Body=$body; Headers=$headers; TimeoutSec=$timeout }
    if ($PSVersionTable.PSVersion.Major -ge 6) { Invoke-RestMethod @common -SkipCertificateCheck | Out-Null }
    else { [System.Net.ServicePointManager]::ServerCertificateValidationCallback = { $true }; Invoke-RestMethod @common | Out-Null }
} catch {}

Write-AktoInfo "=== $HookName hook completed ==="
'{}'
exit 0
