# Ownership guards for the local demo launcher (DEF-18 / Codex candidate-v2 QA section 5).
# A process is "ours" only when it is a process tree this launcher started and recorded (PID + exact start time).
# Nothing here ever stops a process because it holds a port, and an occupied port is never reused.

Set-StrictMode -Version 2

function Get-PortListenerPids([int]$Port) {
    @(Get-NetTCPConnection -State Listen -LocalPort $Port -ErrorAction SilentlyContinue |
        Select-Object -ExpandProperty OwningProcess -Unique | Where-Object { $_ -gt 0 })
}

function Get-ProcessDescription([int]$ProcessId) {
    $p = Get-Process -Id $ProcessId -ErrorAction SilentlyContinue
    if ($p) { "$($p.ProcessName) (PID $ProcessId)" } else { "PID $ProcessId" }
}

# Throws if anything listens on the port. The occupant is reported, never reused or terminated.
function Assert-PortFree([int]$Port, [string]$Purpose) {
    $owners = @(Get-PortListenerPids $Port)
    if ($owners.Count -gt 0) {
        $who = ($owners | ForEach-Object { Get-ProcessDescription $_ }) -join ', '
        throw "Port $Port ($Purpose) is already in use by $who. It was not started by this demo launcher, so it is neither reused nor stopped. Free the port and retry."
    }
}

# A record that identifies exactly one launched process: PID plus its start time (PIDs are reused by Windows).
function New-OwnedProcessRecord([string]$Role, [System.Diagnostics.Process]$Process) {
    $Process.Refresh()
    [ordered]@{ role = $Role; pid = $Process.Id; startTicks = $Process.StartTime.ToUniversalTime().Ticks; name = $Process.ProcessName }
}

function Test-OwnedProcessAlive($Record) {
    $p = Get-Process -Id ([int]$Record.pid) -ErrorAction SilentlyContinue
    if (-not $p) { return $false }
    try { return $p.StartTime.ToUniversalTime().Ticks -eq [long]$Record.startTicks } catch { return $false }
}

function Get-DescendantPids([int]$RootPid) {
    $all = @(Get-CimInstance Win32_Process -ErrorAction SilentlyContinue | Select-Object ProcessId, ParentProcessId)
    $result = New-Object System.Collections.Generic.List[int]
    $frontier = New-Object System.Collections.Generic.Queue[int]
    $frontier.Enqueue($RootPid)
    while ($frontier.Count -gt 0) {
        $current = $frontier.Dequeue()
        foreach ($child in $all | Where-Object { $_.ParentProcessId -eq $current -and $_.ProcessId -ne $current }) {
            if (-not $result.Contains([int]$child.ProcessId)) { $result.Add([int]$child.ProcessId); $frontier.Enqueue([int]$child.ProcessId) }
        }
    }
    , $result.ToArray()
}

# The listener on $Port must be the recorded process or one of its descendants (e.g. a java.exe launcher shim).
function Assert-PortOwnedBy([int]$Port, $Record) {
    if (-not (Test-OwnedProcessAlive $Record)) { throw "The launched $($Record.role) process (PID $($Record.pid)) is no longer running." }
    $tree = @([int]$Record.pid) + @(Get-DescendantPids ([int]$Record.pid))
    $owners = @(Get-PortListenerPids $Port)
    if ($owners.Count -eq 0) { throw "Nothing listens on port $Port for the launched $($Record.role) process." }
    $foreign = @($owners | Where-Object { $tree -notcontains $_ })
    if ($foreign.Count -gt 0) {
        throw "Port $Port is served by $(($foreign | ForEach-Object { Get-ProcessDescription $_ }) -join ', '), not by the launched $($Record.role) process (PID $($Record.pid))."
    }
}

# Stops only a recorded, still-identical process and its descendants. Returns what happened; never throws.
function Stop-OwnedProcessTree($Record) {
    if (-not (Test-OwnedProcessAlive $Record)) {
        return "$($Record.role): PID $($Record.pid) not running or reused by another program; left untouched"
    }
    $tree = @(Get-DescendantPids ([int]$Record.pid))
    [array]::Reverse($tree)
    foreach ($id in $tree) { Stop-Process -Id $id -Force -ErrorAction SilentlyContinue }
    Stop-Process -Id ([int]$Record.pid) -Force -ErrorAction SilentlyContinue
    "$($Record.role): stopped PID $($Record.pid)"
}

function Read-DemoRuntime([string]$Path) {
    if (-not (Test-Path $Path)) { return $null }
    Get-Content -Raw $Path | ConvertFrom-Json
}

function Write-DemoRuntime([string]$Path, $State) {
    $dir = Split-Path -Parent $Path
    if (-not (Test-Path $dir)) { New-Item -ItemType Directory -Force -Path $dir | Out-Null }
    ($State | ConvertTo-Json -Depth 5) | Set-Content -Encoding UTF8 -Path $Path
}

Export-ModuleMember -Function Get-PortListenerPids, Assert-PortFree, New-OwnedProcessRecord, Test-OwnedProcessAlive,
    Get-DescendantPids, Assert-PortOwnedBy, Stop-OwnedProcessTree, Read-DemoRuntime, Write-DemoRuntime
