# FinSight Demo Database Backup Script
param (
    [string]$OutputFile = "backups/finsight_demo_$(Get-Date -Format 'yyyyMMdd_HHmmss').dump"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir

Set-Location $rootDir
# Only the verified init-demo.ps1 container is read, addressed by its full ID; a container is never used because of its name.
Import-Module (Join-Path $scriptDir 'demo-guards.psm1') -Force
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { [Console]::Error.WriteLine("REFUSED: Docker is required."); exit 4 }
$ownership = Test-DemoContainerOwnership (Read-DemoEnvironment (Join-Path $rootDir '.demo-runtime\environment.json')) $rootDir
if (-not $ownership.Owned) {
    [Console]::Error.WriteLine("REFUSED: the demo database container is not verified ($($ownership.Reason)); nothing was read or changed.")
    exit 4
}
$dbId = $ownership.ContainerId
if (-not (Test-Path "backups")) {
    New-Item -ItemType Directory -Path "backups" | Out-Null
}

Write-Host "Backing up finsight_demo database to $OutputFile..." -ForegroundColor Cyan
docker exec $dbId pg_dump -U postgres -d finsight_demo -F c -b -f /tmp/demo_backup.dump
docker cp "${dbId}:/tmp/demo_backup.dump" $OutputFile
docker exec $dbId rm -f /tmp/demo_backup.dump

if (Test-Path $OutputFile) {
    $size = (Get-Item $OutputFile).Length
    Write-Host "Backup completed successfully! Size: $([math]::Round($size/1024, 2)) KB" -ForegroundColor Green
} else {
    Write-Host "Backup failed!" -ForegroundColor Red
    exit 1
}

