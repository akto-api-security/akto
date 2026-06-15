#!/usr/bin/env pwsh
# akto-validate-pre-tool.ps1 - PowerShell port of akto-validate-pre-tool.py (BeforeTool)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "akto-validate-pre-tool.log"

$logDir = Get-AktoLogDir
$WarnState = Join-Path $logDir "akto_pretool_warn_pending.json"
$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
$ConnectorValue = Get-AktoConnectorValue
$McpIngestPath = Get-AktoMcpIngestPath
$NonMcpPrefix = Get-AktoNonMcpPrefix
$ContextSource = Get-AktoContextSource

if ($Mode -eq "atlas") {
    $GeminiApiUrl = if ($DeviceId) { "https://$DeviceId.ai-agent.$ConnectorValue" } else { $(if ($env:GEMINI_API_URL) { $env:GEMINI_API_URL } else { "https://generativelanguage.googleapis.com" }) }
} else {
    $GeminiApiUrl = if ($env:GEMINI_API_URL) { $env:GEMINI_API_URL } else { "https://generativelanguage.googleapis.com" }
}

# ---- gemini tool resolution (mcp_<server>_<tool> OR mcp_context) -------------
function Resolve-GeminiTool {
    param([string]$ToolName, $McpContext)
    $result = @{ IsMcp=$false; Server=""; Tool="" }
    if ($null -ne $McpContext -and ($McpContext -is [pscustomobject] -or $McpContext -is [hashtable])) {
        $sn = if ($McpContext.server_name) { [string]$McpContext.server_name } elseif ($McpContext.serverName) { [string]$McpContext.serverName } else { "" }
        $tn = if ($McpContext.tool_name)   { [string]$McpContext.tool_name }   elseif ($McpContext.toolName)   { [string]$McpContext.toolName }   else { "" }
        if ($sn -and $tn) { $result.IsMcp=$true; $result.Server=$sn; $result.Tool=$tn; return $result }
    }
    if (-not $ToolName.StartsWith("mcp_")) { return $result }
    $rest = $ToolName.Substring(4)
    $idx = $rest.IndexOf('_')
    if ($idx -le 0 -or $idx -ge $rest.Length - 1) { return $result }
    $s = $rest.Substring(0, $idx)
    $t = $rest.Substring($idx + 1)
    if ($s -and $t) { $result.IsMcp=$true; $result.Server=$s; $result.Tool=$t }
    return $result
}

function Get-NonMcpPath {
    param([string]$ToolName)
    if ($env:NON_MCP_INGEST_PATH) { $f=$env:NON_MCP_INGEST_PATH; return $(if ($f.StartsWith("/")){$f}else{"/$f"}) }
    $px = $NonMcpPrefix; if (-not $px.StartsWith("/")) { $px = "/$px" }; $px = $px.TrimEnd("/"); if (-not $px) { $px = "/tool" }
    $x = ($ToolName -replace '[^a-zA-Z0-9._~-]+','-') -replace '-+','-'; $x = $x.Trim('-'); if (-not $x) { $x = "unknown" }
    return "$px/$x"
}

function Get-JsonRpcArguments {
    param($ti)
    if ($ti -is [pscustomobject] -or $ti -is [hashtable]) { return $ti }
    if ($null -eq $ti) { return @{} }
    return @{ input = $ti }
}

