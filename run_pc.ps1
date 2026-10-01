param([switch]$DumpImage)
$ErrorActionPreference = 'Stop'
$carbonRoot = $PSScriptRoot
$carbonExe = Join-Path $carbonRoot 'out\pc\nfscarbon.exe'
$carbonDataRoot = (Resolve-Path -LiteralPath (Join-Path (Split-Path $carbonRoot -Parent) 'Need_for_Speed_Carbon')).ProviderPath
if (-not (Test-Path -LiteralPath $carbonExe)) { throw 'Build nfscarbon.exe first with build_pc.ps1' }
$runtimeRoot = Join-Path $carbonRoot 'out\runtime'
New-Item -ItemType Directory -Path $runtimeRoot -Force | Out-Null
$carbonArgs = @(
    "--game_data_root=`"$carbonDataRoot`"",
    "--user_data_root=`"$runtimeRoot\user`"",
    "--cache_root=`"$runtimeRoot\cache`"",
    "--metadata_root=`"$runtimeRoot\metadata`"",
    "--log_file=`"$runtimeRoot\carbon.log`"",
    '--log_level=info',
    '--headless=true',
    '--mnk_mode=true',
    '--user_language=1',
    '--async_shader_compilation=false'
)
if ($DumpImage) { $carbonArgs += '--carbon_dump_image=true' }
# The game is interactive; launch a visible window for the local play test.
$carbonProcess = Start-Process -FilePath $carbonExe -ArgumentList $carbonArgs `
    -WorkingDirectory (Split-Path $carbonExe -Parent) -WindowStyle Normal -PassThru
$carbonProcess.Id | Set-Content -LiteralPath (Join-Path $runtimeRoot 'process-id.txt')
Write-Output "Carbon test process: $($carbonProcess.Id)"
Write-Output "Log: $runtimeRoot\carbon.log"
