# GestureLink

GestureLink turns the front camera of an Android phone into a private, local hand-gesture controller for Windows. Hand inference runs completely on the phone; only bounded gesture/control JSON is sent through a certificate-pinned TLS connection. No account, cloud, microphone, camera upload, advertising, or default analytics is used.

The product name is centralized in `android/app/.../AppConfig.kt`/`BuildConfig.PRODUCT_NAME` and `windows/GestureLink.Windows.Core/ProductInfo.cs` so it can be renamed without searching gesture logic.

## What is implemented

- Android 7+ native Kotlin/Compose app targeting API 37, CameraX 1.6.2 front preview and `KEEP_ONLY_LATEST` analysis.
- Local MediaPipe Tasks Gesture Recognizer 1.0.0 asset, LIVE_STREAM mode, two hands, CPU default, frame drop/inference diagnostics.
- Mirrored high-contrast preview overlay with 21 landmarks, palm center, gesture labels, spread/fist/zoom feedback.
- Stable hand tracks using position, palm size and handedness history; One-Euro filters and centralized gesture thresholds.
- Relative cursor, FingerSpread clutch/gain, short-fist click, hold-fist drag with forced release, left-hand scroll/right-click, two-pinch zoom and two-open-hand pause.
- Direct known-device control without a mandatory calibration wizard, explicit 3-2-1 arming and lifecycle safety stop.
- One-tap QR pairing using Google Code Scanner without borrowing the app camera permission.
- TLS 1.2/1.3 with exact SHA-256 certificate pinning; long-term Android credential encrypted by Android Keystore AES-GCM and metadata in DataStore.
- Minimal one-page .NET 10 WPF agent with a large pairing QR and status, ECDSA P-256 CurrentUser certificate, single-use 256-bit token, PBKDF2 device verifier, tray safety and emergency hotkey.
- Framed protocol reader (4-byte big-endian + bounded UTF-8 JSON), strict version/sequence/state checks, one active controller and 750 ms watchdog.
- Isolated `IInputInjector`/Win32 implementation with virtual desktop bounds, negative coordinates, button state tracking, wheel accumulation, allowlisted semantic keys and unconditional `ReleaseAll`.
- Shared fixtures, Kotlin tests, xUnit tests, CI workflows and self-contained portable Windows publishing.

## Repository

- `android/` — native Android app and tests
- `windows/` — WPF app, reusable Core and tests
- `protocol/` — normative v1 specification, schema and shared/replay fixtures
- `docs/` — architecture, calibration, security, testing and troubleshooting
- `scripts/` — repeatable local build, test and publish commands

## Prerequisites

- Android Studio/SDK 37 with build-tools 36.0.0 and JDK 17+
- .NET SDK 10.0.400+
- Windows 10 or 11 x64
- Android 7/API 24 or newer with a front camera
- Phone and PC on the same non-isolated private LAN

All dependency versions are pinned in `android/gradle/libs.versions.toml`; there is no `latest.release` dependency.

## Build and start Android

```powershell
cd android
.\gradlew.bat testDebugUnitTest lintDebug assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

The debug APK is `android/app/build/outputs/apk/debug/app-debug.apk`. On Xiaomi/HyperOS enable **Developer options → Install via USB**. Camera permission is explained and requested only when calibration/control starts. Android 17 requests `ACCESS_LOCAL_NETWORK` at runtime; older OS versions do not.

## Build and start Windows

```powershell
cd windows
dotnet restore GestureLink.Windows.sln
dotnet build GestureLink.Windows.sln -c Debug --no-restore
dotnet test GestureLink.Windows.sln -c Debug --no-build
dotnet run --project GestureLink.Windows.App
```

Portable single-file build:

```powershell
.\scripts\publish-windows.ps1
```

Run `artifacts/windows-win-x64/GestureLink.Windows.App.exe`. It requests no elevation. The hackathon PC has Smart App Control enabled and blocks every newly compiled unsigned binary. The development-only launcher below uses the already permitted .NET test host without disabling that Windows protection:

```powershell
.\scripts\run-windows-dev.ps1 -UsbTunnel
```

## Pair and control

1. Start the Windows agent; its only page shows the QR and connection status.
2. In Android tap **Los geht’s**, read the privacy/permission explanations, then **QR scannen und verbinden**.
3. Scan the Windows QR. Android validates local address, protocol version and exact certificate fingerprint before sending the one-time token.
4. The one-time QR is the confirmation; Windows pairs automatically and Android opens calibration.
5. Known devices open Live Control directly. Press **START** and wait for the 3-2-1 countdown.
6. Stop from Android, the Windows tray, or `Ctrl+Alt+Shift+G`. Every path releases held inputs.

Mouse profile gestures:

| Gesture | Action |
|---|---|
| Right open palm | Move pointer; spread controls clutch/precision/speed |
| Right fist | One right click |
| Left fist | One left click |
| Left open palm | Scroll |
| Two visible hands, distance changes | Zoom in/out |
| Both thumb-index pinches | Zoom |
| Both wide open, still for 1 s | Pause/resume |

## Safety and privacy limits

Windows input is accepted only after authentication and explicit arming. Reconnect always returns to `ConnectedNotArmed`. Disconnect, timeout, app background, hand loss during drag, profile stop and exit release every tracked mouse/key state. GestureLink intentionally cannot control the login screen, UAC secure desktop, or higher-integrity programs and does not attempt a bypass.

No camera image is saved or transmitted. The checked-in replay fixtures contain only synthetic normalized state data. Do not add real landmark recording without the explicit opt-in and time limit described in `docs/testing.md`.

## Verification status (2026-08-28)

- Android debug APK: built successfully.
- Android unit tests: 9 passed.
- Android lint: passed (warnings are reported but no errors).
- Windows Debug build: succeeded on .NET 10.
- Windows unit/contract tests: 13 passed, including the opt-in Smart App Control development launcher.
- Windows self-contained single-file publish: succeeded; the newly rebuilt unsigned binary is blocked locally by Smart App Control and needs signing for ordinary launch on this PC.
- Tested physical device: Xiaomi `25010PN30G`, Android 16/API 36; APK install, pinned TLS QR pairing, stored-device authentication, USB-tunnel connection and configuration-change persistence in portrait/landscape confirmed.
- Not yet claimed as validated: precise camera/landmark alignment, cursor feel, drag-loss timing on hardware, two-hand gestures, multi-monitor/DPI matrix, 15-minute thermal run, Android 7–17 matrix and Windows 10/11 matrix. These require the manual checklist in `docs/testing.md`.

## Troubleshooting

See `docs/troubleshooting.md`. The most common hackathon issue is guest-WLAN client isolation: discovery may see the other device while TCP still cannot connect. Use the same private hotspot or a LAN without AP isolation.

For a development-only USB tunnel, set `GESTURELINK_PAIR_HOST=127.0.0.1` when starting the Windows agent and run `adb reverse tcp:39827 tcp:39827`. The application protocol and TLS pinning remain unchanged; production use should stay on the direct private LAN.
