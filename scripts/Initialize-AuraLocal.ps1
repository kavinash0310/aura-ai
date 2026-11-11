[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$repositoryRoot = Split-Path -Parent $PSScriptRoot
$examplePath = Join-Path $repositoryRoot '.env.example'
$environmentPath = Join-Path $repositoryRoot '.env'

if (-not (Test-Path -LiteralPath $examplePath -PathType Leaf)) {
    throw 'The repository .env.example file is missing.'
}
if (Test-Path -LiteralPath $environmentPath) {
    throw 'A .env file already exists. It was left unchanged; edit it directly if you intend to rotate its values.'
}

function New-AuraRandomSecret {
    $randomBytes = New-Object byte[] 32
    $randomGenerator = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $randomGenerator.GetBytes($randomBytes)
        return [BitConverter]::ToString($randomBytes).Replace('-', '').ToLowerInvariant()
    } finally {
        [Array]::Clear($randomBytes, 0, $randomBytes.Length)
        $randomGenerator.Dispose()
    }
}

function Set-AuraEnvironmentValue {
    param(
        [Parameter(Mandatory = $true)][string]$EnvironmentText,
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $pattern = '(?m)^' + [Regex]::Escape($Name) + '=[^\r\n]*'
    $matches = [Regex]::Matches($EnvironmentText, $pattern)
    if ($matches.Count -ne 1) {
        throw "Expected exactly one $Name entry in .env.example."
    }
    $match = $matches[0]
    $replacement = "$Name=$Value"
    return $EnvironmentText.Remove($match.Index, $match.Length).Insert($match.Index, $replacement)
}

$environmentText = Get-Content -LiteralPath $examplePath -Raw
$environmentText = Set-AuraEnvironmentValue -EnvironmentText $environmentText `
    -Name 'AURA_DB_PASSWORD' -Value (New-AuraRandomSecret)
$environmentText = Set-AuraEnvironmentValue -EnvironmentText $environmentText `
    -Name 'AURA_AUTH_PASSWORD' -Value (New-AuraRandomSecret)
$environmentText = Set-AuraEnvironmentValue -EnvironmentText $environmentText `
    -Name 'AURA_AI_INTERNAL_TOKEN' -Value (New-AuraRandomSecret)

$environmentStream = $null
$environmentWriter = $null
try {
    try {
        $environmentStream = [System.IO.File]::Open(
            $environmentPath,
            [System.IO.FileMode]::CreateNew,
            [System.IO.FileAccess]::Write,
            [System.IO.FileShare]::None
        )
    } catch [System.IO.IOException] {
        if (Test-Path -LiteralPath $environmentPath) {
            throw 'A .env file already exists. It was left unchanged.'
        }
        throw
    }

    $utf8WithoutBom = [System.Text.UTF8Encoding]::new($false)
    $environmentWriter = [System.IO.StreamWriter]::new($environmentStream, $utf8WithoutBom)
    $environmentWriter.Write($environmentText)
    $environmentWriter.Flush()
} catch {
    if ($environmentWriter -ne $null) {
        $environmentWriter.Dispose()
    } elseif ($environmentStream -ne $null) {
        $environmentStream.Dispose()
    }
    if ($environmentStream -ne $null -and (Test-Path -LiteralPath $environmentPath)) {
        Remove-Item -LiteralPath $environmentPath -Force -ErrorAction SilentlyContinue
    }
    throw
} finally {
    if ($environmentWriter -ne $null) {
        $environmentWriter.Dispose()
    } elseif ($environmentStream -ne $null) {
        $environmentStream.Dispose()
    }
}

Write-Output 'Created .env with separate cryptographically random database, API, and AI-service secrets.'
Write-Output 'Keep .env private and untracked. Add optional provider keys there, then start with docker compose up --build.'
