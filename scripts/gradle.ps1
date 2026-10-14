# Keep the Windows JDK socket workaround local to this command.
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$socketDirectory = Join-Path $projectRoot '.gradle/socket-tmp'
New-Item -ItemType Directory -Path $socketDirectory -Force | Out-Null
$previousOptions = $env:JAVA_TOOL_OPTIONS
$gradleArguments = $args
if ($gradleArguments.Count -eq 0) { $gradleArguments = @('build') }
$buildExitCode = 1

Push-Location $projectRoot
try {
    if ($previousOptions -notmatch 'jdk\.net\.unixdomain\.tmpdir=') {
        $env:JAVA_TOOL_OPTIONS = ($previousOptions + ' "-Djdk.net.unixdomain.tmpdir=' + $socketDirectory + '"').Trim()
    }
    & .\gradlew.bat @gradleArguments
    $buildExitCode = $LASTEXITCODE
}
finally {
    $env:JAVA_TOOL_OPTIONS = $previousOptions
    Pop-Location
}
exit $buildExitCode
