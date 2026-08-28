# Progress

## 2026-08-28

- Repository and protocol v1 initialized.
- Official stable dependency/toolchain versions pinned.
- Hardware status: phone was not yet enumerated by Windows/ADB at initial inspection.
- Phase A: protocol, pinned Android toolchain, CameraX/MediaPipe/overlay, fixtures, CI, APK, nine unit tests and lint complete.
- Phase B: WPF shell/tray, TLS server, ECDSA certificate, QR token, Android pinned TLS client, Keystore credential and authentication paths implemented.
- Phase C: Win32 cursor/buttons/wheel/semantic input, virtual desktop clamping, ReleaseAll, session safety, watchdog and emergency hotkey implemented.
- Phase D: spread gain/clutch, click/drag/scroll/right-click/zoom/pause engines, tracking/filter tests and replay fixtures implemented.
- Phase E: large-format live UI, explicit countdown/stop, privacy screens and eight-stage calibration flow implemented; calibration sampling/persistence needs hardware tuning.
- Phase F/G partial: Windows dashboard/devices/settings/diagnostics, multi-monitor bounds and portable publish implemented. Presentation/media gesture generation, discovery/HUD and the full manual hardware matrix remain unvalidated.
- Builds: Android APK + lint successful; Windows .NET 10 build + self-contained single-file publish successful. Tests: Android 9/9, Windows 13/13.
- Hardware: Xiaomi 25010PN30G on Android 16/API 36 detected over USB. APK install, automatic stored-device authentication, pinned TLS over ADB reverse, portrait/landscape state retention and real Windows cursor movement were confirmed. Click/drag and long-run gesture quality still need manual tuning.
- UX simplification: Windows now has a single QR/status page, a valid one-time QR pairs without a second approval prompt, known devices reuse their credential with the fresh QR endpoint, and Android connects immediately after scanning. Compose navigation, pairing and calibration state survive configuration changes such as device rotation.
- Gesture UX revision: calibration was removed; right fist maps to right click, left fist to left click, finger spread continuously changes cursor sensitivity from low to very high, and the distance between two visible hands controls zoom.
