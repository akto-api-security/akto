#!/usr/bin/env pwsh
# akto-validate-pre-tool.ps1 - PowerShell port of akto-validate-pre-tool.py (preToolUse)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force

$raw = [Console]::In.ReadToEnd()
try { $in = $raw | ConvertFrom-Json } catch { [Console]::Error.WriteLine("Invalid JSON input"); exit 0 }

$connector = Get-Connector $in
$deviceId  = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
Invoke-ConnectorSetup -Connector $connector -DeviceId $deviceId
$cfg = Get-AktoCfg

Set-AktoLogFile "validate-pre-tool.log"
$warnState = Join-Path (Get-AktoLogDir) "akto_pretool_warn_pending.json"
Send-AktoHeartbeat (Get-AktoLogDir)

Write-AktoInfo "=== PreToolUse Hook - Connector: $connector, Mode: $(if ($env:MODE) { $env:MODE } else { 'argus' }), Sync: $(Test-AktoSync) ==="

# Parse tool name + args (differ by connector)
if ($cfg.IsVscode) {
    $toolName = if ($in.tool_name) { [string]$in.tool_name } else { "unknown" }
    $rawArgs  = $in.tool_input
} else {
    $toolName = if ($in.toolName) { [string]$in.toolName } elseif ($in.tool_name) { [string]$in.tool_name } else { "unknown" }
    $rawArgs  = if ($null -ne $in.toolArgs) { $in.toolArgs } else { $in.tool_input }
}
if ($null -eq $rawArgs) { $rawArgs = @{} }

$rawTs = $in.timestamp
$timestamp = if ($rawTs -match '^\d+$') { [string]$rawTs } else { [string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) }

$mcp = Resolve-GithubTool $toolName
Write-AktoInfo "Tool: $toolName$(if ($mcp.IsMcp) { " (MCP server=$($mcp.Server), tool=$($mcp.Tool))" })"

function Build-AktoRequest {
    param([string]$ToolName, $ToolArgs, [string]$Timestamp)
    if ($mcp.IsMcp) {
        $tags = [ordered]@{ "mcp-server"="MCP Server"; "mcp-client"=$cfg.AiAgentTag }
        if ($script:Mode -eq "atlas") { $tags["source"] = Get-AktoContextSource }
        $host_ = "$deviceId.$($cfg.AiAgentTag).$($mcp.Server)"
        $parsedInput = if ($ToolArgs -is [pscustomobject] -or $ToolArgs -is [hashtable]) { $ToolArgs } else { @{ raw=[string]$ToolArgs } }
        $reqPayload = [ordered]@{ jsonrpc="2.0"; method="tools/call"; params=[ordered]@{ name=$mcp.Tool; arguments=(Get-JsonRpcArguments $parsedInput) }; id=1 }
        $path = Get-AktoMcpIngestPath
    } else {
        $tags = [ordered]@{ "gen-ai"="Gen AI"; "tool-use"="Tool Execution" }
        if ($script:Mode -eq "atlas") { $tags["ai-agent"] = $cfg.AiAgentTag; $tags["source"] = Get-AktoContextSource }
        $host_ = $cfg.ApiUrl -replace '^https?://',''
        $argsStr = if ($ToolArgs -is [pscustomobject] -or $ToolArgs -is [hashtable]) { ConvertTo-Json $ToolArgs -Compress } else { [string]$ToolArgs }
        $reqPayload = [ordered]@{ body = (ConvertTo-AktoJson ([ordered]@{ toolName=$ToolName; toolArgs=$argsStr })) }
        $path = "/copilot/tool/$ToolName"
    }

    $reqHdr = [ordered]@{ host=$host_; ($cfg.HookHeader)="PreToolUse"; "content-type"="application/json" }
    if ($mcp.IsMcp -and $mcp.Server) { $reqHdr["x-mcp-server"] = $mcp.Server }
    $respHdr = [ordered]@{ ($cfg.HookHeader)="PreToolUse" }

    [ordered]@{
        path            = $path
        requestHeaders  = (ConvertTo-AktoJson $reqHdr)
        responseHeaders = (ConvertTo-AktoJson $respHdr)
        method          = "POST"
        requestPayload  = (ConvertTo-AktoJson $reqPayload)
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

if (-not (Test-AktoSync) -or -not (Get-AktoIngestUrl)) {
    Write-AktoInfo "Guardrails disabled (sync mode off or no URL)"; exit 0
}

$body = ConvertTo-AktoJson (Build-AktoRequest -ToolName $toolName -ToolArgs $rawArgs -Timestamp $timestamp)
$resp = Invoke-AktoPost -Url (Get-AktoProxyUrl "prompt_guard") -Body $body
$gr   = Get-AktoGuardrailsResult $resp
$argsStr = if ($rawArgs -is [pscustomobject] -or $rawArgs -is [hashtable]) { ConvertTo-Json $rawArgs -Compress } else { [string]$rawArgs }
$fp   = Get-AktoSha256Hex ('{"a":' + (ConvertTo-Json $argsStr -Compress) + ',"t":' + (ConvertTo-Json $toolName -Compress) + '}')
$flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $warnState

if (-not $flow.Allowed) {
    if (Test-WarnBehaviour $gr.Behaviour) {
        $denialReason = "Warning!! Tool use blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)"
    } else {
        $denialReason = "Blocked by Akto Guardrails: $(if ($gr.Reason) { $gr.Reason } else { 'Policy violation' })"
    }
    Write-AktoWarn "BLOCKING tool use: $toolName"

    # Ingest blocked
    $blockedBody = ConvertTo-AktoJson (Build-AktoRequest -ToolName $toolName -ToolArgs $rawArgs -Timestamp $timestamp)
    $null = Invoke-AktoPost -Url (Get-AktoProxyUrl "ingest") -Body $blockedBody

    ([ordered]@{ permissionDecision="deny"; permissionDecisionReason=$denialReason; hookSpecificOutput=[ordered]@{ permissionDecision="deny"; permissionDecisionReason=$denialReason } } | ConvertTo-Json -Depth 10 -Compress)
    exit $cfg.BlockedExitCode
}

Write-AktoInfo "Tool use PASSED guardrails for $toolName"
exit 0
