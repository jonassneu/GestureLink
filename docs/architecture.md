# Architecture

GestureLink is an offline LAN system. Android owns CameraX, MediaPipe inference, tracking, filtering, calibration and gesture state machines. It sends only bounded semantic control data through a pinned TLS client. Windows owns TLS authentication, session safety, UI and an isolated Win32 input injector. Camera frames never cross the device boundary.

Interfaces (`HandRecognizer`, `ControlTransport`, `IInputInjector`, clock abstractions) make hardware replaceable in tests. UI never calls Win32, camera code never serializes protocol messages, and both transports use a single ordered writer.

