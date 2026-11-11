[CmdletBinding()]
param(
    [string]$OutputDirectory,

    [string]$AgeRecipient
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$containerBackupPath = $null
$containerVerifyPath = $null
$stagingArchive = $null
$stagingEncryptedArchive = $null
$stagingManifest = $null
$finalArchive = $null
$finalManifest = $null
$archiveCommitted = $false
$manifestCommitted = $false

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

Push-Location $repoRoot
try {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Docker CLI was not found. Install Docker Desktop and start the AURA Compose stack.'
    }

    if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
        $OutputDirectory = Join-Path $repoRoot 'backups'
    }
    $encryptBackup = -not [string]::IsNullOrWhiteSpace($AgeRecipient)
    if ($encryptBackup -and -not (Get-Command age -ErrorAction SilentlyContinue)) {
        throw 'The age command was not found. Install age or omit AgeRecipient to create a plaintext archive.'
    }
    if ($encryptBackup -and $AgeRecipient -notmatch '^age1[0-9a-z]+\z') {
        throw 'AgeRecipient must be one native age public recipient beginning with age1.'
    }
    if (-not $encryptBackup) {
        Write-Warning 'This backup will be plaintext. Pass AgeRecipient to encrypt the archive.'
    }
    New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
    $resolvedOutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path

    $runningDatabase = & docker compose ps --status running --services db 2>&1
    $databaseStatus = $LASTEXITCODE
    if ($databaseStatus -ne 0) {
        throw 'Could not inspect the Compose database service. Check Docker Desktop and the .env configuration.'
    }
    if (-not ($runningDatabase -contains 'db')) {
        throw 'The AURA database container is not running. Start the stack with docker compose up -d db.'
    }

    $runId = [Guid]::NewGuid().ToString('N')
    $shortRunId = $runId.Substring(0, 8)
    $timestamp = [DateTimeOffset]::UtcNow.ToString('yyyyMMddTHHmmssZ')
    $containerBackupPath = "/tmp/aura-backup-$runId.dump"
    $containerVerifyPath = "/tmp/aura-verify-$runId.dump"
    $stagingArchiveDirectory = if ($encryptBackup) { [System.IO.Path]::GetTempPath() } else { $resolvedOutputDirectory }
    $stagingArchive = Join-Path $stagingArchiveDirectory ".$runId.partial.dump"
    if ($encryptBackup) {
        $stagingEncryptedArchive = Join-Path $resolvedOutputDirectory ".$runId.partial.dump.age"
    }
    $stagingManifest = Join-Path $resolvedOutputDirectory ".$runId.partial.manifest.json"
    $archiveExtension = if ($encryptBackup) { '.dump.age' } else { '.dump' }
    $finalArchive = Join-Path $resolvedOutputDirectory "aura-db-$timestamp-$shortRunId$archiveExtension"
    $finalManifest = "${finalArchive}.manifest.json"

    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"')

    $dumpCommand = 'pg_dump --format=custom --no-owner --no-privileges -U "$POSTGRES_USER" -d "$POSTGRES_DB" -f ' + $containerBackupPath
    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', $dumpCommand)
    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', "pg_restore --list $containerBackupPath >/dev/null")

    Invoke-AuraCompose @('cp', "db:$containerBackupPath", $stagingArchive)
    $archiveInfo = Get-Item -LiteralPath $stagingArchive
    if ($archiveInfo.Length -le 0) {
        throw 'The copied database archive is empty.'
    }

    Invoke-AuraCompose @('cp', $stagingArchive, "db:$containerVerifyPath")
    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', "pg_restore --list $containerVerifyPath >/dev/null")

    $archiveToCommit = $stagingArchive
    $formatVersion = 1
    $encryptionFormat = $null
    if ($encryptBackup) {
        $ageOutput = & age --encrypt --recipient $AgeRecipient --output $stagingEncryptedArchive $stagingArchive 2>&1
        $ageExitCode = $LASTEXITCODE
        if ($ageOutput) {
            $ageOutput | ForEach-Object { Write-Host "$_" }
        }
        if ($ageExitCode -ne 0) {
            throw "age encryption failed with exit code $ageExitCode."
        }
        $encryptedInfo = Get-Item -LiteralPath $stagingEncryptedArchive
        if ($encryptedInfo.Length -le 0) {
            throw 'The encrypted database archive is empty.'
        }
        $archiveToCommit = $stagingEncryptedArchive
        $formatVersion = 2
        $encryptionFormat = 'age'
    }

    $archiveInfo = Get-Item -LiteralPath $archiveToCommit
    $hash = (Get-FileHash -LiteralPath $archiveToCommit -Algorithm SHA256).Hash
    $manifest = [ordered]@{
        formatVersion = $formatVersion
        encryptionFormat = $encryptionFormat
        createdAtUtc = [DateTimeOffset]::UtcNow.ToString('o')
        archiveFile = [System.IO.Path]::GetFileName($finalArchive)
        bytes = $archiveInfo.Length
        sha256 = $hash
    }
    $manifestJson = $manifest | ConvertTo-Json -Depth 3
    $utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
    [System.IO.File]::WriteAllText($stagingManifest, $manifestJson + [Environment]::NewLine, $utf8WithoutBom)

    [System.IO.File]::Move($archiveToCommit, $finalArchive)
    $archiveCommitted = $true
    [System.IO.File]::Move($stagingManifest, $finalManifest)
    $manifestCommitted = $true

    $backupDescription = if ($encryptBackup) { 'Verified and age-encrypted database backup created' } else { 'Verified database backup created' }
    Write-Host "${backupDescription}: $finalArchive"
    Write-Host "Integrity manifest: $finalManifest"
    Write-Output $finalArchive
}
catch {
    if ($archiveCommitted -and (Test-Path -LiteralPath $finalArchive)) {
        Remove-Item -LiteralPath $finalArchive -Force
    }
    if ($manifestCommitted -and (Test-Path -LiteralPath $finalManifest)) {
        Remove-Item -LiteralPath $finalManifest -Force
    }
    throw
}
finally {
    foreach ($temporaryFile in @($stagingArchive, $stagingEncryptedArchive, $stagingManifest)) {
        if ($temporaryFile -and (Test-Path -LiteralPath $temporaryFile)) {
            try {
                Remove-Item -LiteralPath $temporaryFile -Force
            }
            catch {
                if ($temporaryFile -ceq $stagingArchive) {
                    Write-Warning "Could not remove the temporary plaintext database archive: $temporaryFile"
                }
                else {
                    Write-Warning "Could not remove a temporary backup file: $temporaryFile"
                }
            }
        }
    }

    if ((Get-Command docker -ErrorAction SilentlyContinue) -and $containerBackupPath) {
        try {
            Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', "rm -f -- $containerBackupPath $containerVerifyPath")
        }
        catch {
            Write-Warning 'Could not remove the temporary archive from the database container. Remove the /tmp/aura-backup-* and /tmp/aura-verify-* files after checking the database container.'
        }
    }
    Pop-Location
}
