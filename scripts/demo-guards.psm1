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

# ------------------------------------------------------------------ demo database container identity
# A container is the demo's only when every recorded fact from init-demo.ps1 (.demo-runtime/environment.json) still holds
# for the container with the recorded full ID: same ID, name, creation time, init instance label, Compose project/service
# and working directory, and the recorded data volume. The fixed name alone never identifies it, and every later Docker
# command addresses the verified ID, so a foreign, renamed or recreated container is never started, stopped or changed.

$script:DemoContainerName = 'finsight-postgres-demo'
$script:DemoComposeProject = 'finsight-demo'
$script:DemoComposeService = 'postgres-demo'
$script:DemoInstanceLabel = 'com.finsight.demo.instance'
$script:DemoDataPath = '/var/lib/postgresql/data'
# Go raw-string (backtick) label keys: no embedded double quotes, which Windows PowerShell 5.1 mangles for native programs.
$script:DemoContainerFormat = '{{.Id}}|{{.Name}}|{{.Created}}|{{.State.Running}}|' +
    '{{index .Config.Labels `com.docker.compose.project`}}|{{index .Config.Labels `com.docker.compose.service`}}|' +
    '{{index .Config.Labels `com.finsight.demo.instance`}}|{{index .Config.Labels `com.docker.compose.project.working_dir`}}|' +
    '{{range .Mounts}}{{.Type}}:{{.Name}}:{{.Destination}};{{end}}'

function Get-DemoContainerFacts([string]$Reference) {
    if ([string]::IsNullOrWhiteSpace($Reference)) { return $null }
    $out = docker container inspect --format $script:DemoContainerFormat $Reference 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $out) { return $null }
    $fields = ((@($out) -join "`n").Trim()) -split '\|', 9
    if ($fields.Count -ne 9) { return $null }
    [pscustomobject]@{
        Id = $fields[0]; Name = $fields[1].TrimStart('/'); Created = $fields[2]; Running = ($fields[3] -eq 'true')
        Project = $fields[4]; Service = $fields[5]; Instance = $fields[6]; WorkingDir = $fields[7]
        Mounts = @($fields[8].Split(';') | Where-Object { $_ })
    }
}

