# Fast + Full Content + Single JSON Output

$ErrorActionPreference = 'SilentlyContinue'

function Write-Log {
    param([string]$Message)
    [Console]::Error.WriteLine("[SKILL-SCAN] $Message")
}

$scriptStart = Get-Date
Write-Log "Script started on $env:COMPUTERNAME"

$results = [ordered]@{
    scan_time    = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    hostname     = $env:COMPUTERNAME
    os           = "Windows"
    user         = $env:USERNAME
    skills_found = New-Object System.Collections.ArrayList
}

$skillPatterns = @('SKILL.md','skill.md','skills.md','SKILLS.MD','PROMPT.md','prompt.md')

$skipDirs = [System.Collections.Generic.HashSet[string]]@(
    'node_modules','.git','.svn','.hg','dist','build','cache','.cache',
    'logs','tmp','.venv','.next','target','vendor','__pycache__',
    '.pytest_cache','coverage'
)

function Add-Skill {
    param([string]$Path, [string]$Agent)

    $item = Get-Item -Path $Path -Force
    if (-not $item) { return }

    $skillName = (Split-Path $Path -Parent | Split-Path -Leaf).ToLower() -replace '[^a-z0-9-]', '-'

    try {
        $raw = [System.IO.File]::ReadAllText($Path, [System.Text.Encoding]::UTF8)
        # Remove control characters (including Ctrl+Z / SUB 0x1A) that break JSON serialization
        $content = -join ($raw.ToCharArray() | Where-Object { [int]$_ -ge 0x20 -or $_ -eq "`t" -or $_ -eq "`n" -or $_ -eq "`r" })
    } catch {
        $content = ""
    }

    $obj = [ordered]@{
        path          = $Path
        agent         = $Agent
        size          = $item.Length
        modified      = [DateTimeOffset]::new($item.LastWriteTimeUtc, [TimeSpan]::Zero).ToUnixTimeSeconds()
        skill_name    = $skillName
        skill_content = $content
    }

    [void]$results.skills_found.Add($obj)
}

function Scan-AgentDir {
    param([string]$BasePath, [string]$AgentName)

    if (-not (Test-Path $BasePath)) { return }

    Write-Log "Scanning $BasePath"

    Get-ChildItem -Path $BasePath -Recurse -File -Depth 4 |
        Where-Object { $skillPatterns -contains $_.Name } |
        ForEach-Object {
            $skip = $false
            foreach ($part in ($_.FullName -split '\\')) {
                if ($skipDirs.Contains($part)) { $skip = $true; break }
            }
            if (-not $skip) {
                Add-Skill -Path $_.FullName -Agent $AgentName
            }
        }
}

# Enumerate users
$userProfiles = Get-ChildItem "C:\Users" -Directory -Force |
    Where-Object { $_.Name -notin @('Public','Default','Default User','All Users') } |
    Select-Object -ExpandProperty FullName

Write-Log "Found $($userProfiles.Count) user profile(s)"

foreach ($userProfile in $userProfiles) {
    Write-Log "Scanning profile: $userProfile"

    Scan-AgentDir "$userProfile\.cursor" "cursor"
    Scan-AgentDir "$userProfile\.claude" "claude"
    Scan-AgentDir "$userProfile\.codeium\windsurf" "windsurf"
    Scan-AgentDir "$userProfile\.antigravity" "antigravity"
    Scan-AgentDir "$userProfile\.gemini\antigravity" "antigravity"
    Scan-AgentDir "$userProfile\.copilot" "copilot"
    Scan-AgentDir "$userProfile\.vscode" "vscode"
}

Write-Log "Scan complete. Found $($results.skills_found.Count) skill file(s)"

# ---- FAST JSON SERIALIZATION ----
$start = Get-Date

# Convert ArrayList items (ordered hashtables) to plain PSObjects for ConvertTo-Json
# Wrap in @() to ensure array even when empty (avoids null in JSON output)
$plainSkills = @($results.skills_found | ForEach-Object { [PSCustomObject]$_ })

$output = [PSCustomObject]@{
    scan_time    = $results.scan_time
    hostname     = $results.hostname
    os           = $results.os
    user         = $results.user
    skills_found = $plainSkills
}

$json = $output | ConvertTo-Json -Depth 5 -Compress

Write-Log "JSON serialization took $((Get-Date)-$start)"

# ---- OUTPUT ONLY JSON TO STDOUT ----
Write-Output $json

Write-Log "Total runtime: $((Get-Date)-$scriptStart)"