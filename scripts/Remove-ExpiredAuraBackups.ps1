[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param(
    [string]$OutputDirectory,

    [ValidateRange(1, 3650)]
    [int]$RetentionDays = 30,

    [ValidateRange(1, 50)]
    [int]$MinimumBackups = 2,

    [switch]$Apply
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repoRoot 'backups'
}
if (-not (Test-Path -LiteralPath $OutputDirectory -PathType Container)) {
    throw "Backup directory does not exist: $OutputDirectory"
}
$resolvedOutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
$archivePattern = '^aura-db-\d{8}T\d{6}Z-[0-9a-f]{8}\.dump(?:\.age)?$'
$validBackups = [System.Collections.Generic.List[object]]::new()

foreach ($archive in Get-ChildItem -LiteralPath $resolvedOutputDirectory -File) {
    if ($archive.Name -notmatch $archivePattern) {
        continue
    }

    $manifestPath = "${archive.FullName}.manifest.json"
    if (-not (Test-Path -LiteralPath $manifestPath -PathType Leaf)) {
        Write-Warning "Keeping archive with no manifest: $($archive.Name)"
        continue
    }

    try {
        $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
        $isAgeEncrypted = $archive.Name.EndsWith('.dump.age', [StringComparison]::OrdinalIgnoreCase)
        $manifestFormatValid = if ($isAgeEncrypted) {
            $manifest.formatVersion -eq 2 -and [string]$manifest.encryptionFormat -ceq 'age'
        }
        else {
            $manifest.formatVersion -eq 1 -and [string]::IsNullOrWhiteSpace([string]$manifest.encryptionFormat)
        }
        if (-not $manifestFormatValid -or
                [string]$manifest.archiveFile -cne $archive.Name -or
                [long]$manifest.bytes -ne $archive.Length -or
                [string]$manifest.sha256 -notmatch '^[0-9a-fA-F]{64}$') {
            throw 'Manifest does not match archive metadata.'
        }
        $actualHash = (Get-FileHash -LiteralPath $archive.FullName -Algorithm SHA256).Hash
        if ([string]$manifest.sha256 -ine $actualHash) {
            throw 'Archive checksum does not match its manifest.'
        }
        $createdAt = [DateTimeOffset]::Parse(
            [string]$manifest.createdAtUtc,
            [Globalization.CultureInfo]::InvariantCulture,
            [Globalization.DateTimeStyles]::AssumeUniversal
        ).ToUniversalTime()
        $validBackups.Add([pscustomobject]@{
            ArchivePath = $archive.FullName
            ManifestPath = $manifestPath
            Name = $archive.Name
            CreatedAtUtc = $createdAt
        })
    }
    catch {
        Write-Warning "Keeping archive that failed integrity checks: $($archive.Name) ($($_.Exception.Message))"
    }
}

$newest = @($validBackups | Sort-Object CreatedAtUtc -Descending)
$protectedNames = @($newest | Select-Object -First $MinimumBackups | ForEach-Object Name)
$cutoff = [DateTimeOffset]::UtcNow.AddDays(-$RetentionDays)
$expired = @($newest | Where-Object {
    $_.CreatedAtUtc -lt $cutoff -and $_.Name -notin $protectedNames
})

if ($expired.Count -eq 0) {
    Write-Output "No verified backups are eligible for removal. Retention: $RetentionDays days; minimum kept: $MinimumBackups."
    return
}

foreach ($backup in $expired) {
    if ($Apply) {
        if ($PSCmdlet.ShouldProcess($backup.Name, 'Remove expired verified AURA backup and its manifest')) {
            Remove-Item -LiteralPath $backup.ArchivePath -Force
            Remove-Item -LiteralPath $backup.ManifestPath -Force
            Write-Output "Removed expired backup: $($backup.Name)"
        }
    }
    else {
        Write-Output "Would remove expired backup: $($backup.Name)"
    }
}

if (-not $Apply) {
    Write-Output 'Preview only. Add -Apply to delete the listed archive/manifest pairs.'
}