function ConvertTo-ComparablePath([string]$Path) {
    if ([string]::IsNullOrWhiteSpace($Path)) { return '' }
    try { $full = [System.IO.Path]::GetFullPath($Path) } catch { $full = $Path }
    $full.TrimEnd('\', '/').ToLowerInvariant()
}

# A value from a manifest read from JSON (PSCustomObject) or built in memory (hashtable/ordered dictionary).
function Get-RecordedEntry($Object, [string]$Name) {
    if ($null -eq $Object) { return $null }
    if ($Object -is [System.Collections.IDictionary]) { if ($Object.Contains($Name)) { return $Object[$Name] } else { return $null } }
    $property = $Object.PSObject.Properties[$Name]
    if ($property) { $property.Value } else { $null }
}

function Get-RecordedValue($Object, [string]$Name) {
    $value = Get-RecordedEntry $Object $Name
    if ($null -eq $value) { $null } else { [string]$value }
}

# Read-only. Returns Owned/Reason/ContainerId/Running; never starts, stops or changes anything.
function Test-DemoContainerOwnership($Environment, [string]$ProjectRoot) {
    $result = { param([bool]$owned, [string]$reason, $facts)
        [pscustomobject]@{ Owned = $owned; Reason = $reason; ContainerId = $(if ($owned) { $facts.Id } else { $null })
            Running = $(if ($facts) { $facts.Running } else { $false }) } }
    if ($null -eq $Environment) { return & $result $false 'no environment manifest (.demo-runtime/environment.json) from init-demo.ps1' $null }
    $id = Get-RecordedValue $Environment 'containerId'
    $instance = Get-RecordedValue $Environment 'instance'
    $created = Get-RecordedValue $Environment 'containerCreated'
    $volume = Get-RecordedValue $Environment 'volume'
    $workingDir = Get-RecordedValue $Environment 'composeWorkingDir'
    if (-not ($id -match '^[0-9a-f]{64}$') -or -not ($instance -match '^[0-9a-fA-F-]{36}$') -or -not $created -or -not $volume -or -not $workingDir) {
        return & $result $false 'the environment manifest has no complete container identity (created before ownership records, or edited)' $null
    }
    if ((Get-RecordedValue $Environment 'container') -ne $script:DemoContainerName -or (Get-RecordedValue $Environment 'composeProject') -ne $script:DemoComposeProject) {
        return & $result $false 'the environment manifest names a different container or Compose project' $null
    }
    if ((ConvertTo-ComparablePath (Get-RecordedValue $Environment 'projectRoot')) -ne (ConvertTo-ComparablePath $ProjectRoot)) {
        return & $result $false 'the environment manifest was created for a different checkout' $null
    }
    $facts = Get-DemoContainerFacts $id
    if ($null -eq $facts) { return & $result $false "the recorded container $($id.Substring(0, 12)) no longer exists (stale identity)" $null }
    $checks = [ordered]@{
        'container ID' = ($facts.Id -eq $id)
        'container name' = ($facts.Name -eq $script:DemoContainerName)
        'creation time' = ($facts.Created -eq $created)
        'init instance label' = ($facts.Instance -eq $instance)
        'Compose project' = ($facts.Project -eq $script:DemoComposeProject)
        'Compose service' = ($facts.Service -eq $script:DemoComposeService)
        'Compose working directory' = ($facts.WorkingDir -eq $workingDir)
        'data volume' = ($facts.Mounts -contains "volume:${volume}:$script:DemoDataPath")
    }
    $failed = @($checks.Keys | Where-Object { -not $checks[$_] })
    if ($failed.Count -gt 0) { return & $result $false ("the container does not match its init record (" + ($failed -join ', ') + ')') $null }
    & $result $true 'verified against the init-demo.ps1 record' $facts
}

# Stops the verified demo container by ID, only when the runtime record of this launch matches the environment record.
# Never removes the container, its volume or the document store. Returns a message; never throws.
function Stop-OwnedDemoContainer($Environment, $Runtime, [string]$ProjectRoot) {
    try {
        $database = Get-RecordedEntry $Runtime 'database'
        if (-not $database) {
            return [pscustomobject]@{ Stopped = $false; Refused = $false; Message = "database container: no launcher runtime record; left untouched" }
        }
        $recordedId = Get-RecordedValue $database 'containerId'
        if ($recordedId -ne (Get-RecordedValue $Environment 'containerId') -or (Get-RecordedValue $database 'instance') -ne (Get-RecordedValue $Environment 'instance')) {
            return [pscustomobject]@{ Stopped = $false; Refused = $true; Message = "database container: runtime record does not match the environment record; left untouched" }
        }
        $ownership = Test-DemoContainerOwnership $Environment $ProjectRoot
        if (-not $ownership.Owned) {
            return [pscustomobject]@{ Stopped = $false; Refused = $true; Message = "database container: not verified ($($ownership.Reason)); left untouched" }
        }
        if (-not $ownership.Running) {
            return [pscustomobject]@{ Stopped = $false; Refused = $false; Message = "database container $($ownership.ContainerId.Substring(0, 12)): already stopped" }
        }
        docker stop $ownership.ContainerId *> $null
        if ($LASTEXITCODE -ne 0) {
            return [pscustomobject]@{ Stopped = $false; Refused = $true; Message = "database container $($ownership.ContainerId.Substring(0, 12)): docker stop failed" }
        }
        [pscustomobject]@{ Stopped = $true; Refused = $false; Message = "database container $($ownership.ContainerId.Substring(0, 12)): stopped (volume and documents kept)" }
    } catch {
        [pscustomobject]@{ Stopped = $false; Refused = $true; Message = "database container: $($_.Exception.Message); left untouched" }
    }
}

function Read-DemoEnvironment([string]$Path) {
    if (-not (Test-Path $Path)) { return $null }
    try { Get-Content -Raw $Path | ConvertFrom-Json } catch { $null }
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
    Get-DescendantPids, Assert-PortOwnedBy, Stop-OwnedProcessTree, Read-DemoRuntime, Write-DemoRuntime,
    Get-DemoContainerFacts, Test-DemoContainerOwnership, Stop-OwnedDemoContainer, Read-DemoEnvironment, ConvertTo-ComparablePath
