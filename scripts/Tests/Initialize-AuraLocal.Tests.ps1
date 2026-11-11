[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$bootstrapSource = Join-Path $repositoryRoot 'scripts/Initialize-AuraLocal.ps1'
$exampleSource = Join-Path $repositoryRoot '.env.example'
$temporaryDirectory = [System.IO.Path]::GetTempPath()
$temporaryRoot = Join-Path $temporaryDirectory "aura-setup-integration-$([Guid]::NewGuid().ToString('N'))"
$testScripts = Join-Path $temporaryRoot 'scripts'
$bootstrapCopy = Join-Path $testScripts 'Initialize-AuraLocal.ps1'
$environmentCopy = Join-Path $temporaryRoot '.env'

New-Item -ItemType Directory -Path $testScripts | Out-Null
Copy-Item -LiteralPath $bootstrapSource -Destination $bootstrapCopy
Copy-Item -LiteralPath $exampleSource -Destination (Join-Path $temporaryRoot '.env.example')

try {
    $firstOutput = @(& $bootstrapCopy)
    if (-not (Test-Path -LiteralPath $environmentCopy -PathType Leaf)) {
        throw 'The bootstrap did not create .env.'
    }

    $environmentText = Get-Content -LiteralPath $environmentCopy -Raw
    $secretNames = @('AURA_DB_PASSWORD', 'AURA_AUTH_PASSWORD', 'AURA_AI_INTERNAL_TOKEN')
    $secrets = foreach ($name in $secretNames) {
        $pattern = "(?m)^$([Regex]::Escape($name))=([0-9a-f]{64})\r?$"
        $matches = [Regex]::Matches($environmentText, $pattern)
        if ($matches.Count -ne 1) {
            throw "$name was not replaced with one 256-bit lowercase hexadecimal secret."
        }
        $matches[0].Groups[1].Value
    }
    if (@($secrets | Select-Object -Unique).Count -ne $secretNames.Count) {
        throw 'The generated secrets are not distinct.'
    }
    foreach ($secret in $secrets) {
        if (($firstOutput -join "`n").Contains($secret)) {
            throw 'The bootstrap printed a generated secret.'
        }
    }

    $contentBeforeSecondRun = Get-Content -LiteralPath $environmentCopy -Raw
    $overwriteWasRefused = $false
    try {
        $null = & $bootstrapCopy
    } catch {
        $overwriteWasRefused = $_.Exception.Message.Contains('already exists')
    }
    if (-not $overwriteWasRefused) {
        throw 'The bootstrap did not refuse to overwrite an existing .env.'
    }
    $contentAfterSecondRun = Get-Content -LiteralPath $environmentCopy -Raw
    if ($contentAfterSecondRun -cne $contentBeforeSecondRun) {
        throw 'The existing .env changed after the second invocation.'
    }
} finally {
    if (Test-Path -LiteralPath $temporaryRoot) {
        $resolvedTemporaryRoot = [System.IO.Path]::GetFullPath($temporaryRoot)
        $resolvedTemporaryParent = [System.IO.Path]::GetFullPath(
            [System.IO.Path]::GetDirectoryName($resolvedTemporaryRoot)
        )
        $resolvedSystemTemporaryDirectory = [System.IO.Path]::GetFullPath($temporaryDirectory)
        $temporaryName = [System.IO.Path]::GetFileName($resolvedTemporaryRoot)
        if (-not [string]::Equals(
                $resolvedTemporaryParent.TrimEnd('\', '/'),
                $resolvedSystemTemporaryDirectory.TrimEnd('\', '/'),
                [System.StringComparison]::OrdinalIgnoreCase
            ) -or $temporaryName -notmatch '^aura-setup-integration-[0-9a-f]{32}$') {
            throw 'Refusing to remove a test directory outside the generated temporary path.'
        }
        Remove-Item -LiteralPath $resolvedTemporaryRoot -Recurse -Force
    }
}

Write-Output 'Initialize-AuraLocal integration checks passed.'
