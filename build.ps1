# AstrbotAdapter Build Script
# Usage: .\build.ps1 [all|bukkit|folia|velocity]

param(
    [string]$Target = "all"
)

$OutputDir = "releases"
$Version = "2.0.0"

function Build-Platform($Platform) {
    $PlatformCap = (Get-Culture).TextInfo.ToTitleCase($Platform)
    Write-Host "[INFO] Building $PlatformCap..." -ForegroundColor Cyan
    
    mvn package -P $Platform -DskipTests -q
    
    if ($LASTEXITCODE -ne 0) {
        Write-Host "[ERROR] $PlatformCap build failed!" -ForegroundColor Red
        return $false
    }
    
    $JarName = "AstrbotAdaptor-$Version-$PlatformCap.jar"
    $Source = "target\$JarName"
    
    if (Test-Path $Source) {
        Copy-Item $Source -Destination "$OutputDir\" -Force
        $Size = [math]::Round((Get-Item "$OutputDir\$JarName").Length / 1KB, 2)
        Write-Host "[OK] $JarName ($Size KB)" -ForegroundColor Green
        return $true
    } else {
        Write-Host "[ERROR] Build artifact not found: $Source" -ForegroundColor Red
        return $false
    }
}

# Main
Write-Host ""
Write-Host "========================================"
Write-Host "   AstrbotAdapter Multi-Platform Build"
Write-Host "========================================"
Write-Host ""

# Check Maven
if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host "[ERROR] Maven not found. Please install Maven and add to PATH." -ForegroundColor Red
    exit 1
}

# Create output directory
if (-not (Test-Path $OutputDir)) {
    New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
}

$Platforms = switch ($Target.ToLower()) {
    "all"      { @("bukkit", "folia", "velocity") }
    "bukkit"   { @("bukkit") }
    "folia"    { @("folia") }
    "velocity" { @("velocity") }
    default    { 
        Write-Host "[ERROR] Unknown target: $Target" -ForegroundColor Red
        Write-Host "Usage: .\build.ps1 [all|bukkit|folia|velocity]"
        exit 1
    }
}

Write-Host "[INFO] Target platforms: $($Platforms -join ', ')" -ForegroundColor Cyan
Write-Host ""

$Success = 0
foreach ($P in $Platforms) {
    if (Build-Platform $P) { $Success++ }
}

Write-Host ""
Write-Host "========================================"
if ($Success -eq $Platforms.Count) {
    Write-Host "   Build completed: $Success/$($Platforms.Count) succeeded" -ForegroundColor Green
} else {
    Write-Host "   Build completed: $Success/$($Platforms.Count) succeeded" -ForegroundColor Red
}
Write-Host "   Output: $OutputDir"
Write-Host "========================================"
Write-Host ""

Get-ChildItem "$OutputDir\*.jar" | ForEach-Object {
    Write-Host "  - $($_.Name)"
}
