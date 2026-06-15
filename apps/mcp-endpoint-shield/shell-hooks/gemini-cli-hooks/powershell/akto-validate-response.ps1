#!/usr/bin/env pwsh
# akto-validate-response.ps1 - PowerShell port of akto-validate-response.py (AfterModel)
# Chunk-accumulate per session; ingest + validate on finishReason.
[CmdletBinding()] param()
$ErrorActionPreference = "Stop"
Import-Module (Join-Path $PSScriptRoot "AktoCommon.psm1") -Force
Set-AktoLogFile "akto-validate-response.log"

$logDir = Get-AktoLogDir
$WarnState = Join-Path $logDir "akto_response_warn_pending.json"
$Mode = $(if ($env:MODE) { $env:MODE } else { "argus" }).ToLower()
$DeviceId = if ($env:DEVICE_ID) { $env:DEVICE_ID } else { Get-AktoMachineId }

if ($Mode -eq "atlas") {
    $GeminiApiUrl = if ($DeviceId) { "https://$DeviceId.ai-agent.geminicli" } else { $(if ($env:GEMINI_API_URL) { $env:GEMINI_API_URL } else { "https://generativelanguage.googleapis.com" }) }
} else {
    $GeminiApiUrl = if ($env:GEMINI_API_URL) { $env:GEMINI_API_URL } else { "https://generativelanguage.googleapis.com" }
}

