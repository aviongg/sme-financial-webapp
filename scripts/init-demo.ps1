<#
.SYNOPSIS
    Creates a brand-new, disposable, synthetic-only local FinSight demo database and seeds it once.

.DESCRIPTION
    Initialization only ever targets a NEW environment it creates itself:
      * refuses if the demo container, the demo volume or the demo document store already exists (existing, restored
        or mixed data is never modified, upgraded or reseeded);
      * creates the PostgreSQL container from docker-compose.demo.yml (loopback 127.0.0.1:5433) labelled with a new
        instance GUID, proves Docker created exactly that container (instance label, Compose project/service/working
        directory, data volume) and from then on addresses it only by its full container ID;
      * proves the new database has no tables and marks it with a unique disposable-demo marker (superuser-only
        database comment);
      * creates the restricted roles, migrates V1-V52 as finsight_migrator and verifies Flyway version 52;
      * runs scripts/DemoScenarioSeeder.java, which independently refuses unless the marker is present and every
        application table is empty.
      * records the container identity (ID, creation time, instance, Compose working directory, volume, checkout) in
        .demo-runtime/environment.json; start-demo.ps1 and stop-demo.ps1 act only on a container matching it.
    Nothing is deleted, stopped or changed on failure; the operator decides whether to inspect or remove the new
    environment.
    To start over: scripts/stop-demo.ps1, then  docker compose -p finsight-demo -f docker-compose.demo.yml down -v
    and remove .demo-runtime (only after confirming it holds nothing you need).

.PARAMETER CreateFreshDisposableDemo
    Required. Confirms a new disposable synthetic demo environment should be created.

.PARAMETER PlanOnly
    Validate options and print the plan without touching Docker, Java or the filesystem.
