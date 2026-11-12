# FinSight One-Click Public Demo Shutdown Script

$ErrorActionPreference = "Continue"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   Shutting Down FinSight Demonstration Stack" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# 1. Stop Cloudflare Tunnel Container
Write-Host "`n[1/4] Stopping Cloudflare Quick Tunnel container..." -ForegroundColor Yellow
docker stop finsight-tunnel-demo 2>$null

# 2. Stop Frontend Node Process on Port 3000
Write-Host "`n[2/4] Stopping Next.js Frontend server on port 3000..." -ForegroundColor Yellow
$frontendPort = Get-NetTCPConnection -LocalPort 3000 -ErrorAction SilentlyContinue
if ($frontendPort) {
    $pids = $frontendPort.OwningProcess | Select-Object -Unique
    foreach ($p in $pids) {
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    }
}

# 3. Stop Backend Java Process on Port 8080
Write-Host "`n[3/4] Stopping Spring Boot Backend on port 8080..." -ForegroundColor Yellow
$backendPort = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
if ($backendPort) {
    $pids = $backendPort.OwningProcess | Select-Object -Unique
    foreach ($p in $pids) {
        Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
    }
}

# 4. Stop Postgres Demo Container
Write-Host "`n[4/4] Stopping PostgreSQL Demo database container..." -ForegroundColor Yellow
Set-Location $ProjectRoot
docker compose -f docker-compose.demo.yml stop

Write-Host "`n======================================================================" -ForegroundColor Green
Write-Host "   FINSIGHT DEMONSTRATION STACK SAFELY SHUT DOWN" -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green
Write-Host "Demo data is safely preserved in volume 'finsight_demo_pgdata'." -ForegroundColor Gray
Write-Host "To restart demo at any time: powershell scripts/start-demo.ps1`n" -ForegroundColor Gray

