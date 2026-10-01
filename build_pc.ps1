param(
    [switch]$ConfigureOnly,
    [switch]$SdkOnly,
    [switch]$EntryChecks,
    [string]$SdkRoot,
    [string]$LlvmBin,
    [string]$CMakeBin
)
$ErrorActionPreference = 'Stop'
$carbonRoot = $PSScriptRoot
$mwRoot = Join-Path (Split-Path $carbonRoot -Parent) 'nfsmw-android'
if (-not $SdkRoot) { $SdkRoot = Join-Path $mwRoot 'sdk' }
if (-not $LlvmBin) { $LlvmBin = Join-Path $mwRoot 'out\host-tools\llvm20\bin' }
if (-not $CMakeBin) { $CMakeBin = Join-Path $env:LOCALAPPDATA 'Android\Sdk\cmake\3.30.5\bin' }
foreach ($requiredPath in @((Join-Path $SdkRoot 'CMakeLists.txt'),
    (Join-Path $LlvmBin 'clang.exe'), (Join-Path $LlvmBin 'clang++.exe'),
    (Join-Path $CMakeBin 'cmake.exe'), (Join-Path $CMakeBin 'ninja.exe'))) {
    if (-not (Test-Path -LiteralPath $requiredPath)) { throw "Required build input missing: $requiredPath" }
}
$vswhere = Join-Path ${env:ProgramFiles(x86)} 'Microsoft Visual Studio\Installer\vswhere.exe'
$vsRoot = & $vswhere -latest -products '*' -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath
if (-not $vsRoot) { throw 'Visual Studio C++ tools missing' }
Import-Module (Join-Path $vsRoot 'Common7\Tools\Microsoft.VisualStudio.DevShell.dll')
Enter-VsDevShell -VsInstallPath $vsRoot -SkipAutomaticLocation -DevCmdArguments '-arch=x64 -host_arch=x64'
$env:PATH = "$llvmBin;$cmakeBin;$env:PATH"
$buildRoot = Join-Path $carbonRoot 'out\pc'
function Initialize-CarbonBuild {
    $ErrorActionPreference = 'Continue'
    & (Join-Path $cmakeBin 'cmake.exe') -S $carbonRoot -B $buildRoot -G Ninja `
    "-DCMAKE_MAKE_PROGRAM=$cmakeBin\ninja.exe" "-DCMAKE_C_COMPILER=$llvmBin\clang.exe" `
    "-DCMAKE_CXX_COMPILER=$llvmBin\clang++.exe" "-DREXSDK_DIR=$sdkRoot" `
    -DCMAKE_BUILD_TYPE=Release -DCMAKE_C_FLAGS=-march=x86-64-v2 -DCMAKE_CXX_FLAGS=-march=x86-64-v2
    if ($LASTEXITCODE -ne 0) { throw "CMake configure failed: $LASTEXITCODE" }
}
$ErrorActionPreference = 'Continue'
Initialize-CarbonBuild
if ($ConfigureOnly) { return }
if (-not $SdkOnly -and -not $EntryChecks) {
    # Complete code generation before inspecting or repairing its output.
    & (Join-Path $cmakeBin 'cmake.exe') --build $buildRoot --target nfscarbon_codegen --parallel 4
    if ($LASTEXITCODE -ne 0) { throw "Code generation failed: $LASTEXITCODE" }
    & python (Join-Path $carbonRoot 'tools\fix_local_branches.py')
    if ($LASTEXITCODE -ne 0) { throw 'Local branch repair failed' }
    if (Test-Path -LiteralPath (Join-Path $carbonRoot 'out\runtime\cache\carbon-82000000.bin')) {
        & python (Join-Path $carbonRoot 'tools\recover_small_entries.py')
        if ($LASTEXITCODE -ne 0) { throw 'Small entry recovery failed' }
        & python (Join-Path $carbonRoot 'tools\verify_small_entries.py')
        if ($LASTEXITCODE -ne 0) { throw 'Small entry verification failed' }
    }
    # Discover generated sources and optional recovered entries in fresh clones.
    Initialize-CarbonBuild
}
$targets = if ($EntryChecks) { @('carbon_entry_checks') } elseif ($SdkOnly) { @('rexruntime', 'rexgpu-xenos') } else { @('nfscarbon') }
& (Join-Path $cmakeBin 'cmake.exe') --build $buildRoot --target $targets --parallel 4
if ($LASTEXITCODE -ne 0) { throw "Build failed: $LASTEXITCODE" }
if ($EntryChecks) {
    & (Join-Path $buildRoot 'carbon_entry_checks.exe')
    if ($LASTEXITCODE -ne 0) { throw "Entry checks failed: $LASTEXITCODE" }
    return
}
if ($SdkOnly) {
    Write-Output "PC runtime and Xenos plugin built: $buildRoot\sdk-bin"
    return
}
Write-Output "PC executable: $buildRoot\nfscarbon.exe"
