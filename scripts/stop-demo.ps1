<#
.SYNOPSIS
    Stops the local FinSight demo that scripts/start-demo.ps1 started.

.DESCRIPTION
    Only what this checkout's launcher recorded is stopped:
      * processes in .demo-runtime/runtime.json, only while their PID and exact start time still match (Windows reuses
        PIDs); nothing is stopped because it holds a port;
      * the demo database container, only when the runtime record names the same container as the init-demo.ps1
        environment record and that container's full ID, name, creation time, init instance label, Compose
        project/service/working directory and data volume all still match. It is addressed by its verified ID.
    A container is never stopped because of its name. When the records are missing, stale or inconsistent the
    container is left untouched and the reason is printed. Nothing is removed: the database volume and the document
    store are kept. There is no public tunnel to stop; public exposure is disabled.

    Exit codes: 0 stopped or nothing recorded; 3 a recorded container could not be verified and was left untouched.
#>
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$RuntimeFile = Join-Path $ProjectRoot '.demo-runtime\runtime.json'
$EnvironmentFile = Join-Path $ProjectRoot '.demo-runtime\environment.json'
Import-Module (Join-Path $ScriptDir 'demo-guards.psm1') -Force

Write-Host "Stopping the FinSight local demo (launcher-owned processes and verified database container only)..." -ForegroundColor Cyan
$state = Read-DemoRuntime $RuntimeFile
if (-not $state) {
    Write-Host "  No launcher runtime record; nothing is stopped. Any container named like the demo database is left untouched."
    exit 0
}

foreach ($record in @($state.processes)) { Write-Host ("  " + (Stop-OwnedProcessTree $record)) }

$container = if (Get-Command docker -ErrorAction SilentlyContinue) {
    Stop-OwnedDemoContainer (Read-DemoEnvironment $EnvironmentFile) $state $ProjectRoot
} else {
    [pscustomobject]@{ Stopped = $false; Refused = $false; Message = 'database container: Docker is not available; nothing to stop' }
}
Write-Host ("  " + $container.Message)
Remove-Item $RuntimeFile -Force

Write-Host "Demo data is preserved in its Docker volume and .demo-runtime\documents. Restart with: powershell scripts/start-demo.ps1" -ForegroundColor Gray
if ($container.Refused) {
    [Console]::Error.WriteLine("REFUSED: the database container could not be verified as this demo's own and was not stopped. Inspect it deliberately.")
    exit 3
}
exit 0
