#!/usr/bin/env pwsh
# akto-validate-prompt.ps1 - PowerShell port of akto-validate-prompt.py (UserPromptSubmit)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "validate-prompt.log"

$logDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".claude/akto/logs" }
$WarnState = Join-Path $logDir "akto_prompt_warn_pending.json"

$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$ConnectorValue = if ($env:AKTO_CONNECTOR_VALUE) { $env:AKTO_CONNECTOR_VALUE } else { "claudecli" }
if ($Mode -eq "atlas") {
    $DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
    $ClaudeApiUrl = if ($DeviceId) { "https://$DeviceId.ai-agent.$ConnectorValue" } else { "https://api.anthropic.com" }
} else {
    $ClaudeApiUrl = if ($env:CLAUDE_API_URL) { $env:CLAUDE_API_URL } else { "https://api.anthropic.com" }
    $DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
}

function Build-ValidationRequest {
    param([string]$Prompt, [hashtable]$Session)
    $host_ = $ClaudeApiUrl -replace '^https?://',''
    $tags = [ordered]@{ "gen-ai" = "Gen AI" }
    if ($Mode -eq "atlas") { $tags["ai-agent"] = $ConnectorValue; $tags["source"] = (Get-AktoContextSource) }

    $reqHdr = [ordered]@{ host = $host_; "x-claude-hook" = "UserPromptSubmit"; "content-type" = "application/json" }
    foreach ($k in $Session.Keys) { if ($null -ne $Session[$k]) { $reqHdr["x-akto-installer-$k"] = [string]$Session[$k] } }

    [ordered]@{
        path            = "/v1/messages"
        requestHeaders  = (ConvertTo-AktoJson $reqHdr)
        responseHeaders = (ConvertTo-AktoJson ([ordered]@{ "x-claude-hook" = "UserPromptSubmit" }))
        method          = "POST"
        requestPayload  = (ConvertTo-AktoJson ([ordered]@{ body = $Prompt.Trim() }))
        responsePayload = "{}"
        ip              = (Get-AktoUsername)
        destIp          = "127.0.0.1"
        time            = ([string]([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()))
        statusCode      = "200"; type = "HTTP/1.1"; status = "200"
        akto_account_id = "1000000"
        akto_vxlan_id   = $DeviceId
        is_pending      = "false"; source = "MIRRORING"
        direction = $null; process_id = $null; socket_id = $null; daemonset_id = $null; enabled_graph = $null
        tag             = (ConvertTo-AktoJson $tags)
        metadata        = (ConvertTo-AktoJson $tags)
        contextSource   = (Get-AktoContextSource)
    }
}

# --- read stdin ---
$raw = [Console]::In.ReadToEnd()
try { $input_ = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; exit 0 }

$prompt = [string]$input_.prompt
$session = @{}
foreach ($f in "session_id","transcript_path","cwd","permission_mode","hook_event_name") {
    if ($null -ne $input_.$f) { $session[$f] = $input_.$f }
}

if ([string]::IsNullOrWhiteSpace($prompt)) { Write-AktoInfo "Empty prompt, allowing"; exit 0 }
Write-AktoInfo "Processing prompt (length: $($prompt.Length) chars)"

if (Test-AktoSync) {
    if (-not (Get-AktoIngestUrl)) { Write-AktoWarn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; exit 0 }
    $body = ConvertTo-AktoJson (Build-ValidationRequest -Prompt $prompt -Session $session)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -Guardrails -IngestData) -Body $body
    $gr = Get-AktoGuardrailsResult $resp

    $fp = Get-AktoSha256Hex ([ordered]@{ a = @(); p = $prompt } | ConvertTo-Json -Compress)
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState

    if (-not $flow.Allowed) {
        if (Test-WarnBehaviour $gr.Behaviour) {
            $reason = "Warning!!, prompt blocked, please review it. Send again to bypass. Reason for blocking: $($gr.Reason)"
        } else { $reason = "Prompt blocked: $($gr.Reason)" }
        Write-AktoWarn "BLOCKING prompt - Reason: $($gr.Reason)"
        (@{ decision = "block"; reason = $reason } | ConvertTo-Json -Compress)
        exit 0
    }
}
Write-AktoInfo "Prompt allowed"
exit 0
