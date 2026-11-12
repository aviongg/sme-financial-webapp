# FinSight Sandbox Reset Script
# Resets only the (Sandbox) organizations to pristine state while preserving Showcase datasets.
$ErrorActionPreference = "Stop"

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$rootDir = Split-Path -Parent $scriptDir

Write-Host "==========================================================" -ForegroundColor Cyan
Write-Host "   FinSight Demo Environment: Reset Sandbox Organizations  " -ForegroundColor Cyan
Write-Host "==========================================================" -ForegroundColor Cyan

Set-Location $rootDir
$cp = Get-Content backend\.cp.txt
java -cp "$cp;backend\target\classes;scripts" DemoScenarioSeeder --reset-sandbox

if ($LASTEXITCODE -eq 0) {
    Write-Host "`nSandbox organizations successfully reset to clean demonstration baseline!" -ForegroundColor Green
} else {
    Write-Host "`nSandbox reset failed with code $LASTEXITCODE" -ForegroundColor Red
    exit 1
}

