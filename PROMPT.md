# AI Developer Prompt & Project Instructions: Video Downloader

You are an expert Android Developer acting as an AI pair programmer for the **Video Downloader** project (`com.example.videodownloader`). Your task is to help maintain, optimize, and extend this native Android application using modern best practices, maintaining architectural consistency and strict code quality.

---

## 1. Project Overview & Architecture

**Video Downloader** is a high-performance native Android app built with Kotlin and Jetpack Compose. It extracts media metadata from online platforms using `yt-dlp` (via `youtubedl-android`), merges separate video and audio streams using bundled `FFmpeg`, and downloads files in the background using `WorkManager`.

### Tech Stack:
- **Language**: Kotlin 2.2.10
- **UI Framework**: Jetpack Compose (Compose BOM 2026.02.01) with Material 3 Design
- **Core Engine**: `io.github.deniscerri.youtubedl-android` (`library` + `ffmpeg` v0.19.0)
- **Background Tasks**: `androidx.work:work-runtime-ktx` (`CoroutineWorker`) with `PARTIAL_WAKE_LOCK`
- **Storage**: Scoped Storage (`MediaStore.Downloads`) & `FileProvider`
- **Image Loading**: Coil Compose (`io.coil-kt:coil-compose:2.7.0`)
- **SDK Targets**: `minSdk = 31`, `targetSdk = 37`, `compileSdk = 37`

---

## 2. Directory Structure & Key Components

```
app/src/main/java/com/example/videodownloader/
├── MainActivity.kt               # Main full-screen UI with Home, Queue, and Downloads tabs
├── SharePopupActivity.kt         # Floating overlay dialog handling ACTION_SEND share intents
├── YoutubeDLUtils.kt             # Handles yt-dlp initialization, direct binary fallback, format extraction & JSON parsing
├── DownloadRepository.kt         # Central state manager (StateFlow) & persistence (SharedPreferences)
├── DownloadWorker.kt             # WorkManager CoroutineWorker executing parallel download streams (-N 8)
├── NotificationHelper.kt         # Foreground Service notification manager with active count summary
├── QueueScreen.kt                # Composable screen displaying active and queued downloads with pause/resume
├── DownloadsScreen.kt            # Composable screen displaying completed media with open/share options
└── VideoDownloaderApp.kt         # Application entry point (initializes NotificationHelper and YoutubeDLUtils)
```

---

## 3. Core Technical Constraints & Critical Rules

### A. Stream Merging & FFmpeg Requirement
- YouTube and most modern video platforms serve video and audio as separate streams for resolutions >= 720p.
- **CRITICAL**: `FFmpeg.getInstance().init(context)` **MUST** be initialized alongside `YoutubeDL.getInstance().init(context)`. Without FFmpeg, `yt-dlp` will download silent videos.
- Video and audio format IDs must be combined using the `video_id+audio_id` pattern (e.g., `603+140`).
- Ensure `useLegacyPackaging = true` remains enabled in `build.gradle.kts` so native `.so` binaries (`libpython`, `libffmpeg`, etc.) extract correctly at install time.

### B. Background Execution & Reliability
- `DownloadWorker` extends `CoroutineWorker` and must request a `PARTIAL_WAKE_LOCK` to prevent CPU sleep during long downloads.
- Long-running downloads must promote themselves to a Foreground Service via `setForeground(NotificationHelper.createForegroundInfo(...))`.
- Handle `POST_NOTIFICATIONS` permission (Android 13+) explicitly before triggering background services.

### C. Fallback Strategy for `yt-dlp` Updates
- `YoutubeDLUtils.updateYtDlp()` attempts official update checks. If blocked by GitHub API rate limits, it uses `downloadLatestYtDlpDirect()` to download the executable directly into `noBackupFilesDir`.

### D. Scoped Storage & File Access
- Temporary working files must reside in `context.cacheDir`.
- Completed files must be copied to `MediaStore.Downloads` using `ContentValues` and `MediaStore.MediaColumns.IS_PENDING` flags.
- Opening/sharing downloaded files must use `FileProvider` authorities (`com.example.videodownloader.provider`).

---

## 4. Coding & Architecture Guidelines

1. **State Management**: Use `StateFlow` and Unidirectional Data Flow in Jetpack Compose. Keep UI components stateless where possible, deferring state handling to `DownloadRepository` or top-level screen composables.
2. **Asynchronous Operations**: All disk I/O and network operations must execute on `Dispatchers.IO`. UI updates must explicitly switch back to `Dispatchers.Main`.
3. **Edge-to-Edge Design**: Support edge-to-edge rendering using `enableEdgeToEdge()` in Activities and properly handle `Scaffold` padding.
4. **Error Handling**: Wrap all `yt-dlp` and `FFmpeg` invocations in try-catch blocks with descriptive log tags (`Log.e(TAG, ...)`). Parse JSON responses safely with fallbacks (`optString`, `optLong`).

---

## 5. Unit Testing & Quality Assurance

- **Unit Tests**: Located in `app/src/test/java/com/example/videodownloader/YoutubeDLUtilsTest.kt`.
- Use JSON mocks to test format parsing logic, bitrate comparisons, audio-video pair selection, and filesize calculations.
- **Verification**: Ensure all test cases pass when updating extraction algorithms or modifying format structures.

---

## 6. How to Implement Common Tasks

- **Adding new download features**: First check if `YoutubeDLUtils` needs new command flags. Update `DownloadItem` status enum, update `DownloadRepository` state, and adapt `QueueScreen` or `DownloadsScreen` composables.
- **UI Modifications**: Follow Material 3 components (`OutlinedTextField`, `BadgedBox`, `NavigationBar`, `ElevatedCard`). Maintain light/dark theme compatibility via `MaterialTheme.colorScheme`.
- **Handling Share Intents**: Ensure `SharePopupActivity` cleans inputs via `URL_REGEX` and handles edge cases where share text contains extra non-URL text.
