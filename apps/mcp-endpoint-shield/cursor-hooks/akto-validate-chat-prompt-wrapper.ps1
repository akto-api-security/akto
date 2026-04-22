# Auto-generated wrapper for Akto guardrails hook
# Data Ingestion URL: {{AKTO_DATA_INGESTION_URL}}

$env:MODE = "atlas"
$env:AKTO_DATA_INGESTION_URL = "{{AKTO_DATA_INGESTION_URL}}"
$env:AKTO_SYNC_MODE = "true"
$env:AKTO_TIMEOUT = "5"
$env:AKTO_CONNECTOR = "cursor"
$env:CONTEXT_SOURCE = "ENDPOINT"
$env:DEVICE_ID = "{{DEVICE_ID}}"
$env:PYTHONIOENCODING = "utf-8"

Write-Host "[Cursor Hook] Data Ingestion URL: $env:AKTO_DATA_INGESTION_URL" -ForegroundColor Gray
Write-Host "[Cursor Hook] Device ID: $env:DEVICE_ID" -ForegroundColor Gray

# Cursor sends hook data via stdin as JSON, prefixed with framing bytes (e.g. "n++").
# Strip everything before the first '{', write clean JSON to a temp file, then
# pipe it into Python via PowerShell pipeline (avoids cmd.exe quoting issues on Windows).
$stdinContent = [Console]::In.ReadToEnd()
$jsonStart = $stdinContent.IndexOf('{')
$jsonContent = if ($jsonStart -ge 0) { $stdinContent.Substring($jsonStart) } else { '{}' }
$tempFile = [System.IO.Path]::GetTempFileName()
try {
    [System.IO.File]::WriteAllText($tempFile, $jsonContent, (New-Object System.Text.UTF8Encoding($false)))
    Get-Content -Raw $tempFile | & python "$HOME/.cursor/hooks/akto/akto-validate-chat-prompt.py"
} finally {
    Remove-Item $tempFile -ErrorAction SilentlyContinue
}
