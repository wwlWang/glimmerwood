$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$manifest = Get-Content -LiteralPath (Join-Path $projectRoot 'dependencies/lock.json') -Raw | ConvertFrom-Json
foreach ($artifact in $manifest.artifacts) {
    if ([IO.Path]::GetFileName($artifact.filename) -ne $artifact.filename) { throw 'Invalid artifact filename' }
    $directory = Join-Path $projectRoot ('libs/' + $artifact.kind)
    New-Item -ItemType Directory -Path $directory -Force | Out-Null
    $destination = Join-Path $directory $artifact.filename
    if (Test-Path -LiteralPath $destination) {
        $existingHash = (Get-FileHash -LiteralPath $destination -Algorithm SHA512).Hash.ToLowerInvariant()
        if ($existingHash -eq $artifact.sha512) { Write-Output "Verified $($artifact.filename)"; continue }
        throw "Existing dependency differs from lock: $destination"
    }
    $temporary = $destination + '.partial'
    Invoke-WebRequest -Uri $artifact.url -OutFile $temporary -Headers @{ 'User-Agent'='OriMod development/0.1' }
    $downloadHash = (Get-FileHash -LiteralPath $temporary -Algorithm SHA512).Hash.ToLowerInvariant()
    if ($downloadHash -ne $artifact.sha512) { throw "Downloaded dependency hash mismatch: $temporary" }
    Move-Item -LiteralPath $temporary -Destination $destination
    Write-Output "Downloaded and verified $($artifact.filename)"
}
