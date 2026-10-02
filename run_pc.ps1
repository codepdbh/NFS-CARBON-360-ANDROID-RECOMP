param(
    [switch]$DumpImage,
    [switch]$GpuDiagnostics,
    [ValidateSet('auto', 'rtv', 'rov')]
    [string]$RenderTargetPath = 'auto'
)
$ErrorActionPreference = 'Stop'
$carbonRoot = $PSScriptRoot
$carbonExe = Join-Path $carbonRoot 'out\pc\nfscarbon.exe'
$carbonDataRoot = (Resolve-Path -LiteralPath (Join-Path (Split-Path $carbonRoot -Parent) 'Need_for_Speed_Carbon')).ProviderPath
if (-not (Test-Path -LiteralPath $carbonExe)) { throw 'Build nfscarbon.exe first with build_pc.ps1' }
$runtimeRoot = Join-Path $carbonRoot 'out\runtime'
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
$carbonLog = Join-Path $runtimeRoot 'carbon.log'
if ($GpuDiagnostics) {
    $carbonSession = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $carbonLog = Join-Path $runtimeRoot "carbon-gpu-$carbonSession.log"
}
$carbonArgs = @(
    "--game_data_root=`"$carbonDataRoot`"",
    "--user_data_root=`"$runtimeRoot\user`"",
    "--cache_root=`"$runtimeRoot\cache`"",
    "--metadata_root=`"$runtimeRoot\metadata`"",
    "--log_file=`"$carbonLog`"",
    '--log_level=info',
    '--headless=true',
    '--mnk_mode=true',
    '--user_language=1',
    '--async_shader_compilation=false'
)
if ($DumpImage) { $carbonArgs += '--carbon_dump_image=true' }
if ($GpuDiagnostics) {
    $carbonArgs += '--d3d12_debug=true', '--d3d12_break_on_error=false', '--gpu_debug_markers=true'
}
if ($RenderTargetPath -ne 'auto') {
    $carbonArgs += "--render_target_path_d3d12=$RenderTargetPath"
}
# The game is interactive; launch a visible window for the local play test.
$carbonProcess = Start-Process -FilePath $carbonExe -ArgumentList $carbonArgs `
    -WorkingDirectory (Split-Path $carbonExe -Parent) -WindowStyle Normal -PassThru
$carbonProcess.Id | Set-Content -LiteralPath (Join-Path $runtimeRoot 'process-id.txt')
$carbonLog | Set-Content -LiteralPath (Join-Path $runtimeRoot 'current-log.txt')
Write-Output "Carbon test process: $($carbonProcess.Id)"
Write-Output "Log: $carbonLog"
