# Wireless Drive — Android App

This Android application is a client for the Wireless Drive API. It acts as a media gallery and uploader that connects to the server to store, stream and manage media on your local network.

> Wireless Drive API: https://github.com/AlexisCarvalho/golang-api-website-wireless-drive

## Key Features
- User authentication: login and register flows with JWT token storage.
- Browse media: grid/list views with pagination and search.
- Upload files: multipart uploads with progress and ContentResolver streaming.
- Download files: obtain short-lived stream URLs from the API and save to device with progress.
- Streaming playback: audio and video streaming using ExoPlayer with an authenticated data source; background audio supported via a foreground service.
- Thumbnail management: request server-side thumbnail generation or deletion; UI shows missing thumbnails and can batch-generate them.
- Batch operations: select multiple items to download, delete, or generate thumbnails.
- Session and token management: automatic header injection and session-expiry handling.

## Permissions
The app declares the following Android permissions in the manifest:

- `android.permission.INTERNET`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- `android.permission.WAKE_LOCK`
- `android.permission.POST_NOTIFICATIONS`

You can inspect the manifest at [app/src/main/AndroidManifest.xml](app/src/main/AndroidManifest.xml#L1-L40).

## Configuration
- API base URL: the client default is configured in [app/src/main/java/dev/alexis/wirelessdrive/network/ApiConfig.kt](app/src/main/java/dev/alexis/wirelessdrive/network/ApiConfig.kt). Update this value if your server runs on a different IP or port.
- The app expects the Wireless Drive API server to be available on your local network.

## Building & Running
1. Open the project in Android Studio (recommended).
2. Ensure you have an Android SDK and a device/emulator available.
3. Configure the API base URL if needed (see `ApiConfig.kt`).
4. Run the app on a device or emulator.

For development you can also build from the command line with Gradle:

```bash
./gradlew assembleDebug
./gradlew installDebug
```

## Important Files
- Network client and API interfaces: [app/src/main/java/dev/alexis/wirelessdrive/network/](app/src/main/java/dev/alexis/wirelessdrive/network/)
- App entry and DI: [app/src/main/java/dev/alexis/wirelessdrive/WirelessDriveApplication.kt](app/src/main/java/dev/alexis/wirelessdrive/WirelessDriveApplication.kt)
- Main UI and navigation: [app/src/main/java/dev/alexis/wirelessdrive/MainActivity.kt](app/src/main/java/dev/alexis/wirelessdrive/MainActivity.kt)
- Playback service: [app/src/main/java/dev/alexis/wirelessdrive/playback/PlaybackService.kt](app/src/main/java/dev/alexis/wirelessdrive/playback/PlaybackService.kt)

## Server / API
This Android client consumes the Wireless Drive API. The API repository contains full build and deployment steps (including optional FFmpeg builds and Android deployment). Make sure the server is running and reachable from your device on the same network.

## Troubleshooting
- If playback fails, check that the app can reach the API and that the stream URLs are valid.
- If thumbnails are missing, ensure the server has FFmpeg configured or a Thumbnail API endpoint set.