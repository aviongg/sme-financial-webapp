<#
.SYNOPSIS
    Starts the local, synthetic FinSight demonstration stack.

.DESCRIPTION
    Default (local-only): the demo PostgreSQL container (127.0.0.1:5433, docker-compose.demo.yml), the backend on
    127.0.0.1:8080 and the frontend on 127.0.0.1:3000. Nothing is reachable from other machines, and any Cloudflare
    Quick Tunnel left over from an earlier public run is stopped.

    Public exposure is a deliberate operator decision. It needs BOTH -PublicTunnel and -ConfirmSyntheticPublicDemo,
    and it is refused when:
      * the backend or frontend is already running (this script cannot verify which database or secrets it uses), or
      * the demo database holds accounts or businesses the synthetic seeder did not create (for example a restored
        real backup), unless -AllowNonSeededData is also given after the operator has checked them.
    Live AI and WhatsApp providers are always disabled for the demo backend, in both modes.

.PARAMETER PublicTunnel
    Also publish the frontend through a Cloudflare Quick Tunnel (a public trycloudflare.com URL).

.PARAMETER ConfirmSyntheticPublicDemo
    Required with -PublicTunnel: confirms the demo database contains synthetic demonstration data only.

.PARAMETER AllowNonSeededData
    With -PublicTunnel: accept accounts/businesses not created by scripts/DemoScenarioSeeder.java (e.g. evaluator
    sign-ups) after verifying they contain no real client data.

.PARAMETER PlanOnly
    Validate the options and print the plan and the demo accounts without starting anything.

.EXAMPLE
    powershell scripts/start-demo.ps1
.EXAMPLE
    powershell scripts/start-demo.ps1 -PublicTunnel -ConfirmSyntheticPublicDemo