# ---- chunk storage (mirrors AKTO_CHUNKS_DIR in python) ----------------------
$ChunksDir = Join-Path ($(if ($env:TMPDIR) { $env:TMPDIR } elseif ($env:TEMP) { $env:TEMP } else { "/tmp" }).TrimEnd('/\')) "akto_gemini_cli_chunks"

function Get-ChunkPath { param([string]$SessionId) Join-Path $ChunksDir "$SessionId.txt" }

function Add-Chunk {
    param([string]$SessionId, [string]$Text)
    if ([string]::IsNullOrEmpty($Text)) { return }
    New-Item -ItemType Directory -Force -Path $ChunksDir -ErrorAction SilentlyContinue | Out-Null
    try { [System.IO.File]::AppendAllText((Get-ChunkPath $SessionId), $Text, [System.Text.Encoding]::UTF8) } catch {}
}

function Read-AccumulatedAndClear {
    param([string]$SessionId)
    $p = Get-ChunkPath $SessionId
    $text = ""
    if (Test-Path $p) {
        try { $text = [System.IO.File]::ReadAllText($p, [System.Text.Encoding]::UTF8) } catch {}
        Remove-Item $p -ErrorAction SilentlyContinue
    }
    return $text
}

# ---- chunk text extraction ---------------------------------------------------
function Get-ChunkText {
    param($LlmResponse)
    if ($null -eq $LlmResponse) { return "" }
    $candidates = $LlmResponse.candidates
    if ($null -eq $candidates -or $candidates.Count -eq 0) { return "" }
    $first = @($candidates)[0]
    if ($null -eq $first) { return "" }
    $parts = $first.content.parts
    if ($null -eq $parts) { return "" }
    $sb = [System.Text.StringBuilder]::new()
    foreach ($p in @($parts)) {
        if ($null -eq $p) { continue }
        $thought = $p.thought
        if ($null -ne $thought -and [string]$thought -eq "True") { continue }
        if ($null -ne $p.text) { [void]$sb.Append([string]$p.text) }
    }
    return $sb.ToString()
}

# ---- thinking-block stripper (matches THINKING_BLOCK_PATTERN in python) ------
function Remove-ThinkingBlocks {
    param([string]$Text)
    if ([string]::IsNullOrWhiteSpace($Text)) { return $Text }
    $paragraphs = $Text -split '\r?\n\s*\r?\n+'
    $started = $false
    $keep = New-Object System.Collections.Generic.List[string]
    foreach ($para in $paragraphs) {
        $trimmed = $para.Trim()
        if (-not $started -and ($trimmed -match '^\*\*[^*]+\*\*\s*$' -or $trimmed -match '^\*\*[^*]+\*\*\s*\n')) {
            continue
        }
        $started = $true
        $keep.Add($para)
    }
    return ($keep -join "`n`n").Trim()
}

# ---- user prompt extraction --------------------------------------------------
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
                $parts = foreach ($p in $content) { if ($null -ne $p.text) { [string]$p.text } }
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
    param([string]$UserPrompt, [string]$ResponseText, [string]$Model, $UsageMeta, [hashtable]$Session)
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

    $reqHdr = [ordered]@{ host = $host_; "x-geminicli-hook" = "AfterModel"; "content-type" = "application/json" }
    $respHdr = [ordered]@{ "x-geminicli-hook" = "AfterModel"; "content-type" = "application/json" }

    $respBody = [ordered]@{ result = $ResponseText }
    if ($null -ne $UsageMeta) { $respBody["usageMetadata"] = $UsageMeta }

    [ordered]@{
        path            = "/gemini/chat"
        requestHeaders  = (ConvertTo-AktoJson $reqHdr)
        responseHeaders = (ConvertTo-AktoJson $respHdr)
        method          = "POST"
        requestPayload  = (ConvertTo-AktoJson ([ordered]@{ body = $UserPrompt.Trim() }))
        responsePayload = (ConvertTo-AktoJson ([ordered]@{ body = $respBody }))
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
if ($hookEvent -and $hookEvent -ne "AfterModel") { '{}'; exit 0 }

$sessionId = if ($input_.session_id) { [string]$input_.session_id } else { "default" }

$session = [ordered]@{}
if ($null -ne $input_.session_id)       { $session["session_id"]       = $input_.session_id }
if ($null -ne $input_.transcript_path)  { $session["transcript_path"]  = $input_.transcript_path }
if ($null -ne $input_.cwd)              { $session["cwd"]              = $input_.cwd }
if ($null -ne $input_.hook_event_name)  { $session["hook_event_name"]  = $input_.hook_event_name }
if ($null -ne $input_.timestamp)        { $session["hook_timestamp"]   = $input_.timestamp }

$llmReq = $input_.llm_request
$model = if ($llmReq) { [string]$llmReq.model } else { "" }
$userPrompt = Get-UserPrompt $(if ($llmReq) { $llmReq.messages } else { $null })

$llmResponse = $input_.llm_response
$chunkText = Get-ChunkText $llmResponse
if (-not [string]::IsNullOrEmpty($chunkText)) { Add-Chunk -SessionId $sessionId -Text $chunkText }

# finishReason from candidates[0]
$finishReason = ""
$usageMeta = $null
if ($null -ne $llmResponse) {
    $cands = $llmResponse.candidates
    if ($null -ne $cands -and @($cands).Count -gt 0) {
        $first = @($cands)[0]
        if ($null -ne $first.finishReason) { $finishReason = [string]$first.finishReason }
    }
    $usageMeta = $llmResponse.usageMetadata
}

if (-not [string]::IsNullOrWhiteSpace($userPrompt) -and -not [string]::IsNullOrEmpty($finishReason)) {
    $fullText = Read-AccumulatedAndClear -SessionId $sessionId
    $fullText = Remove-ThinkingBlocks $fullText

    if (Test-AktoSync) {
        if (-not (Get-AktoIngestUrl)) { Write-AktoWarn "AKTO_DATA_INGESTION_URL not set, allowing (fail-open)"; '{}'; exit 0 }
        if (-not [string]::IsNullOrWhiteSpace($fullText)) {
            $body = ConvertTo-AktoJson (Build-ValidationRequest -UserPrompt $userPrompt -ResponseText $fullText -Model $model -UsageMeta $usageMeta -Session $session)
            $resp = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails) -Body $body
            $gr = Get-AktoGuardrailsResult $resp
            $fp = Get-AktoSha256Hex ('{"p": ' + (ConvertTo-Json $userPrompt -Compress) + ', "r": ' + (ConvertTo-Json $fullText -Compress) + '}')
            $flow = Invoke-WarnResubmitFlow -GrAllowed $gr.Allowed -Reason $gr.Reason -Behaviour $gr.Behaviour -Fingerprint $fp -WarnFile $WarnState
            if (-not $flow.Allowed) {
                if (Test-WarnBehaviour $gr.Behaviour) {
                    $reason = "Warning!! Response blocked, please review it. Reason: $(if ($gr.Reason) { $gr.Reason } else { 'Policy violation' })"
                } else {
                    $reason = "Blocked by Akto Guardrails: $(if ($gr.Reason) { $gr.Reason } else { 'Policy violation' })"
                }
                Write-AktoWarn "BLOCKING response - Reason: $($gr.Reason)"
                (@{ decision = "deny"; reason = $reason } | ConvertTo-Json -Compress)
                exit 0
            }
        }
    }

    # normal ingestion
    if ((Get-AktoIngestUrl) -and -not [string]::IsNullOrWhiteSpace($userPrompt)) {
        $body = ConvertTo-AktoJson (Build-ValidationRequest -UserPrompt $userPrompt -ResponseText $fullText -Model $model -UsageMeta $usageMeta -Session $session)
        $rg = if (Test-AktoSync) { $false } else { $true }
        if ($rg) { $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -ResponseGuardrails -IngestData) -Body $body }
        else     { $null = Invoke-AktoPost -Url (Get-AktoProxyUrl -IngestData) -Body $body }
    }
} else {
    Write-AktoInfo "No final response to ingest yet (waiting for finishReason/userPrompt)"
}

'{}'
exit 0
