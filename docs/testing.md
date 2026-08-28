# Testing

Run `scripts/verify-all.ps1`. JVM tests cover metrics, filtering and gesture timing; .NET tests cover framing, safety, input accumulation and state. Shared fixtures are parsed on both sides. Opt-in replay records normalized landmarks and states only, never camera frames.

Manual validation matrix: Android 7–17; Windows 10/11; one/two monitors including negative coordinates; 100/150/200% scaling; 0.5/1/1.5 m; difficult lighting/backgrounds and diverse hands; crossings/occlusion; Wi-Fi loss; app/agent termination during drag; and concurrent physical mouse input.

