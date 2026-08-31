<#
.SYNOPSIS
    Windows counterpart to sh/akto-hook.sh — the Akto guardrails hook handler.

.DESCRIPTION
    Same contract as the bash version: the agent's hook JSON arrives on stdin, the
    decision is written to stdout and/or signalled by the exit code.

        akto-hook.ps1 <connector> <event>

    PowerShell has native JSON, so this side needs no equivalent of lib/json.awk.
    Written for Windows PowerShell 5.1 (the version present on a stock Windows box)
    as well as PowerShell 7+: no ternary operator, no null-coalescing, and every
    ConvertTo-Json call passes an explicit -Depth because 5.1 silently truncates
    at depth 2.

    Every path fails OPEN.
#>
[CmdletBinding()]
param(
    [Parameter(Position = 0)][string]$Connector,
    [Parameter(Position = 1)][string]$EventName
)

$ErrorActionPreference = 'Stop'
$JSON_DEPTH = 100

function Get-EnvOr {
    param([string]$Name, [string]$Default = '')
    $v = [Environment]::GetEnvironmentVariable($Name)
    if ([string]::IsNullOrEmpty($v)) { return $Default }
    return $v
}

# ── Config ────────────────────────────────────────────────────────────────────

$Mode          = (Get-EnvOr 'MODE' 'argus').ToLower()
$IngestUrl     = (Get-EnvOr 'AKTO_DATA_INGESTION_URL').TrimEnd('/')
$ApiToken      = Get-EnvOr 'AKTO_API_TOKEN'
$TimeoutSec    = [int](Get-EnvOr 'AKTO_TIMEOUT' '5')
$SyncMode      = (Get-EnvOr 'AKTO_SYNC_MODE' 'true').ToLower()
$ContextSource = Get-EnvOr 'CONTEXT_SOURCE' 'ENDPOINT'
$McpIngestPath = Get-EnvOr 'MCP_INGEST_PATH' '/mcp'
$NonMcpPrefix  = Get-EnvOr 'NON_MCP_TOOL_PATH_PREFIX' '/tool'
$LogPayloads   = (Get-EnvOr 'LOG_PAYLOADS' 'false').ToLower()

if ([string]::IsNullOrEmpty($Connector)) { $Connector = Get-EnvOr 'AKTO_CONNECTOR' }
if ([string]::IsNullOrEmpty($Connector) -or [string]::IsNullOrEmpty($EventName)) {
    [Console]::Error.WriteLine('Usage: akto-hook.ps1 <connector> <event>')
    exit 0
}

function Get-ConnectorTag {
    param([string]$C)
    switch ($C) {
        'claude_code_cli' { return 'claudecli' }
        'cursor'          { return 'cursor' }
        'vscode'          { return 'vscode' }
        'gemini_cli'      { return 'geminicli' }
        'github'          { return 'github' }
        'codex_cli'       { return 'codexcli' }
        'kiro_cli'        { return 'kirocli' }
        default           { return $C }
    }
}

function Get-DefaultLogDir {
    param([string]$C)
    $home_ = $env:USERPROFILE
    switch ($C) {
        'claude_code_cli' { return "$home_\.claude\akto\logs" }
        'cursor'          { return "$home_\.cursor\akto\chat-logs" }
        'gemini_cli'      { return "$home_\.gemini\akto\chat-logs" }
        'codex_cli'       { return "$home_\.codex\akto\logs" }
        'github'          { return "$home_\akto\.github\akto\vscode\logs" }
        'vscode'          { return "$home_\.vscode\copilot\hooks\akto\logs" }
        'kiro_cli'        { return "$home_\.kiro\akto\logs" }
        default           { return "$home_\akto\$C-hooks\logs" }
    }
}

$TagName        = Get-ConnectorTag $Connector
$ConnectorValue = Get-EnvOr 'AKTO_CONNECTOR_VALUE' $TagName
$HookHeader     = "x-$TagName-hook"
$LogDir         = Get-EnvOr 'LOG_DIR' (Get-DefaultLogDir $Connector)
if (-not (Test-Path -LiteralPath $LogDir)) {
    New-Item -ItemType Directory -Force -Path $LogDir | Out-Null
}
$LogFile = Join-Path $LogDir 'hook-executions.log'

