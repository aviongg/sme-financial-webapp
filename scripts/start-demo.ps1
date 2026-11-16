<#
.SYNOPSIS
    Starts the local, synthetic FinSight demonstration stack (loopback only).

.DESCRIPTION
    Local-only. Public exposure (tunnels, LAN binding) is disabled until a public demonstration is separately approved.

    The launcher only runs an environment created by scripts/init-demo.ps1 and only runs processes it starts itself:
      * refuses if anything already listens on 8080 or 3000 (an existing process is never reused or stopped);
      * requires the init-created demo container and verifies its disposable marker, its link to this checkout's
        .demo-runtime/environment.json, Flyway version 52 and its loopback-only port;
      * packages the Next standalone server with its .next/static and public assets;
      * starts the backend (demo database, demo document store, live AI/WhatsApp/OCR/SMTP off) and the frontend;
      * declares readiness only after real HTTP checks: the backend CSRF endpoint, the frontend health route, the root
        page and every referenced CSS/JS asset, and the API through the frontend proxy; it then proves the listening
        ports belong to the launched processes and that the backend's own connection reached the demo database.
    On any failure it stops only what it started and reports why.

.PARAMETER PublicTunnel
    Refused: public demonstration is not approved.

.PARAMETER PlanOnly
    Validate options and print the plan and demo accounts without starting anything.
