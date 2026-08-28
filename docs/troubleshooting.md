# Troubleshooting

- No ADB device: choose File Transfer USB mode, accept the RSA dialog, install the OEM ADB driver, then run `adb kill-server; adb start-server; adb devices`.
- Pairing cannot connect: both devices must be on the same non-isolated LAN; allow the CurrentUser Windows firewall prompt; confirm the displayed private IP.
- Development-only fallback with a USB-debug device: run `adb reverse tcp:39827 tcp:39827` and start the Windows agent with `GESTURELINK_PAIR_HOST=127.0.0.1`; this preserves TLS/pinning but is not the normal product route.
- Smart App Control blocks a fresh unsigned hackathon build: do not disable Windows protection. Run `scripts\run-windows-dev.ps1 -UsbTunnel` on this development PC, or code-sign the portable release for distribution.
- UAC/admin app is not controlled: this is an intentional Windows security boundary.
- Tracking is slow: use CPU/Auto, improve lighting, keep the whole palm visible and reduce distance. GPU is optional and device-dependent.
- Drag uncertainty: press Ctrl+Alt+Shift+G or use tray **Release All**.
