#!/usr/bin/env pwsh
# akto-validate-post-tool.ps1 - PowerShell port of codex akto-validate-post-tool.py (PostToolUse)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "validate-post-tool.log"

$logDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".codex/akto/logs" }
$WarnState = Join-Path $logDir "akto_posttool_warn_pending.json"
$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$ConnectorValue = if ($env:AKTO_CONNECTOR_VALUE) { $env:AKTO_CONNECTOR_VALUE } else { "codexcli" }
$McpIngestPath = if ($env:MCP_INGEST_PATH) { $env:MCP_INGEST_PATH } else { "/mcp" }
$NonMcpPrefix = if ($env:NON_MCP_TOOL_PATH_PREFIX) { $env:NON_MCP_TOOL_PATH_PREFIX } else { "/tool" }
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
$IngestNonMcp = $(if ($env:AKTO_INGEST_NON_MCP_TOOLS) { $env:AKTO_INGEST_NON_MCP_TOOLS } else { "false" } ).ToLower() -eq "true"

function Resolve-CodexApi {
    if ($env:OPENAI_BASE_URL) { return @{ Host=($env:OPENAI_BASE_URL).TrimEnd("/"); Path="/v1/responses" } }
    if ($env:OPENAI_API_KEY)  { return @{ Host="https://api.openai.com"; Path="/v1/responses" } }
    return @{ Host="https://chatgpt.com"; Path="/backend-api/codex/responses" }
}
$api = Resolve-CodexApi
if ($Mode -eq "atlas") {
    $CodexApiHost = if ($DeviceId) { "https://$DeviceId.ai-agent.$ConnectorValue" } else { $api.Host }
} else { $CodexApiHost = $api.Host }

function Resolve-CodexTool {
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
    if ($env:NON_MCP_INGEST_PATH) { $f=$env:NON_MCP_INGEST_PATH; return $(if ($f.StartsWith("/")){$f}else{"/$f"}) }
    $prefix=$NonMcpPrefix; if (-not $prefix.StartsWith("/")){$prefix="/$prefix"}; $prefix=$prefix.TrimEnd("/"); if(-not $prefix){$prefix="/tool"}
    $s=($ToolName -replace '[^a-zA-Z0-9._~-]+','-') -replace '-+','-'; $s=$s.Trim('-'); if(-not $s){$s="unknown"}
    "$prefix/$s"
}
function Get-JsonRpcArguments { param($ti) if ($ti -is [pscustomobject] -or $ti -is [hashtable]) { $ti } elseif ($null -eq $ti) { @{} } else { @{ input=$ti } } }
function Get-ResultBody { param($tr) if ($tr -is [pscustomobject] -or $tr -is [hashtable]) { $tr } else { @{ output=$tr } } }

function Build-IngestionPayload {
    param([string]$ToolName, $ToolInput, $ToolResponse, [hashtable]$Mcp, [hashtable]$Session)
    if ($Mcp.IsMcp) {
        $tags=[ordered]@{ "mcp-server"="MCP Server"; "mcp-client"=$ConnectorValue }
        $host_="$DeviceId.$ConnectorValue.$($Mcp.Server)"
        $reqPayload=[ordered]@{ jsonrpc="2.0"; method="tools/call"; params=[ordered]@{ name=$Mcp.Tool; arguments=(Get-JsonRpcArguments $ToolInput) }; id=1 }
        $respPayload=[ordered]@{ jsonrpc="2.0"; id=1; result=(Get-ResultBody $ToolResponse) }
        $path=$McpIngestPath
    } else {
        $tags=[ordered]@{ "gen-ai"="Gen AI"; "ai-agent"=$ConnectorValue; "tool-use"="Tool Execution"; "tool_name"=$ToolName }
        $host_=$CodexApiHost -replace '^https?://',''
        $reqPayload=[ordered]@{ body=[ordered]@{ toolName=$ToolName; toolArgs=$ToolInput } }
        $respPayload=[ordered]@{ body=[ordered]@{ result=$ToolResponse } }
        $path=Get-NonMcpPath $ToolName
    }
    if ($Mode -eq "atlas") { $tags["source"]=(Get-AktoContextSource) }
    $reqHdr=[ordered]@{ host=$host_; "x-codex-hook"="PostToolUse"; "content-type"="application/json" }
    if ($Mcp.IsMcp -and $Mcp.Server) { $reqHdr["x-mcp-server"]=$Mcp.Server }
    foreach ($k in $Session.Keys) { if ($null -ne $Session[$k]) { $reqHdr["x-akto-installer-$k"]=[string]$Session[$k] } }
    [ordered]@{
        path=$path
        requestHeaders=(ConvertTo-AktoJson $reqHdr)
        responseHeaders=(ConvertTo-AktoJson ([ordered]@{ "x-codex-hook"="PostToolUse"; "content-type"="application/json" }))
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
try { $input_ = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; exit 0 }
$session = @{}
foreach ($f in "session_id","transcript_path","cwd","hook_event_name","model","turn_id","tool_use_id") {
    if ($null -ne $input_.$f) { $session[$f] = $input_.$f }
}
$toolName = [string]$input_.tool_name
$toolInput = if ($null -ne $input_.tool_input) { $input_.tool_input } else { @{} }
$toolResponse = if ($null -ne $input_.tool_response) { $input_.tool_response } else { @{} }
$toolUseId = [string]$input_.tool_use_id
$mcp = Resolve-CodexTool $toolName
$shouldIngest = ($mcp.IsMcp -or $IngestNonMcp)

if (Test-AktoSync) {
    if (-not (Get-AktoIngestUrl)) { Write-AktoWarn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; exit 0 }
    $body = ConvertTo-AktoJson (Build-IngestionPayload -ToolName $toolName -ToolInput $toolInput -ToolResponse $toolResponse -Mcp $mcp -Session $session)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails) -Body $body
    $gr = Get-AktoGuardrailsResult $resp
    $fpObj = [ordered]@{ i=$toolInput; r=$toolResponse; t=$toolName; u=$toolUseId }
    $fp = Get-AktoSha256Hex ($fpObj | ConvertTo-Json -Depth 30 -Compress)
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState
    if (-not $flow.Allowed) {
        if (Test-WarnBehaviour $gr.Behaviour) {
            $reason = "Warning!!, tool result blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)"
        } else { $reason = "Tool result blocked: $($gr.Reason)" }
        Write-AktoWarn "BLOCKING tool result - Tool: $toolName, Reason: $($gr.Reason)"
        $out = [ordered]@{ decision="block"; reason=$reason; hookSpecificOutput=[ordered]@{ hookEventName="PostToolUse"; additionalContext=$(if ($gr.Reason) { $gr.Reason } else { "Policy violation" }) } }
        ($out | ConvertTo-Json -Depth 10 -Compress)
        exit 0
    }
}
if ((Get-AktoIngestUrl) -and $shouldIngest) {
    $body = ConvertTo-AktoJson (Build-IngestionPayload -ToolName $toolName -ToolInput $toolInput -ToolResponse $toolResponse -Mcp $mcp -Session $session)
    if (Test-AktoSync) { $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -IngestData) -Body $body }
    else { $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails -IngestData) -Body $body }
}
exit 0
