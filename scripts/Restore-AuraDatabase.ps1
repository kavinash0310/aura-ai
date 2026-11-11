[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BackupPath,

    [switch]$ConfirmRestore,

    [string]$BackupDirectory,

    [string]$AgeIdentityFile
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$containerRestorePath = $null
$stagingDecryptedArchive = $null
$apiWasRunning = $false
$apiStopped = $false
$restoreStarted = $false
$restoreSucceeded = $false
$preRestoreBackup = $null
$ageRecipientForPreRestore = $null

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
        throw 'BackupPath must point to one .dump or .dump.age archive file.'
    }
    $isAgeEncrypted = $resolvedBackupPath.EndsWith('.dump.age', [StringComparison]::OrdinalIgnoreCase)
    $isPlainArchive = $resolvedBackupPath.EndsWith('.dump', [StringComparison]::OrdinalIgnoreCase)
    if (-not $isAgeEncrypted -and -not $isPlainArchive) {
        throw 'BackupPath must point to one generated .dump or .dump.age archive.'
    }
    $manifestPath = "${resolvedBackupPath}.manifest.json"
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        throw "Integrity manifest was not found: $manifestPath"
    }

    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($isAgeEncrypted -and ($manifest.formatVersion -ne 2 -or [string]$manifest.encryptionFormat -cne 'age')) {
        throw 'The encrypted archive requires a format version 2 age manifest.'
    }
    if ($isPlainArchive -and ($manifest.formatVersion -ne 1 -or -not [string]::IsNullOrWhiteSpace([string]$manifest.encryptionFormat))) {
        throw 'The plaintext archive requires a format version 1 manifest with no encryption format.'
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

    $archiveToRestore = $resolvedBackupPath
    if ($isAgeEncrypted) {
        if ([string]::IsNullOrWhiteSpace($AgeIdentityFile)) {
            throw 'AgeIdentityFile is required to restore an encrypted archive. Keep the private identity separate from the backup.'
        }
        if (-not (Get-Command age -ErrorAction SilentlyContinue)) {
            throw 'The age command was not found. Install age before restoring this encrypted archive.'
        }
        if (-not (Get-Command age-keygen -ErrorAction SilentlyContinue)) {
            throw 'The age-keygen command was not found; it is required to create the encrypted pre-restore safety backup.'
        }
        $resolvedAgeIdentity = (Resolve-Path -LiteralPath $AgeIdentityFile).Path
        if ((Get-Item -LiteralPath $resolvedAgeIdentity).PSIsContainer) {
            throw 'AgeIdentityFile must point to one private identity file.'
        }

        $identityOutput = & age-keygen -y $resolvedAgeIdentity 2>&1
        $identityExitCode = $LASTEXITCODE
        if ($identityExitCode -ne 0) {
            throw "age-keygen could not derive the safety-backup recipient (exit code $identityExitCode)."
        }
        $ageRecipientForPreRestore = @($identityOutput | ForEach-Object { ([string]$_).Trim() } | Where-Object { $_ -match '^age1[0-9a-z]+$' })
        if ($ageRecipientForPreRestore.Count -ne 1) {
            throw 'The identity did not yield one age public recipient for the pre-restore safety backup.'
        }
        $ageRecipientForPreRestore = [string]$ageRecipientForPreRestore[0]

        $stagingDecryptedArchive = Join-Path ([System.IO.Path]::GetTempPath()) "aura-restore-$([Guid]::NewGuid().ToString('N')).dump"
        $ageOutput = & age --decrypt --identity $resolvedAgeIdentity --output $stagingDecryptedArchive $resolvedBackupPath 2>&1
        $ageExitCode = $LASTEXITCODE
        if ($ageOutput) {
            $ageOutput | ForEach-Object { Write-Host "$_" }
        }
        if ($ageExitCode -ne 0) {
            throw "age could not decrypt the archive with the supplied identity (exit code $ageExitCode)."
        }
        if (-not (Test-Path -LiteralPath $stagingDecryptedArchive -PathType Leaf) -or
                (Get-Item -LiteralPath $stagingDecryptedArchive).Length -le 0) {
            throw 'age did not produce a nonempty PostgreSQL archive.'
        }
        $archiveToRestore = $stagingDecryptedArchive
    }

    $runningDatabase = Get-AuraComposeOutput @('ps', '--status', 'running', '--services', 'db')
    if (-not ($runningDatabase -contains 'db')) {
        throw 'The AURA database container is not running. Start it with docker compose up -d db.'
    }
    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"')

    $runId = [Guid]::NewGuid().ToString('N')
    $containerRestorePath = "/tmp/aura-restore-$runId.dump"
    Invoke-AuraCompose @('cp', $archiveToRestore, "db:$containerRestorePath")
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
    $backupArguments = @{ OutputDirectory = $BackupDirectory }
    if ($ageRecipientForPreRestore) {
        $backupArguments.AgeRecipient = $ageRecipientForPreRestore
    }
    $preRestoreBackup = & $backupScript @backupArguments
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
    if ($stagingDecryptedArchive -and (Test-Path -LiteralPath $stagingDecryptedArchive)) {
        try {
            Remove-Item -LiteralPath $stagingDecryptedArchive -Force
        }
        catch {
            Write-Warning 'Could not remove the temporary decrypted archive. Remove it after confirming no restore process is using it.'
        }
    }
    Pop-Location
}
