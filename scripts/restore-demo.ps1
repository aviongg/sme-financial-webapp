# FinSight Demo Database Restore Script
param (
    [string]$InputFile
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir

Set-Location $rootDir

if (-not $InputFile) {
    # Pick the latest backup file in backups/
    $latest = Get-ChildItem -Path "backups\*.dump" | Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if (-not $latest) {
        Write-Host "No backup file specified and none found in backups/ directory." -ForegroundColor Red
        exit 1
    }
    $InputFile = $latest.FullName
}

if (-not (Test-Path $InputFile)) {
    Write-Host "Backup file not found: $InputFile" -ForegroundColor Red
    exit 1
}

Write-Host "Restoring finsight_demo database from $InputFile..." -ForegroundColor Cyan

# Terminate active connections and recreate finsight_demo database
docker exec -i finsight-postgres-demo psql -U postgres -d postgres -c "DROP DATABASE IF EXISTS finsight_demo WITH (FORCE);" | Out-Null
docker exec -i finsight-postgres-demo psql -U postgres -d postgres -c "CREATE DATABASE finsight_demo OWNER postgres;" | Out-Null

# Copy dump into container and restore
docker cp $InputFile finsight-postgres-demo:/tmp/demo_restore.dump
docker exec -i finsight-postgres-demo pg_restore -U postgres -d finsight_demo -v /tmp/demo_restore.dump | Out-Null
docker exec -i finsight-postgres-demo rm -f /tmp/demo_restore.dump

Write-Host "Database successfully restored from $InputFile!" -ForegroundColor Green

