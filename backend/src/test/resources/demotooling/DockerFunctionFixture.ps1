# Isolated Docker FUNCTION fixture for the demo-tooling ownership tests (DemoContainerOwnershipTest).
# Dot-source it with a JSON state file; it defines a global `docker` function that shadows any real Docker CLI in this
# PowerShell session, records every call and simulates only the containers listed in the state file. No real Docker
# command, container, volume or daemon is ever used. Windows PowerShell 5.1 and PowerShell 7 compatible.
param([Parameter(Mandatory = $true)][string]$StatePath)

$global:DockerFixtureStatePath = $StatePath

function global:Read-DockerFixtureState { Get-Content -Raw -LiteralPath $global:DockerFixtureStatePath | ConvertFrom-Json }
function global:Save-DockerFixtureState($State) {
    $State | ConvertTo-Json -Depth 10 | Set-Content -Encoding UTF8 -LiteralPath $global:DockerFixtureStatePath
}

# Docker resolves a reference as a full ID, a container name or a unique ID prefix (at least 12 characters here).
function global:Find-DockerFixtureContainer($State, [string]$Reference) {
    foreach ($c in @($State.containers)) {
        if ($c.Id -eq $Reference -or $c.Name -eq $Reference -or ($Reference.Length -ge 12 -and $c.Id.StartsWith($Reference))) { return $c }
    }
    $null
}

function global:docker {
    $a = @($args | ForEach-Object { [string]$_ })
    $s = Read-DockerFixtureState
    $s.calls = @($s.calls) + @($a -join ' ')
    $global:LASTEXITCODE = 0
    $out = $null
    switch ($a[0]) {
        'container' {
            if ($a[1] -eq 'inspect') {
                $c = Find-DockerFixtureContainer $s $a[-1]
                $flag = if ($a -contains '--format') { '--format' } elseif ($a -contains '-f') { '-f' } else { $null }
                if (-not $c) { $global:LASTEXITCODE = 1 }
                elseif ($flag) {
                    $format = $a[[array]::IndexOf($a, $flag) + 1]
                    $running = if ($c.Running) { 'true' } else { 'false' }
                    if ($format.StartsWith('{{.Id}}|')) {
                        $mounts = (@($c.Mounts) | ForEach-Object { "$_;" }) -join ''
                        $out = @($c.Id, ('/' + $c.Name), $c.Created, $running, $c.Project, $c.Service, $c.Instance, $c.WorkingDir, $mounts) -join '|'
                    } elseif ($format -eq '{{.State.Running}}') { $out = $running }
                    else { $global:LASTEXITCODE = 2 }
                }
            }
        }
        'volume' { if (-not (@($s.volumes) -contains $a[-1])) { $global:LASTEXITCODE = 1 } }
        'ps' {
            $name = (($a | Where-Object { $_ -like 'name=*' }) -replace '^name=\^?', '') -replace '\$$', ''
            $out = @($s.containers | Where-Object { $_.Name -eq $name -and $_.Running } | ForEach-Object { $_.Id.Substring(0, 12) })
        }
        { $_ -in 'start', 'stop' } {
            $c = Find-DockerFixtureContainer $s $a[-1]
            if ($c) { $c.Running = ($a[0] -eq 'start'); $out = $a[-1] } else { $global:LASTEXITCODE = 1 }
        }
        'port' {
            $c = Find-DockerFixtureContainer $s $a[1]
            if ($c -and $c.Running) { $out = '127.0.0.1:5433' } else { $global:LASTEXITCODE = 1 }
        }
        'exec' {
            $rest = @($a | Select-Object -Skip 1 | Where-Object { $_ -ne '-i' })
            $c = Find-DockerFixtureContainer $s $rest[0]
            if (-not $c -or -not $c.Running) { $global:LASTEXITCODE = 1; break }
            $s.execCount = [int]$s.execCount + 1
            if ($rest[1] -eq 'psql' -and $rest -contains '-c') {
                $sql = $rest[[array]::IndexOf($rest, '-c') + 1]
                if ($sql -like '*pg_tables*') { $out = $s.psql.tables }
                elseif ($sql -like '*shobj_description*') { $out = $s.psql.marker }
                elseif ($sql -like '*flyway_schema_history*') { $out = $s.psql.version }
                elseif ($sql -like '*pg_stat_activity*') { $out = $s.psql.tagged }
                elseif ($sql -like 'COMMENT ON DATABASE*') { $s.psql.marker = ($sql -replace "^COMMENT ON DATABASE finsight_demo IS '", '') -replace "'$", '' }
            }
            # Simulate the recorded container being replaced by a different one under the same name mid-run.
            if ([int]$s.swapAfterExec -gt 0 -and [int]$s.execCount -ge [int]$s.swapAfterExec) {
                $c.Id = $s.swapToId; $c.Instance = 'replaced-by-someone-else'; $c.Created = '2030-01-01T00:00:00Z'; $s.swapAfterExec = 0
            }
        }
        'compose' {
            if ($a -contains 'up') {
                $template = $s.compose
                $instance = if ($template.instanceOverride) { $template.instanceOverride } else { $env:FINSIGHT_DEMO_INSTANCE }
                $workingDir = if ($template.WorkingDir) { $template.WorkingDir } else { (Get-Location).Path }
                $s.containers = @($s.containers) + @([pscustomobject]@{ Id = $template.Id; Name = $template.Name; Created = $template.Created
                    Running = $true; Project = $template.Project; Service = $template.Service; Instance = $instance; WorkingDir = $workingDir
                    Mounts = @($template.Mounts) })
            } elseif ($a -contains 'ps') {
                $out = @($s.containers | Where-Object { $_.Project -eq 'finsight-demo' -and $_.Service -eq 'postgres-demo' } | ForEach-Object { $_.Id })
            }
        }
        default { $global:LASTEXITCODE = 125 }
    }
    $code = $global:LASTEXITCODE
    Save-DockerFixtureState $s
    $global:LASTEXITCODE = $code
    if ($null -ne $out) { $out }
}
