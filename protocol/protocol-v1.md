# GestureLink Protocol Version 1

Protocol v1 runs over TLS (minimum TLS 1.2) on a direct LAN TCP connection. Every UTF-8 JSON message is prefixed by an unsigned 4-byte big-endian length. The maximum JSON length is 65,536 bytes. Images, audio and raw key codes are not protocol data.

## Envelope

```json
{"version":1,"type":"pointerDelta","sessionId":"uuid","sequence":123,"payload":{"dx":0.0012,"dy":-0.0008,"confidence":0.94,"sourceFrame":1204}}
```

Fields use camelCase. `version`, `type`, `sessionId`, `sequence`, and object `payload` are mandatory. Sequence numbers are strictly increasing independently per direction. Receivers reject malformed JSON, non-finite numbers, lengths above 64 KiB, commands before authentication, and commands outside an Armed session. Unknown message types receive `error/unknownType`; an unsupported version receives `error/unsupportedVersion`.

## Lifecycle and security

1. `clientHello` / `serverHello` negotiate version 1.
2. First use sends `pairRequest` with the one-time QR token. Scanning that short-lived visual token is the explicit pairing action; Windows replies `pairAccepted` without a second confirmation dialog. The token is single-use and expires after 180 seconds.
3. Later use sends `authenticate` over the exactly pinned TLS connection. A device ID plus 256-bit credential is verified using a constant-time comparison against the protected server record.
4. `startControl` is accepted only from ConnectedNotArmed and moves to Armed. Stop, watchdog, disconnect, or fault invokes `releaseAll` and never auto-resumes.
5. Only one authenticated controller can be active.

The QR URI is `gesturelink://pair?v=1&host=<private-ip>&port=39827&serverId=<uuid>&fingerprint=<sha256-hex>&token=<base64url>`. Clients accept only loopback, link-local, RFC1918 IPv4, or local IPv6 targets and compare the certificate SHA-256 fingerprint byte-for-byte. Trust-all validation is forbidden.

## Message types

| Type | Direction | Payload / effect |
|---|---|---|
| `clientHello`, `serverHello` | both | names, IDs, supported versions |
| `pairRequest` | A→W | `token`, `clientId`, `deviceName` |
| `pairAccepted`, `pairRejected` | W→A | credential or reason |
| `authenticate`, `authenticated` | A→W / W→A | client credential / status |
| `screenInfo` | W→A | virtual bounds and scale |
| `startControl`, `stopControl` | A→W | requested profile / reason |
| `pauseControl`, `resumeControl` | both | reason |
| `heartbeat`, `ping`, `pong` | both | monotonic timestamps |
| `pointerDelta` | A→W | `dx`,`dy` ∈ [-0.25,0.25], `confidence` ∈ [0,1], `sourceFrame` |
| `mouseClick` | A→W | `button`: left/right/middle |
| `mouseButton` | A→W | `button`, `down` |
| `scroll` | A→W | `vertical`,`horizontal` ∈ [-20,20] |
| `semanticAction` | A→W | allowlisted `action` |
| `gestureState` | A→W | UI-only labels and confidence |
| `releaseAll` | both | reason |
| `status`, `error`, `disconnect` | both | state or sanitized reason |

Allowlisted semantic actions are `leftClick`, `rightClick`, `middleClick`, `beginLeftDrag`, `endLeftDrag`, `scrollVertical`, `scrollHorizontal`, `zoomIn`, `zoomOut`, `presentationNext`, `presentationPrevious`, `mediaPlayPause`, `mediaNext`, `mediaPrevious`, `volumeUp`, `volumeDown`, `toggleMute`, `pauseControl`, `resumeControl`, and `releaseAll`. `gestureState` never causes input injection.

## Backpressure

There is exactly one ordered writer per direction. Pointer and scroll values are accumulated and emitted at no more than 60 Hz; they never form an unbounded stale queue. Button-up, `releaseAll`, stop, and pause are safety-priority reliable messages. Other discrete events use a bounded reliable FIFO.
