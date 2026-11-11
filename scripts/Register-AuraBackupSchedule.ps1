[CmdletBinding()]
param(
    [string]$OutputDirectory,

    [ValidatePattern('^\d{2}:\d{2}$')]
    [string]$DailyAt = '02:30',

    [ValidateRange(1, 3650)]
    [int]$RetentionDays = 30,

    [ValidateRange(1, 50)]
    [int]$MinimumBackups = 2,

    [string]$AgeRecipient,

    [ValidateLength(1, 128)]
    [string]$TaskName = 'AURA Database Backup'
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot

if (-not (Get-Command Register-ScheduledTask -ErrorAction SilentlyContinue)) {
    throw 'Windows Task Scheduler cmdlets are unavailable. Run this script in Windows PowerShell or PowerShell 7 on Windows.'
}
$pwshCommand = Get-Command pwsh.exe -ErrorAction SilentlyContinue
if (-not $pwshCommand) {
    throw 'pwsh.exe is required to register the backup task.'
}
if ([string]::IsNullOrWhiteSpace($OutputDirectory)) {
    $OutputDirectory = Join-Path $repoRoot 'backups'
}
if (-not [string]::IsNullOrWhiteSpace($AgeRecipient)) {
    if ($AgeRecipient -match '["\r\n]') {
        throw 'AgeRecipient cannot contain quotes or line breaks.'
    }
    if ($AgeRecipient -notmatch '^age1[0-9a-z]+\z') {
        throw 'AgeRecipient must be one native age public recipient beginning with age1.'
    }
    if (-not (Get-Command age -ErrorAction SilentlyContinue)) {
        throw 'The age command must be installed and available on PATH before registering an encrypted backup schedule.'
    }
}
New-Item -ItemType Directory -Path $OutputDirectory -Force | Out-Null
$resolvedOutputDirectory = (Resolve-Path -LiteralPath $OutputDirectory).Path
if ($resolvedOutputDirectory.Contains('"')) {
    throw 'Backup directory paths containing a double quote are unsupported.'
}

$parsedTime = [TimeSpan]::Zero
$timeParsed = [TimeSpan]::TryParseExact(
    $DailyAt,
    'hh\:mm',
    [Globalization.CultureInfo]::InvariantCulture,
    [ref]$parsedTime
)
if (-not $timeParsed -or $parsedTime -ge [TimeSpan]::FromDays(1)) {
    throw 'DailyAt must be a valid local time in 24-hour HH:mm format, such as 02:30.'
}

$existingTask = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if ($existingTask) {
    throw "A scheduled task named '$TaskName' already exists. Remove it deliberately or choose another TaskName."
}

$runnerPath = Join-Path $PSScriptRoot 'Invoke-AuraScheduledBackup.ps1'
$arguments = @(
    '-NoLogo'
    '-NoProfile'
    '-NonInteractive'
    '-ExecutionPolicy Bypass'
    "-File `"$runnerPath`""
    "-OutputDirectory `"$resolvedOutputDirectory`""
    "-RetentionDays $RetentionDays"
    "-MinimumBackups $MinimumBackups"
) -join ' '
if (-not [string]::IsNullOrWhiteSpace($AgeRecipient)) {
    $arguments += " -AgeRecipient `"$AgeRecipient`""
}
$action = New-ScheduledTaskAction `
    -Execute $pwshCommand.Source `
    -Argument $arguments `
    -WorkingDirectory $repoRoot
$trigger = New-ScheduledTaskTrigger -Daily -At ([DateTime]::Today.Add($parsedTime))
$identity = [System.Security.Principal.WindowsIdentity]::GetCurrent().Name
$principal = New-ScheduledTaskPrincipal `
    -UserId $identity `
    -LogonType Interactive `
    -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet `
    -StartWhenAvailable `
    -MultipleInstances IgnoreNew `
    -ExecutionTimeLimit (New-TimeSpan -Hours 3) `
    -AllowStartIfOnBatteries `
    -DontStopIfGoingOnBatteries

Register-ScheduledTask `
    -TaskName $TaskName `
    -Description 'Create a verified AURA PostgreSQL backup and apply configured retention.' `
    -Action $action `
    -Trigger $trigger `
    -Principal $principal `
    -Settings $settings `
    -ErrorAction Stop | Out-Null

Write-Output "Registered '$TaskName' for $DailyAt local time as $identity. The task runs only while this Windows user is logged in."
Write-Output "Backup directory: $resolvedOutputDirectory; retention: $RetentionDays days; minimum verified backups: $MinimumBackups."
if (-not [string]::IsNullOrWhiteSpace($AgeRecipient)) {
    Write-Output 'The scheduled archive will be encrypted to the configured age recipient; the private identity is not stored in the task.'
}
