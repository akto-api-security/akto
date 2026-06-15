#!/usr/bin/env pwsh
# akto-validate-mcp-request.ps1 - PowerShell port of akto-validate-mcp-request.py
# Cursor beforeMCPExecution hook. JSON-RPC tools/call envelope on /mcp + guardrails.
# Output: {"permission":"allow"|"deny"[,"user_message","agent_message"]}.
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "akto-validate-request.log"

$logDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".cursor/akto/mcp-logs" }
$WarnState = Join-Path $logDir "akto_pretool_warn_pending.json"
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

function ConvertFrom-ToolInput {
    param($Raw)
    if ($null -eq $Raw) { return @{} }
    if ($Raw -is [string]) {
        if ([string]::IsNullOrWhiteSpace($Raw)) { return @{} }
        try { return ($Raw | ConvertFrom-Json) } catch { return @{ raw = $Raw } }
    }
    return $Raw
}

function Get-JsonRpcArguments { param($ti) if ($ti -is [pscustomobject] -or $ti -is [hashtable]) { $ti } elseif ($null -eq $ti) { @{} } else { @{ input=$ti } } }

function Build-ValidationRequest {
    param([string]$ToolName, $ToolInput, [string]$Server)
    $tags = [ordered]@{ "mcp-server"="MCP Server"; "mcp-client"=$ConnectorValue }
    if ($Mode -eq "atlas") { $tags["source"] = (Get-AktoContextSource) }
    $tags["mcp_server_name"] = $Server
    $host_ = "$DeviceId.$ConnectorValue.$Server"
    $reqPayload = [ordered]@{ jsonrpc="2.0"; method="tools/call"; params=[ordered]@{ name=$ToolName; arguments=(Get-JsonRpcArguments $ToolInput) }; id=1 }
    $reqHdr = [ordered]@{ host=$host_; "x-cursor-hook"="beforeMCPExecution"; "x-mcp-server"=$Server; "content-type"="application/json" }
    [ordered]@{
        path=$McpIngestPath
        requestHeaders=(ConvertTo-AktoJson $reqHdr)
        responseHeaders=(ConvertTo-AktoJson ([ordered]@{ "x-cursor-hook"="beforeMCPExecution" }))
        method="POST"
        requestPayload=(ConvertTo-AktoJson $reqPayload)
        responsePayload="{}"
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
if (-not $raw) { '{"permission":"allow"}'; exit 0 }
try { $input_ = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; '{"permission":"allow"}'; exit 0 }

$toolName = [string]$input_.tool_name
$toolInput = ConvertFrom-ToolInput $input_.tool_input
$server = Get-McpServerName -InputObj $input_ -ToolInputObj $toolInput

Write-AktoInfo "Processing MCP tools/call: $toolName (server=$server)"

$isEmpty = $false
if ($null -eq $toolInput) { $isEmpty = $true }
elseif (($toolInput -is [pscustomobject]) -and (@($toolInput.PSObject.Properties).Count -eq 0)) { $isEmpty = $true }
elseif (($toolInput -is [hashtable]) -and ($toolInput.Count -eq 0)) { $isEmpty = $true }
if ($isEmpty) { Write-AktoInfo "Empty tool input, allowing request"; '{"permission":"allow"}'; exit 0 }

if (Test-AktoSync) {
    $gr = [ordered]@{ Allowed=$true; Reason=""; Behaviour="" }
    if (Get-AktoIngestUrl) {
        $body = ConvertTo-AktoJson (Build-ValidationRequest -ToolName $toolName -ToolInput $toolInput -Server $server)
        # cursor mcp validate: guardrails=true & ingest_data=false
        $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -Guardrails) -Body $body
        $gr = Get-AktoGuardrailsResult $resp
    } else { Write-AktoWarn "AKTO_DATA_INGESTION_URL not set, allowing" }

    # fingerprint: json.dumps({"t":tool,"i":input}, sort_keys=True) -> {"i":..,"t":..}
    $fp = Get-AktoSha256Hex ([ordered]@{ i = $toolInput; t = $toolName } | ConvertTo-Json -Depth 30 -Compress)
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState

    if (-not $flow.Allowed) {
        $reasonText = $(if ($gr.Reason) { $gr.Reason } else { "Policy violation" })
        if (Test-WarnBehaviour $gr.Behaviour) {
            $userMessage = "Warning!! Cursor MCP call blocked, send the same request again to bypass. Reason: $reasonText"
        } else { $userMessage = "Request blocked by Akto security policy" }
        $agentMessage = "Blocked by Akto Guardrails: $reasonText"
        Write-AktoWarn "BLOCKING request - server: $server, reason: $($gr.Reason)"
        (@{ permission="deny"; user_message=$userMessage; agent_message=$agentMessage } | ConvertTo-Json -Compress)
        # ingest blocked (best-effort)
        if (Get-AktoIngestUrl) {
            $bbody = Build-ValidationRequest -ToolName $toolName -ToolInput $toolInput -Server $server
            $bbody.responseHeaders = (ConvertTo-AktoJson ([ordered]@{ "x-cursor-hook"="beforeMCPExecution"; "x-blocked-by"="Akto Proxy"; "content-type"="application/json" }))
            $bbody.responsePayload = (ConvertTo-AktoJson ([ordered]@{ body=[ordered]@{ "x-blocked-by"="Akto Proxy"; reason=$reasonText } }))
            $bbody.statusCode = "403"; $bbody.status = "403"
            $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -IngestData) -Body (ConvertTo-AktoJson $bbody)
        }
        exit 0
    }
}
Write-AktoInfo "Request allowed"
'{"permission":"allow"}'
exit 0
