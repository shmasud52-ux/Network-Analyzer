# Network Analyzer — Native Android

A Kotlin + Jetpack Compose Android app for checking internet speed and cellular signal.

## Features
- Download and upload speed test using Cloudflare's public test endpoint
- HTTP latency measurement (shown as ping estimate)
- Live cellular signal reading through Android Telephony APIs
- LTE RSRP / RSRQ display where the phone and carrier expose them
- 360-degree compass heading with manual signal-strength samples
- Wi-Fi / Mobile Data connection display
- Local history of the latest 50 speed tests
- GitHub Actions workflow to build a debug APK

## Privacy and connectivity
- No paid API key, account, custom backend, or tower database is required.
- Speed tests send test traffic to Cloudflare. Internet access is required and mobile-data charges may apply.
- Test history is stored locally on the phone.
- The app does not locate cell towers or upload personal data.

## Measurement notes
- The displayed ping is HTTP response time to the Cloudflare test endpoint, not ICMP ping.
- Direction results are approximate. Indoor reflections, phone orientation, hand position, and network handover can affect readings.
- Android, carrier, and device restrictions may hide cellular information. Location and Phone permissions plus Location services may be required.
- Some devices may not expose 5G NR metrics to third-party apps.

## Build APK
1. Open the **Actions** tab.
2. Select **Build Android APK** and choose **Run workflow**, or push a commit to the main branch.
3. Open the successful workflow run and download the `NetworkAnalyzer-debug-apk` artifact.
4. Extract the artifact ZIP and install `app-debug.apk` on your Android phone.

Minimum Android version: Android 8.0 (API 26).
