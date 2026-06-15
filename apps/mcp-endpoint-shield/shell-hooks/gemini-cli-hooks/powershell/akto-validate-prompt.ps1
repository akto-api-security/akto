#!/usr/bin/env pwsh
# akto-validate-prompt.ps1 - PowerShell port of akto-validate-prompt.py (BeforeModel)
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "akto-validate-prompt.log"

$logDir = Get-AktoLogDir
$WarnState = Join-Path $logDir "akto_prompt_warn_pending.json"

$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }
if ($Mode -eq "atlas") {
    $GeminiApiUrl = if ($DeviceId) { "https://$DeviceId.ai-agent.geminicli" } else { $(if ($env:GEMINI_API_URL) { $env:GEMINI_API_URL } else { "https://generativelanguage.googleapis.com" }) }
} else {
    $GeminiApiUrl = if ($env:GEMINI_API_URL) { $env:GEMINI_API_URL } else { "https://generativelanguage.googleapis.com" }
}

function Get-UserPrompt {
    param($Messages)
    if ($null -eq $Messages) { return "" }
    $arr = @($Messages)
    for ($i = $arr.Count - 1; $i -ge 0; $i--) {
        $m = $arr[$i]
        if ($m.role -eq "user") {
            $content = $m.content
            if ($content -is [string]) { return $content }
            if ($content -is [System.Collections.IEnumerable]) {
                $parts = foreach ($p in $content) { if ($p -is [pscustomobject] -or $p -is [hashtable]) { [string]$p.text } }
                return ($parts -join "")
            }
            return ""
        }
    }
    return ""
}

function Build-SessionMetadata {
    param([hashtable]$Session)
    if (-not $Session -or $Session.Count -eq 0) { return $null }
    $o = [ordered]@{}
    foreach ($k in $Session.Keys) { if ($null -ne $Session[$k]) { $o[$k] = $Session[$k] } }
    if ($o.Count -eq 0) { return $null }
    return $o
}

function Build-ValidationRequest {
    param([string]$Prompt, [string]$Model, [hashtable]$Session)
    $host_ = $GeminiApiUrl -replace '^https?://',''
    $tags = [ordered]@{ "gen-ai" = "Gen AI" }
    if ($Mode -eq "atlas") { $tags["ai-agent"] = "geminicli"; $tags["source"] = "ENDPOINT" }

    $meta = [ordered]@{ model = $(if ($Model) { $Model } else { "" }) }
    if ($Mode -eq "atlas") {
        $meta["machine_id"] = $DeviceId
        $meta["log_storage"] = [ordered]@{ type = "local_file"; path = $logDir }
    }
    $sess = Build-SessionMetadata $Session
    if ($sess) { $meta["gemini_cli_session"] = $sess }

    $reqHdr = [ordered]@{ host = $host_; "x-geminicli-hook" = "BeforeModel"; "content-type" = "application/json" }

    [ordered]@{
        path            = "/gemini/chat"
        requestHeaders  = (ConvertTo-AktoJson $reqHdr)
        responseHeaders = (ConvertTo-AktoJson ([ordered]@{ "x-geminicli-hook" = "BeforeModel" }))
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
        metadata        = (ConvertTo-AktoJson $meta)
        contextSource   = "ENDPOINT"
    }
}

# --- read stdin ---
$raw = [Console]::In.ReadToEnd()
try { $input_ = $raw | ConvertFrom-Json } catch { Write-AktoError "Invalid JSON input"; '{}'; exit 0 }

$hookEvent = [string]$input_.hook_event_name
if ($hookEvent -and $hookEvent -ne "BeforeModel") { '{}'; exit 0 }

$session = @{}
foreach ($f in "session_id","transcript_path","cwd","hook_event_name") {
    if ($null -ne $input_.$f) { $session[$f] = $input_.$f }
}
if ($null -ne $input_.timestamp) { $session["hook_timestamp"] = $input_.timestamp }

$llmReq = $input_.llm_request
$model = if ($llmReq) { [string]$llmReq.model } else { "" }
$prompt = Get-UserPrompt $(if ($llmReq) { $llmReq.messages } else { $null })

if ([string]::IsNullOrWhiteSpace($prompt)) { Write-AktoInfo "Empty prompt, allowing"; '{}'; exit 0 }
Write-AktoInfo "Processing prompt (length: $($prompt.Length) chars)"

if (Test-AktoSync) {
    if (-not (Get-AktoIngestUrl)) { Write-AktoWarn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; '{}'; exit 0 }
    $body = ConvertTo-AktoJson (Build-ValidationRequest -Prompt $prompt -Model $model -Session $session)
    $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -Guardrails) -Body $body
    $gr = Get-AktoGuardrailsResult $resp

    # fingerprint matches python json.dumps({"p":query}, sort_keys=True)
    $fp = Get-AktoSha256Hex ('{"p": ' + (ConvertTo-Json $prompt -Compress) + '}')
    $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState

    if (-not $flow.Allowed) {
        if (Test-WarnBehaviour $gr.Behaviour) {
            $reason = "Warning!! Prompt blocked, please review it. Send the same prompt again to bypass. Reason: $(if ($gr.Reason) { $gr.Reason } else { 'Policy violation' })"
        } else { $reason = "Blocked by Akto Guardrails: $(if ($gr.Reason) { $gr.Reason } else { 'Policy violation' })" }
        Write-AktoWarn "BLOCKING prompt - Reason: $($gr.Reason)"
        (@{ decision = "deny"; reason = $reason } | ConvertTo-Json -Compress)
        exit 0
    }
}
Write-AktoInfo "Prompt allowed"
'{}'
exit 0
