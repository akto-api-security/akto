# ========================================================================================
# MCP Endpoint Shield - OpenClaw (Clawdbot) Config Updater (Hardened for S1)
# ========================================================================================

param(
    [string]$TargetUserHome = "",
    [string]$OpenAIApiKey = "",
    [string]$AktoGuardrailsUrl = "https://guardrails.akto.io",
    [string]$OriginalProvider = "openai/gpt-4o-mini",
    [string]$ModelApi = "openai",
    [string]$ModelId = "gpt-4o-mini"
)

# ── Parse KEY=VALUE args ──────────────────────────────────────────────────────
foreach ($arg in $args) {
    if ($arg -match '^([^=]+)=(.*)$') {
        $key = $matches[1]; $val = $matches[2]
        switch ($key) {
            "TARGET_USER_HOME"    { $TargetUserHome = $val }
            "OPENAI_API_KEY"      { $OpenAIApiKey = $val }
            "AKTO_GUARDRAILS_URL" { $AktoGuardrailsUrl = $val }
            "ORIGINAL_PROVIDER"   { $OriginalProvider = $val }
            "MODEL_API"           { $ModelApi = $val }
            "MODEL_ID"            { $ModelId = $val }
        }
    }
}

function Write-Log { param([string]$Message) Write-Host "[OpenClaw Config] $Message" -ForegroundColor Green }
function Write-LogError { param([string]$Message) Write-Host "[OpenClaw Config] ERROR: $Message" -ForegroundColor Red }

# ── Helper: Ensure Nested PSObject Properties Exist ───────────────────────────
function Ensure-Property {
    param($Object, $PropertyName)
    if ($null -eq $Object.$PropertyName) {
        $Object | Add-Member -MemberType NoteProperty -Name $PropertyName -Value (New-Object PSObject)
    }
    return $Object.$PropertyName
}

# ── Update openclaw.json Logic ────────────────────────────────────────────────
function Update-Config {
    param([string]$ConfigFile)

    $baseUrl = $AktoGuardrailsUrl.TrimEnd('/')
    $apiKeyValue = if ($OpenAIApiKey) { "$OpenAIApiKey; X-Original-Provider: $OriginalProvider" } else { "`${OPENAI_API_KEY}; X-Original-Provider: $OriginalProvider" }
    $providerKey = "secure-local/$ModelId"

    # 1. Load JSON (No -AsHashtable to keep it PS 5.1 compatible)
    $config = Get-Content $ConfigFile -Raw | ConvertFrom-Json
    if ($null -eq $config) { $config = New-Object PSObject }

    # 2. Build Models Hierarchy
    $models = Ensure-Property $config "models"
    $providers = Ensure-Property $models "providers"

    # 3. Inject secure-local provider
    $secureLocalBlock = @{
        api     = $ModelApi
        apiKey  = $apiKeyValue
        baseUrl = $baseUrl
        models  = @(@{ id = $ModelId; name = $ModelId })
    }
    $providers | Add-Member -MemberType NoteProperty -Name "secure-local" -Value $secureLocalBlock -Force

    # 4. Build Agents Hierarchy
    $agents = Ensure-Property $config "agents"
    $defaults = Ensure-Property $agents "defaults"
    $modelDefaults = Ensure-Property $defaults "model"
    $modelsList = Ensure-Property $defaults "models"

    # 5. Set Primary Model
    $modelDefaults | Add-Member -MemberType NoteProperty -Name "primary" -Value $providerKey -Force
    $modelsList | Add-Member -MemberType NoteProperty -Name $providerKey -Value @{} -Force

    # 6. Convert back to JSON with DEPTH 20 (Prevents truncation)
    $json = $config | ConvertTo-Json -Depth 20
    
    # 7. Write UTF-8 (No BOM)
    [System.IO.File]::WriteAllText($ConfigFile, $json, [System.Text.Encoding]::UTF8)
    Write-Log "Successfully updated $ConfigFile"
}

# ── Write .env file ───────────────────────────────────────────────────────────
function Write-Env {
    param([string]$EnvFile)
    $content = @"
AKTO_PROXY_URL=$AktoGuardrailsUrl
OPENAI_API_KEY=$OpenAIApiKey
ORIGINAL_PROVIDER=$OriginalProvider
MODEL_API=$ModelApi
MODEL_ID=$ModelId
"@
    [System.IO.File]::WriteAllText($EnvFile, $content, [System.Text.Encoding]::UTF8)
}

# ── Per-user installation ─────────────────────────────────────────────────────
function Install-ForUser {
    param([string]$UserHome)

    if (-not (Test-Path (Join-Path $UserHome "AppData"))) { return }

    # Detect Config Locations
    $openclawDir = Join-Path $UserHome ".openclaw"
    $configFile = Join-Path $openclawDir "openclaw.json"
    
    # Fallback to Roaming if .openclaw doesn't exist
    if (-not (Test-Path $configFile)) {
        $openclawDir = Join-Path $UserHome "AppData\Roaming\openclaw"
        $configFile = Join-Path $openclawDir "openclaw.json"
    }

    if (Test-Path $configFile) {
        Write-Log "Processing: $configFile"
        
        # Permissions Fix (SYSTEM -> USER)
        icacls "$openclawDir" /grant "Users:(OI)(CI)F" /T /C /Q | Out-Null
        
        # Backup
        Copy-Item $configFile "$configFile.bak" -Force
        
        # Execute Updates
        Update-Config -ConfigFile $configFile
        Write-Env -EnvFile (Join-Path $openclawDir "akto.env")
    }
}

# ── Main ──────────────────────────────────────────────────────────────────────
if ($TargetUserHome -and (Test-Path $TargetUserHome)) {
    Install-ForUser -UserHome $TargetUserHome
} else {
    $users = Get-ChildItem "C:\Users" -Directory | Where-Object { $_.Name -notin @("Public", "Default", "All Users") }
    foreach ($u in $users) { Install-ForUser -UserHome $u.FullName }
}