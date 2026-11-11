[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$BackupPath,

    [string]$AgeIdentityFile
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$containerDrillArchive = $null
$stagingDecryptedArchive = $null
$drillDatabase = $null
$drillDatabaseCreated = $false
$cleanupFailure = $false

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
    return @($output | ForEach-Object { "$($_)".Trim() } | Where-Object { $_ })
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

    $archiveToDrill = $resolvedBackupPath
    if ($isAgeEncrypted) {
        if ([string]::IsNullOrWhiteSpace($AgeIdentityFile)) {
            throw 'AgeIdentityFile is required for an encrypted archive.'
        }
        if (-not (Get-Command age -ErrorAction SilentlyContinue)) {
            throw 'The age command was not found. Install age before drilling an encrypted archive.'
        }
        $resolvedAgeIdentity = (Resolve-Path -LiteralPath $AgeIdentityFile).Path
        if ((Get-Item -LiteralPath $resolvedAgeIdentity).PSIsContainer) {
            throw 'AgeIdentityFile must point to one private identity file.'
        }
        $stagingDecryptedArchive = Join-Path ([System.IO.Path]::GetTempPath()) "aura-restore-drill-$([Guid]::NewGuid().ToString('N')).dump"
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
        $archiveToDrill = $stagingDecryptedArchive
    }

    $runningDatabase = Get-AuraComposeOutput @('ps', '--status', 'running', '--services', 'db')
    if (-not ($runningDatabase -contains 'db')) {
        throw 'The AURA database container is not running. Start it with docker compose up -d db.'
    }
    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', 'pg_isready -U "$POSTGRES_USER" -d "$POSTGRES_DB"')

    $runId = [Guid]::NewGuid().ToString('N')
    $drillDatabase = "aura_restore_drill_$($runId.Substring(0, 12))"
    $containerDrillArchive = "/tmp/aura-restore-drill-$runId.dump"
    Invoke-AuraCompose @('cp', $archiveToDrill, "db:$containerDrillArchive")
    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', "pg_restore --list $containerDrillArchive >/dev/null")

    $existsCommand = 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1 -Atqc '
    $existsCommand += "`"SELECT 1 FROM pg_database WHERE datname = '$drillDatabase'`""
    $existingDatabase = Get-AuraComposeOutput @('exec', '-T', 'db', 'sh', '-ec', $existsCommand)
    if ($existingDatabase -contains '1') {
        throw 'The generated recovery-drill database name already exists; refusing to modify it.'
    }

    $createCommand = 'createdb -U "$POSTGRES_USER" -d "$POSTGRES_DB" -T template0 ' + $drillDatabase
    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', $createCommand)
    $drillDatabaseCreated = $true

    $restoreCommand = 'pg_restore --single-transaction --exit-on-error --no-owner --no-privileges -U "$POSTGRES_USER" -d ' + $drillDatabase + ' ' + $containerDrillArchive
    Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', $restoreCommand)

    $schemaCommand = 'psql -U "$POSTGRES_USER" -d ' + $drillDatabase + ' -v ON_ERROR_STOP=1 -Atqc '
    $schemaCommand += '"SELECT COALESCE((SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1), ''none'') || ''|'' || COALESCE(to_regclass(''public.research_jobs'')::text, ''missing'')"'
    $schemaResult = Get-AuraComposeOutput @('exec', '-T', 'db', 'sh', '-ec', $schemaCommand)
    $schemaParts = ([string]($schemaResult -join '')) -split '\|', 2
    if ($schemaParts.Count -ne 2 -or $schemaParts[0] -eq 'none' -or $schemaParts[1] -ne 'research_jobs') {
        throw 'The restored database did not contain a successful Flyway version and the core research_jobs table.'
    }

    Write-Output "Backup recovery drill passed in temporary database $drillDatabase. Latest Flyway version: $($schemaParts[0])."
    Write-Output 'The AURA application database was not modified.'
}
finally {
    if ($drillDatabaseCreated -and $drillDatabase) {
        try {
            $dropCommand = 'dropdb --if-exists -U "$POSTGRES_USER" -d "$POSTGRES_DB" ' + $drillDatabase
            Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', $dropCommand)
        }
        catch {
            Write-Warning "Could not remove the temporary recovery-drill database '$drillDatabase'. Drop it manually after checking no process is using it."
            $cleanupFailure = $true
        }
    }

    if ((Get-Command docker -ErrorAction SilentlyContinue) -and $containerDrillArchive) {
        try {
            Invoke-AuraCompose @('exec', '-T', 'db', 'sh', '-ec', "rm -f -- $containerDrillArchive")
        }
        catch {
            Write-Warning 'Could not remove the temporary archive from the database container.'
            $cleanupFailure = $true
        }
    }
    if ($stagingDecryptedArchive -and (Test-Path -LiteralPath $stagingDecryptedArchive)) {
        try {
            Remove-Item -LiteralPath $stagingDecryptedArchive -Force
        }
        catch {
            Write-Warning 'Could not remove the temporary decrypted archive. Remove it after confirming no process is using it.'
            $cleanupFailure = $true
        }
    }
    Pop-Location
}

if ($cleanupFailure) {
    throw 'The recovery drill finished with cleanup failures. Review the warnings before continuing.'
}
