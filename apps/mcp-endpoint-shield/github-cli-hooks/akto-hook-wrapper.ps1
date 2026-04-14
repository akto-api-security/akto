# Generic Akto VSCode Copilot hook wrapper
# Usage: powershell -ExecutionPolicy Bypass -File .github/hooks/akto-hook-wrapper.ps1 akto-hooks.py <hookName>

$env:MODE = "atlas"
$env:AKTO_DATA_INGESTION_URL = "{{AKTO_DATA_INGESTION_URL}}"
$env:AKTO_SYNC_MODE = "true"
$env:AKTO_TIMEOUT = "5"
$env:AKTO_CONNECTOR = "vscode"
$env:CONTEXT_SOURCE = "ENDPOINT"
$env:AKTO_API_TOKEN = "{{AKTO_API_TOKEN}}"

$env:LOG_DIR = "$env:USERPROFILE\akto\.github\akto\vscode\logs"
$env:LOG_LEVEL = "INFO"
$env:LOG_PAYLOADS = "false"

python .github/hooks/$args[0] $args[1..($args.Length - 1)]
