<#
.SYNOPSIS
    Stops the local FinSight demo processes that scripts/start-demo.ps1 started.

.DESCRIPTION
    Only processes recorded in .demo-runtime/runtime.json are stopped, and only while their PID and exact start time
    still match (Windows reuses PIDs). Nothing is stopped because it holds a port. The demo database container
    (finsight-postgres-demo) is stopped, never removed; its data stays in its volume.
#>
$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Split-Path -Parent $ScriptDir
$RuntimeFile = Join-Path $ProjectRoot '.demo-runtime\runtime.json'
Import-Module (Join-Path $ScriptDir 'demo-guards.psm1') -Force

Write-Host "Stopping the FinSight local demo (launcher-owned processes only)..." -ForegroundColor Cyan
$state = Read-DemoRuntime $RuntimeFile
if ($state) {
    foreach ($record in @($state.processes)) { Write-Host ("  " + (Stop-OwnedProcessTree $record)) }
    Remove-Item $RuntimeFile -Force
} else {
    Write-Host "  No launcher-owned processes are recorded; nothing to stop."
}

if (Get-Command docker -ErrorAction SilentlyContinue) {
    foreach ($container in 'finsight-postgres-demo', 'finsight-tunnel-demo') {
        $id = docker ps -q --filter "name=^$container$"
        if ($id) { docker stop $container | Out-Null; Write-Host "  stopped container $container" }
    }
}
Write-Host "Demo data is preserved in its Docker volume. Restart with: powershell scripts/start-demo.ps1" -ForegroundColor Gray
