# Positive Windows ownership/shutdown probe. Only the child started here is stopped.
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$RepoRoot, [Parameter(Mandatory=$true)][string]$OutputDir)
$ErrorActionPreference='Stop'
$root=[IO.Path]::GetFullPath($RepoRoot)
$out=[IO.Path]::GetFullPath($OutputDir)
if (-not $out.StartsWith(($root+[IO.Path]::DirectorySeparatorChar),[StringComparison]::OrdinalIgnoreCase)) { throw 'Output must stay in the task worktree.' }
New-Item -ItemType Directory -Force -Path $out | Out-Null
Import-Module (Join-Path $root 'scripts/demo-guards.psm1') -Force
$stamp=[guid]::NewGuid().ToString('N')
$script=Join-Path $out "owned-child-$stamp.cjs"
$ready=Join-Path $out "owned-child-$stamp.json"
@'
const fs=require('node:fs'), net=require('node:net');
const server=net.createServer(socket=>socket.end());
server.listen(0,'127.0.0.1',()=>fs.writeFileSync(process.argv[2],JSON.stringify({pid:process.pid,port:server.address().port})));
'@ | Set-Content -Encoding UTF8 -LiteralPath $script
$child=Start-Process node -ArgumentList "`"$script`"", "`"$ready`"" -WindowStyle Hidden -PassThru
$record=New-OwnedProcessRecord 'task-owned-probe' $child
try {
    for($i=0;$i -lt 100 -and -not (Test-Path -LiteralPath $ready);$i++) { Start-Sleep -Milliseconds 100 }
    if (-not (Test-Path -LiteralPath $ready)) { throw 'Task-owned child did not start.' }
    $server=Get-Content -LiteralPath $ready -Raw | ConvertFrom-Json
    if ([int]$server.pid -ne [int]$record.pid) { throw 'Ready file has a different PID.' }
    Assert-PortOwnedBy ([int]$server.port) $record
    $message=Stop-OwnedProcessTree $record
    for($i=0;$i -lt 50 -and (Test-OwnedProcessAlive $record);$i++) { Start-Sleep -Milliseconds 100 }
    if (Test-OwnedProcessAlive $record) { throw 'Owned child still alive after shutdown.' }
    Assert-PortFree ([int]$server.port) 'released task-owned probe'
    [ordered]@{ scope='Windows guard module with one task-owned ephemeral Node listener'; status='PASS';
        identity=$record; port=[int]$server.port; ownedListenerVerified=$true;
        recordedProcessStopped=$true; portReleased=$true; message=$message } |
        ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath (Join-Path $out 'owned-process.json')
    'PASS recorded PID/start-time ownership, actual own-child shutdown and port release'
} finally {
    if (Test-OwnedProcessAlive $record) { Stop-OwnedProcessTree $record | Out-Null }
}
