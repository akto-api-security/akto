# Skill File Discovery Script for SentinelOne RemoteOps (Windows PowerShell)
# Mirrors: mcp-endpoint-shield/mcp/skill_detector.go
#
# What this approximates:
#   - Scans hardcoded agent directories (depth 5): %USERPROFILE%\.cursor, %USERPROFILE%\.claude,
#     %USERPROFILE%\.codeium\windsurf, %USERPROFILE%\.antigravity, %USERPROFILE%\.copilot,
#     %USERPROFILE%\.vscode
#   - Scans user profile (depth 6) with skip logic for:
#     * Hardcoded paths (already scanned)
#     * Junk dirs (node_modules, .git, dist, build, cache, etc.)
#   - Finds files with exact basename match (case-insensitive):
#     SKILL.md, skill.md, skills.md, SKILLS.MD, PROMPT.md, prompt.md
#
# Known gaps vs full parity:
#   - Cannot determine agent name from directory context as precisely as Go code
#   - Does not validate skill content or extract metadata
#   - Windows permission issues may prevent access to some directories
#
# Requirements: PowerShell 5.1+
# Optional: None (uses PowerShell builtins only)

$ErrorActionPreference = 'Continue'

# Log to stderr for debugging
function Write-Log {
    param([string]$Message)
    [Console]::Error.WriteLine("[SKILL-SCAN] $Message")
}

Write-Log "Script started"
Write-Log "PowerShell version: $($PSVersionTable.PSVersion)"
Write-Log "Computer: $env:COMPUTERNAME"
Write-Log "User: $env:USERNAME"

try {
    $results = @{
        scan_time = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
        hostname = $env:COMPUTERNAME
        os = "Windows"
        user = $env:USERNAME
        skills_found = @()
    }
    Write-Log "Results object initialized"
} catch {
    Write-Log "ERROR: Failed to initialize results: $_"
    exit 1
}

function Add-Skill {
    param(
        [string]$Path,
        [string]$Agent
    )
    
    if (Test-Path -Path $Path -PathType Leaf) {
        $item = Get-Item -Path $Path -Force
        
        # Extract skill_name from parent directory (e.g. ...\mcp-gateway-dev\SKILL.md -> mcp-gateway-dev)
        $skillName = (Split-Path $Path -Parent | Split-Path -Leaf).ToLower() -replace '[^a-z0-9-]', '-'
        
        # Read file content
        try {
            $skillContent = Get-Content -Path $Path -Raw -Encoding UTF8 -ErrorAction Stop
        } catch {
            $skillContent = ''
        }
        
        $results.skills_found += @{
            path = $Path
            agent = $Agent
            size = $item.Length
            modified = [int][double]::Parse((Get-Date $item.LastWriteTime -UFormat %s))
            skill_name = $skillName
            skill_content = $skillContent
        }
    }
}

function Should-SkipDir {
    param([string]$DirName)
    
    $skipDirs = @(
        'node_modules', '.git', '.svn', '.hg', 'dist', 'build', 'cache', '.cache',
        'logs', 'tmp', '.venv', '.next', 'target', 'vendor', '__pycache__',
        '.pytest_cache', 'coverage'
    )
    
    return $skipDirs -contains $DirName
}

function Scan-AgentDir {
    param(
        [string]$BasePath,
        [string]$AgentName,
        [int]$MaxDepth = 5
    )
    
    if (-not (Test-Path -Path $BasePath -PathType Container)) {
        return
    }
    
    $skillPatterns = @('SKILL.md', 'skill.md', 'skills.md', 'SKILLS.MD', 'PROMPT.md', 'prompt.md')
    
    Get-ChildItem -Path $BasePath -Recurse -Depth $MaxDepth -File -Force -ErrorAction SilentlyContinue | 
        Where-Object { $skillPatterns -contains $_.Name } | 
        ForEach-Object {
            # Check if in junk directory
            $skip = $false
            $pathParts = $_.FullName -split '\\'
            foreach ($part in $pathParts) {
                if (Should-SkipDir -DirName $part) {
                    $skip = $true
                    break
                }
            }
            
            if (-not $skip) {
                Add-Skill -Path $_.FullName -Agent $AgentName
            }
        }
}

# Get all user profile directories
$userProfiles = @()
try {
    if (Test-Path "C:\Users") {
        Write-Log "Scanning C:\Users for user profiles"
        $userProfiles = Get-ChildItem "C:\Users" -Directory -Force -ErrorAction SilentlyContinue | 
            Where-Object { $_.Name -notin @('Public', 'Default', 'Default User', 'All Users') } |
            Select-Object -ExpandProperty FullName
        Write-Log "Found $($userProfiles.Count) user profile(s)"
    } else {
        Write-Log "C:\Users not found"
    }
} catch {
    Write-Log "ERROR: Failed to enumerate user profiles: $_"
}

