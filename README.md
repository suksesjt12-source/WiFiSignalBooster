# WiFi Signal Booster

Native Android Wi-Fi diagnostics and optimization assistant. Minimum supported version: Android 8.0 (API 26).

## Features

- Active network and Android internet-validation status.
- Wi-Fi RSSI, frequency band, link rate, and router gateway details where Android exposes them.
- User-initiated nearby access-point scan and estimated 2.4 GHz channel recommendation.
- 5 GHz and 6 GHz access-point counts when reported by the device.
- Four HTTPS connectivity probes with average response time, response variation, and failed-probe percentage.
- Shortcuts to Android Wi-Fi settings, Private DNS settings, and the local router admin page.
- Practical guidance for signal placement, Wi-Fi band selection, channel planning, firmware, and router QoS.
- GitHub Actions workflow to build and upload a debug APK.

## Technical limits

The app cannot increase physical antenna power, override Android's Wi-Fi radio controls, force an access-point roam, or silently change router settings. Android restricts Wi-Fi scans and access to connection details. The app opens system or router settings so the network owner can apply changes manually.

Channel recommendations are estimates based only on access points returned by the latest scan. Android can cache or throttle scan results, and a phone scan cannot measure every source of interference. The connection test uses HTTPS requests, not raw ICMP ping; DNS, TLS, server response, and the route affect its timings.

## Build APK with GitHub Actions

1. Open the repository's **Actions** tab.
2. Select **Build Android APK** and choose **Run workflow**, or push to `main`.
3. When the run succeeds, open it and download the `WiFiSignalBooster-debug-apk` artifact.
4. Extract the artifact ZIP and install `app-debug.apk`.

Build environment: Java 17, Android Gradle Plugin 9.4.0, Gradle 9.6.0, Android SDK Platform 37, Build Tools 36.0.0.

## Permissions

The app asks for fine-location and nearby-Wi-Fi permissions only when the user starts a scan. These permissions are required for Wi-Fi scan results on supported Android versions. Internet and network-state permissions support connectivity diagnostics.
