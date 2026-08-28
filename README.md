# GestureLink

Control a Windows PC with hand movements captured by an Android phone's front camera.

Hand detection runs on the phone. Camera frames are never sent to Windows; the PC receives only encrypted cursor, click, scroll, zoom, and status messages over the local connection.

## Demo setup

Requirements:

- Windows 10/11
- .NET 10 SDK
- Android Studio or Android SDK 37 with JDK 17+
- Android 7+ phone with USB debugging enabled
- `adb` available in `PATH`

Connect one authorized Android phone, then run from the repository root:

```powershell
.\scripts\setup-demo.ps1
```

For subsequent starts without rebuilding:

```powershell
.\scripts\setup-demo.ps1 -SkipBuild
```

The script builds and tests both applications, installs the APK, creates an `adb reverse` tunnel on port `39827`, starts the Windows agent, and opens GestureLink on the phone.

On first use, scan the QR shown by Windows. The QR itself confirms pairing; there is no second approval dialog. Known phones reconnect automatically.

## Use

Keep GestureLink visible on the phone and press **START**. Leaving the app, losing the connection, or stopping control immediately releases simulated input.

| Gesture | Result |
|---|---|
| Move one visible hand | Move cursor |
| Fingers close together | Low cursor sensitivity |
| Fingers spread wide | Very high cursor sensitivity |
| Hold right fist | Hold right mouse button until the hand opens |
| Hold left fist | Hold left mouse button until the hand opens; move to draw or drag |
| Move two visible hands apart/together | Zoom in/out |
| Left open hand when the right is also visible | Scroll |

A fist holds its mouse button down. Opening the hand, losing tracking, stopping control, or disconnecting releases it.

## Normal LAN mode

The USB tunnel is only a development fallback for guest Wi-Fi with client isolation. For normal local-network use, start the Windows project without `-UsbTunnel`, put both devices on the same private LAN, and scan the generated QR.

```powershell
dotnet run --project .\windows\GestureLink.Windows.App
```

Windows listens on TCP `39827` with TLS 1.2 or newer. Android pins the exact certificate fingerprint from the QR code.

## Build separately

Android:

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

Windows:

```powershell
.\scripts\build-windows.ps1
.\scripts\publish-windows.ps1
```

The portable Windows output is written to `artifacts/windows-win-x64`. This hackathon laptop has Smart App Control enabled, so its freshly built unsigned executable is blocked locally. `setup-demo.ps1` uses the permitted .NET test host without disabling Windows security. A distributed release should be code-signed.

## Privacy and safety

- No cloud account or external server
- No microphone permission
- No camera recording or camera upload
- Credentials protected by Android Keystore and a hashed Windows verifier
- Length-bounded, versioned JSON over certificate-pinned TLS
- `ReleaseAll` on stop, disconnect, watchdog timeout, app background, or exit
- No attempt to control UAC, the login screen, or higher-integrity applications

## Current status

Validated on a Xiaomi `25010PN30G` running Android 16/API 36:

- secure QR pairing and stored-device reconnect
- USB/TLS connection
- portrait/landscape recreation
- real Windows cursor movement
- Android unit tests: 12 passing
- Windows unit/contract tests: 13 passing

Still needs broader hardware testing: click/zoom tuning, multi-monitor and DPI combinations, long thermal runs, Android 7–17, and Windows 10/11 coverage.

## Repository layout

- `android/` — Kotlin, Compose, CameraX, MediaPipe
- `windows/` — .NET 10 WPF agent, protocol server, Win32 input
- `protocol/` — protocol v1, schema, and shared fixtures
- `docs/` — architecture, security, testing, and troubleshooting
- `scripts/` — build, publish, verification, and demo setup

Product naming is centralized in Android `AppConfig`/`BuildConfig` and Windows `ProductInfo`.

License: MIT.