function Write-Log {
    param([string]$Level, [string]$Message)
    try {
        $line = '{0} - {1} - {2}' -f (Get-Date -Format 'yyyy-MM-dd HH:mm:ss'), $Level, $Message
        Add-Content -LiteralPath $LogFile -Value $line -ErrorAction SilentlyContinue
    } catch {}
}
function Log-Info  { param([string]$m) Write-Log 'INFO' $m }
function Log-Warn  { param([string]$m) Write-Log 'WARNING' $m }
function Log-Error {
    param([string]$m)
    Write-Log 'ERROR' $m
    [Console]::Error.WriteLine("Akto hook: $m")
}

# ── Device label ──────────────────────────────────────────────────────────────
# Mirrors utils.GetDeviceLabel() in the Go agent, and Get-WindowsDeviceLabel in
# mdm-scripts/install.ps1: lowercased hostname with non-alphanumerics replaced by
# '-', then '-' + the first 8 chars of the machine id.

function Get-DeviceLabel {
    $fromEnv = Get-EnvOr 'DEVICE_ID'
    if (-not [string]::IsNullOrEmpty($fromEnv)) { return $fromEnv }

    $rawName = $env:COMPUTERNAME
    if ([string]::IsNullOrEmpty($rawName)) {
        try { $rawName = [System.Net.Dns]::GetHostName() } catch { $rawName = '' }
    }
    $devName = ''
    if (-not [string]::IsNullOrEmpty($rawName)) {
        $devName = $rawName.ToLower() -replace '[^a-z0-9]', '-'
    }

    $machineId = ''
    try {
        $guid = (Get-ItemProperty -Path 'HKLM:\SOFTWARE\Microsoft\Cryptography' -Name MachineGuid -ErrorAction Stop).MachineGuid
        if ($guid) { $machineId = ($guid -replace '-', '').ToLower() }
    } catch {}
    if ([string]::IsNullOrEmpty($machineId)) {
        try {
            $mac = (Get-NetAdapter -ErrorAction SilentlyContinue | Where-Object { $_.Status -eq 'Up' } | Select-Object -First 1).MacAddress
            if ($mac) { $machineId = ($mac -replace '[-:]', '').ToLower() }
        } catch {}
    }
    $shortId = $machineId
    if ($machineId.Length -gt 8) { $shortId = $machineId.Substring(0, 8) }

    if ($devName -and $shortId)  { return "$devName-$shortId" }
    if ($devName)                { return $devName }
    if ($machineId)              { return $machineId }
    return 'unknown-device'
}

$DeviceLabel = Get-DeviceLabel

# ── HTTP ──────────────────────────────────────────────────────────────────────

function Get-ProxyUrl {
    param([bool]$Guardrails, [bool]$Ingest, [string]$ClientHook = '', [bool]$ResponseGuardrails = $false)
    $q = "akto_connector=$Connector"
    if ($Guardrails)         { $q = "guardrails=true&$q" }
    if ($ResponseGuardrails) { $q = "response_guardrails=true&$q" }
    if ($Ingest)     { $q = "$q&ingest_data=true" }
    if ($ClientHook) { $q = "$q&client_hook=$ClientHook" }
    return "$IngestUrl/api/http-proxy?$q"
}

