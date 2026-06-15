#!/usr/bin/env pwsh
# akto-validate-mcp-request.ps1 - PowerShell port of akto-validate-mcp-request.py (PreToolUse)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "validate-mcp-request.log"

$logDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".claude/akto/logs" }
$WarnState = Join-Path $logDir "akto_pretool_warn_pending.json"

$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$ConnectorValue = if ($env:AKTO_CONNECTOR_VALUE) { $env:AKTO_CONNECTOR_VALUE } else { "claudecli" }
$McpIngestPath = if ($env:MCP_INGEST_PATH) { $env:MCP_INGEST_PATH } else { "/mcp" }
$NonMcpPrefix = if ($env:NON_MCP_TOOL_PATH_PREFIX) { $env:NON_MCP_TOOL_PATH_PREFIX } else { "/tool" }
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
if ($Mode -eq "atlas") {
    $ClaudeApiUrl = if ($DeviceId) { "https://$DeviceId.ai-agent.$ConnectorValue" } else { "https://api.anthropic.com" }
} else { $ClaudeApiUrl = if ($env:CLAUDE_API_URL) { $env:CLAUDE_API_URL } else { "https://api.anthropic.com" } }

function Resolve-ClaudeTool {
    param([string]$ToolName)
    if (-not $ToolName.StartsWith("mcp__")) { return @{ IsMcp=$false; Server=""; Tool="" } }
    $parts = $ToolName -split "__"
    if ($parts.Count -lt 3) { return @{ IsMcp=$false; Server=""; Tool="" } }
    $server = $parts[1]; $tool = ($parts[2..($parts.Count-1)] -join "__")
    if (-not $server -or -not $tool) { return @{ IsMcp=$false; Server=""; Tool="" } }
    return @{ IsMcp=$true; Server=$server; Tool=$tool }
}

function Get-NonMcpPath {
    param([string]$ToolName)
    if ($env:NON_MCP_INGEST_PATH) { $f = $env:NON_MCP_INGEST_PATH; return $(if ($f.StartsWith("/")) { $f } else { "/$f" }) }
    $prefix = $NonMcpPrefix; if (-not $prefix.StartsWith("/")) { $prefix = "/$prefix" }
    $prefix = $prefix.TrimEnd("/"); if (-not $prefix) { $prefix = "/tool" }
    $s = ($ToolName -replace '[^a-zA-Z0-9._~-]+','-') -replace '-+','-'
    $s = $s.Trim('-'); if (-not $s) { $s = "unknown" }
    "$prefix/$s"
}

function Get-JsonRpcArguments {
    param($ToolInput)
    if ($ToolInput -is [System.Management.Automation.PSCustomObject] -or $ToolInput -is [hashtable]) { return $ToolInput }
    if ($null -eq $ToolInput) { return @{} }
    return @{ input = $ToolInput }
}

