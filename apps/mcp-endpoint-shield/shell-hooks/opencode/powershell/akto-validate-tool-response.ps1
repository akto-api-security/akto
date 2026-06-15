#!/usr/bin/env pwsh
# akto-validate-tool-response.ps1 - PowerShell port of akto-validate-tool-response.py (tool.execute.after)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "validate-tool-response.log"

$deviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
$apiUrl = Get-OpenCodeApiUrl -DeviceId $deviceId
$mode = $(if ($env:MODE) { $env:MODE } else { "atlas" }).ToLower()

Write-AktoInfo "=== PostToolUse hook started - Mode: $mode, Sync: $(Test-AktoSync) ==="

$raw = [Console]::In.ReadToEnd()
try { $in = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; exit 0 }

$toolName     = [string]$in.tool_name
$toolInput    = if ($null -ne $in.tool_input)    { $in.tool_input }    else { @{} }
$toolResponse = if ($null -ne $in.tool_response) { $in.tool_response } else { @{} }
Write-AktoInfo "Processing tool response: $toolName"

if (-not (Get-AktoIngestUrl)) { Write-AktoInfo "No ingestion URL, skipping"; exit 0 }

$inputIsEmpty    = ($null -eq $toolInput    -or ($toolInput    -is [hashtable] -and $toolInput.Count    -eq 0) -or ($toolInput    -is [pscustomobject] -and ($toolInput    | Get-Member -MemberType NoteProperty).Count -eq 0))
$responseIsEmpty = ($null -eq $toolResponse -or ($toolResponse -is [hashtable] -and $toolResponse.Count -eq 0) -or ($toolResponse -is [pscustomobject] -and ($toolResponse | Get-Member -MemberType NoteProperty).Count -eq 0))
if ($inputIsEmpty)    { Write-AktoInfo "Skipping ingestion: empty tool input"; exit 0 }
if ($responseIsEmpty) { Write-AktoInfo "Skipping ingestion: empty tool response"; exit 0 }

$host_ = $apiUrl -replace '^https?://',''
$tags = [ordered]@{ "gen-ai" = "Gen AI"; "tool-use" = "Tool Execution"; "tool_server_name" = "opencode" }
if ($mode -eq "atlas") { $tags["ai-agent"] = "opencode"; $tags["source"] = Get-AktoContextSource }

$body = ConvertTo-AktoJson ([ordered]@{
    path            = "/v1/messages"
    requestHeaders  = (ConvertTo-AktoJson ([ordered]@{ host=$host_; "x-claude-hook"="PostToolUse"; "content-type"="application/json" }))
    responseHeaders = (ConvertTo-AktoJson ([ordered]@{ "x-claude-hook"="PostToolUse"; "content-type"="application/json" }))
    method          = "POST"
    requestPayload  = (ConvertTo-AktoJson ([ordered]@{ body = [ordered]@{ toolName=$toolName; toolArgs=$toolInput } }))
    responsePayload = (ConvertTo-AktoJson ([ordered]@{ body = [ordered]@{ result=$toolResponse } }))
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
})

# guardrails = NOT AKTO_SYNC_MODE (mirrors python), ingest_data = true
$useGuardrails = -not (Test-AktoSync)
$null = Invoke-AktoPost -Url (Get-AktoProxyUrl -Guardrails $useGuardrails -IngestData $true) -Body $body
Write-AktoInfo "Tool response ingestion successful"
exit 0
