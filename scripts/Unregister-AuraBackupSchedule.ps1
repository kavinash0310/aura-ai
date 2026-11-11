[CmdletBinding()]
param(
    [ValidateLength(1, 128)]
    [string]$TaskName = 'AURA Database Backup',

    [switch]$ConfirmRemoval
)

$ErrorActionPreference = 'Stop'
if (-not (Get-Command Unregister-ScheduledTask -ErrorAction SilentlyContinue)) {
    throw 'Windows Task Scheduler cmdlets are unavailable.'
}
if (-not $ConfirmRemoval) {
    throw "This removes the scheduled task '$TaskName' but leaves backup files untouched. Rerun with -ConfirmRemoval."
}

$task = Get-ScheduledTask -TaskName $TaskName -ErrorAction SilentlyContinue
if (-not $task) {
    Write-Output "No scheduled task named '$TaskName' exists."
    return
}

Unregister-ScheduledTask -TaskName $TaskName -Confirm:$false -ErrorAction Stop
Write-Output "Removed scheduled task '$TaskName'. Existing backup files were left untouched."