#>
[CmdletBinding()]
param(
    [switch]$PublicTunnel,
    [switch]$PlanOnly
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir

# Synthetic local-demo accounts. Must equal DEMO_ACCOUNTS in scripts/DemoScenarioSeeder.java (DemoToolingContractTest).
# They exist only in a database created by scripts/init-demo.ps1 and are never production credentials.
$DemoAccounts = @(
    @{ Role = 'OWNER';       Email = 'demo.owner@finsight.local';      Password = 'FinSight@Owner2026!' },
    @{ Role = 'ACCOUNTANT';  Email = 'demo.accountant@finsight.local'; Password = 'FinSight@Account2026!' },
    @{ Role = 'MANAGER';     Email = 'demo.manager@finsight.local';    Password = 'FinSight@Manager2026!' },
    @{ Role = 'SALESPERSON'; Email = 'demo.sales@finsight.local';      Password = 'FinSight@Sales2026!' },
    @{ Role = 'VIEWER';      Email = 'demo.viewer@finsight.local';     Password = 'FinSight@Viewer2026!' }
)

$DbContainer = 'finsight-postgres-demo'
$RuntimeDir = Join-Path $ProjectRoot '.demo-runtime'
$RuntimeFile = Join-Path $RuntimeDir 'runtime.json'
$EnvironmentFile = Join-Path $RuntimeDir 'environment.json'
$DocumentsDir = Join-Path $RuntimeDir 'documents'
$BackendUrl = 'http://127.0.0.1:8080'
$FrontendUrl = 'http://127.0.0.1:3000'
$ExpectedSchemaVersion = '52'

function Stop-Refused([int]$Code, [string]$Message) {
    [Console]::Error.WriteLine("REFUSED: $Message")
    exit $Code
}

if ($PublicTunnel) {
    Stop-Refused 2 ("Public demonstration is disabled until it is separately approved (coordinate with Developer A before any " +
        "public demo or deployment). Without -PublicTunnel the demo runs on this computer only.")
}

function Write-DemoAccounts {
    Write-Host "`nLOCAL SYNTHETIC DEMO ACCOUNTS (only valid against the seeded demo database; not production credentials):" -ForegroundColor White
    foreach ($a in $DemoAccounts) {
        Write-Host ("  {0,-12} {1,-32} {2}" -f $a.Role, $a.Email, $a.Password)
    }
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "   Starting FinSight FYP Demonstration Stack (local only)" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host "Mode: local-only"
Write-Host "Frontend bind: 127.0.0.1:3000  Backend bind: 127.0.0.1:8080  Database: 127.0.0.1:5433 (finsight_demo)"
Write-Host "Backend CORS origins: http://localhost:3000"
Write-Host "Live AI, WhatsApp, OCR and SMTP providers: disabled"
Write-Host "Public exposure: disabled (not approved)"
Write-Host "Readiness: HTTP application and static-asset checks; ports must belong to processes started by this launcher"

if ($PlanOnly) {
    Write-DemoAccounts
    Write-Host "`nPlan only: nothing was started."
    exit 0
}

Import-Module (Join-Path $ScriptDir 'demo-guards.psm1') -Force
$RuntimeHelper = Join-Path $ScriptDir 'demo-runtime.mjs'

# ------------------------------------------------------------------ preconditions (nothing started yet)
$existing = Read-DemoRuntime $RuntimeFile
if ($existing) {
    $alive = @($existing.processes | Where-Object { Test-OwnedProcessAlive $_ })
    if ($alive.Count -gt 0) { Stop-Refused 3 "The demo launched earlier is still running. Use scripts/stop-demo.ps1 first." }
    Remove-Item $RuntimeFile -Force
}
try {
    Assert-PortFree 8080 'backend'
    Assert-PortFree 3000 'frontend'
} catch {
    Stop-Refused 3 $_.Exception.Message
}
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Stop-Refused 4 "Docker is required." }
if (-not (Test-Path $EnvironmentFile)) { Stop-Refused 4 "No initialized demo environment. Run: powershell scripts/init-demo.ps1 -CreateFreshDisposableDemo" }
$environment = Get-Content -Raw $EnvironmentFile | ConvertFrom-Json
docker container inspect $DbContainer *> $null
if ($LASTEXITCODE -ne 0) { Stop-Refused 4 "Demo container $DbContainer does not exist. Run scripts/init-demo.ps1 -CreateFreshDisposableDemo." }
$backendJar = Join-Path $ProjectRoot "backend\target\sme-health-backend-0.0.1-SNAPSHOT.jar"
if (-not (Test-Path $backendJar)) { Stop-Refused 4 "Backend jar not found at $backendJar. Build it first (backend: mvnw -DskipTests package)." }

$state = [ordered]@{ startedAt = (Get-Date).ToUniversalTime().ToString('o'); dbStartedByLauncher = $false; processes = @() }
$started = New-Object System.Collections.ArrayList

function Save-State { $state.processes = @($started); Write-DemoRuntime $RuntimeFile $state }

function Invoke-Psql([string]$Sql) {
    $out = docker exec $DbContainer psql -U postgres -d finsight_demo -v ON_ERROR_STOP=1 -tA -c $Sql 2>$null
    if ($LASTEXITCODE -ne 0) { throw "Could not query the demo database: $Sql" }
    (($out | Out-String).Trim())
}

function Invoke-RuntimeCheck([string]$Command, [string]$Url, [int]$TimeoutSec) {
    $out = node $RuntimeHelper $Command $Url $TimeoutSec 2>&1
    if ($LASTEXITCODE -ne 0) { throw "$Command $Url failed: $(($out | Out-String).Trim())" }
    (($out | Out-String).Trim())
}

function Set-ProcessEnv([hashtable]$Values, [string[]]$Remove) {
    foreach ($name in $Remove) { Remove-Item "Env:$name" -ErrorAction SilentlyContinue }
    foreach ($key in $Values.Keys) { Set-Item "Env:$key" $Values[$key] }
}

try {
    Set-Location $ProjectRoot

    # 1. Demo database: the init-created container, proven by marker, environment link, schema and loopback port.
    Write-Host "`n[1/4] Verifying the initialized demo database..." -ForegroundColor Yellow
    $running = (docker container inspect -f '{{.State.Running}}' $DbContainer).Trim()
    if ($running -ne 'true') {
        docker start $DbContainer | Out-Null
        if ($LASTEXITCODE -ne 0) { throw "Could not start $DbContainer." }
        $state.dbStartedByLauncher = $true; Save-State
    }
    $ready = $false
    for ($i = 0; $i -lt 60 -and -not $ready; $i++) {
        docker exec $DbContainer pg_isready -U postgres -d finsight_demo *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true } else { Start-Sleep -Seconds 1 }
    }
    if (-not $ready) { throw "PostgreSQL in $DbContainer did not accept connections within 60s." }
    $binding = ((docker port $DbContainer 5432/tcp) | Out-String).Trim()
    if ($binding -ne '127.0.0.1:5433') { throw "Demo database is published on '$binding', expected loopback 127.0.0.1:5433 only." }
    $marker = Invoke-Psql "SELECT coalesce(shobj_description(oid, 'pg_database'), '') FROM pg_database WHERE datname = 'finsight_demo'"
    if (-not $marker.StartsWith('finsight-disposable-synthetic-demo:') -or $marker -ne $environment.marker) {
        throw "The demo database is not the disposable environment created by init-demo.ps1 for this checkout (marker mismatch). A restored or replaced database is never started."
    }
    $version = Invoke-Psql "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"
    if ($version -ne $ExpectedSchemaVersion) { throw "Demo schema is at Flyway version '$version', expected $ExpectedSchemaVersion." }
    Write-Host "Demo database verified (disposable marker, schema V$version, loopback 127.0.0.1:5433)." -ForegroundColor Green

    # 2. Frontend assets packaged into the standalone server.
    Write-Host "`n[2/4] Packaging the standalone frontend with its static assets..." -ForegroundColor Yellow
    $package = node $RuntimeHelper package (Join-Path $ProjectRoot 'frontend') 2>&1
    if ($LASTEXITCODE -ne 0) { throw "Frontend packaging failed: $(($package | Out-String).Trim())" }

    # 3. Backend: demo database through a tagged connection, demo document store, every live provider off.
    Write-Host "`n[3/4] Starting the Spring Boot backend..." -ForegroundColor Yellow
    $connectionTag = "finsight-demo-$([guid]::NewGuid().ToString('N'))"
    New-Item -ItemType Directory -Force -Path $DocumentsDir | Out-Null
    Set-ProcessEnv @{
        SPRING_DATASOURCE_URL = "jdbc:postgresql://127.0.0.1:5433/finsight_demo?ApplicationName=$connectionTag"
        POSTGRES_APP_USER = 'finsight_app'; POSTGRES_APP_PASSWORD = 'DemoDbPassword2026!'
        FINSIGHT_CRYPTO_KEY_K1 = 'MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI='
        DOCUMENTS_STORAGE_DIR = $DocumentsDir
        SERVER_ADDRESS = '127.0.0.1'; SERVER_PORT = '8080'; SESSION_COOKIE_SECURE = 'false'
        CORS_ALLOWED_ORIGINS = 'http://localhost:3000'
        AI_ENABLED = 'false'; AI_PROVIDER = 'mock'; AI_LIVE_ACTIVATION_APPROVED = 'false'
        WHATSAPP_PROVIDER = 'mock'; WHATSAPP_INBOUND_ENABLED = 'false'; OCR_INTEGRATION_ENABLED = 'false'
    } @('SPRING_PROFILES_ACTIVE', 'OPENAI_API_KEY', 'WHATSAPP_META_ACCESS_TOKEN', 'WHATSAPP_APP_SECRET', 'WHATSAPP_WEBHOOK_VERIFY_TOKEN',
        'WHATSAPP_PHONE_HASH_KEY', 'OCR_SERVICE_URL', 'SPRING_MAIL_HOST', 'SPRING_MAIL_USERNAME', 'SPRING_MAIL_PASSWORD')
    $java = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { Join-Path $env:JAVA_HOME 'bin\java.exe' } else { 'java' }
    $backend = Start-Process $java -ArgumentList "-jar", "`"$backendJar`"" -WorkingDirectory (Join-Path $ProjectRoot "backend") -WindowStyle Hidden -PassThru
    $backendRecord = New-OwnedProcessRecord 'backend' $backend
    [void]$started.Add($backendRecord); Save-State
    $backendReady = Invoke-RuntimeCheck 'verify-backend' $BackendUrl 120
    Assert-PortOwnedBy 8080 $backendRecord
    $tagged = Invoke-Psql "SELECT count(*) FROM pg_stat_activity WHERE datname = 'finsight_demo' AND usename = 'finsight_app' AND application_name = '$connectionTag'"
    if ([int]$tagged -lt 1) { throw "The launched backend has no connection to the demo database (tag $connectionTag)." }
    Write-Host "Backend ready over HTTP ($backendReady), owns port 8080 and is connected to the demo database." -ForegroundColor Green

    # 4. Frontend standalone server, loopback only.
    Write-Host "`n[4/4] Starting the Next.js standalone frontend..." -ForegroundColor Yellow
    $standalone = Join-Path $ProjectRoot 'frontend\.next\standalone'
    Set-ProcessEnv @{ PORT = '3000'; HOSTNAME = '127.0.0.1'; BACKEND_API_URL = 'http://127.0.0.1:8080'; NEXT_PUBLIC_DATA_MODE = 'live'; NEXT_TELEMETRY_DISABLED = '1' } @()
    $frontend = Start-Process node -ArgumentList "`"$(Join-Path $standalone 'server.js')`"" -WorkingDirectory $standalone -WindowStyle Hidden -PassThru
    $frontendRecord = New-OwnedProcessRecord 'frontend' $frontend
    [void]$started.Add($frontendRecord); Save-State
    $frontendReady = Invoke-RuntimeCheck 'verify-frontend' $FrontendUrl 90
    Assert-PortOwnedBy 3000 $frontendRecord
    Invoke-RuntimeCheck 'verify-backend' $FrontendUrl 30 | Out-Null
    Write-Host "Frontend ready over HTTP ($frontendReady), owns port 3000 and proxies the API." -ForegroundColor Green
} catch {
    [Console]::Error.WriteLine("FAILED: $($_.Exception.Message)")
    foreach ($record in @($started)) { [Console]::Error.WriteLine("  cleanup " + (Stop-OwnedProcessTree $record)) }
    if ($state.dbStartedByLauncher) { docker stop $DbContainer *> $null; [Console]::Error.WriteLine("  cleanup: stopped $DbContainer (it was started by this launcher)") }
    Remove-Item $RuntimeFile -Force -ErrorAction SilentlyContinue
    exit 1
}

Write-Host "`n======================================================================" -ForegroundColor Green
Write-Host "   FINSIGHT DEMO IS RUNNING LOCALLY (NOT PUBLICLY EXPOSED)" -ForegroundColor Green
Write-Host "======================================================================" -ForegroundColor Green
Write-Host "Local Web URL:   http://localhost:3000" -ForegroundColor Gray
Write-Host "Backend API:     http://localhost:8080" -ForegroundColor Gray
Write-Host "Demo Database:   127.0.0.1:5433 (finsight_demo, initialized $($environment.createdAt))" -ForegroundColor Gray
Write-DemoAccounts
Write-Host "`nORGANIZATIONS AVAILABLE:" -ForegroundColor White
Write-Host "  Showcase (read/test):    Alpha Garments, Apex Digital, Crescent Electronics, Indus Textiles"
Write-Host "  Sandbox (full mutation): Sandbox A, Sandbox B, Sandbox C, Sandbox D"
Write-Host "`nTo start from clean synthetic data, stop the demo, remove the demo environment and run init-demo.ps1 again."
Write-Host "To shut down the demo stack:  powershell scripts/stop-demo.ps1"
Write-Host "======================================================================`n"
