#!/usr/bin/env pwsh
# akto-validate-mcp-response.ps1 - PowerShell port of claude-cli-hooks-argus/akto-validate-mcp-response.py (PostToolUse)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "validate-mcp-response.log"
$logDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".claude/akto/logs" }
$WarnState = Join-Path $logDir "akto_posttool_warn_pending.json"
$AktoHost = if ($env:AKTO_HOST) { $env:AKTO_HOST } else { "https://api.anthropic.com" }
$ContextSource = if ($env:CONTEXT_SOURCE) { $env:CONTEXT_SOURCE } else { "AGENTIC" }
$ConnectorValue = if ($env:AKTO_CONNECTOR_VALUE) { $env:AKTO_CONNECTOR_VALUE } else { "claudecli" }
$McpIngestPath = if ($env:MCP_INGEST_PATH) { $env:MCP_INGEST_PATH } else { "/mcp" }
$NonMcpPrefix = if ($env:NON_MCP_TOOL_PATH_PREFIX) { $env:NON_MCP_TOOL_PATH_PREFIX } else { "/tool" }
$HostHeader = $AktoHost -replace '^https?://',''
$DeviceIp = Get-AktoDeviceIp
$IngestNonMcp = $(if ($env:AKTO_INGEST_NON_MCP_TOOLS) { $env:AKTO_INGEST_NON_MCP_TOOLS } else { "false" }).ToLower() -eq "true"

