param([switch]$UsbTunnel)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$testProject = Join-Path $repoRoot 'windows\GestureLink.Windows.Tests\GestureLink.Windows.Tests.csproj'

$env:GESTURELINK_RUN_APP = '1'
if ($UsbTunnel) {
    $env:GESTURELINK_PAIR_HOST = '127.0.0.1'
    & adb reverse tcp:39827 tcp:39827
} else {
    Remove-Item Env:GESTURELINK_PAIR_HOST -ErrorAction SilentlyContinue
}

dotnet test $testProject -c Release --filter 'FullyQualifiedName~DeveloperLauncherTests'