function Build-ValidationRequest {
    param([string]$ToolName, $ToolInput, [hashtable]$Mcp, [hashtable]$Session)
    if ($Mcp.IsMcp) {
        $tags = [ordered]@{ "mcp-server" = "MCP Server"; "mcp-client" = $ConnectorValue }
        if ($Mode -eq "atlas") { $tags["source"] = $ContextSource }
        $host_ = "$DeviceId.$ConnectorValue.$($Mcp.Server)"
        $reqPayload = [ordered]@{ jsonrpc="2.0"; method="tools/call"; params=[ordered]@{ name=$Mcp.Tool; arguments=(Get-JsonRpcArguments $ToolInput) }; id=1 }
        $path = $McpIngestPath
    } else {
        $tags = [ordered]@{ "gen-ai" = "Gen AI"; "ai-agent" = $ConnectorValue }
        if ($Mode -eq "atlas") { $tags["source"] = $ContextSource }
        $host_ = $GeminiApiUrl -replace '^https?://',''
        $reqPayload = [ordered]@{ body = $ToolInput; toolName = $ToolName }
        $path = Get-NonMcpPath $ToolName
    }

    $reqHdr = [ordered]@{ host = $host_; "x-gemini-hook" = "BeforeTool"; "content-type" = "application/json" }
    if ($Mcp.IsMcp -and $Mcp.Server) { $reqHdr["x-mcp-server"] = $Mcp.Server }
    foreach ($k in $Session.Keys) { if ($null -ne $Session[$k]) { $reqHdr["x-akto-installer-$k"] = [string]$Session[$k] } }

    [ordered]@{
        path            = $path
        requestHeaders  = (ConvertTo-AktoJson $reqHdr)
        responseHeaders = (ConvertTo-AktoJson ([ordered]@{ "x-gemini-hook" = "BeforeTool" }))
        method          = "POST"
        requestPayload  = (ConvertTo-AktoJson $reqPayload)
        responsePayload = "{}"
        ip              = (Get-AktoUsername)
        destIp          = "127.0.0.1"
        time            = ([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
        statusCode      = "200"; type = "HTTP/1.1"; status = "200"
        akto_account_id = "1000000"
        akto_vxlan_id   = 0
        is_pending      = "false"; source = "MIRRORING"
        direction = $null; process_id = $null; socket_id = $null; daemonset_id = $null; enabled_graph = $null
        tag             = (ConvertTo-AktoJson $tags)
        metadata        = (ConvertTo-AktoJson $tags)
        contextSource   = $ContextSource
    }
}

# --- read stdin ---
$raw = [Console]::In.ReadToEnd()
try { $in = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; '{}'; exit 0 }

$hookEvent = [string]$in.hook_event_name
if ($hookEvent -and $hookEvent -ne "BeforeTool") { Write-AktoInfo "Ignoring non-BeforeTool event: $hookEvent"; '{}'; exit 0 }

$session = @{}
foreach ($f in "session_id","transcript_path","cwd","hook_event_name","timestamp","original_request_name") {
    if ($null -ne $in.$f) { $session[$f] = $in.$f }
}

$toolName  = [string]$in.tool_name
$toolInput = if ($null -ne $in.tool_input)  { $in.tool_input }  else { @{} }
$mcpCtx    = if ($null -ne $in.mcp_context) { $in.mcp_context } else { $null }
$mcp = Resolve-GeminiTool -ToolName $toolName -McpContext $mcpCtx

if ($mcp.IsMcp) {
    Write-AktoInfo "Processing MCP tool request: $toolName (server=$($mcp.Server), tool=$($mcp.Tool))"
} else {
    Write-AktoInfo "Processing non-MCP tool request: $toolName"
}

if (Test-AktoSync) {
    $inputIsEmpty = ($null -eq $toolInput -or ($toolInput -is [hashtable] -and $toolInput.Count -eq 0) -or ($toolInput -is [pscustomobject] -and ($toolInput | Get-Member -MemberType NoteProperty).Count -eq 0))
    if ($inputIsEmpty) { Write-AktoInfo "Empty tool input, allowing"; '{}'; exit 0 }
    if (-not (Get-AktoIngestUrl)) { Write-AktoWarn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; '{}'; exit 0 }

    $body = ConvertTo-AktoJson (Build-ValidationRequest -ToolName $toolName -ToolInput $toolInput -Mcp $mcp -Session $session)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -Guardrails -IngestData) -Body $body
    $gr = Get-AktoGuardrailsResult $resp
    $fp = Get-AktoSha256Hex (([ordered]@{ i=$toolInput; t=$toolName } | ConvertTo-Json -Depth 30 -Compress))
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState

    if (-not $flow.Allowed) {
        if (Test-WarnBehaviour $gr.Behaviour) {
            $reason = "Warning!!, tool request blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)"
        } else {
            $reason = "Tool request blocked: $($gr.Reason)"
        }
        Write-AktoWarn "BLOCKING tool request - $toolName"
        (@{ decision = "deny"; reason = $reason } | ConvertTo-Json -Compress)
        exit 0
    }
}

Write-AktoInfo "Tool request allowed for $toolName"
'{}'
exit 0
