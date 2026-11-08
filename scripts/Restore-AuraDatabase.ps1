[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BackupPath,

    [switch]$ConfirmRestore,

    [string]$BackupDirectory
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$containerRestorePath = $null
$apiWasRunning = $false
$apiStopped = $false
$restoreStarted = $false
$restoreSucceeded = $false
$preRestoreBackup = $null

function Invoke-AuraCompose {
    param([Parameter(Mandatory)][string[]]$ComposeArguments)

    $output = & docker compose @ComposeArguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($output) {
        $output | ForEach-Object { Write-Host "$_" }
    }
    if ($exitCode -ne 0) {
        throw "docker compose $($ComposeArguments -join ' ') failed with exit code $exitCode."
    }
}

function Get-AuraComposeOutput {
    param([Parameter(Mandatory)][string[]]$ComposeArguments)

    $output = & docker compose @ComposeArguments 2>&1
    $exitCode = $LASTEXITCODE
    if ($exitCode -ne 0) {
        throw "docker compose $($ComposeArguments -join ' ') failed with exit code $exitCode."
    }
    return @($output | ForEach-Object { "$_" })
}

if (-not $ConfirmRestore) {
    throw 'Restore replaces the current AURA database. Review the archive and rerun with -ConfirmRestore to proceed.'
}

Push-Location $repoRoot
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI was not found. Install Docker Desktop and start the AURA Compose stack.'
    }

    $resolvedBackupPath = (Resolve-Path -LiteralPath $BackupPath).Path
    if ((Get-Item -LiteralPath $resolvedBackupPath).PSIsContainer) {
        throw 'BackupPath must point to one .dump archive file.'
    }
    $manifestPath = "${resolvedBackupPath}.manifest.json"
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "Integrity manifest was not found: $manifestPath"
    }

    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.formatVersion -ne 1) {
        throw 'Unsupported or missing backup manifest format version.'
    }
    if ([string]$manifest.archiveFile -cne [System.IO.Path]::GetFileName($resolvedBackupPath)) {
        throw 'The manifest archive filename does not match BackupPath.'
    }

    $archiveInfo = Get-Item -LiteralPath $resolvedBackupPath
    if ([long]$manifest.bytes -ne $archiveInfo.Length) {
        throw 'The archive size does not match its integrity manifest.'
    }
    $actualHash = (Get-FileHash -LiteralPath $resolvedBackupPath -Algorithm SHA256).Hash
    if ([string]$manifest.sha256 -ine $actualHash) {
        throw 'The archive checksum does not match its integrity manifest.'
    }

    $runningDatabase = Get-AuraComposeOutput @('ps', '--status', 'running', '--services', 'db')
    if (-not ($runningDatabase -contains 'db')) {
        throw 'The AURA database container is not running. Start it with docker compose up -d db.'
    }
    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"')

    $runId = [Guid]::NewGuid().ToString('N')
    $containerRestorePath = "/tmp/aura-restore-$runId.dump"
    Invoke-AuraCompose @('cp', $resolvedBackupPath, "db:$containerRestorePath")
    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', "pg_restore --list $containerRestorePath >/dev/null")

    $runningApi = Get-AuraComposeOutput @('ps', '--status', 'running', '--services', 'api')
    $apiWasRunning = $runningApi -contains 'api'
    if ($apiWasRunning) {
        Invoke-AuraCompose @('stop', 'api')
        $apiStopped = $true
    }

    if ([string]::IsNullOrWhiteSpace($BackupDirectory)) {
        $BackupDirectory = Split-Path -Parent $resolvedBackupPath
    }
    $backupScript = Join-Path $PSScriptRoot 'Backup-AuraDatabase.ps1'
    $preRestoreBackup = & $backupScript -OutputDirectory $BackupDirectory
    if ([string]::IsNullOrWhiteSpace([string]$preRestoreBackup)) {
        throw 'The automatic pre-restore backup did not return a path; restore was not started.'
    }

    $restoreStarted = $true
    $restoreCommand = 'pg_restore --clean --if-exists --exit-on-error --single-transaction --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB" ' + $containerRestorePath
    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', $restoreCommand)
    $restoreSucceeded = $true

    if ($apiWasRunning) {
        Invoke-AuraCompose @('start', 'api')
    }

    Write-Host "Database restored from: $resolvedBackupPath"
    Write-Host "Pre-restore safety backup: $preRestoreBackup"
}
catch {
    if ($restoreStarted -and -not $restoreSucceeded) {
        Write-Warning "Restore did not complete. The API is being left stopped if it was running. The pre-restore backup, if created, is: $preRestoreBackup"
    }
    throw
}
finally {
    if ($apiStopped -and -not $restoreStarted) {
        try {
            Invoke-AuraCompose @('start', 'api')
        }
        catch {
            Write-Warning 'The API could not be restarted after restore preparation failed. Inspect docker compose ps and start it manually when safe.'
        }
    }

    if ((Get-Command docker -ErrorAction SilentlyContinue) -and $containerRestorePath) {
        try {
            Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', "rm -f -- $containerRestorePath")
        }
        catch {
            Write-Warning 'Could not remove the temporary restore archive from the database container.'
        }
    }
    Pop-Location
}
