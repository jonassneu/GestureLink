# Security

The agent creates a self-signed ECDSA P-256 TLS certificate in the Windows CurrentUser certificate store. A 256-bit, three-minute, single-use QR token bootstraps pairing; scanning it is the user's pairing confirmation, so there is no second Windows approval click. Android pins the SHA-256 certificate fingerprint and encrypts its long-term device credential with an Android Keystore AES-GCM key. Windows stores only a PBKDF2-derived credential verifier plus salt. Releases never use plaintext or trust-all TLS.

Input commands are allowlisted and accepted only after authentication and explicit arming. Watchdog, disconnect, backgrounding, stop and every fault perform `ReleaseAll`. GestureLink cannot and does not bypass UAC, secure desktop, login screen or higher-integrity applications.
