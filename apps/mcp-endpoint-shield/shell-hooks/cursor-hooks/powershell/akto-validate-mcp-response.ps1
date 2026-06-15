#!/usr/bin/env pwsh
# akto-validate-mcp-response.ps1 - PowerShell port of akto-validate-mcp-response.py
# Cursor afterMCPExecution hook. Observe-only: ingest JSON-RPC result on /mcp with
# response_guardrails=true. Output: {} (cannot block).
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "akto-validate-response.log"

$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$ConnectorValue = if ($env:AKTO_CONNECTOR_VALUE) { $env:AKTO_CONNECTOR_VALUE } else { "cursor" }
$McpIngestPath = if ($env:MCP_INGEST_PATH) { $env:MCP_INGEST_PATH } else { "/mcp" }
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }

$script:McpAliases = $null
function Get-McpAliases {
    if ($null -ne $script:McpAliases) { return $script:McpAliases }
    $aliases = @{}
    foreach ($p in @((Join-Path $HOME ".cursor/mcp.json"), (Join-Path (Get-Location) ".cursor/mcp.json"))) {
        if (-not (Test-Path $p)) { continue }
        try {
            $data = Get-Content $p -Raw | ConvertFrom-Json
            if ($data.mcpServers) {
                foreach ($prop in $data.mcpServers.PSObject.Properties) {
                    $srv = $prop.Value
                    if ($srv.url) { $aliases[[string]$srv.url] = $prop.Name }
                    if ($srv.command) { $aliases[[string]$srv.command] = $prop.Name }
                }
            }
        } catch { Write-AktoWarn "Could not parse $p" }
    }
    $script:McpAliases = $aliases
    return $aliases
}

function Get-McpServerName {
    param($InputObj, $ToolInputObj)
    $aliases = Get-McpAliases
    $url = $InputObj.url; $command = $InputObj.command
    if ($url -and $aliases.ContainsKey([string]$url)) { return $aliases[[string]$url] }
    if ($command -and $aliases.ContainsKey([string]$command)) { return $aliases[[string]$command] }
    if ($InputObj.server) { return [string]$InputObj.server }
    if ($url) { return (([string]$url) -replace '^https?://','').Split("/")[0] }
    if ($ToolInputObj -and $ToolInputObj.url) { return (([string]$ToolInputObj.url) -replace '^https?://','').Split("/")[0] }
    if ($command) { return [string]$command }
    $tn = [string]$InputObj.tool_name
    if ($tn.StartsWith("mcp__")) { $parts = $tn -split "__"; if ($parts.Count -gt 1) { return $parts[1] } }
    return "cursor-unknown"
}

function ConvertFrom-JsonStringField {
    param($Raw)
    if ($null -eq $Raw) { return @{} }
    if ($Raw -is [string]) {
        if ([string]::IsNullOrWhiteSpace($Raw)) { return @{} }
        try { return ($Raw | ConvertFrom-Json) } catch { return @{ raw = $Raw } }
    }
    return $Raw
}

function Get-JsonRpcArguments { param($ti) if ($ti -is [pscustomobject] -or $ti -is [hashtable]) { $ti } elseif ($null -eq $ti) { @{} } else { @{ input=$ti } } }
function Get-ResultBody { param($tr) if ($tr -is [pscustomobject] -or $tr -is [hashtable]) { $tr } else { @{ output=$tr } } }

function Test-EmptyObj {
    param($o)
    if ($null -eq $o) { return $true }
    if (($o -is [pscustomobject]) -and (@($o.PSObject.Properties).Count -eq 0)) { return $true }
    if (($o -is [hashtable]) -and ($o.Count -eq 0)) { return $true }
    return $false
}

function Build-IngestionPayload {
    param([string]$ToolName, $ToolInput, $ToolResponse, [string]$Server)
    $tags = [ordered]@{ "mcp-server"="MCP Server"; "mcp-client"=$ConnectorValue }
    if ($Mode -eq "atlas") { $tags["source"]=(Get-AktoContextSource) }
    $tags["mcp_server_name"]=$Server
    $host_ = "$DeviceId.$ConnectorValue.$Server"
    $reqPayload = [ordered]@{ jsonrpc="2.0"; method="tools/call"; params=[ordered]@{ name=$ToolName; arguments=(Get-JsonRpcArguments $ToolInput) }; id=1 }
    $respPayload = [ordered]@{ jsonrpc="2.0"; id=1; result=(Get-ResultBody $ToolResponse) }
    $reqHdr = [ordered]@{ host=$host_; "x-cursor-hook"="afterMCPExecution"; "x-mcp-server"=$Server; "content-type"="application/json" }
    [ordered]@{
        path=$McpIngestPath
        requestHeaders=(ConvertTo-AktoJson $reqHdr)
        responseHeaders=(ConvertTo-AktoJson ([ordered]@{ "x-cursor-hook"="afterMCPExecution"; "content-type"="application/json" }))
        method="POST"
        requestPayload=(ConvertTo-AktoJson $reqPayload)
        responsePayload=(ConvertTo-AktoJson $respPayload)
        ip=(Get-AktoUsername); destIp="127.0.0.1"
        time=([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
        statusCode="200"; type="HTTP/1.1"; status="200"
        akto_account_id="1000000"; akto_vxlan_id=0
        is_pending="false"; source="MIRRORING"
        direction=$null; process_id=$null; socket_id=$null; daemonset_id=$null; enabled_graph=$null
        tag=(ConvertTo-AktoJson $tags); metadata=(ConvertTo-AktoJson $tags)
        contextSource=(Get-AktoContextSource)
    }
}

$raw = [Console]::In.ReadToEnd()
if (-not $raw) { '{}'; exit 0 }
try { $input_ = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; '{}'; exit 0 }

$toolName = [string]$input_.tool_name
$toolInput = ConvertFrom-JsonStringField $input_.tool_input
$toolResponse = ConvertFrom-JsonStringField $(if ($null -ne $input_.result_json) { $input_.result_json } else { "{}" })
$server = Get-McpServerName -InputObj $input_ -ToolInputObj $toolInput

Write-AktoInfo "Processing afterMCPExecution: $toolName (server=$server)"

if ((Test-EmptyObj $toolInput) -or (Test-EmptyObj $toolResponse)) { Write-AktoInfo "Empty input or result, skipping ingestion"; '{}'; exit 0 }

if (Get-AktoIngestUrl) {
    Write-AktoInfo "Ingesting MCP tools/call result for $toolName (server=$server)"
    $body = ConvertTo-AktoJson (Build-IngestionPayload -ToolName $toolName -ToolInput $toolInput -ToolResponse $toolResponse -Server $server)
    $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails -IngestData) -Body $body
}
Write-AktoInfo "Response ingestion completed"
'{}'
exit 0
