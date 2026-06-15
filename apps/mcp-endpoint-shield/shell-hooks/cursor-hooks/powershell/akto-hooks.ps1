#!/usr/bin/env pwsh
# akto-hooks.ps1 - PowerShell port of akto-hooks.py + run_observability_hook() (cursor)
# Usage: akto-hooks.ps1 <HookName>
[CmdletBinding()] param([Parameter(Position=0)][string]$HookName)
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "hook-executions.log"
if (-not $HookName) { [Console]::Error.WriteLine("Usage: akto-hooks.ps1 <hookName>"); exit 1 }

$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$Connector = if ($env:AKTO_CONNECTOR) { $env:AKTO_CONNECTOR } else { "cursor" }
$tagMap = @{ "claude_code_cli"="claudecli"; "cursor"="cursor"; "vscode"="vscode"; "gemini_cli"="geminicli"; "github"="github"; "codex_cli"="codexcli" }
$TagName = if ($tagMap.ContainsKey($Connector)) { $tagMap[$Connector] } else { $Connector }
$HookHeader = "x-$TagName-hook"
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
if ($Mode -eq "atlas") {
    $ApiUrl = if ($DeviceId) { "https://$DeviceId.ai-agent.$TagName" } else { ($env:AKTO_API_URL) }
} else { $ApiUrl = ($env:AKTO_API_URL) }

Write-AktoInfo "=== $HookName hook started ==="
$raw = [Console]::In.ReadToEnd()
$inputObj = $null
if ($raw) { try { $inputObj = $raw | ConvertFrom-Json } catch { $inputObj = $null } }
if ($null -eq $inputObj) { $inputObj = [pscustomobject]@{} }

if (-not (Get-AktoIngestUrl)) { Write-AktoInfo "AKTO_DATA_INGESTION_URL not set, skipping ingestion"; '{}'; exit 0 }

$host_ = ([string]$ApiUrl) -replace '^https?://',''
$tags = [ordered]@{ "gen-ai"="Gen AI"; "hook"=$HookName }
if ($Mode -eq "atlas") { $tags["ai-agent"]=$TagName; $tags["source"]=(Get-AktoContextSource) }
$reqHdr = [ordered]@{ host=$host_; $HookHeader=$HookName; "content-type"="application/json" }
$respHdr = [ordered]@{ $HookHeader=$HookName; "content-type"="application/json" }

$payload = [ordered]@{
    path="/v1/hooks/$HookName"
    requestHeaders=(ConvertTo-AktoJson $reqHdr)
    responseHeaders=(ConvertTo-AktoJson $respHdr)
    method="POST"
    requestPayload=(ConvertTo-AktoJson ([ordered]@{ body=$inputObj }))
    responsePayload=(ConvertTo-AktoJson ([ordered]@{ body=@{} }))
    ip=(Get-AktoUsername); destIp="127.0.0.1"
    time=([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
    statusCode="200"; type="HTTP/1.1"; status="200"
    akto_account_id="1000000"; akto_vxlan_id=$DeviceId
    is_pending="false"; source="MIRRORING"
    direction=$null; process_id=$null; socket_id=$null; daemonset_id=$null; enabled_graph=$null
    tag=(ConvertTo-AktoJson $tags); metadata=(ConvertTo-AktoJson $tags)
    contextSource=(Get-AktoContextSource)
}
$url = "$(Get-AktoIngestUrl)/api/http-proxy?akto_connector=$Connector&ingest_data=true&client_hook=$HookName"
$null = Invoke-AktoPost -Url $url -Body (ConvertTo-AktoJson $payload)
Write-AktoInfo "=== $HookName hook completed ==="
'{}'
exit 0
