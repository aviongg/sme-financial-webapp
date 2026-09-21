[CmdletBinding()]
param(
    [string] $SulemanRef = 'origin/dev/suleman',
    [string] $BackendSourceRoot,
    [string] $MavenRepository,
    [string] $MavenCommand,
    [switch] $PrepareOnly
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest
$backendRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$workspaceRoot = [System.IO.Path]::GetFullPath((Join-Path $backendRoot '..'))
if ([string]::IsNullOrWhiteSpace($BackendSourceRoot)) {
    $BackendSourceRoot = $backendRoot
}
$BackendSourceRoot = [System.IO.Path]::GetFullPath($BackendSourceRoot)
foreach ($required in @('pom.xml', 'src', 'integration')) {
    if (!(Test-Path -LiteralPath (Join-Path $BackendSourceRoot $required))) {
        throw "Missing backend source input: $required in $BackendSourceRoot"
    }
}
$integrationRoot = Join-Path $BackendSourceRoot 'integration'
$targetRoot = [System.IO.Path]::GetFullPath((Join-Path $backendRoot 'target'))
$combinedRoot = [System.IO.Path]::GetFullPath((Join-Path $targetRoot ('combined-integration-' + [guid]::NewGuid().ToString('N'))))
if (!$combinedRoot.StartsWith($targetRoot + [System.IO.Path]::DirectorySeparatorChar, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw 'The combined build must remain inside backend/target.'
}
if (Test-Path -LiteralPath $combinedRoot) {
    throw 'Refusing to overwrite an existing integration workspace.'
}

$resolvedRef = (& git -C $workspaceRoot rev-parse --verify ($SulemanRef + '^{commit}'))
if ($LASTEXITCODE -ne 0 -or $resolvedRef -notmatch '^[0-9a-f]{40,64}$') {
    throw "Cannot resolve the locally available Suleman ref: $SulemanRef"
}
$resolvedRef = $resolvedRef.Trim()
$utf8 = [System.Text.UTF8Encoding]::new($false)

function Write-Utf8([string] $Path, [string] $Text) {
    [void][System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($Path))
    [System.IO.File]::WriteAllText($Path, $Text, $utf8)
}

function Read-RefFile([string] $GitPath) {
    $lines = & git -C $workspaceRoot show ($resolvedRef + ':' + $GitPath)
    if ($LASTEXITCODE -ne 0) { throw "Unable to read $GitPath from $resolvedRef" }
    return (($lines -join "`n").TrimEnd() + "`n")
}

function Copy-RefTree([string] $GitPrefix) {
    $paths = @(& git -C $workspaceRoot ls-tree -r --name-only $resolvedRef -- $GitPrefix)
    if ($LASTEXITCODE -ne 0 -or $paths.Count -eq 0) { throw "Missing integration source tree: $GitPrefix" }
    foreach ($gitPath in $paths) {
        if (!$gitPath.StartsWith('backend/') -or $gitPath.Contains('..')) {
            throw "Unexpected git source path: $gitPath"
        }
        $destination = Join-Path $combinedRoot $gitPath.Substring('backend/'.Length)
        Write-Utf8 $destination (Read-RefFile $gitPath)
    }
}

function Get-NormalizedHash([string] $Text) {
    $normalized = ($Text -replace "`r`n", "`n").TrimEnd() + "`n"
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        return [System.BitConverter]::ToString($sha.ComputeHash($utf8.GetBytes($normalized))).Replace('-', '').ToLowerInvariant()
    } finally { $sha.Dispose() }
}

[void][System.IO.Directory]::CreateDirectory($combinedRoot)
foreach ($entry in @('src', 'pom.xml', 'mvnw', 'mvnw.cmd', '.mvn', 'docs')) {
    $source = Join-Path $BackendSourceRoot $entry
    if (Test-Path -LiteralPath $source) {
        Copy-Item -LiteralPath $source -Destination $combinedRoot -Recurse
    }
}

# Only Suleman's owned modules are overlaid; Fatima's advice, i18n, Zakat,
# language endpoint, profile locking, migrations, and tests stay from this workspace.
$javaPrefix = 'backend/src/main/java/com/app/sme_health_backend/'
foreach ($feature in @('scoring', 'cashflow', 'dashboard', 'records', 'search')) {
    Copy-RefTree ($javaPrefix + $feature + '/')
}
Copy-RefTree ($javaPrefix + 'profile/entity/')
Copy-RefTree ($javaPrefix + 'profile/dto/')

# Compile and exercise Suleman's owned unit tests too; a runtime-only smoke test
# would miss constructor and service-contract conflicts in the actual merge.
$testPrefix = 'backend/src/test/java/com/app/sme_health_backend/'
foreach ($feature in @('scoring', 'cashflow', 'dashboard', 'records', 'search')) {
    Copy-RefTree ($testPrefix + $feature + '/')
}
foreach ($testFile in @('MonthlyRecordControllerTests.java', 'MonthlyRecordServiceTests.java')) {
    Copy-RefTree ($testPrefix + $testFile)
}

# Preserve local profile methods while including the fields required by the real calculators.
$profilePath = Join-Path $combinedRoot 'src/main/java/com/app/sme_health_backend/profile/service/BusinessProfileService.java'
$profileText = [System.IO.File]::ReadAllText($profilePath)
$profileAnchor = '        profile.setWhatsappOptIn(request.isWhatsappOptIn());'
if ([regex]::Matches($profileText, [regex]::Escape($profileAnchor)).Count -ne 1) {
    throw 'Profile creation has changed; review the three-field integration handoff before running.'
}
if ($profileText.Contains('profile.setPaymentBehavior(') -or $profileText.Contains('profile.setNtnRegistered(') -or
        $profileText.Contains('profile.setBusinessRegistered(')) {
    throw 'Profile scoring fields already exist; review the integration harness to avoid duplicate assignments.'
}
$profileFields = @'
        profile.setPaymentBehavior(request.getPaymentBehavior());
        profile.setNtnRegistered(request.getNtnRegistered());
        profile.setBusinessRegistered(request.getBusinessRegistered());
'@
Write-Utf8 $profilePath ($profileText.Replace($profileAnchor, $profileAnchor + "`n" + $profileFields))

# The handoff is reviewed against a specific upstream dashboard. Fail visibly if it changes.
$dashboardRelative = 'src/main/java/com/app/sme_health_backend/dashboard/service/DashboardService.java'
$dashboardPath = Join-Path $combinedRoot $dashboardRelative
$expectedDashboardHash = ([System.IO.File]::ReadAllText((Join-Path $integrationRoot 'DashboardService.upstream.sha256'))).Trim()
if ((Get-NormalizedHash ([System.IO.File]::ReadAllText($dashboardPath))) -ne $expectedDashboardHash) {
    throw 'Suleman dashboard differs from the reviewed handoff. Refresh DashboardService.java and its upstream hash first.'
}
Copy-Item -LiteralPath (Join-Path $integrationRoot 'DashboardService.java') -Destination $dashboardPath -Force
$testPath = Join-Path $combinedRoot 'src/test/java/com/app/sme_health_backend/integration/CombinedWorkflowIT.java'
[void][System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($testPath))
Copy-Item -LiteralPath (Join-Path $integrationRoot 'CombinedWorkflowIT.java') -Destination $testPath
$dashboardTestPath = Join-Path $combinedRoot 'src/test/java/com/app/sme_health_backend/dashboard/DashboardServiceTests.java'
Copy-Item -LiteralPath (Join-Path $integrationRoot 'DashboardServiceTests.java') -Destination $dashboardTestPath -Force

# Retain exact inputs and output for inspection; this script never deletes a build or edits .git.
$inputHashes = @(Get-ChildItem -LiteralPath (Join-Path $combinedRoot 'src') -Recurse -File | ForEach-Object {
    [ordered]@{ path = $_.FullName.Substring($combinedRoot.Length + 1); sha256 = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
})
$manifest = [ordered]@{
    generatedAtUtc = [DateTime]::UtcNow.ToString('o')
    localWorkspace = $workspaceRoot
    localHead = ((& git -C $workspaceRoot rev-parse HEAD) -join '').Trim()
    backendSource = $BackendSourceRoot
    sulemanRef = $SulemanRef
    sulemanCommit = $resolvedRef
    dashboardHandoffUpstreamHash = $expectedDashboardHash
    sources = $inputHashes
}
Write-Utf8 (Join-Path $combinedRoot 'integration-inputs.json') ($manifest | ConvertTo-Json -Depth 5)
Write-Host "Combined integration workspace: $combinedRoot"
Write-Host "Suleman source commit: $resolvedRef"
if ($PrepareOnly) { return }

if ([string]::IsNullOrWhiteSpace($MavenCommand)) {
    $MavenCommand = Join-Path $combinedRoot 'mvnw.cmd'
}
$mavenArguments = @('-q', '-Ppostgres-it', 'test', 'failsafe:integration-test', 'failsafe:verify',
    '-Dtest=**/scoring/*Tests,**/cashflow/*Tests,**/dashboard/*Tests,**/search/*Tests,**/MonthlyRecord*Tests,!**/*IntegrationTests', '-Dit.test=CombinedWorkflowIT')
if (![string]::IsNullOrWhiteSpace($MavenRepository)) {
    $mavenArguments += '-Dmaven.repo.local=' + [System.IO.Path]::GetFullPath($MavenRepository)
}
Push-Location -LiteralPath $combinedRoot
try {
    & $MavenCommand @mavenArguments
    $mavenExit = $LASTEXITCODE
} finally { Pop-Location }
if ($mavenExit -ne 0) {
    throw "Combined integration failed (Maven exit $mavenExit). Inspect $combinedRoot/target/failsafe-reports."
}
Write-Host "Combined integration passed. Reports: $combinedRoot/target/failsafe-reports"
