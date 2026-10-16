$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$manifest = Get-Content -LiteralPath (Join-Path $projectRoot 'dependencies/lock.json') -Raw | ConvertFrom-Json
$renderer = $manifest.sourceBuilds | Where-Object project -eq 'GemRender' | Select-Object -First 1
$checkout = Join-Path $projectRoot '.external/GemRender'
if (-not (Test-Path -LiteralPath $checkout)) {
    git clone $renderer.source $checkout
    if ($LASTEXITCODE -ne 0) { throw 'Renderer clone failed' }
    git -C $checkout checkout --detach $renderer.commit
    if ($LASTEXITCODE -ne 0) { throw 'Renderer checkout failed' }
}
$revision = git -C $checkout rev-parse HEAD
if ($revision -ne $renderer.commit) { throw 'Renderer checkout does not match the locked revision' }
if (git -C $checkout status --porcelain --untracked-files=no) { throw 'Renderer checkout contains edits; preserve them and use a clean locked checkout' }
$socketDirectory = Join-Path $projectRoot '.gradle/socket-tmp'
New-Item -ItemType Directory -Path $socketDirectory -Force | Out-Null
$previousOptions = $env:JAVA_TOOL_OPTIONS
Push-Location $checkout
try {
    if ($previousOptions -notmatch 'jdk\.net\.unixdomain\.tmpdir=') {
        $env:JAVA_TOOL_OPTIONS = ($previousOptions + ' "-Djdk.net.unixdomain.tmpdir=' + $socketDirectory + '"').Trim()
    }
    # Only configure our MC target. The upstream multi-loader build otherwise downloads
    # unrelated Minecraft installations. Pin JarJar as well as compile-time Flywheel.
    & .\gradlew.bat :1.21.1:jar --configure-on-demand '-Pflywheel_version_range=1.0.6' --console=plain @args
    if ($LASTEXITCODE -ne 0) { throw "Renderer build failed: $LASTEXITCODE" }
}
finally {
    $env:JAVA_TOOL_OPTIONS = $previousOptions
    Pop-Location
}
Add-Type -AssemblyName System.IO.Compression.FileSystem
$candidates = @(Get-ChildItem (Join-Path $checkout 'versions/1.21.1/build/libs') -Filter '*.jar' | Where-Object Name -NotMatch 'sources|javadoc')
if ($candidates.Count -ne 1) { throw 'Expected one renderer artifact' }
$runtimeDir = Join-Path $projectRoot 'libs/runtime'
$compileDir = Join-Path $projectRoot 'libs/compile'
New-Item -ItemType Directory -Path $runtimeDir,$compileDir -Force | Out-Null
$destination = Join-Path $runtimeDir $candidates[0].Name
Copy-Item -LiteralPath $candidates[0].FullName -Destination $destination -Force
$archive = [IO.Compression.ZipFile]::OpenRead($destination)
try {
    $flywheel = @($archive.Entries | Where-Object FullName -Match 'META-INF/jarjar/.*flywheel.*\.jar$')
    if ($flywheel.Count -ne 1) { throw 'Expected one bundled Flywheel jar' }
    [IO.Compression.ZipFileExtensions]::ExtractToFile($flywheel[0], (Join-Path $compileDir $flywheel[0].Name), $true)
}
finally { $archive.Dispose() }
Write-Output "Renderer prepared: $destination"
Get-FileHash -LiteralPath $destination -Algorithm SHA512 | Format-List
