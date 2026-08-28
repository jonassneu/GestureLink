$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$out = "$root\artifacts\windows-win-x64"
dotnet publish "$root\windows\GestureLink.Windows.App\GestureLink.Windows.App.csproj" -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true -p:IncludeNativeLibrariesForSelfExtract=true -p:PublishTrimmed=false -o $out
Compress-Archive -Path "$out\*" -DestinationPath "$root\artifacts\GestureLink-Windows-win-x64.zip" -Force