#>
[CmdletBinding()]
param(
    [switch]$CreateFreshDisposableDemo,
    [switch]$PlanOnly
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$ComposeProject = 'finsight-demo'
$DbContainer = 'finsight-postgres-demo'
$DbVolume = "${ComposeProject}_finsight_demo_pgdata"
$RuntimeDir = Join-Path $ProjectRoot '.demo-runtime'
$DocumentsDir = Join-Path $RuntimeDir 'documents'
$EnvironmentFile = Join-Path $RuntimeDir 'environment.json'
$ExpectedSchemaVersion = '52'

function Stop-Refused([int]$Code, [string]$Message) {
    [Console]::Error.WriteLine("REFUSED: $Message")
    exit $Code
}

if (-not $CreateFreshDisposableDemo) {
    Stop-Refused 2 "init-demo.ps1 only creates a NEW disposable synthetic demo. Re-run with -CreateFreshDisposableDemo."
}

Write-Host "FinSight local demo initialization (new disposable synthetic environment only)"
Write-Host "Compose project: $ComposeProject  Container: $DbContainer  Volume: $DbVolume  Database: 127.0.0.1:5433/finsight_demo"
Write-Host "Document store: $DocumentsDir"
Write-Host "Steps: create container -> prove empty -> mark disposable -> roles -> migrate V1-V$ExpectedSchemaVersion -> grants -> seed once"
if ($PlanOnly) {
    Write-Host "Plan only: nothing was created."
    exit 0
}

function Invoke-Native([string]$What, [scriptblock]$Command) {
    & $Command
    if ($LASTEXITCODE -ne 0) { throw "$What failed with exit code $LASTEXITCODE." }
}

function Invoke-Psql([string]$Sql) {
    $out = docker exec $script:DbId psql -U postgres -d finsight_demo -v ON_ERROR_STOP=1 -tA -c $Sql
    if ($LASTEXITCODE -ne 0) { throw "psql failed: $Sql" }
    (($out | Out-String).Trim())
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) { Stop-Refused 4 "Docker is required." }
docker container inspect $DbContainer *> $null
if ($LASTEXITCODE -eq 0) {
    Stop-Refused 3 "Container $DbContainer already exists. Existing demo data is never modified or reseeded; remove it deliberately first."
}
docker volume inspect $DbVolume *> $null
if ($LASTEXITCODE -eq 0) {
    Stop-Refused 3 "Volume $DbVolume already exists. Existing demo data is never modified or reseeded; remove it deliberately first."
}
if ((Test-Path $DocumentsDir) -and (Get-ChildItem -Force $DocumentsDir | Select-Object -First 1)) {
    Stop-Refused 3 "Document store $DocumentsDir is not empty. Existing uploads are never reused for a new demo."
}

Import-Module (Join-Path $ScriptDir 'demo-guards.psm1') -Force
Set-Location $ProjectRoot
$instance = [guid]::NewGuid().ToString()
$script:DbId = $null
try {
    Write-Host "`n[1/7] Creating new PostgreSQL demo container (instance $instance)..." -ForegroundColor Yellow
    $env:FINSIGHT_DEMO_INSTANCE = $instance
    Invoke-Native "docker compose up" { docker compose -p $ComposeProject -f docker-compose.demo.yml up -d }
    $ids = @(docker compose -p $ComposeProject -f docker-compose.demo.yml ps -q --all postgres-demo | Where-Object { $_ })
    if ($LASTEXITCODE -ne 0 -or $ids.Count -ne 1) { throw "Docker Compose did not report exactly one $ComposeProject database container." }
    $facts = Get-DemoContainerFacts $ids[0]
    if ($null -eq $facts -or -not ($facts.Id -match '^[0-9a-f]{64}$')) { throw "The new database container could not be inspected." }
    $expectedVolume = "volume:${DbVolume}:/var/lib/postgresql/data"
    $provenance = [ordered]@{ 'instance label' = ($facts.Instance -eq $instance); 'container name' = ($facts.Name -eq $DbContainer)
        'Compose project' = ($facts.Project -eq $ComposeProject); 'Compose service' = ($facts.Service -eq 'postgres-demo')
        'Compose working directory' = ((ConvertTo-ComparablePath $facts.WorkingDir) -eq (ConvertTo-ComparablePath $ProjectRoot))
        'data volume' = ($facts.Mounts -contains $expectedVolume) }
    $mismatch = @($provenance.Keys | Where-Object { -not $provenance[$_] })
    if ($mismatch.Count -gt 0) {
        throw ("Container $($facts.Id.Substring(0, 12)) is not the one this initialization created (" + ($mismatch -join ', ') + "); it is left untouched.")
    }
    $script:DbId = $facts.Id
    $ready = $false
    for ($i = 0; $i -lt 60 -and -not $ready; $i++) {
        docker exec $script:DbId pg_isready -U postgres -d finsight_demo *> $null
        if ($LASTEXITCODE -eq 0) { $ready = $true } else { Start-Sleep -Seconds 1 }
    }
    if (-not $ready) { throw "PostgreSQL did not accept connections within 60s." }

    Write-Host "[2/7] Proving the new database is empty and marking it disposable..." -ForegroundColor Yellow
    $tables = Invoke-Psql "SELECT count(*) FROM pg_catalog.pg_tables WHERE schemaname NOT IN ('pg_catalog','information_schema')"
    if ($tables -ne '0') { throw "The new database unexpectedly contains $tables tables; refusing to initialize it." }
    $marker = "finsight-disposable-synthetic-demo:$([guid]::NewGuid()) created $((Get-Date).ToUniversalTime().ToString('o'))"
    Invoke-Psql "COMMENT ON DATABASE finsight_demo IS '$marker'" | Out-Null

    Write-Host "[3/7] Creating restricted demo roles..." -ForegroundColor Yellow
    Invoke-Native "role setup" { Get-Content -Raw (Join-Path $ScriptDir 'demo_roles_init.sql') | docker exec -i $script:DbId psql -U postgres -d finsight_demo -v ON_ERROR_STOP=1 -q -f - }

    Write-Host "[4/7] Resolving backend classpath..." -ForegroundColor Yellow
    $classpathFile = Join-Path $ProjectRoot 'backend\target\demo-classpath.txt'
    Push-Location (Join-Path $ProjectRoot 'backend')
    try { Invoke-Native "classpath" { .\mvnw.cmd -q -B dependency:build-classpath "-Dmdep.outputFile=target/demo-classpath.txt" } } finally { Pop-Location }
    $classpath = (Get-Content -Raw $classpathFile).Trim()

    Write-Host "[5/7] Migrating V1-V$ExpectedSchemaVersion as finsight_migrator..." -ForegroundColor Yellow
    Invoke-Native "migration" { java -cp $classpath (Join-Path $ScriptDir 'FlywayMigratorDemo.java') }
    $version = Invoke-Psql "SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank DESC LIMIT 1"
    if ($version -ne $ExpectedSchemaVersion) { throw "Schema is at Flyway version '$version', expected $ExpectedSchemaVersion." }

    Write-Host "[6/7] Granting runtime privileges..." -ForegroundColor Yellow
    Invoke-Native "grants" { Get-Content -Raw (Join-Path $ScriptDir 'grant_demo_permissions.sql') | docker exec -i $script:DbId psql -U postgres -d finsight_demo -v ON_ERROR_STOP=1 -q -f - }

    Write-Host "[7/7] Seeding synthetic demo data once..." -ForegroundColor Yellow
    Invoke-Native "seeding" { java -cp $classpath (Join-Path $ScriptDir 'DemoScenarioSeeder.java') --fresh-disposable-database }

    New-Item -ItemType Directory -Force -Path $DocumentsDir | Out-Null
    ([ordered]@{ provenance = 'init-demo.ps1'; marker = $marker; instance = $instance; composeProject = $ComposeProject
        container = $DbContainer; containerId = $facts.Id; containerCreated = $facts.Created; composeWorkingDir = $facts.WorkingDir
        volume = $DbVolume; projectRoot = $ProjectRoot; schemaVersion = $version
        createdAt = (Get-Date).ToUniversalTime().ToString('o') } | ConvertTo-Json) | Set-Content -Encoding UTF8 -Path $EnvironmentFile
} catch {
    [Console]::Error.WriteLine("FAILED: $($_.Exception.Message)")
    [Console]::Error.WriteLine("Nothing was stopped or removed; the new environment was left as-is for inspection. Remove it deliberately with: docker compose -p $ComposeProject -f docker-compose.demo.yml down -v")
    exit 1
}

Write-Host "`nNew disposable synthetic demo initialized (schema V$version). Start it with: powershell scripts/start-demo.ps1" -ForegroundColor Green