function Resolve-ClaudeTool { param([string]$ToolName)
    if (-not $ToolName.StartsWith("mcp__")) { return @{ IsMcp=$false; Server=""; Tool="" } }
    $p=$ToolName -split "__"; if ($p.Count -lt 3) { return @{ IsMcp=$false; Server=""; Tool="" } }
    $s=$p[1]; $t=($p[2..($p.Count-1)] -join "__"); if (-not $s -or -not $t) { return @{ IsMcp=$false; Server=""; Tool="" } }
    @{ IsMcp=$true; Server=$s; Tool=$t }
}
function Get-NonMcpPath { param([string]$ToolName)
    if ($env:NON_MCP_INGEST_PATH) { $f=$env:NON_MCP_INGEST_PATH; return $(if ($f.StartsWith("/")){$f}else{"/$f"}) }
    $px=$NonMcpPrefix; if (-not $px.StartsWith("/")){$px="/$px"}; $px=$px.TrimEnd("/"); if(-not $px){$px="/tool"}
    $x=($ToolName -replace '[^a-zA-Z0-9._~-]+','-') -replace '-+','-'; $x=$x.Trim('-'); if(-not $x){$x="unknown"}; "$px/$x"
}
function Get-JsonRpcArguments { param($ti) if ($ti -is [pscustomobject] -or $ti -is [hashtable]) { $ti } elseif ($null -eq $ti) { @{} } else { @{ input=$ti } } }
function Get-ResultBody { param($tr) if ($tr -is [pscustomobject] -or $tr -is [hashtable]) { $tr } else { @{ output=$tr } } }
function Build-IngestionPayload { param([string]$ToolName,$ToolInput,$ToolResponse,[hashtable]$Mcp,[hashtable]$Session)
    if ($Mcp.IsMcp) {
        $tags=[ordered]@{ "mcp-server"="MCP Server"; "mcp-client"=$ConnectorValue; "source"=$ContextSource }
        $reqPayload=[ordered]@{ jsonrpc="2.0"; method="tools/call"; params=[ordered]@{ name=$Mcp.Tool; arguments=(Get-JsonRpcArguments $ToolInput) }; id=1 }
        $respPayload=[ordered]@{ jsonrpc="2.0"; id=1; result=(Get-ResultBody $ToolResponse) }; $path=$McpIngestPath
    } else {
        $tags=[ordered]@{ "gen-ai"="Gen AI"; "ai-agent"=$ConnectorValue; "source"=$ContextSource }
        $reqPayload=[ordered]@{ body=[ordered]@{ toolName=$ToolName; toolArgs=$ToolInput } }; $respPayload=[ordered]@{ body=[ordered]@{ result=$ToolResponse } }; $path=Get-NonMcpPath $ToolName
    }
    $reqHdr=[ordered]@{ host=$HostHeader; "x-claude-hook"="PostToolUse"; "content-type"="application/json" }
    if ($Mcp.IsMcp -and $Mcp.Server) { $reqHdr["x-mcp-server"]=$Mcp.Server }
    foreach ($k in $Session.Keys) { if ($null -ne $Session[$k]) { $reqHdr["x-akto-installer-$k"]=[string]$Session[$k] } }
    [ordered]@{
        path=$path; requestHeaders=(ConvertTo-AktoJson $reqHdr)
        responseHeaders=(ConvertTo-AktoJson ([ordered]@{ "x-claude-hook"="PostToolUse"; "content-type"="application/json" }))
        method="POST"; requestPayload=(ConvertTo-AktoJson $reqPayload); responsePayload=(ConvertTo-AktoJson $respPayload)
        ip=$DeviceIp; destIp="127.0.0.1"; time=([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
        statusCode="200"; type="HTTP/1.1"; status="200"; akto_account_id="1000000"; akto_vxlan_id=0
        is_pending="false"; source="MIRRORING"; direction=$null; process_id=$null; socket_id=$null; daemonset_id=$null; enabled_graph=$null
        tag=(ConvertTo-AktoJson $tags); metadata=(ConvertTo-AktoJson $tags); contextSource=$ContextSource
    }
}
$raw = [Console]::In.ReadToEnd()
try { $in = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; exit 0 }
$session=@{}; foreach ($f in "session_id","transcript_path","cwd","permission_mode","hook_event_name","tool_use_id") { if ($null -ne $in.$f) { $session[$f]=$in.$f } }
$toolName=[string]$in.tool_name
$toolInput=if ($null -ne $in.tool_input) { $in.tool_input } else { @{} }
$toolResponse=if ($null -ne $in.tool_response) { $in.tool_response } else { @{} }
$mcp=Resolve-ClaudeTool $toolName
$shouldIngest=($mcp.IsMcp -or $IngestNonMcp)
if (Test-AktoSync) {
    if (-not (Get-AktoIngestUrl)) { Write-AktoWarn "no URL, fail-open"; exit 0 }
    $body = ConvertTo-AktoJson (Build-IngestionPayload -ToolName $toolName -ToolInput $toolInput -ToolResponse $toolResponse -Mcp $mcp -Session $session)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails) -Body $body
    $gr = Get-AktoGuardrailsResult $resp
    $fp = Get-AktoSha256Hex ([ordered]@{ i=$toolInput } | ConvertTo-Json -Depth 30 -Compress)
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState
    if (-not $flow.Allowed) {
        if (Test-WarnBehaviour $gr.Behaviour) { $reason="Warning!!, tool result blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)" } else { $reason="Tool result blocked: $($gr.Reason)" }
        Write-AktoWarn "BLOCKING tool result - $toolName"
        $out=[ordered]@{ decision="block"; reason=$reason; hookSpecificOutput=[ordered]@{ hookEventName="PostToolUse"; additionalContext=$(if ($gr.Reason) { $gr.Reason } else { "Policy violation" }) } }
        ($out | ConvertTo-Json -Depth 10 -Compress); exit 0
    }
}
if ((Get-AktoIngestUrl) -and $shouldIngest) {
    $body = ConvertTo-AktoJson (Build-IngestionPayload -ToolName $toolName -ToolInput $toolInput -ToolResponse $toolResponse -Mcp $mcp -Session $session)
    if (Test-AktoSync) { $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -IngestData) -Body $body } else { $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails -IngestData) -Body $body }
}
exit 0