function Build-ValidationRequest {
    param([string]$ToolName, $ToolInput, [hashtable]$Mcp, [hashtable]$Session)
    if ($Mcp.IsMcp) {
        $tags = [ordered]@{ "mcp-server"="MCP Server"; "mcp-client"=$ConnectorValue }
        $host_ = "$DeviceId.$ConnectorValue.$($Mcp.Server)"
        $reqPayload = [ordered]@{ jsonrpc="2.0"; method="tools/call"; params=[ordered]@{ name=$Mcp.Tool; arguments=(Get-JsonRpcArguments $ToolInput) }; id=1 }
        $path = $McpIngestPath
    } else {
        $tags = [ordered]@{ "gen-ai"="Gen AI"; "ai-agent"=$ConnectorValue }
        $host_ = $ClaudeApiUrl -replace '^https?://',''
        $reqPayload = [ordered]@{ body=$ToolInput; toolName=$ToolName }
        $path = Get-NonMcpPath $ToolName
    }
    if ($Mode -eq "atlas") { $tags["source"] = (Get-AktoContextSource) }

    $reqHdr = [ordered]@{ host=$host_; "x-claude-hook"="PreToolUse"; "content-type"="application/json" }
    if ($Mcp.IsMcp -and $Mcp.Server) { $reqHdr["x-mcp-server"] = $Mcp.Server }
    foreach ($k in $Session.Keys) { if ($null -ne $Session[$k]) { $reqHdr["x-akto-installer-$k"] = [string]$Session[$k] } }

    [ordered]@{
        path=$path
        requestHeaders=(ConvertTo-AktoJson $reqHdr)
        responseHeaders=(ConvertTo-AktoJson ([ordered]@{ "x-claude-hook"="PreToolUse" }))
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

function Get-ModifiedInput {
    param($ModifiedPayload, $Fallback, [bool]$IsMcp)
    if ($null -eq $ModifiedPayload) { return $Fallback }
    $parsed = $ModifiedPayload
    if ($parsed -is [string]) {
        if ([string]::IsNullOrWhiteSpace($parsed)) { return $Fallback }
        try { $parsed = $parsed | ConvertFrom-Json } catch { Write-AktoWarn "ModifiedPayload not valid JSON; keeping original"; return $Fallback }
    }
    if ($IsMcp) {
        if ($parsed.params -and $parsed.params.arguments) { return $parsed.params.arguments }
        Write-AktoWarn "MCP ModifiedPayload missing params.arguments; keeping original"; return $Fallback
    }
    if ($parsed.body) { return $parsed.body }
    Write-AktoWarn "Non-MCP ModifiedPayload missing body; keeping original"; return $Fallback
}

# --- read stdin ---
$raw = [Console]::In.ReadToEnd()
try { $input_ = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; exit 0 }

$session = @{}
foreach ($f in "session_id","transcript_path","cwd","permission_mode","hook_event_name","tool_use_id") {
    if ($null -ne $input_.$f) { $session[$f] = $input_.$f }
}
$toolName = [string]$input_.tool_name
$toolInput = if ($null -ne $input_.tool_input) { $input_.tool_input } else { @{} }
$mcp = Resolve-ClaudeTool $toolName

if (Test-AktoSync) {
    if (-not (Get-AktoIngestUrl)) { Write-AktoWarn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; exit 0 }
    $body = ConvertTo-AktoJson (Build-ValidationRequest -ToolName $toolName -ToolInput $toolInput -Mcp $mcp -Session $session)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -Guardrails -IngestData) -Body $body
    $gr = Get-AktoGuardrailsResult $resp

    $fpObj = [ordered]@{ i = $toolInput; t = $toolName }
    $fp = Get-AktoSha256Hex ($fpObj | ConvertTo-Json -Depth 30 -Compress)
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState

    if (-not $flow.Allowed) {
        if (Test-WarnBehaviour $gr.Behaviour) {
            $reason = "Warning!!, tool request blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)"
        } else { $reason = "Tool request blocked: $($gr.Reason)" }
        Write-AktoWarn "BLOCKING tool request - Tool: $toolName, Reason: $($gr.Reason)"
        (@{ hookSpecificOutput = [ordered]@{ hookEventName="PreToolUse"; permissionDecision="deny"; permissionDecisionReason=$reason } } | ConvertTo-Json -Depth 10 -Compress)
        exit 0
    }

    if ($gr.Modified -and $gr.ModifiedPayload) {
        $newInput = Get-ModifiedInput -ModifiedPayload $gr.ModifiedPayload -Fallback $toolInput -IsMcp $mcp.IsMcp
        Write-AktoInfo "Applying guardrail-modified tool_input for $toolName"
        $out = [ordered]@{ hookSpecificOutput = [ordered]@{ hookEventName="PreToolUse"; permissionDecision="allow"; permissionDecisionReason="Tool request allowed (Akto guardrails)"; updatedInput=$newInput } }
        ($out | ConvertTo-Json -Depth 30 -Compress)
        exit 0
    }
}
Write-AktoInfo "Tool request allowed for $toolName"
exit 0