#>
[CmdletBinding()]
param(
    [switch]$PublicTunnel,
    [switch]$ConfirmSyntheticPublicDemo,
    [switch]$AllowNonSeededData,
    [switch]$PlanOnly
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

# Synthetic local-demo accounts. Must equal DEMO_ACCOUNTS in scripts/DemoScenarioSeeder.java (DemoToolingContractTest).
# They exist only in the seeded demo database and are never production credentials.
$DemoAccounts = @(
    @{ Role = 'OWNER';       Email = 'demo.owner@finsight.local';      Password = 'FinSight@Owner2026!' },
    @{ Role = 'ACCOUNTANT';  Email = 'demo.accountant@finsight.local'; Password = 'FinSight@Account2026!' },
    @{ Role = 'MANAGER';     Email = 'demo.manager@finsight.local';    Password = 'FinSight@Manager2026!' },
    @{ Role = 'SALESPERSON'; Email = 'demo.sales@finsight.local';      Password = 'FinSight@Sales2026!' },
    @{ Role = 'VIEWER';      Email = 'demo.viewer@finsight.local';     Password = 'FinSight@Viewer2026!' }
)

# Businesses created by scripts/DemoScenarioSeeder.java (showcase and sandbox copies).
$SeededBusinessNames = @(
    'Alpha Garments & Packaging Ltd', 'Alpha Garments & Packaging (Sandbox)',
    'Apex Digital Solutions', 'Apex Digital Solutions (Sandbox)',
    'Crescent Electronics Trading', 'Crescent Electronics Trading (Sandbox)',
    'Indus Textiles Export Corp', 'Indus Textiles Export Corp (Sandbox)'
)

$TunnelContainerName = 'finsight-tunnel-demo'
$DbContainerName = 'finsight-postgres-demo'

function Stop-Refused([int]$Code, [string]$Message) {
    [Console]::Error.WriteLine("REFUSED: $Message")
    exit $Code
}

function Test-LocalPort([int]$Port) {
    try {
        $tcp = New-Object System.Net.Sockets.TcpClient
        $tcp.Connect("127.0.0.1", $Port)
        $tcp.Close()
        return $true
    } catch {
        return $false
    }
}

function Wait-LocalPort([int]$Port, [int]$Seconds) {
    for ($i = 0; $i -lt $Seconds; $i++) {
        if (Test-LocalPort $Port) { return $true }
        Start-Sleep -Seconds 1
    }
    return (Test-LocalPort $Port)
}

function Get-TunnelContainer {
    docker ps --filter "name=^$TunnelContainerName$" --format "{{.ID}}"
}

function Get-DemoCount([string]$Sql) {
    $out = docker exec $DbContainerName psql -U postgres -d finsight_demo -v ON_ERROR_STOP=1 -tA -c $Sql 2>$null
    if ($LASTEXITCODE -ne 0) { return $null }
    $value = 0
    if ([int]::TryParse((($out | Out-String).Trim()), [ref]$value)) { return $value }
    return $null
}

function Format-SqlList([string[]]$Values) {
    ($Values | ForEach-Object { "'" + ($_ -replace "'", "''") + "'" }) -join ', '
}

# ---------------------------------------------------------------------------------------------------- option checks
if (-not $PublicTunnel -and ($ConfirmSyntheticPublicDemo -or $AllowNonSeededData)) {
    Stop-Refused 2 "-ConfirmSyntheticPublicDemo and -AllowNonSeededData only apply together with -PublicTunnel."
}
if ($PublicTunnel -and -not $ConfirmSyntheticPublicDemo) {
    Stop-Refused 2 ("-PublicTunnel publishes this demo on a public URL. Re-run with -ConfirmSyntheticPublicDemo to " +
        "confirm the demo database holds synthetic demonstration data only. Without -PublicTunnel the demo stays local.")
}

$Mode = if ($PublicTunnel) { 'public-tunnel' } else { 'local-only' }
$FrontendHost = if ($PublicTunnel) { '0.0.0.0' } else { '127.0.0.1' }
$CorsOrigins = if ($PublicTunnel) { 'http://localhost:3000,https://*.trycloudflare.com' } else { 'http://localhost:3000' }

function Write-DemoAccounts {
    Write-Host "`nLOCAL SYNTHETIC DEMO ACCOUNTS (only valid against the seeded demo database; not production credentials):" -ForegroundColor White
    foreach ($a in $DemoAccounts) {
        Write-Host ("  {0,-12} {1,-32} {2}" -f $a.Role, $a.Email, $a.Password)
    }
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   Starting FinSight FYP Demonstration Stack" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "Mode: $Mode"
Write-Host "Frontend bind: ${FrontendHost}:3000  Backend bind: 127.0.0.1:8080  Database: 127.0.0.1:5433 (finsight_demo)"
Write-Host "Backend CORS origins: $CorsOrigins"
Write-Host "Live AI and WhatsApp providers: disabled (mock)"
if ($PublicTunnel) {
    Write-Host "Public tunnel: will start after the synthetic-data checks pass" -ForegroundColor Yellow
} else {
    Write-Host "Public tunnel: not started (local-only; any leftover $TunnelContainerName container is stopped)"
}

if ($PlanOnly) {
    Write-DemoAccounts
    Write-Host "`nPlan only: nothing was started."
    exit 0
}

Set-Location $ProjectRoot

# Never leave an earlier public tunnel running behind a local-only start.
if (-not $PublicTunnel) {
    $leftover = Get-TunnelContainer
    if ($leftover) {
        Write-Host "`nStopping leftover public tunnel container $TunnelContainerName (local-only mode)..." -ForegroundColor Yellow
        docker stop $TunnelContainerName | Out-Null
    }
}

$backendAlreadyRunning = Test-LocalPort 8080
$frontendAlreadyRunning = Test-LocalPort 3000
if ($PublicTunnel -and ($backendAlreadyRunning -or $frontendAlreadyRunning)) {
    Stop-Refused 3 ("A backend (8080) or frontend (3000) is already running and was not started by this script, so its " +
        "database and secrets cannot be verified for public exposure. Run scripts/stop-demo.ps1 first.")
}

# 1. PostgreSQL demo container
Write-Host "`n[1/4] Ensuring PostgreSQL Demo Container is running..." -ForegroundColor Yellow
docker compose -f docker-compose.demo.yml up -d
if (-not (Wait-LocalPort 5433 30)) {
    Write-Error "PostgreSQL demo database failed to become ready on port 5433."
    exit 1
}
Write-Host "PostgreSQL demo database online on port 5433." -ForegroundColor Green

if ($PublicTunnel) {
    $seededEmails = Format-SqlList ($DemoAccounts | ForEach-Object { $_.Email })
    $seededBusinesses = Format-SqlList $SeededBusinessNames
    $otherUsers = Get-DemoCount "SELECT count(*) FROM app_users WHERE email NOT IN ($seededEmails)"
    $otherBusinesses = Get-DemoCount "SELECT count(*) FROM businesses WHERE business_name NOT IN ($seededBusinesses)"
    if ($null -eq $otherUsers -or $null -eq $otherBusinesses) {
        Stop-Refused 4 "Could not verify the demo database contents (is it migrated and seeded?). Public tunnel not started."
    }
    if (($otherUsers -gt 0 -or $otherBusinesses -gt 0) -and -not $AllowNonSeededData) {
        Stop-Refused 4 ("The demo database holds $otherUsers account(s) and $otherBusinesses business(es) that the synthetic " +
            "seeder did not create (possibly a restored real backup or earlier sign-ups). Verify they contain no real " +
            "client data, then re-run with -AllowNonSeededData. Public tunnel not started.")
    }
    Write-Host "Synthetic-data check passed ($otherUsers non-seeded accounts, $otherBusinesses non-seeded businesses)." -ForegroundColor Green
}

# 2. Spring Boot backend (demo database, demo-only key, loopback only, live providers off)
Write-Host "`n[2/4] Checking Spring Boot Backend on port 8080..." -ForegroundColor Yellow
if (-not $backendAlreadyRunning) {
    $backendJar = Join-Path $ProjectRoot "backend\target\sme-health-backend-0.0.1-SNAPSHOT.jar"
    if (-not (Test-Path $backendJar)) {
        Write-Error "Backend jar not found at $backendJar. Build it first (backend: mvnw -DskipTests package)."
        exit 1
    }
    Write-Host "Starting Spring Boot backend in background..." -ForegroundColor Yellow
    $env:POSTGRES_HOST = "127.0.0.1"
    $env:POSTGRES_PORT = "5433"
    $env:POSTGRES_DB = "finsight_demo"
    $env:POSTGRES_APP_USER = "finsight_app"
    $env:POSTGRES_APP_PASSWORD = "DemoDbPassword2026!"
    $env:FINSIGHT_CRYPTO_KEY_K1 = "MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI="
    $env:SERVER_ADDRESS = "127.0.0.1"
    $env:SERVER_PORT = "8080"
    $env:SESSION_COOKIE_SECURE = "false"
    $env:CORS_ALLOWED_ORIGINS = $CorsOrigins
    # Demo never talks to live providers, whatever this shell has configured.
    $env:AI_ENABLED = "false"
    $env:AI_PROVIDER = "mock"
    $env:AI_LIVE_ACTIVATION_APPROVED = "false"
    $env:WHATSAPP_PROVIDER = "mock"
    $env:WHATSAPP_INBOUND_ENABLED = "false"
    foreach ($secret in 'OPENAI_API_KEY', 'WHATSAPP_META_ACCESS_TOKEN', 'WHATSAPP_APP_SECRET', 'WHATSAPP_WEBHOOK_VERIFY_TOKEN', 'WHATSAPP_PHONE_HASH_KEY') {
        Remove-Item "Env:$secret" -ErrorAction SilentlyContinue
    }
    Start-Process java -ArgumentList "-jar", "`"$backendJar`"" -WorkingDirectory (Join-Path $ProjectRoot "backend") -WindowStyle Hidden | Out-Null
    if (-not (Wait-LocalPort 8080 45)) {
        Write-Error "Spring Boot backend did not start on port 8080."
        exit 1
    }
} else {
    Write-Host "A backend is already running on port 8080; reusing it as-is (local-only)." -ForegroundColor Yellow
}
Write-Host "Spring Boot backend online on port 8080." -ForegroundColor Green

# 3. Next.js standalone frontend
Write-Host "`n[3/4] Checking Next.js Standalone Frontend on port 3000..." -ForegroundColor Yellow
if (-not $frontendAlreadyRunning) {
    $serverJs = Join-Path $ProjectRoot "frontend\.next\standalone\server.js"
    if (-not (Test-Path $serverJs)) {
        Write-Error "Frontend standalone build not found at $serverJs. Build the frontend first."
        exit 1
    }
    Write-Host "Starting Next.js frontend in background..." -ForegroundColor Yellow
    $env:PORT = "3000"
    $env:HOSTNAME = $FrontendHost
    $env:BACKEND_API_URL = "http://localhost:8080"
    $env:NEXT_PUBLIC_DATA_MODE = "live"
    Start-Process node -ArgumentList "`"$serverJs`"" -WorkingDirectory (Join-Path $ProjectRoot "frontend") -WindowStyle Hidden | Out-Null
    if (-not (Wait-LocalPort 3000 20)) {
        Write-Error "Next.js frontend did not start on port 3000."
        exit 1
    }
} else {
    Write-Host "A frontend is already running on port 3000; reusing it as-is (local-only)." -ForegroundColor Yellow
}
Write-Host "Next.js frontend online on port 3000." -ForegroundColor Green

# 4. Cloudflare Quick Tunnel: only on explicit opt-in
$tunnelUrl = $null
if ($PublicTunnel) {
    Write-Host "`n[4/4] Starting Cloudflare Quick Tunnel (public exposure opted in)..." -ForegroundColor Yellow
    if (-not (Get-TunnelContainer)) {
        docker run -d --name $TunnelContainerName --rm cloudflare/cloudflared:latest tunnel --url http://host.docker.internal:3000 | Out-Null
    }
    for ($i = 0; $i -lt 30 -and -not $tunnelUrl; $i++) {
        $tunnelLogs = (docker logs $TunnelContainerName 2>&1 | Out-String)
        $match = [regex]::Match($tunnelLogs, 'https://[a-zA-Z0-9\-]+\.trycloudflare\.com')
        if ($match.Success) { $tunnelUrl = $match.Value } else { Start-Sleep -Seconds 1 }
    }
} else {
    Write-Host "`n[4/4] Public tunnel skipped (local-only)." -ForegroundColor Gray
}

Write-Host "`n======================================================================" -ForegroundColor Green
if ($PublicTunnel) {
    Write-Host "   FINSIGHT DEMO IS RUNNING AND PUBLICLY EXPOSED" -ForegroundColor Yellow
} else {
    Write-Host "   FINSIGHT DEMO IS RUNNING LOCALLY (NOT PUBLICLY EXPOSED)" -ForegroundColor Green
}
Write-Host "======================================================================" -ForegroundColor Green
if ($PublicTunnel) {
    if ($tunnelUrl) {
        Write-Host "Public Demo URL: $tunnelUrl" -ForegroundColor Cyan
    } else {
        Write-Host "Public Demo URL: not detected yet; check 'docker logs $TunnelContainerName'" -ForegroundColor Yellow
    }
}
Write-Host "Local Web URL:   http://localhost:3000" -ForegroundColor Gray
Write-Host "Backend API:     http://localhost:8080" -ForegroundColor Gray
Write-Host "Demo Database:   127.0.0.1:5433 (finsight_demo)" -ForegroundColor Gray
Write-DemoAccounts
Write-Host "`nORGANIZATIONS AVAILABLE:" -ForegroundColor White
Write-Host "  Showcase (read/test):    Alpha Garments, Apex Digital, Crescent Electronics, Indus Textiles"
Write-Host "  Sandbox (full mutation): Sandbox A, Sandbox B, Sandbox C, Sandbox D"
if (-not $PublicTunnel) {
    Write-Host "`nTo expose deliberately:          powershell scripts/start-demo.ps1 -PublicTunnel -ConfirmSyntheticPublicDemo"
}
Write-Host "To reset Sandbox data anytime:   powershell scripts/reset-sandbox.ps1"
Write-Host "To shut down entire demo stack:  powershell scripts/stop-demo.ps1"
Write-Host "======================================================================`n"
