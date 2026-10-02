param([string]$SdkRoot)
$ErrorActionPreference = 'Stop'
$repoRoot = $PSScriptRoot
$wrapper = Join-Path $repoRoot 'android\gradlew.bat'
if (-not (Test-Path -LiteralPath (Join-Path $repoRoot 'generated\default\nfscarbon_pch.h')) -or
    -not (Test-Path -LiteralPath (Join-Path $repoRoot 'generated\carbon_recovered_thunks.cpp'))) {
    throw 'Prepare the PC code generation and local entry recovery described in README.md before building Android.'
}
& python (Join-Path $repoRoot 'tools\verify_registration_maps.py')
if ($LASTEXITCODE -ne 0) { throw 'Android registration map verification failed.' }
$carbonGradleArguments = @('assembleRelease')
if ($SdkRoot) {
    $SdkRoot = (Resolve-Path -LiteralPath $SdkRoot).ProviderPath
    $carbonGradleArguments += "-PrexSdkDir=$SdkRoot"
}

if (-not $env:JAVA_HOME) {
    $studioJbr = Join-Path $env:ProgramFiles 'Android\Android Studio\jbr'
    if (Test-Path -LiteralPath (Join-Path $studioJbr 'bin\java.exe')) {
        $env:JAVA_HOME = $studioJbr
    }
}
if (-not (Get-Command java -ErrorAction SilentlyContinue) -and
    -not ($env:JAVA_HOME -and (Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin\java.exe')))) {
    throw 'Java 17 or newer is required. Install a JDK and ensure java is on PATH.'
}
if (-not (Test-Path -LiteralPath $wrapper)) {
    throw 'Gradle wrapper is missing from android\gradlew.bat.'
}
if (-not $env:ANDROID_HOME) {
    $sdk = Join-Path $env:LOCALAPPDATA 'Android\Sdk'
    if (Test-Path -LiteralPath $sdk) { $env:ANDROID_HOME = $sdk }
}
if ($env:ANDROID_HOME) {
    $env:ANDROID_SDK_ROOT = $env:ANDROID_HOME
}

Push-Location (Join-Path $repoRoot 'android')
try {
    # Gradle and javac print warnings on stderr; with 'Stop' PowerShell would abort on the first one.
    $ErrorActionPreference = 'Continue'
    & $wrapper @carbonGradleArguments
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
    $apk = Join-Path $repoRoot 'android\app\build\outputs\apk\release\app-release.apk'
    Write-Host "APK: $apk"
} finally {
    Pop-Location
}
# Native warnings on stderr must not turn a successful Gradle build into exit 1.
exit 0
