# Read-only negative probe: Docker is a function fixture, never the real CLI.
# The task worktree must have no runtime manifest, so no OS process can be stopped.
[CmdletBinding()]
param([Parameter(Mandatory=$true)][string]$RepoRoot, [Parameter(Mandatory=$true)][string]$OutputFile)
$ErrorActionPreference='Stop'
$runtime=Join-Path $RepoRoot '.demo-runtime/runtime.json'
if (Test-Path -LiteralPath $runtime) { throw 'Refusing: runtime manifest exists; this probe must not touch any recorded process.' }
$global:StageBShutdownCalls=New-Object System.Collections.Generic.List[string]
function global:docker {
    param([Parameter(ValueFromRemainingArguments=$true)][string[]]$Arguments)
    $global:StageBShutdownCalls.Add(($Arguments -join ' '))
    if ($Arguments[0] -eq 'ps') { 'synthetic-unowned-container-id' }
    $global:LASTEXITCODE=0
}
try {
    & (Join-Path $RepoRoot 'scripts/stop-demo.ps1')
    $calls=@($global:StageBShutdownCalls)
    $result=[ordered]@{ scope='Exact stop-demo script; Docker function fixture only; no real container/process stopped';
        runtimeManifestPresent=$false; calls=$calls;
        stoppedUnrecordedNamedContainer=(@($calls | Where-Object { $_ -eq 'stop finsight-postgres-demo' }).Count -gt 0);
        stoppedLegacyTunnelByName=(@($calls | Where-Object { $_ -eq 'stop finsight-tunnel-demo' }).Count -gt 0) }
    $result | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 -LiteralPath $OutputFile
    if (-not $result.stoppedUnrecordedNamedContainer) { throw 'Reviewed behavior changed; reassess this negative probe.' }
    Write-Output 'CONFIRMED: named container shutdown is not guarded by runtime ownership. No Docker CLI was called.'
} finally { Remove-Item Function:/docker -ErrorAction SilentlyContinue }