# Parity with the Python hooks, which used an unverified TLS context so on-prem
# deployments behind a corporate MITM proxy keep working.
if ($PSVersionTable.PSVersion.Major -lt 6) {
    try {
        Add-Type -TypeDefinition @'
using System.Net;
using System.Security.Cryptography.X509Certificates;
public class AktoCertPolicy : ICertificatePolicy {
    public bool CheckValidationResult(ServicePoint sp, X509Certificate cert, WebRequest req, int problem) { return true; }
}
'@ -ErrorAction SilentlyContinue
        [System.Net.ServicePointManager]::CertificatePolicy = New-Object AktoCertPolicy
        [System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12
    } catch {}
}

function Invoke-AktoPost {
    param([string]$Url, [string]$Body)
    Log-Info "API CALL: POST $Url"
    if ($LogPayloads -eq 'true') { Write-Log 'DEBUG' "Request payload: $Body" }

    $headers = @{ 'Content-Type' = 'application/json' }
    if ($ApiToken) { $headers['Authorization'] = $ApiToken }
    try {
        # Not $args: that is an automatic variable inside a function.
        $req = @{
            Uri         = $Url
            Method      = 'POST'
            Headers     = $headers
            Body        = [System.Text.Encoding]::UTF8.GetBytes($Body)
            TimeoutSec  = $TimeoutSec
            ErrorAction = 'Stop'
        }
        if ($PSVersionTable.PSVersion.Major -ge 6) { $req['SkipCertificateCheck'] = $true }
        $resp = Invoke-WebRequest @req
        return $resp.Content
    } catch {
        Log-Error "API CALL FAILED: $($_.Exception.Message)"
        return $null
    }
}

# ── Payload ───────────────────────────────────────────────────────────────────
# The header/payload fields are JSON documents carried as JSON strings, so each
# inner object is serialised first and the resulting text stored as a string —
# the same double-encoding the Python hooks produced.

function New-Payload {
    param(
        [string]$Path, $ReqHeaders, $RespHeaders, $ReqPayload, $RespPayload,
        $Tags, [string]$StatusCode, $Vxlan
    )
    $o = [ordered]@{
        path            = $Path
        requestHeaders  = ($ReqHeaders  | ConvertTo-Json -Depth $JSON_DEPTH -Compress)
        responseHeaders = ($RespHeaders | ConvertTo-Json -Depth $JSON_DEPTH -Compress)
        method          = 'POST'
        requestPayload  = ($ReqPayload  | ConvertTo-Json -Depth $JSON_DEPTH -Compress)
        responsePayload = ($RespPayload | ConvertTo-Json -Depth $JSON_DEPTH -Compress)
        ip              = $env:USERNAME
        destIp          = '127.0.0.1'
        time            = [string][DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()
        statusCode      = $StatusCode
        type            = 'HTTP/1.1'
        status          = $StatusCode
        akto_account_id = '1000000'
        akto_vxlan_id   = $Vxlan
        is_pending      = 'false'
        source          = 'MIRRORING'
        direction       = $null
        process_id      = $null
        socket_id       = $null
        daemonset_id    = $null
        enabled_graph   = $null
        tag             = ($Tags | ConvertTo-Json -Depth $JSON_DEPTH -Compress)
        metadata        = ($Tags | ConvertTo-Json -Depth $JSON_DEPTH -Compress)
        contextSource   = $ContextSource
    }
    return ($o | ConvertTo-Json -Depth $JSON_DEPTH -Compress)
}

function New-Tags {
    param([bool]$IsMcp)
    $t = [ordered]@{}
    if ($IsMcp) {
        $t['mcp-server'] = 'MCP Server'
        $t['mcp-client'] = $ConnectorValue
    } else {
        $t['gen-ai']   = 'Gen AI'
        $t['ai-agent'] = $ConnectorValue
    }
    if ($Mode -eq 'atlas') { $t['source'] = $ContextSource }
    return $t
}

function Get-AtlasHost {
    if ($Mode -eq 'atlas' -and $DeviceLabel) { return "$DeviceLabel.ai-agent.$ConnectorValue" }
    return (Get-EnvOr 'AKTO_API_URL' '127.0.0.1')
}

# ── Guardrails ────────────────────────────────────────────────────────────────

function Invoke-Guardrails {
    param([string]$Payload, [string]$ClientHook)
    $result = [ordered]@{ Allowed = $true; Reason = ''; Behaviour = ''; Modified = $false; ModifiedPayload = $null }
    if ([string]::IsNullOrEmpty($IngestUrl)) {
        Log-Warn 'AKTO_DATA_INGESTION_URL not set, allowing (fail-open)'
        return $result
    }
    $raw = Invoke-AktoPost (Get-ProxyUrl $true $true $ClientHook) $Payload
    if (-not $raw) { return $result }
    try {
        $gr = ($raw | ConvertFrom-Json).data.guardrailsResult
        if ($null -eq $gr) { return $result }
        if ($null -ne $gr.Allowed) { $result.Allowed = [bool]$gr.Allowed }
        if ($gr.Reason)            { $result.Reason = [string]$gr.Reason }
        if ($gr.behaviour)         { $result.Behaviour = [string]$gr.behaviour }
        elseif ($gr.Behaviour)     { $result.Behaviour = [string]$gr.Behaviour }
        if ($null -ne $gr.Modified) { $result.Modified = [bool]$gr.Modified }
        if ($gr.ModifiedPayload)    { $result.ModifiedPayload = $gr.ModifiedPayload }
    } catch {
        Log-Error "Could not parse guardrails response: $($_.Exception.Message)"
    }
    return $result
}

function Send-Ingest {
    param([string]$Payload, [string]$ClientHook, [bool]$ResponseGuardrails = $false)
    if ([string]::IsNullOrEmpty($IngestUrl)) { return }
    Invoke-AktoPost (Get-ProxyUrl $false $true $ClientHook $ResponseGuardrails) $Payload | Out-Null
}

# ── Warn / alert resubmit flow ────────────────────────────────────────────────

function Get-Fingerprint {
    param([string]$Text)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    $bytes = $sha.ComputeHash([System.Text.Encoding]::UTF8.GetBytes($Text))
    return -join ($bytes | ForEach-Object { $_.ToString('x2') })
}

function Test-WarnFlow {
    param([string]$StateName, [string]$Fingerprint, [string]$Behaviour)
    $b = $Behaviour.ToLower()
    if ($b -eq 'alert') {
        Log-Info 'Alert behaviour: allowing despite violation (server-side alert only)'
        return $true
    }
    if ($b -ne 'warn') { return $false }

    $path = Join-Path $LogDir "akto_${StateName}_warn_pending.json"
    $pending = @()
    if (Test-Path -LiteralPath $path) {
        try { $pending = @((Get-Content -Raw -LiteralPath $path | ConvertFrom-Json).warn_pending) } catch {}
    }
    if ($pending -contains $Fingerprint) {
        $kept = @($pending | Where-Object { $_ -ne $Fingerprint })
        (@{ warn_pending = $kept } | ConvertTo-Json -Depth 3 -Compress) | Set-Content -LiteralPath $path
        Log-Info 'Warn flow: allowing resubmit'
        return $true
    }
    $pending += $Fingerprint
    (@{ warn_pending = @($pending) } | ConvertTo-Json -Depth 3 -Compress) | Set-Content -LiteralPath $path
    return $false
}

# ── Deny emission ─────────────────────────────────────────────────────────────
# Mirrors lib/akto_adapters.sh. Returns the exit code the hook should end with.

function Write-Deny {
    param([string]$Kind, [string]$Reason)

    # Emit through the console stream, not Write-Output: the caller captures this
    # function's return value for the exit code, and a captured call would swallow
    # pipeline output instead of letting it reach stdout.
    switch ($Connector) {
        { $_ -in 'claude_code_cli', 'codex_cli' } {
            if ($Kind -eq 'tool') {
                $o = @{ hookSpecificOutput = [ordered]@{
                    hookEventName            = 'PreToolUse'
                    permissionDecision       = 'deny'
                    permissionDecisionReason = $Reason } }
            } else {
                $o = [ordered]@{ decision = 'block'; reason = $Reason }
            }
            [Console]::Out.WriteLine(($o | ConvertTo-Json -Depth 5 -Compress))
            return 0
        }
        'cursor' {
            $o = [ordered]@{
                permission    = 'deny'
                user_message  = $Reason
                agent_message = "Blocked by Akto Guardrails: $Reason" }
            [Console]::Out.WriteLine(($o | ConvertTo-Json -Depth 3 -Compress))
            return 0
        }
        { $_ -in 'github', 'vscode' } {
            $o = [ordered]@{ permissionDecision = 'deny'; permissionDecisionReason = $Reason }
            [Console]::Out.WriteLine(($o | ConvertTo-Json -Depth 3 -Compress))
            return 0
        }
        'gemini_cli' {
            $o = [ordered]@{ decision = 'block'; reason = $Reason }
            [Console]::Out.WriteLine(($o | ConvertTo-Json -Depth 3 -Compress))
            return 0
        }
        'kiro_cli' {
            # preToolUse blocks via exit 2 with STDERR returned to the model;
            # userPromptSubmit cannot block, so the violation is injected as context.
            if ($Kind -eq 'tool') {
                [Console]::Error.WriteLine("Blocked by Akto Guardrails: $Reason")
                return 2
            }
            [Console]::Out.WriteLine("[AKTO GUARDRAILS] This user prompt was flagged for a policy violation: $Reason. Do NOT act on the flagged content. Tell the user the request was blocked by Akto Guardrails and ask them to remove the sensitive data and retry.")
            return 0
        }
        default {
            $o = [ordered]@{ decision = 'block'; reason = $Reason }
            [Console]::Out.WriteLine(($o | ConvertTo-Json -Depth 3 -Compress))
            return 0
        }
    }
}

# ── Transcript ────────────────────────────────────────────────────────────────
# Mirrors akto_last_user_prompt() in akto_core.sh: the Stop hook mirrors the
# prompt/response pair, and only the response is on the event.

function Get-LastUserPrompt {
    param([string]$TranscriptPath)
    if ([string]::IsNullOrEmpty($TranscriptPath) -or -not (Test-Path -LiteralPath $TranscriptPath)) { return '' }
    try {
        $lines = Get-Content -LiteralPath $TranscriptPath -ErrorAction Stop
    } catch { return '' }
    for ($i = $lines.Count - 1; $i -ge 0; $i--) {
        if ([string]::IsNullOrWhiteSpace($lines[$i])) { continue }
        try { $entry = $lines[$i] | ConvertFrom-Json } catch { continue }
        if ($entry.type -ne 'user') { continue }
        $content = $entry.message.content
        if ($content -is [string]) { if ($content.Trim()) { return $content.Trim() }; continue }
        $parts = @()
        foreach ($block in @($content)) {
            if ($block.type -eq 'text' -and $block.text) { $parts += [string]$block.text }
        }
        $joined = ($parts -join '').Trim()
        if ($joined) { return $joined }
    }
    return ''
}

# ── Event classification ──────────────────────────────────────────────────────

function Get-EventKind {
    param([string]$C, [string]$E)
    switch ("${C}:${E}") {
        { $_ -in 'claude_code_cli:UserPromptSubmit', 'codex_cli:UserPromptSubmit' } { return 'prompt' }
        { $_ -in 'claude_code_cli:PreToolUse', 'codex_cli:PreToolUse' }             { return 'tool' }
        { $_ -in 'claude_code_cli:PostToolUse', 'codex_cli:PostToolUse' }           { return 'tool_result' }
        { $_ -in 'claude_code_cli:Stop', 'codex_cli:Stop' }                         { return 'response' }
        'cursor:beforeSubmitPrompt' { return 'prompt' }
        'cursor:beforeMCPExecution' { return 'tool' }
        'cursor:afterMCPExecution'  { return 'tool_result' }
        'cursor:afterAgentResponse' { return 'response' }
        { $_ -in 'gemini_cli:BeforeAgent', 'gemini_cli:UserPromptSubmit' } { return 'prompt' }
        { $_ -in 'gemini_cli:BeforeTool', 'gemini_cli:PreToolUse' }        { return 'tool' }
        { $_ -in 'gemini_cli:AfterTool', 'gemini_cli:PostToolUse' }        { return 'tool_result' }
        { $_ -in 'gemini_cli:AfterAgent', 'gemini_cli:Stop' }              { return 'response' }
        { $_ -in 'github:userPromptSubmitted', 'vscode:userPromptSubmitted' } { return 'prompt' }
        { $_ -in 'github:preToolUse', 'vscode:preToolUse' }                   { return 'tool' }
        { $_ -in 'github:postToolUse', 'vscode:postToolUse' }                 { return 'tool_result' }
        'kiro_cli:userPromptSubmit' { return 'prompt' }
        'kiro_cli:preToolUse'       { return 'tool' }
        'kiro_cli:postToolUse'      { return 'tool_result' }
        default { return 'observe' }
    }
}

function Get-McpParts {
    param([string]$ToolName)
    $r = [ordered]@{ IsMcp = $false; Server = ''; Tool = '' }
    if ($ToolName -like 'mcp__*') {
        $rest = $ToolName.Substring(5)
        $idx = $rest.IndexOf('__')
        if ($idx -gt 0 -and $idx + 2 -lt $rest.Length) {
            $r.Server = $rest.Substring(0, $idx)
            $r.Tool   = $rest.Substring($idx + 2)
            $r.IsMcp  = $true
        }
    }
    return $r
}

function Get-NonMcpPath {
    param([string]$ToolName)
    $fixed = Get-EnvOr 'NON_MCP_INGEST_PATH'
    if ($fixed) { if ($fixed.StartsWith('/')) { return $fixed } else { return "/$fixed" } }
    $prefix = $NonMcpPrefix
    if (-not $prefix.StartsWith('/')) { $prefix = "/$prefix" }
    $prefix = $prefix.TrimEnd('/')
    if (-not $prefix) { $prefix = '/tool' }
    $name = $ToolName
    if (-not $name) { $name = 'unknown' }
    $name = ($name -replace '[^a-zA-Z0-9._~-]+', '-') -replace '-+', '-'
    $name = $name.Trim('-')
    if (-not $name) { $name = 'unknown' }
    return "$prefix/$name"
}

# ── Main ──────────────────────────────────────────────────────────────────────

Log-Info "=== $Connector/$EventName started (mode=$Mode sync=$SyncMode) ==="

$raw = [Console]::In.ReadToEnd()
$input_ = $null
try { $input_ = $raw | ConvertFrom-Json } catch {}
if ($null -eq $input_) {
    Log-Error 'stdin was not valid JSON; allowing'
    exit 0
}

$kind = Get-EventKind $Connector $EventName

# Live kill switch, matching akto_hooks_enabled() in akto-hook.sh: the agent writes
# ENABLE_PROMPT_HOOKS_* / ENABLE_MCP_HOOKS_* into config.env and either set to
# "false" disables the hook without uninstalling it.
function Test-HooksEnabled {
    param([string]$Kind)
    $suffix = switch ($Connector) {
        'claude_code_cli' { 'CLAUDE' }
        'cursor'          { 'CURSOR' }
        'gemini_cli'      { 'GEMINI' }
        'codex_cli'       { 'CODEX' }
        'github'          { 'GITHUB_CLI' }
        'vscode'          { 'VSCODE_COPILOT' }
        'kiro_cli'        { 'KIRO_CLI' }
        'opencode'        { 'OPENCODE' }
        default           { '' }
    }
    if (-not $suffix) { return $true }
    if ($Kind -in 'tool', 'tool_result') { $flag = "ENABLE_MCP_HOOKS_$suffix" }
    else                                 { $flag = "ENABLE_PROMPT_HOOKS_$suffix" }
    if ((Get-EnvOr $flag).ToLower() -eq 'false') { return $false }
    return $true
}
if (-not (Test-HooksEnabled $kind)) {
    Log-Info "Disabled by ENABLE_* flag for $Connector/$kind; allowing"
    exit 0
}

$sessionKey = ''
if ($Connector -eq 'cursor') {
    if ($input_.PSObject.Properties['conversation_id']) { $sessionKey = [string]$input_.conversation_id }
} elseif ($input_.PSObject.Properties['session_id']) {
    $sessionKey = [string]$input_.session_id
}
if (-not $sessionKey) { $sessionKey = '_latest' }

$statePath = Join-Path $LogDir 'akto_session_state.json'
$msgId = ''
if ($kind -eq 'prompt') {
    if ($input_.PSObject.Properties['generation_id']) { $msgId = [string]$input_.generation_id }
    if (-not $msgId) { $msgId = "${sessionKey}:$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())" }
    try {
        (@{ $sessionKey = @{ current_message_id = $msgId } } | ConvertTo-Json -Depth 5 -Compress) |
            Set-Content -LiteralPath $statePath
    } catch {}
} elseif (Test-Path -LiteralPath $statePath) {
    try { $msgId = [string]((Get-Content -Raw -LiteralPath $statePath | ConvertFrom-Json).$sessionKey.current_message_id) } catch {}
}

function New-Headers {
    param([string]$HostName)
    $h = [ordered]@{ host = $HostName }
    $h[$HookHeader] = $EventName
    $h['content-type'] = 'application/json'
    if ($sessionKey) { $h['x-akto-installer-akto_session_id'] = $sessionKey }
    if ($msgId)      { $h['x-akto-installer-akto_message_id'] = $msgId }
    return $h
}

function Invoke-Blocking {
    param(
        [string]$EmitKind, [string]$Label, [string]$StateName, [string]$MirrorPath,
        $ReqPayload, $Tags, [bool]$IsMcp, [string]$HostName, $RespPayload = @{}
    )
    # MCP traffic carries the bare number 0 here, non-MCP the device label —
    # matching akto_vxlan_id in the Python hooks.
    $vxlan = $DeviceLabel
    if ($IsMcp) { $vxlan = 0 }
    $respHeaders = [ordered]@{}
    $respHeaders[$HookHeader] = $EventName

    $payload = New-Payload $MirrorPath (New-Headers $HostName) $respHeaders $ReqPayload $RespPayload $Tags '200' $vxlan

    if ($SyncMode -ne 'true') {
        # Not blocking: have the backend scan the response asynchronously instead.
        Send-Ingest $payload $EventName ($EmitKind -eq 'response')
        return 0
    }

    $gr = Invoke-Guardrails $payload $EventName
    if ($gr.Allowed) {
        if ($gr.Modified -and $gr.ModifiedPayload -and $EmitKind -eq 'tool' -and
            $Connector -in 'claude_code_cli', 'codex_cli') {
            $newInput = $null
            try {
                $mp = $gr.ModifiedPayload
                if ($mp -is [string]) { $mp = $mp | ConvertFrom-Json }
                if ($IsMcp) { $newInput = $mp.params.arguments } else { $newInput = $mp.body }
            } catch {}
            if ($newInput) {
                Log-Info 'Applying guardrail-modified tool input'
                $mo = @{ hookSpecificOutput = [ordered]@{
                    hookEventName            = 'PreToolUse'
                    permissionDecision       = 'allow'
                    permissionDecisionReason = 'Tool request allowed (Akto guardrails)'
                    updatedInput             = $newInput } }
                [Console]::Out.WriteLine(($mo | ConvertTo-Json -Depth $JSON_DEPTH -Compress))
                return 0
            }
        }
        Log-Info "$Label allowed"
        return 0
    }

    $fp = Get-Fingerprint ($ReqPayload | ConvertTo-Json -Depth $JSON_DEPTH -Compress)
    if (Test-WarnFlow $StateName $fp $gr.Behaviour) {
        Log-Info "$Label allowed after warn/alert flow"
        return 0
    }

    Log-Warn "BLOCKING ${Label}: $($gr.Reason)"
    if ($gr.Behaviour.ToLower() -eq 'warn') {
        $reason = "Warning!!, $Label blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)"
    } else {
        $reason = "$Label blocked: $($gr.Reason)"
    }

    # Non-MCP tool blocks are not mirrored unless AKTO_INGEST_NON_MCP_TOOLS=true,
    # matching ingest_blocked_request() in the Python hooks.
    $ingestNonMcp = (Get-EnvOr 'AKTO_INGEST_NON_MCP_TOOLS' 'false').ToLower()
    if ($EmitKind -eq 'tool' -and -not $IsMcp -and $ingestNonMcp -ne 'true') {
        Log-Info 'Skipping non-MCP blocked-request ingestion (set AKTO_INGEST_NON_MCP_TOOLS=true to re-enable)'
        return (Write-Deny $EmitKind $reason)
    }

    $blockedRespHeaders = [ordered]@{}
    $blockedRespHeaders[$HookHeader] = $EventName
    $blockedRespHeaders['x-blocked-by'] = 'Akto Proxy'
    Send-Ingest (New-Payload $MirrorPath (New-Headers $HostName) $blockedRespHeaders $ReqPayload `
        @{ body = @{ 'x-blocked-by' = 'Akto Proxy'; reason = $gr.Reason } } $Tags '403' $vxlan) $EventName

    return (Write-Deny $EmitKind $reason)
}

$rc = 0
switch ($kind) {
    'prompt' {
        $p = $null
        if ($input_.PSObject.Properties['prompt']) { $p = $input_.prompt }
        if ([string]::IsNullOrEmpty([string]$p)) { Log-Info 'Empty prompt, allowing'; exit 0 }
        $rc = Invoke-Blocking 'prompt' 'Prompt' 'prompt' '/v1/messages' @{ body = $p } (New-Tags $false) $false (Get-AtlasHost)
    }
    'tool' {
        $toolName = ''
        if ($input_.PSObject.Properties['tool_name']) { $toolName = [string]$input_.tool_name }
        $toolInput = @{}
        if ($input_.PSObject.Properties['tool_input'] -and $input_.tool_input) { $toolInput = $input_.tool_input }
        $mcp = Get-McpParts $toolName
        if ($Connector -eq 'cursor' -and $EventName -eq 'beforeMCPExecution') {
            $mcp.IsMcp = $true
            if ($input_.PSObject.Properties['server_name']) { $mcp.Server = [string]$input_.server_name }
            $mcp.Tool = $toolName
        }
        if ($mcp.IsMcp) {
            $hostName = "$DeviceLabel.$ConnectorValue.$($mcp.Server)"
            $req = [ordered]@{ jsonrpc = '2.0'; method = 'tools/call'
                params = [ordered]@{ name = $mcp.Tool; arguments = $toolInput }; id = 1 }
            $rc = Invoke-Blocking 'tool' 'Tool request' 'pretool' $McpIngestPath $req (New-Tags $true) $true $hostName
        } else {
            $req = [ordered]@{ body = $toolInput; toolName = $toolName }
            $rc = Invoke-Blocking 'tool' 'Tool request' 'pretool' (Get-NonMcpPath $toolName) $req (New-Tags $false) $false (Get-AtlasHost)
        }
    }
    'tool_result' {
        $toolName = ''
        if ($input_.PSObject.Properties['tool_name']) { $toolName = [string]$input_.tool_name }
        $toolResp = @{}
        if ($input_.PSObject.Properties['tool_response'] -and $input_.tool_response) { $toolResp = $input_.tool_response }
        elseif ($input_.PSObject.Properties['tool_result'] -and $input_.tool_result) { $toolResp = $input_.tool_result }
        $mcp = Get-McpParts $toolName
        if ($Connector -eq 'cursor' -and $EventName -eq 'afterMCPExecution') {
            $mcp.IsMcp = $true
            if ($input_.PSObject.Properties['server_name']) { $mcp.Server = [string]$input_.server_name }
            $mcp.Tool = $toolName
        }
        if ($mcp.IsMcp) {
            $hostName = "$DeviceLabel.$ConnectorValue.$($mcp.Server)"
            $req = [ordered]@{ jsonrpc = '2.0'; method = 'tools/call'
                params = [ordered]@{ name = $mcp.Tool; arguments = @{} }; id = 1 }
            $rc = Invoke-Blocking 'tool' 'Tool response' 'posttool' $McpIngestPath $req (New-Tags $true) $true $hostName
        } else {
            $req = [ordered]@{ body = $toolResp; toolName = $toolName }
            $rc = Invoke-Blocking 'tool' 'Tool response' 'posttool' (Get-NonMcpPath $toolName) $req (New-Tags $false) $false (Get-AtlasHost)
        }
    }
    'response' {
        if ($input_.PSObject.Properties['stop_hook_active'] -and $input_.stop_hook_active) {
            Log-Info 'stop_hook_active=true: skipping guardrails to avoid a Stop loop'
            exit 0
        }
        $r = $null
        if ($input_.PSObject.Properties['last_assistant_message']) { $r = $input_.last_assistant_message }
        if (-not $r -and $input_.PSObject.Properties['response']) { $r = $input_.response }
        if ([string]::IsNullOrEmpty([string]$r)) { Log-Info 'No assistant response on this event, allowing'; exit 0 }
        # The prompt half of the pair is not on the event; read it back from the
        # transcript, as the Python Stop hook did.
        $prompt = ''
        if ($input_.PSObject.Properties['transcript_path']) {
            $prompt = Get-LastUserPrompt ([string]$input_.transcript_path)
        }
        $rc = Invoke-Blocking 'response' 'Response' 'response' '/v1/messages' `
            @{ body = $prompt } (New-Tags $false) $false (Get-AtlasHost) @{ body = $r }
    }
    default {
        $respHeaders = [ordered]@{}
        $respHeaders[$HookHeader] = $EventName
        Send-Ingest (New-Payload "/v1/hooks/$EventName" (New-Headers (Get-AtlasHost)) $respHeaders `
            @{ body = $input_ } @{} (New-Tags $false) '200' $DeviceLabel) $EventName
        Log-Info "=== $Connector/$EventName completed (observe) ==="
        exit 0
    }
}

exit $rc
