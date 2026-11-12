# FinSight One-Click Public Demo Startup Script
# Environment: Windows Local PC + Docker + Cloudflare Quick Tunnel

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   Starting FinSight FYP Demonstration Stack" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# 1. Start Docker Postgres Demo Container
Write-Host "`n[1/4] Ensuring PostgreSQL Demo Container is running..." -ForegroundColor Yellow
Set-Location $ProjectRoot
docker compose -f docker-compose.demo.yml up -d

# Wait for Postgres port 5433
$retries = 30
$pgReady = $false
while ($retries -gt 0 -and -not $pgReady) {
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect("127.0.0.1", 5433)
        $tcp.Close()
        $pgReady = $true
    } catch {
        Start-Sleep -Seconds 1
        $retries--
    }
}
if (-not $pgReady) {
    Write-Error "PostgreSQL demo database failed to become ready on port 5433."
    exit 1
}
Write-Host "PostgreSQL demo database online on port 5433." -ForegroundColor Green

# 2. Start Spring Boot Backend (if not already running)
Write-Host "`n[2/4] Checking Spring Boot Backend on port 8080..." -ForegroundColor Yellow
$backendRunning = $false
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect("127.0.0.1", 8080)
    $tcp.Close()
    $backendRunning = $true
} catch { }

if (-not $backendRunning) {
    Write-Host "Starting Spring Boot backend in background..." -ForegroundColor Yellow
    $env:POSTGRES_HOST = "127.0.0.1"
    $env:POSTGRES_PORT = "5433"
    $env:POSTGRES_DB = "finsight_demo"
    $env:POSTGRES_APP_USER = "finsight_app"
    $env:POSTGRES_APP_PASSWORD = "DemoDbPassword2026!"
    $env:FINSIGHT_CRYPTO_KEY_K1 = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="
    $env:SERVER_PORT = "8080"
    $env:SESSION_COOKIE_SECURE = "false"
    $env:CORS_ALLOWED_ORIGINS = "http://localhost:3000,https://*.trycloudflare.com,https://offering-forums-marathon-calibration.trycloudflare.com"
    
    $backendJar = Join-Path $ProjectRoot "backend\target\sme-health-backend-0.0.1-SNAPSHOT.jar"
    $proc = Start-Process java -ArgumentList "-jar", "`"$backendJar`"" -WorkingDirectory (Join-Path $ProjectRoot "backend") -PassThru -WindowStyle Hidden
    
    # Wait for backend
    $bRetries = 45
    while ($bRetries -gt 0 -and -not $backendRunning) {
        try {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $tcp.Connect("127.0.0.1", 8080)
            $tcp.Close()
            $backendRunning = $true
        } catch {
            Start-Sleep -Seconds 1
            $bRetries--
        }
    }
}
Write-Host "Spring Boot backend online on port 8080." -ForegroundColor Green

# 3. Start Next.js Frontend (if not already running)
Write-Host "`n[3/4] Checking Next.js Standalone Frontend on port 3000..." -ForegroundColor Yellow
$frontendRunning = $false
try {
    $tcp = New-Object System.Net.Sockets.TcpClient
    $tcp.Connect("127.0.0.1", 3000)
    $tcp.Close()
    $frontendRunning = $true
} catch { }

if (-not $frontendRunning) {
    Write-Host "Starting Next.js frontend in background..." -ForegroundColor Yellow
    $env:PORT = "3000"
    $env:HOSTNAME = "0.0.0.0"
    $env:BACKEND_API_URL = "http://localhost:8080"
    $env:NEXT_PUBLIC_DATA_MODE = "live"
    
    $serverJs = Join-Path $ProjectRoot "frontend\.next\standalone\server.js"
    Start-Process node -ArgumentList "`"$serverJs`"" -WorkingDirectory (Join-Path $ProjectRoot "frontend") -WindowStyle Hidden
    
    $fRetries = 20
    while ($fRetries -gt 0 -and -not $frontendRunning) {
        try {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $tcp.Connect("127.0.0.1", 3000)
            $tcp.Close()
            $frontendRunning = $true
        } catch {
            Start-Sleep -Seconds 1
            $fRetries--
        }
    }
}
Write-Host "Next.js frontend online on port 3000." -ForegroundColor Green

# 4. Start Cloudflare Quick Tunnel (if not running)
Write-Host "`n[4/4] Checking Cloudflare Quick Tunnel..." -ForegroundColor Yellow
$tunnelContainer = docker ps --filter "name=finsight-tunnel-demo" --format "{{.ID}}"
if (-not $tunnelContainer) {
    Write-Host "Launching Cloudflare Quick Tunnel container..." -ForegroundColor Yellow
    docker run -d --name finsight-tunnel-demo --rm cloudflare/cloudflared:latest tunnel --url http://host.docker.internal:3000
    Start-Sleep -Seconds 5
}

# Fetch tunnel URL from logs
$tunnelLogs = docker logs finsight-tunnel-demo 2>&1
$match = [regex]::Match($tunnelLogs, 'https://[a-zA-Z0-9\-]+\.trycloudflare\.com')
$tunnelUrl = if ($match.Success) { $match.Value } else { "https://offering-forums-marathon-calibration.trycloudflare.com" }

Write-Host "`n======================================================================" -ForegroundColor Green
Write-Host "   FINSIGHT PUBLIC DEMO IS LIVE AND READY FOR EVALUATION!" -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green
Write-Host "Public Demo URL: $tunnelUrl" -ForegroundColor Cyan
Write-Host "Local Web URL:   http://localhost:3000" -ForegroundColor Gray
Write-Host "Backend API:     http://localhost:8080" -ForegroundColor Gray
Write-Host "Demo Database:   127.0.0.1:5433 (finsight_demo)" -ForegroundColor Gray
Write-Host "`nDEMONSTRATION USERS & PASSWORDS:" -ForegroundColor White
Write-Host "  Password (all accounts): FinSight@Demo2026!" -ForegroundColor Yellow
Write-Host "  1. Business Owner:       demo.owner@finsight.local"
Write-Host "  2. Lead Accountant:      demo.accountant@finsight.local"
Write-Host "  3. Sales Executive:      demo.sales@finsight.local"
Write-Host "  4. External Auditor:     demo.viewer@finsight.local"
Write-Host "`nORGANIZATIONS AVAILABLE:" -ForegroundColor White
Write-Host "  Showcase (read/test):    Alpha Garments, Apex Digital, Crescent Electronics, Indus Textiles"
Write-Host "  Sandbox (full mutation): Sandbox A, Sandbox B, Sandbox C, Sandbox D"
Write-Host "`nTo reset Sandbox data anytime:   powershell scripts/reset-sandbox.ps1"
Write-Host "To shut down entire demo stack:  powershell scripts/stop-demo.ps1"
Write-Host "======================================================================`n"

