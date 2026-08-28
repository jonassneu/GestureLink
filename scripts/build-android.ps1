param([switch]$Install)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
Push-Location "$root\android"
try {
  .\gradlew.bat testDebugUnitTest lintDebug assembleDebug
  if ($Install) { .\gradlew.bat installDebug }
} finally { Pop-Location }
