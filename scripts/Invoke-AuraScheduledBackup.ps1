[CmdletBinding()]
param(
    [Parameter(Mandatory)]
    [string]$OutputDirectory,

    [ValidateRange(1, 3650)]
    [int]$RetentionDays = 30,

    [ValidateRange(1, 50)]
    [int]$MinimumBackups = 2
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$backupScript = Join-Path $PSScriptRoot 'Backup-AuraDatabase.ps1'
$retentionScript = Join-Path $PSScriptRoot 'Remove-ExpiredAuraBackups.ps1'

Push-Location $repoRoot
try {
    $backupOutput = & $backupScript -OutputDirectory $OutputDirectory
    $backupOutputItems = @($backupOutput | Where-Object { $_ -is [string] })
    $backupPath = if ($backupOutputItems.Count -gt 0) {
        [string]$backupOutputItems[-1]
    }
    if ([string]::IsNullOrWhiteSpace($backupPath) -or
            -not (Test-Path -LiteralPath $backupPath -PathType Leaf) -or
            -not (Test-Path -LiteralPath "${backupPath}.manifest.json" -PathType Leaf)) {
        throw 'Backup did not return a verified archive and manifest; retention was not run.'
    }

    & $retentionScript `
        -OutputDirectory $OutputDirectory `
        -RetentionDays $RetentionDays `
        -MinimumBackups $MinimumBackups `
        -Apply
    Write-Output "Scheduled backup completed. Archive: $backupPath"
}
finally {
    Pop-Location
}
