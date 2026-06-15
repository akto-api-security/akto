#!/usr/bin/env pwsh
# akto-validate-prompt.ps1 - PowerShell port of codex akto-validate-prompt.py (UserPromptSubmit)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "validate-prompt.log"

$logDir = if ($env:LOG_DIR) { $env:LOG_DIR } else { Join-Path $HOME ".codex/akto/logs" }
$WarnState = Join-Path $logDir "akto_prompt_warn_pending.json"

$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()

function Resolve-CodexApi {
    if ($env:OPENAI_BASE_URL) { return @{ Host=($env:OPENAI_BASE_URL).TrimEnd("/"); Path="/v1/responses" } }
    if ($env:OPENAI_API_KEY)  { return @{ Host="https://api.openai.com"; Path="/v1/responses" } }
    return @{ Host="https://chatgpt.com"; Path="/backend-api/codex/responses" }
}
$api = Resolve-CodexApi
$CodexApiPath = $api.Path
if ($Mode -eq "atlas") {
    $DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
    $CodexApiHost = if ($DeviceId) { "https://$DeviceId.ai-agent.codexcli" } else { $api.Host }
} else {
    $CodexApiHost = $api.Host
}

function Build-ValidationRequest {
    param([string]$Prompt, [hashtable]$Session)
    $host_ = $CodexApiHost -replace '^https?://',''
    $tags = [ordered]@{ "gen-ai" = "Gen AI" }
    if ($Mode -eq "atlas") { $tags["ai-agent"] = "codexcli"; $tags["source"] = (Get-AktoContextSource) }

    $reqHdr = [ordered]@{ host = $host_; "x-codex-hook" = "UserPromptSubmit"; "content-type" = "application/json" }
    foreach ($k in $Session.Keys) { if ($null -ne $Session[$k]) { $reqHdr["x-akto-installer-$k"] = [string]$Session[$k] } }

    [ordered]@{
        path            = $CodexApiPath
        requestHeaders  = (ConvertTo-AktoJson $reqHdr)
        responseHeaders = (ConvertTo-AktoJson ([ordered]@{ "x-codex-hook" = "UserPromptSubmit" }))
        method          = "POST"
        requestPayload  = (ConvertTo-AktoJson ([ordered]@{ body = $Prompt.Trim() }))
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
        contextSource   = (Get-AktoContextSource)
    }
}

# --- read stdin ---
$raw = [Console]::In.ReadToEnd()
try { $input_ = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; exit 0 }

$prompt = [string]$input_.prompt
$session = @{}
foreach ($f in "session_id","transcript_path","cwd","hook_event_name","model","turn_id") {
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
