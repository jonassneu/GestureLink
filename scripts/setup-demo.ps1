param([switch]$SkipBuild)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$androidRoot = Join-Path $repoRoot 'android'
$testProject = Join-Path $repoRoot 'windows\GestureLink.Windows.Tests\GestureLink.Windows.Tests.csproj'
$apk = Join-Path $androidRoot 'app\build\outputs\apk\debug\app-debug.apk'

foreach ($command in 'adb', 'dotnet') {
    if (-not (Get-Command $command -ErrorAction SilentlyContinue)) {
        throw "$command was not found in PATH. See README prerequisites."
    }
}

$devices = @(& adb devices | Select-Object -Skip 1 | Where-Object { $_ -match '\sdevice$' })
if ($devices.Count -ne 1) {
    throw "Expected exactly one authorized Android device, found $($devices.Count)."
}
$serial = ($devices[0] -split '\s+')[0]

if (-not $SkipBuild) {
    if (-not $env:JAVA_HOME) {
        $androidStudioJdk = 'C:\Program Files\Android\Android Studio\jbr'
        if (Test-Path $androidStudioJdk) { $env:JAVA_HOME = $androidStudioJdk }
    }
    Push-Location $androidRoot
    try { .\gradlew.bat testDebugUnitTest assembleDebug --no-daemon }
    finally { Pop-Location }

    dotnet test $testProject -c Release
}

if (-not (Test-Path $apk)) { throw "APK not found: $apk" }

& adb -s $serial reverse tcp:39827 tcp:39827
& adb -s $serial install -r $apk

$listener = Get-NetTCPConnection -State Listen -LocalPort 39827 -ErrorAction SilentlyContinue
if (-not $listener) {
    $env:GESTURELINK_RUN_APP = '1'
    $env:GESTURELINK_PAIR_HOST = '127.0.0.1'
    Start-Process -FilePath 'dotnet' -ArgumentList @(
        'test', $testProject, '-c', 'Release', '--no-build', '--no-restore',
        '--filter', 'FullyQualifiedName~DeveloperLauncherTests'
    ) -WorkingDirectory $repoRoot -WindowStyle Hidden
}

Start-Sleep -Seconds 3
& adb -s $serial shell am start -n de.gesturelink.app/.MainActivity
Write-Host 'GestureLink is running. Scan the Windows QR once; known devices reconnect automatically.' -ForegroundColor Green