# Phase 1: Scan hardcoded agent directories (depth 5)
foreach ($userProfile in $userProfiles) {
    try {
        Write-Log "Scanning profile: $userProfile"
        Scan-AgentDir -BasePath "$userProfile\.cursor" -AgentName "cursor"
        Scan-AgentDir -BasePath "$userProfile\.claude" -AgentName "claude"
        Scan-AgentDir -BasePath "$userProfile\.codeium\windsurf" -AgentName "windsurf"
        Scan-AgentDir -BasePath "$userProfile\.antigravity" -AgentName "antigravity"
        Scan-AgentDir -BasePath "$userProfile\.copilot" -AgentName "copilot"
        Scan-AgentDir -BasePath "$userProfile\.vscode" -AgentName "vscode"
        Scan-AgentDir -BasePath "$userProfile\.gemini\antigravity" -AgentName "antigravity"
        Write-Log "Completed scanning profile: $userProfile"
    } catch {
        Write-Log "ERROR: Failed to scan profile $userProfile : $_"
    }
}

# Phase 2: Scan user profile (depth 6) with skip logic
foreach ($userProfile in $userProfiles) {
    if (-not (Test-Path -Path $userProfile -PathType Container)) {
        continue
    }
    
    $hardcodedPaths = @('.cursor', '.claude', '.codeium', '.antigravity', '.copilot', '.vscode', '.gemini')
    $skillPatterns = @('SKILL.md', 'skill.md', 'skills.md', 'SKILLS.MD', 'PROMPT.md', 'prompt.md')
    
    Get-ChildItem -Path $userProfile -Recurse -Depth 6 -File -Force -ErrorAction SilentlyContinue | 
        Where-Object { $skillPatterns -contains $_.Name } | 
        ForEach-Object {
            # Skip if in hardcoded agent path (already scanned)
            $skip = $false
            foreach ($hardcodedPath in $hardcodedPaths) {
                if ($_.FullName -like "$userProfile\$hardcodedPath\*") {
                    $skip = $true
                    break
                }
            }
            
            # Skip if in junk directory
            if (-not $skip) {
                $pathParts = $_.FullName -split '\\'
                foreach ($part in $pathParts) {
                    if (Should-SkipDir -DirName $part) {
                        $skip = $true
                        break
                    }
                }
            }
            
            if (-not $skip) {
                # Determine agent from parent directory name
                $dirName = (Split-Path $_.FullName -Parent | Split-Path -Leaf).ToLower() -replace '[ _]', '-'
                Add-Skill -Path $_.FullName -Agent $dirName
            }
        }
}

# Phase 3: Scan common Docker/container paths (depth 6)
Write-Log "Scanning container paths"
foreach ($containerPath in @("C:\app", "C:\workspace", "C:\opt", "C:\srv")) {
    if (Test-Path -Path $containerPath -PathType Container) {
        Write-Log "Scanning container path: $containerPath"
        try {
            $skillPatterns = @('SKILL.md', 'skill.md', 'skills.md', 'SKILLS.MD', 'PROMPT.md', 'prompt.md')
            
            Get-ChildItem -Path $containerPath -Recurse -Depth 6 -File -Force -ErrorAction SilentlyContinue | 
                Where-Object { $skillPatterns -contains $_.Name } | 
                ForEach-Object {
                    # Skip if in junk directory
                    $skip = $false
                    $pathParts = $_.FullName -split '\\'
                    foreach ($part in $pathParts) {
                        if (Should-SkipDir -DirName $part) {
                            $skip = $true
                            break
                        }
                    }
                    
                    if (-not $skip) {
                        $dirName = (Split-Path $_.FullName -Parent | Split-Path -Leaf).ToLower() -replace '[ _]', '-'
                        Add-Skill -Path $_.FullName -Agent $dirName
                    }
                }
        } catch {
            Write-Log "ERROR: Failed to scan container path $containerPath : $_"
        }
    }
}

# Output JSON - wrap in try-catch to ensure output even on errors
Write-Log "Scan complete. Found $($results.skills_found.Count) skill file(s)"
Write-Log "Outputting JSON to stdout"

try {
    $jsonOutput = $results | ConvertTo-Json -Depth 10 -Compress:$false
    Write-Output $jsonOutput
    Write-Log "JSON output successful, length: $($jsonOutput.Length) chars"
} catch {
    Write-Log "ERROR: Failed to convert to JSON: $_"
    # Output minimal valid JSON on error
    Write-Output '{"scan_time":"","hostname":"","os":"Windows","user":"","skills_found":[]}'
}

Write-Log "Script finished"
