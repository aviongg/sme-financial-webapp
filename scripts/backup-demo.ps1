# FinSight Demo Database Backup Script
param (
    [string]$OutputFile = "backups/finsight_demo_$(Get-Date -Format 'yyyyMMdd_HHmmss').dump"
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir

Set-Location $rootDir
if (-not (Test-Path "backups")) {
    New-Item -ItemType Directory -Path "backups" | Out-Null
}

Write-Host "Backing up finsight_demo database to $OutputFile..." -ForegroundColor Cyan
docker exec finsight-postgres-demo pg_dump -U postgres -d finsight_demo -F c -b -f /tmp/demo_backup.dump
docker cp finsight-postgres-demo:/tmp/demo_backup.dump $OutputFile
docker exec finsight-postgres-demo rm -f /tmp/demo_backup.dump

if (Test-Path $OutputFile) {
    $size = (Get-Item $OutputFile).Length
    Write-Host "Backup completed successfully! Size: $([math]::Round($size/1024, 2)) KB" -ForegroundColor Green
} else {
    Write-Host "Backup failed!" -ForegroundColor Red
    exit 1
}

