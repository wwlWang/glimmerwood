param([string] $Action)
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$connection = Get-Content -LiteralPath (Join-Path $projectRoot 'run-devtools/devtools/connection.json') -Raw | ConvertFrom-Json
$headers = @{ 'X-Ori-Devtools'=$connection.token }
if ($Action) {
    $result = Invoke-RestMethod -Method Post -Uri ($connection.url + '/action') -Headers $headers -ContentType 'application/json' -Body ([Text.Encoding]::UTF8.GetBytes($Action)) -TimeoutSec 50
} else {
    $result = Invoke-RestMethod -Uri ($connection.url + '/state') -Headers $headers -TimeoutSec 15
}
$result | ConvertTo-Json -Depth 12
