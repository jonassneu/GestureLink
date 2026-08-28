# Windows agent

Build with `dotnet build GestureLink.Windows.sln` and test with `dotnet test GestureLink.Windows.sln`. Run without elevation. `IInputInjector` is separated from WPF and networking; `DryRunInputInjector` is available for safe protocol work.

Use `../scripts/publish-windows.ps1` for the self-contained win-x64 portable artifact. Trimming is intentionally disabled for WPF. The agent stores its ECDSA certificate in the CurrentUser certificate store and device verifier records under `%LOCALAPPDATA%\GestureLink`; private keys and plaintext credentials are not repository files.

