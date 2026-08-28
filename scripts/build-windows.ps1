$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
dotnet restore "$root\windows\GestureLink.Windows.sln"
dotnet build "$root\windows\GestureLink.Windows.sln" -c Debug --no-restore
dotnet test "$root\windows\GestureLink.Windows.sln" -c Debug --no-build

