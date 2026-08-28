# Android app

Open this directory in Android Studio or run `./gradlew testDebugUnitTest lintDebug assembleDebug`. The MediaPipe task model is checked in at `app/src/main/assets/gesture_recognizer.task`; camera frames remain inside the process. Release signing reads no repository secret—create a local `signing.properties` only when a real release key is available.

The analyzer uses 640×480, CameraX `KEEP_ONLY_LATEST`, a single executor and MediaPipe LIVE_STREAM. Every `ImageProxy` is closed in `finally`; only one inference is accepted at once. Pointer/scroll samples are conflated or accumulated by the one-writer TLS client while discrete and release events remain ordered.

